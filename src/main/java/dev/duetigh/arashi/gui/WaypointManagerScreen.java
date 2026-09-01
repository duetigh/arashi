package dev.duetigh.arashi.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import dev.duetigh.arashi.config.ArashiConfig;
import dev.duetigh.arashi.gui.theme.ArashiTheme;
import dev.duetigh.arashi.gui.widget.ArashiButton;
import dev.duetigh.arashi.gui.widget.ArashiListRow;
import dev.duetigh.arashi.gui.widget.ArashiScrollPanel;
import dev.duetigh.arashi.gui.widget.ArashiTextField;
import dev.duetigh.arashi.waypoint.Waypoint;
import dev.duetigh.arashi.waypoint.WaypointEditorState;
import dev.duetigh.arashi.waypoint.WaypointGroup;
import dev.duetigh.arashi.waypoint.WaypointStore;

/**
 * Browse, activate, rename, or delete saved waypoint groups, create new ones, and (via the "Edit"
 * action) drill into a group to see its individual waypoints and delete them one at a time.
 * Placing new waypoints still happens in-world with {@code /arashi waypoint add <type>}, targeting
 * whichever group was last opened with "Edit" ({@link WaypointEditorState}).
 */
public final class WaypointManagerScreen extends ArashiScreen {
	private static final int ROW_HEIGHT = 44;
	private static final int NAME_ROW_HEIGHT = 18;
	private static final int ACTION_HEIGHT = 16;
	private static final int ACTION_GAP = 4;
	private static final int ACTION_COUNT = 5;
	private static final int TOP_ROW_Y = 36;
	private static final int FIELD_WIDTH = 150;
	private static final int WAYPOINT_ROW_HEIGHT = 22;
	private static final int WAYPOINT_DELETE_WIDTH = 50;
	private static final int BACK_BUTTON_WIDTH = 60;

	private final ArashiConfig config;
	private final WaypointStore store;
	private final WaypointEditorState editorState;

	private ArashiScrollPanel list;
	private ArashiTextField nameField;
	private String renamingId;
	private String renamingName = "";
	private String statusMessage = "";
	private String viewingGroupId;

	public WaypointManagerScreen(Screen parent, ArashiConfig config, WaypointStore store, WaypointEditorState editorState) {
		super(Component.literal("Arashi - Waypoints"), parent);
		this.config = config;
		this.store = store;
		this.editorState = editorState;
	}

	@Override
	protected void buildWidgets() {
		int margin = ArashiTheme.PADDING;

		if (viewingGroupId != null) {
			buildDetailWidgets(margin);
			return;
		}

		if (renamingId != null) {
			nameField = track(new ArashiTextField(renamingName, v -> { }));
			nameField.setBounds(margin, TOP_ROW_Y, FIELD_WIDTH, 20);

			ArashiButton saveButton = track(new ArashiButton("Save", ArashiButton.Style.PRIMARY, b -> confirmRename()));
			saveButton.setBounds(margin + FIELD_WIDTH + ArashiTheme.GAP, TOP_ROW_Y, 52, 20);

			ArashiButton cancelButton = track(new ArashiButton("Cancel", ArashiButton.Style.SECONDARY, b -> cancelRename()));
			cancelButton.setBounds(margin + FIELD_WIDTH + ArashiTheme.GAP * 2 + 52, TOP_ROW_Y, 52, 20);
		} else {
			nameField = track(new ArashiTextField("", v -> { }));
			nameField.setBounds(margin, TOP_ROW_Y, FIELD_WIDTH, 20);

			ArashiButton createButton = track(new ArashiButton("New Group", ArashiButton.Style.PRIMARY, b -> createGroup()));
			createButton.setBounds(margin + FIELD_WIDTH + ArashiTheme.GAP, TOP_ROW_Y, 80, 20);

			ArashiButton importButton = track(new ArashiButton("Import", ArashiButton.Style.SECONDARY, b -> importFromClipboard()));
			importButton.setBounds(margin + FIELD_WIDTH + ArashiTheme.GAP * 2 + 80, TOP_ROW_Y, 60, 20);
		}

		int listTop = TOP_ROW_Y + 20 + margin;
		int listWidth = this.width - margin * 2;
		int listHeight = this.height - listTop - margin - 24;

		list = track(new ArashiScrollPanel());
		list.setBounds(margin, listTop, listWidth, listHeight);

		ArashiButton doneButton = track(new ArashiButton("Done", ArashiButton.Style.SECONDARY, b -> onClose()));
		doneButton.setBounds(this.width / 2 - 40, this.height - margin - 20, 80, 20);

		refresh();
	}

