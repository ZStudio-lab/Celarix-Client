package com.mnchacks;

/**
 * Shared behaviour switches, editable live from the in-game menu.
 *
 * <p>{@link #runAway} — at low health, kite away from the opponent and heal
 * (default). Turn it off to stand and fight through the damage while Health
 * Freeze keeps the hearts up.</p>
 */
public final class BotConfig {

	/** When true, low health runs away to heal; false = stand and fight. */
	public static boolean runAway = true;

	private BotConfig() {
	}
}