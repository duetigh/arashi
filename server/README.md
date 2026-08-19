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

1. **Create the instance.** Oracle Cloud console -> Compute -> Instances -> Create instance. Pick
   an Always Free-eligible shape (`VM.Standard.A1.Flex` Ampere, or `VM.Standard.E2.1.Micro` AMD;
   either is far more than this relay needs). Use the default Ubuntu image.
2. **Reserve a static public IP.** By default the assigned public IP is ephemeral and can change
   on stop/start. In the instance's attached VNIC, reserve/promote the public IP to a **Reserved
   Public IP** so it never changes under you - `partyServerUrl` in the mod config points at this
   IP directly (v1 uses plain `ws://`, not a domain).
3. **Open the port in *two* places** - this is the #1 cause of "it works locally but nobody can
   connect" with Oracle instances:
   - The VCN's **Security List** (or a Network Security Group): add an ingress rule for TCP port
     `8887` (or whatever `ARASHI_PARTY_PORT` you chose) from `0.0.0.0/0`.
   - The instance's **own OS firewall** - Oracle's stock Ubuntu images ship with `iptables`
     pre-configured to drop unlisted inbound ports even though the cloud-level security list
     allows it. Either add an explicit accept rule for the port, or (simpler) install `ufw` and
     `ufw allow 8887/tcp`.
4. **Install a JDK** (25+): `sudo apt update && sudo apt install -y openjdk-25-jdk` (or whatever
   package name your Ubuntu release provides - use a recent LTS JDK if 25 isn't packaged yet).
5. **Deploy the jar.**
   ```
   sudo mkdir -p /opt/arashi-party-server
   sudo useradd -r -s /usr/sbin/nologin arashi   # if the user doesn't already exist
   sudo cp arashi-party-server.jar /opt/arashi-party-server/
   sudo chown -R arashi:arashi /opt/arashi-party-server
   ```
6. **Install the systemd unit** for 24/7 auto-restart and boot persistence:
   ```
   sudo cp systemd/arashi-party-server.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now arashi-party-server
   sudo systemctl status arashi-party-server
   ```
7. **Point the mod at it.** Set `partyServerUrl` in `config/arashi.json` (or via the party GUI, if
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
