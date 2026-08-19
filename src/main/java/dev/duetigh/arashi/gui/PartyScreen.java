package dev.duetigh.arashi.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import dev.duetigh.arashi.party.PartyManager;
import dev.duetigh.arashi.party.PartyMember;

/** Invite-by-username, incoming/outgoing invites, and the current party roster with skin heads. */
public final class PartyScreen extends Screen {
	private static final int ROW_WIDTH = 260;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 4;
	private static final int FACE_SIZE = 16;
	private static final int SMALL_BUTTON_WIDTH = 60;

	private final PartyManager party;
	private final Map<UUID, PlayerSkin> skinCache = new HashMap<>();
	private final Set<UUID> skinRequested = new HashSet<>();
	private final List<FaceRow> faceRows = new ArrayList<>();

	private EditBox inviteBox;

	public PartyScreen(PartyManager party) {
		super(Component.literal("Arashi - Party"));
		this.party = party;
	}

	@Override
	protected void init() {
		faceRows.clear();

		int left = this.width / 2 - ROW_WIDTH / 2;
		int y = 30;

		this.inviteBox = this.addRenderableWidget(new EditBox(this.font, left, y, ROW_WIDTH - SMALL_BUTTON_WIDTH - ROW_GAP, ROW_HEIGHT, Component.literal("Username")));
		this.addRenderableWidget(Button.builder(Component.literal("Invite"), b -> sendInvite())
				.pos(left + ROW_WIDTH - SMALL_BUTTON_WIDTH, y)
				.size(SMALL_BUTTON_WIDTH, ROW_HEIGHT)
				.build());
		y += ROW_HEIGHT + 12;

		for (PartyManager.IncomingInvite invite : party.incomingInvites()) {
			y = addIncomingInviteRow(invite, left, y);
		}

		for (PartyManager.OutgoingInvite invite : party.outgoingInvites()) {
			y = addOutgoingInviteRow(invite, left, y);
		}

		for (PartyMember member : party.members()) {
			y = addMemberRow(member, left, y);
		}

		y += 6;

		if (party.members().size() > 1) {
			this.addRenderableWidget(Button.builder(Component.literal("Leave Party"), b -> party.leave())
					.pos(left, y)
					.size(ROW_WIDTH, ROW_HEIGHT)
					.build());
			y += ROW_HEIGHT + ROW_GAP;
		}

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(left, y)
				.size(ROW_WIDTH, ROW_HEIGHT)
				.build());

		party.setOnRosterChanged(this::rebuildWidgets);
		party.setOnConnectionStateChanged(this::rebuildWidgets);
	}

	private int addIncomingInviteRow(PartyManager.IncomingInvite invite, int left, int y) {
		faceRows.add(new FaceRow(invite.fromUuid(), left, y, invite.fromUsername() + " invited you"));

		this.addRenderableWidget(Button.builder(Component.literal("Accept"), b -> party.respondToInvite(invite.inviteId(), true))
				.pos(left + ROW_WIDTH - SMALL_BUTTON_WIDTH * 2 - ROW_GAP, y)
				.size(SMALL_BUTTON_WIDTH, ROW_HEIGHT)
				.build());
		this.addRenderableWidget(Button.builder(Component.literal("Decline"), b -> party.respondToInvite(invite.inviteId(), false))
				.pos(left + ROW_WIDTH - SMALL_BUTTON_WIDTH, y)
				.size(SMALL_BUTTON_WIDTH, ROW_HEIGHT)
				.build());

		return y + ROW_HEIGHT + ROW_GAP;
	}

	private int addOutgoingInviteRow(PartyManager.OutgoingInvite invite, int left, int y) {
		faceRows.add(new FaceRow(invite.toUuid(), left, y, "Invited " + invite.toUsername() + " (pending)"));
		return y + ROW_HEIGHT + ROW_GAP;
	}

	private int addMemberRow(PartyMember member, int left, int y) {
		boolean self = member.uuid().equals(party.selfUuid());
		boolean isLeaderRow = member.uuid().equals(party.leaderUuid());

		StringBuilder label = new StringBuilder(member.username());
		if (isLeaderRow) {
			label.append(" (Leader)");
		}
		if (self) {
			label.append(" (You)");
		}
		if (!member.online()) {
			label.append(" (offline)");
		}

		faceRows.add(new FaceRow(member.uuid(), left, y, label.toString()));

		if (party.isSelfLeader() && !self && party.members().size() > 1) {
			this.addRenderableWidget(Button.builder(Component.literal("Kick"), b -> party.kick(member.uuid()))
					.pos(left + ROW_WIDTH - SMALL_BUTTON_WIDTH, y)
					.size(SMALL_BUTTON_WIDTH, ROW_HEIGHT)
					.build());
		}

		return y + ROW_HEIGHT + ROW_GAP;
	}

	private void sendInvite() {
		String username = inviteBox.getValue().strip();
		if (!username.isEmpty()) {
			party.invite(username);
			inviteBox.setValue("");
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 8, 0xFFFFFFFF, true);

		if (party.connectionState() != PartyManager.ConnectionState.CONNECTED) {
			Component status = Component.literal("Disconnected from party relay - reconnecting...")
					.withStyle(ChatFormatting.RED);
			graphics.text(this.font, status, this.width / 2 - this.font.width(status) / 2, 18, 0xFFFFFFFF, true);
		}

		for (FaceRow row : faceRows) {
			PlayerSkin skin = skinFor(row.uuid(), row.label());
			PlayerFaceExtractor.extractRenderState(graphics, skin, row.x(), row.y(), FACE_SIZE);
			graphics.text(this.font, Component.literal(row.label()), row.x() + FACE_SIZE + 6,
					row.y() + (FACE_SIZE - this.font.lineHeight) / 2, 0xFFFFFFFF, true);
		}
	}

	private PlayerSkin skinFor(UUID uuid, String username) {
		PlayerSkin cached = skinCache.get(uuid);
		if (cached != null) {
			return cached;
		}

		if (skinRequested.add(uuid)) {
			Minecraft client = Minecraft.getInstance();
			client.getSkinManager().get(new GameProfile(uuid, username))
					.thenAcceptAsync(skin -> skin.ifPresent(value -> skinCache.put(uuid, value)), client);
		}

		return DefaultPlayerSkin.get(uuid);
	}

	@Override
	public void onClose() {
		party.setOnRosterChanged(null);
		party.setOnConnectionStateChanged(null);
		super.onClose();
	}

	private record FaceRow(UUID uuid, int x, int y, String label) {
	}
}