	private void refresh() {
		list.clear();
		int y = 0;
		int listWidth = list.getWidth();
		int actionWidth = (listWidth - ACTION_GAP * (ACTION_COUNT - 1)) / ACTION_COUNT;

		for (WaypointStore.GroupEntry entry : store.list()) {
			boolean active = entry.id().equals(config.activeWaypointGroupId());
			String trailing = entry.waypointCount() + " waypoints" + (active ? " * active" : "");
			ArashiListRow row = new ArashiListRow(entry.name(), trailing, null);
			row.setSize(listWidth, NAME_ROW_HEIGHT);
			list.add(row, 0, y);

			int actionY = y + NAME_ROW_HEIGHT + 2;
			ArashiButton.Style activeStyle = active ? ArashiButton.Style.PRIMARY : ArashiButton.Style.SECONDARY;
			addAction(actionWidth, 0, actionY, active ? "Deactivate" : "Set Active", activeStyle, () -> toggleActive(entry.id()));
			addAction(actionWidth, actionWidth + ACTION_GAP, actionY, "Edit", ArashiButton.Style.SECONDARY, () -> editGroup(entry.id()));
			addAction(actionWidth, (actionWidth + ACTION_GAP) * 2, actionY, "Rename", ArashiButton.Style.SECONDARY, () -> beginRename(entry));
			addAction(actionWidth, (actionWidth + ACTION_GAP) * 3, actionY, "Export", ArashiButton.Style.SECONDARY, () -> exportGroup(entry));
			addAction(actionWidth, (actionWidth + ACTION_GAP) * 4, actionY, "Delete", ArashiButton.Style.SECONDARY, () -> deleteGroup(entry.id()));

			y += ROW_HEIGHT;
		}
	}

	private void addAction(int width, int relativeX, int relativeY, String label, ArashiButton.Style style, Runnable action) {
		ArashiButton button = new ArashiButton(label, style, b -> action.run());
		button.setSize(width, ACTION_HEIGHT);
		list.add(button, relativeX, relativeY);
	}

	/** The per-waypoint view for one group: a back button, a delete-able row per waypoint, and Done. */
	private void buildDetailWidgets(int margin) {
		ArashiButton backButton = track(new ArashiButton("Back", ArashiButton.Style.SECONDARY, b -> {
			viewingGroupId = null;
			statusMessage = "";
			this.rebuildWidgets();
		}));
		backButton.setBounds(margin, TOP_ROW_Y, BACK_BUTTON_WIDTH, 20);

		int listTop = TOP_ROW_Y + 20 + margin;
		int listWidth = this.width - margin * 2;
		int listHeight = this.height - listTop - margin - 24;

		list = track(new ArashiScrollPanel());
		list.setBounds(margin, listTop, listWidth, listHeight);

		ArashiButton doneButton = track(new ArashiButton("Done", ArashiButton.Style.SECONDARY, b -> onClose()));
		doneButton.setBounds(this.width / 2 - 40, this.height - margin - 20, 80, 20);

		refreshDetail();
	}

