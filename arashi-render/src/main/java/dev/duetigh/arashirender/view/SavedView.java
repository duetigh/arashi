package dev.duetigh.arashirender.view;

import java.util.ArrayList;
import java.util.List;

/** A named snapshot of the display filters (not camera pose - see plan notes) for one loaded scan. */
public final class SavedView {
	public String name;
	public int minY;
	public int maxY;
	public float opacity = 1.0f;
	public List<String> hiddenBlockIds = new ArrayList<>();
	public boolean isolateEnabled;
	public String isolateSeedBlockId;
	public int isolateConnectivity = 6;
}
