package dev.duetigh.arashi.gui.theme;

/**
 * A single animated float that eases towards a target value over a fixed duration, driven by
 * wall-clock time. Widgets use one of these per visual state (hover amount, press amount, ...)
 * rather than snapping instantly.
 */
public final class Transition {
	private final long durationMillis;
	private float from;
	private float to;
	private long startedAt;

	public Transition(long durationMillis, float initial) {
		this.durationMillis = durationMillis;
		this.from = initial;
		this.to = initial;
		this.startedAt = 0L;
	}

	public void setTarget(float target) {
		if (target == to) {
			return;
		}

		from = get();
		to = target;
		startedAt = System.currentTimeMillis();
	}

	public float get() {
		if (startedAt == 0L) {
			return to;
		}

		long elapsed = System.currentTimeMillis() - startedAt;

		if (elapsed >= durationMillis) {
			return to;
		}

		float t = elapsed / (float) durationMillis;
		float eased = 1f - (1f - t) * (1f - t);
		return from + (to - from) * eased;
	}
}
