package dev.duetigh.arashi.waypoint;

/** Tracks which waypoint group, if any, the player is currently placing waypoints into via chat commands. */
public final class WaypointEditorState {
	private volatile String editingGroupId;

	public void enter(String groupId) {
		this.editingGroupId = groupId;
	}

	public void exit() {
		this.editingGroupId = null;
	}

	public boolean isEditing() {
		return editingGroupId != null;
	}

	public String editingGroupId() {
		return editingGroupId;
	}
}
