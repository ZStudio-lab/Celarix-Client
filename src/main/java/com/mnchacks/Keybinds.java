package com.mnchacks;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Keybindings for the mod. All live in the "Celarix Client" controls category
 * and can be rebound in Options -> Controls.
 *
 * <p>The physical key every binding was registered with is kept in
 * {@link #KEYS} so the external control panel can simulate a press of that
 * module's key (there is no public getter for a binding's bound key).</p>
 */
public final class Keybinds {

	public static KeyBinding flyToggle;
	public static KeyBinding espToggle;
	public static KeyBinding aimToggle;
	public static KeyBinding speedToggle;
	public static KeyBinding oneShotToggle;
	public static KeyBinding clickToggle;
	public static KeyBinding healToggle;
	public static KeyBinding walkToggle;
	public static KeyBinding fpsToggle;
	public static KeyBinding waypointAdd;
	public static KeyBinding waypointNext;
	public static KeyBinding waypointClear;
	public static KeyBinding uiToggle;
	public static KeyBinding survivalToggle;
	public static KeyBinding freeCamToggle;
	public static KeyBinding zoomToggle;

	private static final Map<KeyBinding, InputUtil.Key> KEYS = new HashMap<>();

	private Keybinds() {
	}

	public static void register() {
		flyToggle = register("key.celarix.fly", GLFW.GLFW_KEY_R);
		espToggle = register("key.celarix.esp", GLFW.GLFW_KEY_B);
		aimToggle = register("key.celarix.aim", GLFW.GLFW_KEY_V);
		speedToggle = register("key.celarix.speed", GLFW.GLFW_KEY_G);
		oneShotToggle = register("key.celarix.oneshot", GLFW.GLFW_KEY_H);
		clickToggle = register("key.celarix.autoclick", GLFW.GLFW_KEY_J);
		healToggle = register("key.celarix.autoheal", GLFW.GLFW_KEY_K);
		walkToggle = register("key.celarix.autowalk", GLFW.GLFW_KEY_N);
		fpsToggle = register("key.celarix.fps", GLFW.GLFW_KEY_X);
		waypointAdd = register("key.celarix.wp.add", GLFW.GLFW_KEY_M);
		waypointNext = register("key.celarix.wp.next", GLFW.GLFW_KEY_COMMA);
		waypointClear = register("key.celarix.wp.clear", GLFW.GLFW_KEY_PERIOD);
		uiToggle = register("key.celarix.ui", GLFW.GLFW_KEY_LEFT_ALT);
		survivalToggle = register("key.celarix.survival", GLFW.GLFW_KEY_C);
		freeCamToggle = register("key.celarix.freecam", GLFW.GLFW_KEY_Y);
		zoomToggle = register("key.celarix.zoom", GLFW.GLFW_KEY_U);
	}

	/** The key a module's binding was registered with. */
	public static InputUtil.Key keyOf(KeyBinding binding) {
		InputUtil.Key key = KEYS.get(binding);
		return key != null ? key : InputUtil.UNKNOWN_KEY;
	}

	private static KeyBinding register(String translationKey, int code) {
		InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(code);
		KeyBinding binding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				translationKey,
				InputUtil.Type.KEYSYM,
				code,
				"category.celarix"));
		KEYS.put(binding, key);
		return binding;
	}
}