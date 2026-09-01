package dev.duetigh.arashi.waypoint;

/** The two kinds of waypoint, each with its own default color and floating-text label. */
public enum WaypointType {
	PICKOBULUS("Pickobulus"),
	ETHERWARP("Etherwarp");

	private final String label;

	WaypointType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