	private void refreshDetail() {
		list.clear();
		WaypointGroup group = store.get(viewingGroupId).orElse(null);

		if (group == null) {
			return;
		}

		int y = 0;
		int listWidth = list.getWidth();
		int rowWidth = listWidth - WAYPOINT_DELETE_WIDTH - ACTION_GAP;

		for (Waypoint waypoint : group.waypoints()) {
			BlockPos pos = waypoint.pos();
			String leading = "#" + waypoint.order() + " " + waypoint.type().label();
			String trailing = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
			ArashiListRow row = new ArashiListRow(leading, trailing, null);
			row.setSize(rowWidth, WAYPOINT_ROW_HEIGHT - 2);
			list.add(row, 0, y);

			int order = waypoint.order();
			ArashiButton deleteButton = new ArashiButton("Delete", ArashiButton.Style.SECONDARY, b -> deleteWaypoint(order));
			deleteButton.setSize(WAYPOINT_DELETE_WIDTH, WAYPOINT_ROW_HEIGHT - 2);
			list.add(deleteButton, rowWidth + ACTION_GAP, y);

			y += WAYPOINT_ROW_HEIGHT;
		}
	}

	private void deleteWaypoint(int order) {
		store.removeWaypoint(viewingGroupId, order);
		refreshDetail();
	}

	private void createGroup() {
		String name = nameField.getValue().strip();

		if (name.isEmpty()) {
			return;
		}

		WaypointGroup group = store.createGroup(name);
		editorState.enter(group.id());
		viewingGroupId = group.id();
		statusMessage = "Created \"" + name + "\" - add waypoints with /arashi waypoint add <pickobulus|etherwarp>";
		this.rebuildWidgets();
	}

	/** Setting a group active always deactivates whichever one was active before, since only one route navigates at a time. */
	private void toggleActive(String id) {
		config.setActiveWaypointGroupId(id.equals(config.activeWaypointGroupId()) ? null : id);
		config.save();
		refresh();
	}

	private void exportGroup(WaypointStore.GroupEntry entry) {
		store.export(entry.id()).ifPresent(encoded -> {
			Minecraft.getInstance().keyboardHandler.setClipboard(encoded);
			statusMessage = "Copied \"" + entry.name() + "\" to clipboard - share it with /arashi waypoint or the Import button.";
		});
	}

	private void importFromClipboard() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();

		if (clipboard == null || clipboard.isBlank()) {
			statusMessage = "Clipboard is empty - copy an exported waypoint group first.";
			return;
		}

		try {
			WaypointGroup imported = store.importGroup(clipboard);
			statusMessage = "Imported \"" + imported.name() + "\" (" + imported.waypoints().size() + " waypoints).";
		} catch (IllegalArgumentException e) {
			statusMessage = e.getMessage();
		}

		this.rebuildWidgets();
	}

	private void editGroup(String id) {
		editorState.enter(id);
		viewingGroupId = id;
		statusMessage = "Now editing that group - add waypoints with /arashi waypoint add <pickobulus|etherwarp>";
		this.rebuildWidgets();
	}

	private void beginRename(WaypointStore.GroupEntry entry) {
		renamingId = entry.id();
		renamingName = entry.name();
		this.rebuildWidgets();
	}

	private void confirmRename() {
		String newName = nameField.getValue().strip();

		if (!newName.isEmpty() && renamingId != null) {
			store.rename(renamingId, newName);
		}

		renamingId = null;
		this.rebuildWidgets();
	}

	private void cancelRename() {
		renamingId = null;
		this.rebuildWidgets();
	}

	private void deleteGroup(String id) {
		if (id.equals(config.activeWaypointGroupId())) {
			config.setActiveWaypointGroupId(null);
			config.save();
		}

		if (id.equals(editorState.editingGroupId())) {
			editorState.exit();
		}

		store.delete(id);
		refresh();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);

		if (viewingGroupId != null) {
			String name = store.get(viewingGroupId).map(WaypointGroup::name).orElse("");
			int textY = TOP_ROW_Y + (20 - this.font.lineHeight) / 2;
			ctx.text(this.font, Component.literal(name), ArashiTheme.PADDING + BACK_BUTTON_WIDTH + ArashiTheme.GAP, textY, ArashiTheme.TEXT_PRIMARY, false);
		}

		if (!statusMessage.isEmpty()) {
			ctx.text(this.font, Component.literal(statusMessage), ArashiTheme.PADDING, TOP_ROW_Y + 24, ArashiTheme.TEXT_SECONDARY, false);
		}
	}
}
