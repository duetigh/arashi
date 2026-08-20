# Arashi party relay server

Standalone WebSocket relay that routes party invites, roster updates, and shared lobby-cache
events between mod clients. Deliberately **not** part of the Fabric mod's build - this is its
own Gradle project, built and deployed independently.

In-memory only: a restart clears all party/invite state. Identity is self-reported by the
client (no Mojang session verification) - acceptable since this only shares party visibility
and lobby-cache data, nothing sensitive.

## Build

```
cd server
./gradlew shadowJar
```

Produces `server/build/libs/arashi-party-server.jar`, a self-contained fat jar. Run locally with:

```
java -jar build/libs/arashi-party-server.jar
```

Listens on port `8887` by default; override with the `ARASHI_PARTY_PORT` environment variable.

## Deploying to Oracle Cloud Free Tier ("Always Free")

This is the recommended host: genuinely free forever (no trial expiry, no idle-timeout), unlike
Render/Fly.io's free tiers which sleep or now require a card on file. If Oracle signup or card
verification fails for you, Google Cloud's `e2-micro` Always Free tier (specific US regions) is
the next-best genuinely-free-forever fallback.

1. **Availability domain matters.** Check the warning banner on the instance-creation page - in a
   fresh tenancy the Always Free shapes are often only available in *one specific* AD (e.g.
   `VM.Standard.E2.1.Micro` only in AD 3), not all three. Picking the wrong AD is the first thing
   that silently breaks shape selection.
2. **Image catalog quirk.** On a brand-new trial tenancy, the platform image catalog (Oracle
   Linux, Ubuntu) can come back empty ("No items to display") in the wrong AD/compartment
   combination even though the images exist - this resolved itself once the correct AD was
   selected. If it persists, a Marketplace-listed distro (AlmaLinux, Rocky) works too, but avoid
   any listing whose Security column shows "Shielded instance"/"Confidential computing" baked in -
   those need hardware the Always Free shapes don't have and shape selection will fail with "No
   shapes are available for this image."
3. **Create the instance.** Compute -> Instances -> Create instance. Pick an Always Free-eligible
   shape (`VM.Standard.A1.Flex` Ampere, or `VM.Standard.E2.1.Micro` AMD; either is far more than
   this relay needs) in the AD identified above, and a plain Ubuntu or Oracle Linux image.
4. **Networking - build it manually if the inline flow breaks.** The "Create new virtual cloud
   network" + "Create new public subnet" shortcut inside instance creation can get stuck refusing
   to enable "Automatically assign public IPv4 address" even with a public subnet selected. If
   that happens, back out and build the pieces yourself under Networking -> Virtual cloud
   networks: **Create VCN** (plain "VCN Only" form, e.g. `10.0.0.0/16`) -> **Gateways** tab ->
   create an **Internet Gateway** -> **Routing** tab -> edit the default route table -> add a rule
   routing `0.0.0.0/0` to that Internet Gateway -> **Subnets** tab -> **Create Subnet** with
   **Public Subnet** access type and the route table above. *Then* go back to instance creation
   and select that VCN/subnet via "Select existing virtual cloud network" - the public-IP toggle
   works reliably once it's pointed at an already-existing public subnet.
5. **Reserve a static public IP.** By default the assigned public IP is ephemeral and can change
   on stop/start. On the instance's Networking tab -> primary VNIC -> IP administration, edit the
   public IP entry and promote it from **Ephemeral** to **Reserved** so it never changes under you
   - `partyServerUrl` in the mod config points at this IP directly (v1 uses plain `ws://`, not a
   domain).
6. **Open the port in *three* places** - this is the #1 cause of "it works locally but nobody can
   connect" with Oracle instances, and there's one more layer than you'd expect:
   - The VCN's **Security List** (or a Network Security Group): add an ingress rule for TCP port
     `8887` (or whatever `ARASHI_PARTY_PORT` you chose) from `0.0.0.0/0`.
   - The instance's **`ufw`**: `sudo ufw allow 8887/tcp` (after `sudo ufw allow OpenSSH` and
     `sudo ufw enable`).
   - **Oracle's baked-in `iptables` ruleset** - separate from `ufw` and easy to miss entirely.
     Oracle's stock cloud images ship a pre-existing `iptables` INPUT chain (see the `CLOUD_IMG`
     comment in `/etc/iptables/rules.v4`) that only ACCEPTs SSH and explicitly **REJECTs
     everything else with `icmp-host-prohibited`** - and this rule is evaluated *before* `ufw`'s
     own chains even run, so `ufw allow` alone silently does nothing. Confirm with
     `sudo iptables -L INPUT -n -v --line-numbers`; if you see an unconditional `REJECT` rule
     ahead of the `ufw-*` chains, insert an explicit accept above it:
     ```
     sudo iptables -I INPUT <line-number-of-the-REJECT-rule> -p tcp --dport 8887 -j ACCEPT
     sudo apt-get install -y netfilter-persistent iptables-persistent
     sudo netfilter-persistent save   # persists it so a reboot doesn't undo the fix
     sudo systemctl enable netfilter-persistent
     ```
     A `tcpdump -i any port 8887 -n` on the box while connecting from outside is the fastest way
     to confirm whether a SYN is even reaching the guest (rules out the security list) and whether
     anything is sent back (an ICMP "admin prohibited" reply is this exact rule firing).
7. **Install a JDK** (21+ is enough - the server build targets Java 21, not the mod's Java 25, so
   it runs on whatever's readily available via `apt`): `sudo apt update && sudo apt install -y
   openjdk-21-jdk`.
8. **Deploy the jar.**
   ```
   sudo mkdir -p /opt/arashi-party-server
   sudo useradd -r -s /usr/sbin/nologin arashi   # if the user doesn't already exist
   sudo cp arashi-party-server.jar /opt/arashi-party-server/
   sudo chown -R arashi:arashi /opt/arashi-party-server
   ```
9. **Install the systemd unit** for 24/7 auto-restart and boot persistence:
   ```
   sudo cp systemd/arashi-party-server.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now arashi-party-server
   sudo systemctl status arashi-party-server
   ```
10. **Point the mod at it.** Set `partyServerUrl` in `config/arashi.json` (or via the party GUI, if
    exposed there) to `ws://<reserved-ip>:8887`.

### Redeploying an update

```
sudo systemctl stop arashi-party-server
sudo cp arashi-party-server.jar /opt/arashi-party-server/
sudo systemctl start arashi-party-server
```

## Future upgrade: `wss://` (TLS)

v1 intentionally ships plain `ws://` to avoid needing a domain name. If you later want TLS
(recommended if this ever gets real traffic - some networks/proxies mangle unencrypted upgrade
traffic): point a free DDNS/subdomain at the reserved IP, run Caddy or nginx in front of the
relay as a TLS-terminating reverse proxy (Let's Encrypt via HTTP-01 needs a hostname, not a bare
IP), and switch `partyServerUrl` to `wss://your-subdomain/...`. Not required to ship v1.

## Known limitations (v1, accepted)

- In-memory state only - a restart wipes all parties and pending invites.
- Self-reported identity - no Mojang session-server verification. One live connection per UUID
  is enforced (a new `hello` for an already-connected UUID replaces the old socket), which is the
  only mitigation against impersonation.
- Plain `ws://`, not `wss://`.
