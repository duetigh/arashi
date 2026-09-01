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
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.widget.ArashiButton;
import dev.duetigh.arashi.gui.widget.ArashiTextField;
import dev.duetigh.arashi.party.PartyManager;
import dev.duetigh.arashi.party.PartyMember;

/** Invite-by-username, incoming/outgoing invites, and the current party roster with skin heads. */
public final class PartyScreen extends ArashiScreen {
	private static final int ROW_WIDTH = 200;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 4;
	private static final int FACE_SIZE = 16;
	private static final int SMALL_BUTTON_WIDTH = 46;

	private final PartyManager party;
	private final Map<UUID, PlayerSkin> skinCache = new HashMap<>();
	private final Set<UUID> skinRequested = new HashSet<>();
	private final List<FaceRow> faceRows = new ArrayList<>();

	private ArashiTextField inviteField;

	public PartyScreen(PartyManager party) {
		super(Component.literal("Arashi - Party"), null);
		this.party = party;
	}

	@Override
	protected void buildWidgets() {
		faceRows.clear();

		int left = this.width / 2 - ROW_WIDTH / 2;
		int y = 30;

		inviteField = track(new ArashiTextField("", v -> { }));
		inviteField.setBounds(left, y, ROW_WIDTH - SMALL_BUTTON_WIDTH - ROW_GAP, ROW_HEIGHT);

		ArashiButton inviteButton = track(new ArashiButton("Invite", ArashiButton.Style.PRIMARY, b -> sendInvite()));
		inviteButton.setBounds(left + ROW_WIDTH - SMALL_BUTTON_WIDTH, y, SMALL_BUTTON_WIDTH, ROW_HEIGHT);
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
			ArashiButton leaveButton = track(new ArashiButton("Leave Party", ArashiButton.Style.SECONDARY, b -> party.leave()));
			leaveButton.setBounds(left, y, ROW_WIDTH, ROW_HEIGHT);
			y += ROW_HEIGHT + ROW_GAP;
		}

		ArashiButton doneButton = track(new ArashiButton("Done", ArashiButton.Style.SECONDARY, b -> onClose()));
		doneButton.setBounds(left, y, ROW_WIDTH, ROW_HEIGHT);

		party.setOnRosterChanged(this::rebuildWidgets);
		party.setOnConnectionStateChanged(this::rebuildWidgets);
	}

	private int addIncomingInviteRow(PartyManager.IncomingInvite invite, int left, int y) {
		faceRows.add(new FaceRow(invite.fromUuid(), left, y, invite.fromUsername() + " invited you"));

		ArashiButton acceptButton = track(new ArashiButton("Accept", ArashiButton.Style.PRIMARY, b -> party.respondToInvite(invite.inviteId(), true)));
		acceptButton.setBounds(left + ROW_WIDTH - SMALL_BUTTON_WIDTH * 2 - ROW_GAP, y, SMALL_BUTTON_WIDTH, ROW_HEIGHT);

		ArashiButton declineButton = track(new ArashiButton("Decline", ArashiButton.Style.SECONDARY, b -> party.respondToInvite(invite.inviteId(), false)));
		declineButton.setBounds(left + ROW_WIDTH - SMALL_BUTTON_WIDTH, y, SMALL_BUTTON_WIDTH, ROW_HEIGHT);

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
			ArashiButton kickButton = track(new ArashiButton("Kick", ArashiButton.Style.SECONDARY, b -> party.kick(member.uuid())));
			kickButton.setBounds(left + ROW_WIDTH - SMALL_BUTTON_WIDTH, y, SMALL_BUTTON_WIDTH, ROW_HEIGHT);
		}

		return y + ROW_HEIGHT + ROW_GAP;
	}

	private void sendInvite() {
		String username = inviteField.getValue().strip();

		if (!username.isEmpty()) {
			party.invite(username);
			this.rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);

		if (party.connectionState() != PartyManager.ConnectionState.CONNECTED) {
			String status = "Disconnected from party relay - reconnecting...";
			Component styled = Component.literal(status).withStyle(ChatFormatting.RED);
			ctx.text(this.font, styled, this.width / 2 - this.font.width(status) / 2, 18, ArashiTheme.TEXT_PRIMARY, true);
		}

		for (FaceRow row : faceRows) {
			PlayerSkin skin = skinFor(row.uuid(), row.label());
			PlayerFaceExtractor.extractRenderState(ctx, skin, row.x(), row.y(), FACE_SIZE);
			ctx.text(this.font, Component.literal(row.label()), row.x() + FACE_SIZE + 6,
					row.y() + (FACE_SIZE - this.font.lineHeight) / 2, ArashiTheme.TEXT_PRIMARY, true);
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
