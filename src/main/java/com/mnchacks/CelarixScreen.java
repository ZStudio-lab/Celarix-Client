package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The Celarix Click GUI — draggable category windows with a
 * module search bar and expandable per-module settings.
 *
 * <p>Click a module row to toggle it. Rows with a "+" have settings: click
 * the "+" to expand them (booleans flip on click, sliders set on click/drag).
 * The search box at the top filters every window live. Windows are dragged
 * by their title bars. Esc closes.</p>
 */
public final class CelarixScreen extends Screen {

	public static final class Entry {
		final String label;
		final BooleanSupplier on;
		final Runnable toggle;
		final List<DoubleSetting> settings;

		Entry(String label, BooleanSupplier on, Runnable toggle) {
			this(label, on, toggle, List.of());
		}

		Entry(String label, BooleanSupplier on, Runnable toggle, List<DoubleSetting> settings) {
			this.label = label;
			this.on = on;
			this.toggle = toggle;
			this.settings = settings;
		}

		public String label() {
			return label;
		}

		public boolean on() {
			return on.getAsBoolean();
		}

		public Runnable toggle() {
			return toggle;
		}

		public List<DoubleSetting> settings() {
			return settings;
		}
	}

	public static final class DoubleSetting {
		private final String name;
		private final double min;
		private final double max;
		private final java.util.function.DoubleSupplier get;
		private final java.util.function.DoubleConsumer set;

		public DoubleSetting(String name, double min, double max,
							 java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set) {
			this.name = name;
			this.min = min;
			this.max = max;
			this.get = get;
			this.set = set;
		}

		public String getName() {
			return name;
		}

		public String getValueText() {
			return String.format(Locale.ROOT, "%.1f", get.getAsDouble());
		}

		public boolean isSlider() {
			return true;
		}

		public double getFraction() {
			return MathHelper.clamp((get.getAsDouble() - min) / (max - min), 0.0D, 1.0D);
		}

		public void setFraction(double fraction) {
			set.accept(min + MathHelper.clamp(fraction, 0.0D, 1.0D) * (max - min));
		}
	}

	private static final class ClickWindow {
		static final int WIDTH = 128;
		static final int HEADER = 12;
		static final int ROW = 13;
		static final int SETTING_ROW = 12;

		final String title;
		final List<Entry> entries;
		int x;
		int y;
		boolean dragging;
		int dragOffX;
		int dragOffY;

		ClickWindow(String title, int x, int y, List<Entry> entries) {
			this.title = title;
			this.x = x;
			this.y = y;
			this.entries = entries;
		}
	}

	private static Entry entry(String label, BooleanSupplier on, Runnable toggle) {
		return new Entry(label, on, toggle);
	}

	private static Entry entry(String label, BooleanSupplier on, Runnable toggle, List<DoubleSetting> settings) {
		return new Entry(label, on, toggle, settings);
	}

	private static final List<ClickWindow> WINDOWS = List.of(
			new ClickWindow("Combat", 4, 22, List.of(
					entry("Aimbot", AimModule::isEnabled, AimModule::uiToggle,
							List.of(new DoubleSetting("Range", 16.0D, 128.0D,
									AimModule::getRange, AimModule::setRange))),
					entry("Bow Aimbot", BowAimbotModule::isEnabled, BowAimbotModule::toggle),
					entry("Fight Bot", FightBotModule::isEnabled, FightBotModule::toggle,
							List.of(new DoubleSetting("Range", 4.0D, 24.0D,
									FightBotModule::getRange, FightBotModule::setRange))),
					entry("KillAura", KillAuraModule::isEnabled, KillAuraModule::toggle,
							List.of(new DoubleSetting("Range", 3.0D, 6.0D,
									KillAuraModule::getRange, KillAuraModule::setRange))),
					entry("TriggerBot", TriggerBotModule::isEnabled, TriggerBotModule::toggle),
					entry("Reach +5", ReachModule::isEnabled, ReachModule::toggle),
					entry("One-Shot", OneShotModule::isEnabled, OneShotModule::uiToggle),
					entry("Auto Click", AutoClickModule::isEnabled, AutoClickModule::uiToggle),
					entry("Criticals", CriticalsModule::isEnabled, CriticalsModule::toggle),
					entry("Fast Break", FastBreakModule::isEnabled, FastBreakModule::toggle),
					entry("Anti Knockback", KnockbackModule::isEnabled, KnockbackModule::toggle))),
			new ClickWindow("Movement", 138, 22, List.of(
					entry("Fly", FlyModule::isEnabled, FlyModule::uiToggle,
						List.of(new DoubleSetting("Speed", 0.05D, 0.5D,
								FlyModule::getSpeed, FlyModule::setSpeed))),
					entry("Speed", SpeedModule::isEnabled, SpeedModule::uiToggle),
					entry("Auto Walk", AutoWalkModule::isEnabled, AutoWalkModule::uiToggle),
					entry("Auto Sprint", AutoSprintModule::isEnabled, AutoSprintModule::toggle),
					entry("No Fall", NoFallModule::isEnabled, NoFallModule::toggle),
					entry("Elytra Fly", ElytraFlyModule::isEnabled, ElytraFlyModule::toggle),
					entry("Fast Climb", FastClimbModule::isEnabled, FastClimbModule::toggle),
					entry("Fast Fall", FastFallModule::isEnabled, FastFallModule::toggle),
					entry("Blink", BlinkModule::isEnabled, BlinkModule::toggle),
					entry("Click TP", ClickTPModule::isEnabled, ClickTPModule::toggle),
					entry("Anti Levitation", AntiLevitationModule::isEnabled, AntiLevitationModule::toggle),
					entry("Anti Web", AntiWebModule::isEnabled, AntiWebModule::toggle),
					entry("Dolphin", DolphinModule::isEnabled, DolphinModule::toggle),
					entry("Free Cam", FreeCamModule::isEnabled, FreeCamModule::toggle),
					entry("Inventory Walk", InventoryWalkModule::isEnabled, InventoryWalkModule::toggle),
					entry("Glide", GlideModule::isEnabled, GlideModule::toggle),
					entry("Snow Shoe", SnowShoeModule::isEnabled, SnowShoeModule::toggle),
					entry("Tunneller", TunnellerModule::isEnabled, TunnellerModule::toggle),
					entry("Auto Bucket", AutoBucketModule::isEnabled, AutoBucketModule::toggle))),
			new ClickWindow("Player", 272, 22, List.of(
					entry("Survival", SurvivalModule::isEnabled, SurvivalModule::toggle),
					entry("Health Freeze", HealthFreezeModule::isEnabled, HealthFreezeModule::toggle),
					entry("Invis", InvisModule::isEnabled, InvisModule::toggle),
					entry("Invis Reveal", InvisRevealModule::isEnabled, InvisRevealModule::toggle),
					entry("Auto Eat", AutoEatModule::isEnabled, AutoEatModule::toggle,
							List.of(new DoubleSetting("Food", 6.0D, 18.0D,
									AutoEatModule::getThreshold, AutoEatModule::setThreshold))),
					entry("Auto Armor", AutoArmorModule::isEnabled, AutoArmorModule::toggle),
					entry("Auto Totem", AutoTotemModule::isEnabled, AutoTotemModule::toggle),
					entry("Auto Fish", AutoFishModule::isEnabled, AutoFishModule::toggle),
					entry("Fast Place", FastPlaceModule::isEnabled, FastPlaceModule::toggle),
					entry("Auto Translate", AutoTranslateModule::isEnabled, AutoTranslateModule::toggle),
					entry("Chest Stealer", ChestStealerModule::isEnabled, ChestStealerModule::toggle),
					entry("Inv Cleaner", InventoryCleanerModule::isEnabled, InventoryCleanerModule::toggle),
					entry("Auto Potion", AutoPotionModule::isEnabled, AutoPotionModule::toggle,
							List.of(new DoubleSetting("Health", 4.0D, 16.0D,
									AutoPotionModule::getThreshold, AutoPotionModule::setThreshold))),
					entry("Restock", RestockModule::isEnabled, RestockModule::toggle),
					entry("Auto Shear", AutoShearModule::isEnabled, AutoShearModule::toggle),
					entry("Auto Tame", AutoTameModule::isEnabled, AutoTameModule::toggle))),
			new ClickWindow("World", 406, 22, List.of(
					entry("Nuker", NukerModule::isEnabled, NukerModule::toggle,
							List.of(new DoubleSetting("Radius", 2.0D, 4.0D,
									NukerModule::getRadius, NukerModule::setRadius))),
					entry("Vein Miner", VeinMinerModule::isEnabled, VeinMinerModule::toggle),
					entry("Bonemeal", BonemealAuraModule::isEnabled, BonemealAuraModule::toggle),
					entry("Till Aura", TillAuraModule::isEnabled, TillAuraModule::toggle),
					entry("Bunker", InstantBunkerModule::isEnabled, InstantBunkerModule::toggle),
					entry("Scaffold", ScaffoldModule::isEnabled, ScaffoldModule::toggle),
					entry("Anti Void", AntiVoidModule::isEnabled, AntiVoidModule::toggle),
					entry("Auto Farm", AutoFarmModule::isEnabled, AutoFarmModule::toggle,
							List.of(new DoubleSetting("Radius", 3.0D, 8.0D,
									AutoFarmModule::getRadius, AutoFarmModule::setRadius))),
					entry("Auto Torch", AutoTorchModule::isEnabled, AutoTorchModule::toggle),
					entry("Hole Snap", HoleSnapModule::isEnabled, HoleSnapModule::toggle))),
			new ClickWindow("Render", 540, 22, List.of(
					entry("ESP", EspModule::isEnabled, EspModule::uiToggle),
					entry("Tracers", TracersModule::isEnabled, TracersModule::toggle),
					entry("Fullbright", FullbrightModule::isEnabled, FullbrightModule::toggle),
					entry("Search", SearchModule::isEnabled, SearchModule::toggle,
							List.of(new DoubleSetting("Radius", 8.0D, 24.0D,
									SearchModule::getRadius, SearchModule::setRadius))),
					entry("Hole ESP", HoleEspModule::isEnabled, HoleEspModule::toggle),
					entry("Breadcrumbs", BreadcrumbsModule::isEnabled, BreadcrumbsModule::toggle),
					entry("Trajectories", TrajectoriesModule::isEnabled, TrajectoriesModule::toggle),
					entry("Zoom", ZoomModule::isEnabled, ZoomModule::toggle,
							List.of(new DoubleSetting("Level", 2.0D, 10.0D,
									ZoomModule::getLevel, ZoomModule::setLevel))),
					entry("Chest ESP", ChestEspModule::isEnabled, ChestEspModule::toggle),
					entry("X-Ray", XRayModule::isEnabled, XRayModule::toggle),
					entry("No Overlay", NoOverlayModule::isEnabled, NoOverlayModule::toggle),
					entry("New Chunks", NewChunksModule::isEnabled, NewChunksModule::toggle),
					entry("No Fog", NoFogModule::isEnabled, NoFogModule::toggle),
					entry("Radar", RadarModule::isEnabled, RadarModule::toggle),
					entry("No Hurt Cam", NoHurtCamModule::isEnabled, NoHurtCamModule::toggle),
					entry("No Weather", NoWeatherModule::isEnabled, NoWeatherModule::toggle),
					entry("No Armor Stand", NoArmorStandModule::isEnabled, NoArmorStandModule::toggle),
					entry("Anti Blindness", AntiBlindnessModule::isEnabled, AntiBlindnessModule::toggle),
					entry("Crosshair", CrosshairModule::isEnabled, CrosshairModule::toggle))),
			new ClickWindow("Client", 674, 22, List.of(
					entry("Auto Reconnect", AutoReconnectModule::isEnabled, AutoReconnectModule::toggle),
					entry("Friends", FriendsModule::isEnabled, FriendsModule::toggle),
					entry("No Teleport", AntiTeleportModule::isEnabled, AntiTeleportModule::toggle),
					entry("Air Place", AirPlaceModule::isEnabled, AirPlaceModule::toggle),
					entry("Panic", () -> false, PanicModule::trigger),
					entry("Auto Leave", AutoLeaveModule::isEnabled, AutoLeaveModule::toggle,
							List.of(new DoubleSetting("Health", 1.0D, 18.0D,
									AutoLeaveModule::getThreshold, AutoLeaveModule::setThreshold))))),
			new ClickWindow("Misc", 808, 22, List.of(
					entry("Anti AFK+", AntiAfkPlusModule::isEnabled, AntiAfkPlusModule::toggle),
					entry("MC Pearl", MiddleClickPearlModule::isEnabled, MiddleClickPearlModule::toggle),
					entry("No Rotate", NoRotateModule::isEnabled, NoRotateModule::toggle),
					entry("Run Away", () -> BotConfig.runAway, () -> BotConfig.runAway = !BotConfig.runAway),
					entry("Sneak", SneakModule::isEnabled, SneakModule::toggle),
					entry("Rotation Lock", RotationLockModule::isEnabled, RotationLockModule::toggle),
					entry("No Jump Delay", NoJumpDelayModule::isEnabled, NoJumpDelayModule::toggle),
					entry("Anti AFK", AntiAfkModule::isEnabled, AntiAfkModule::toggle),
					entry("Derp", DerpModule::isEnabled, DerpModule::toggle),
					entry("Anti Spam", AntiSpamModule::isEnabled, AntiSpamModule::toggle),
					entry("Multi Task", MultiTaskModule::isEnabled, MultiTaskModule::toggle))));

	private static final int ACCENT = 0xFF3FBFEE;
	private static final Set<String> expanded = new HashSet<>();
	private static String search = "";
	private static boolean searchFocused = false;
	private static DoubleSetting draggingSlider = null;
	private static int sliderX;
	private static int sliderWidth;

	private CelarixScreen() {
		super(Text.literal("Celarix Client"));
	}

	/** Opens the menu when the menu key is pressed. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (Keybinds.uiToggle.wasPressed()) {
				client.setScreen(new CelarixScreen());
			}
		});
	}

	private static List<Entry> visible(ClickWindow window) {
		if (search.isEmpty()) {
			return window.entries;
		}
		String query = search.toLowerCase(Locale.ROOT);
		List<Entry> out = new ArrayList<>();
		for (Entry entry : window.entries) {
			if (entry.label.toLowerCase(Locale.ROOT).contains(query)) {
				out.add(entry);
			}
		}
		return out;
	}

	private static int visibleHeight(ClickWindow window, List<Entry> entries) {
		int rows = 0;
		for (Entry entry : entries) {
			rows++;
			if (expanded.contains(window.title + "|" + entry.label)) {
				rows += entry.settings.size();
			}
		}
		return ClickWindow.HEADER + 1 + rows * ClickWindow.ROW + 3;
	}

	private static String expandKey(ClickWindow window, Entry entry) {
		return window.title + "|" + entry.label;
	}

	protected void init() {
		// Grid layout with real row heights, recomputed on EVERY open, so a
		// changed window size, GUI scale or a drag from an earlier session
		// can never leave stale overlapping positions behind.
		layoutWindows(this.width);
		// Safety clamp every open: keep windows inside the visible area.
		for (ClickWindow window : WINDOWS) {
			if (window.x + ClickWindow.WIDTH > this.width) {
				window.x = Math.max(2, this.width - ClickWindow.WIDTH - 2);
			}
			if (window.y + visibleHeight(window, visible(window)) > this.height) {
				window.y = Math.max(2, this.height - visibleHeight(window, visible(window)) - 2);
			}
		}
	}

	/**
	 * Packs the windows into a non-overlapping grid: columns fit the current
	 * screen width, each row advances past the tallest window in it (row
	 * heights include expanded settings). Called on every open and whenever
	 * a window grows or shrinks (expand toggle, search filter), so windows
	 * below always shift instead of being overlapped.
	 */
	private static void layoutWindows(int screenWidth) {
		int columns = Math.max(1, screenWidth / (ClickWindow.WIDTH + 8));
		int x = 4;
		int y = 22;
		int rowTallest = 0;
		int col = 0;
		for (ClickWindow window : WINDOWS) {
			if (col >= columns) {
				x = 4;
				y += rowTallest + 6;
				rowTallest = 0;
				col = 0;
			}
			window.x = x;
			window.y = y;
			int windowH = visibleHeight(window, visible(window));
			rowTallest = Math.max(rowTallest, windowH);
			x += ClickWindow.WIDTH + 8;
			col++;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		TextRenderer textRenderer = this.textRenderer;

		// Search bar.
		int searchWidth = 200;
		int searchX = (this.width - searchWidth) / 2;
		int searchY = 4;
		context.fill(searchX, searchY, searchX + searchWidth, searchY + 12, 0xE0101018);
		context.fill(searchX, searchY, searchX + searchWidth, searchY + 1,
				searchFocused ? ACCENT : 0xFF3A3A44);
		String display = search + (searchFocused && (clientAge() / 12) % 2 == 0 ? "_" : "");
		context.drawTextWithShadow(textRenderer,
				search.isEmpty() && !searchFocused ? "Search modules..." : display,
				searchX + 4, searchY + 3, searchFocused ? 0xFFFFFFFF : 0xFF8A8A96);

		for (ClickWindow window : WINDOWS) {
			renderWindow(context, textRenderer, window, mouseX, mouseY);
		}
	}

	private static int clientAge() {
		return (int) (net.minecraft.client.MinecraftClient.getInstance()
				.world != null ? net.minecraft.client.MinecraftClient.getInstance().world.getTime()
				: System.currentTimeMillis() / 50L);
	}

	private void renderWindow(DrawContext context, TextRenderer textRenderer,
							  ClickWindow window, int mouseX, int mouseY) {
		List<Entry> entries = visible(window);
		if (entries.isEmpty() && !search.isEmpty()) {
			return;
		}
		int width = ClickWindow.WIDTH;
		int header = ClickWindow.HEADER;
		int height = visibleHeight(window, entries);

		context.fill(window.x, window.y, window.x + width, window.y + height, 0xE00D0D13);
		context.fill(window.x, window.y, window.x + width, window.y + header,
				window.dragging ? 0xFF2C9FD4 : ACCENT);
		context.fill(window.x, window.y + header, window.x + width, window.y + header + 1, 0xFF3FBFEE);
		context.drawTextWithShadow(textRenderer, window.title, window.x + 5, window.y + 3, 0xFF062330);

		int y = window.y + header + 1;
		for (Entry entry : entries) {
			boolean on;
			try {
				on = entry.on();
			} catch (Throwable error) {
				on = false;
			}
			boolean hovered = mouseX >= window.x && mouseX < window.x + width
					&& mouseY >= y && mouseY < y + ClickWindow.ROW;
			if (hovered) {
				context.fill(window.x, y, window.x + width, y + ClickWindow.ROW, 0x24FFFFFF);
			}
			context.drawTextWithShadow(textRenderer, entry.label, window.x + 5, y + 3,
					on ? 0xFF7DFFA8 : 0xFFC9C9D4);
			String state = on ? "ON" : "OFF";
			context.drawTextWithShadow(textRenderer, state,
					window.x + width - 5 - textRenderer.getWidth(state), y + 3,
					on ? 0xFF7DFFA8 : 0xFF6A6A76);
			if (!entry.settings.isEmpty()) {
				String mark = expanded.contains(expandKey(window, entry)) ? "-" : "+";
				context.drawTextWithShadow(textRenderer, mark, window.x + width - 5
						- textRenderer.getWidth(state) - textRenderer.getWidth(mark) - 5, y + 3, ACCENT);
			}
			y += ClickWindow.ROW;

			if (expanded.contains(expandKey(window, entry))) {
				for (DoubleSetting setting : entry.settings) {
					context.fill(window.x + 5, y, window.x + width - 5, y + 1, 0xFF2A2A34);
					context.drawTextWithShadow(textRenderer, setting.name, window.x + 10, y + 2,
							0xFF9A9AA6);
					context.drawTextWithShadow(textRenderer, setting.getValueText(),
							window.x + width - 5 - textRenderer.getWidth(setting.getValueText()),
							y + 2, 0xFFE0E0E8);
					if (setting.isSlider()) {
						int barX = window.x + 10;
						int barW = width - 20;
						int barY = y + ClickWindow.SETTING_ROW - 3;
						context.fill(barX, barY, barX + barW, barY + 2, 0xFF2E2E3A);
						context.fill(barX, barY, barX + (int) (barW * setting.getFraction()),
								barY + 2, ACCENT);
					}
					y += ClickWindow.SETTING_ROW;
				}
			}
		}
	}

	private static DoubleSetting sliderAt(ClickWindow window, Entry entry, double mouseY) {
		List<DoubleSetting> settings = entry.settings;
		if (settings.size() == 1) {
			return settings.get(0);
		}
		return null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int searchWidth = 200;
			int searchX = (this.width - searchWidth) / 2;
			if (mouseY >= 4 && mouseY < 16 && mouseX >= searchX && mouseX < searchX + searchWidth) {
				searchFocused = true;
				return true;
			}
			searchFocused = false;

			for (ClickWindow window : WINDOWS) {
				List<Entry> entries = visible(window);
				int height = visibleHeight(window, entries);
				if (mouseX >= window.x && mouseX < window.x + ClickWindow.WIDTH
						&& mouseY >= window.y && mouseY < window.y + height) {
					if (mouseY < window.y + ClickWindow.HEADER) {
						window.dragging = true;
						window.dragOffX = (int) (mouseX - window.x);
						window.dragOffY = (int) (mouseY - window.y);
						return true;
					}
					int y = window.y + ClickWindow.HEADER + 1;
					for (Entry entry : entries) {
						if (mouseY >= y && mouseY < y + ClickWindow.ROW) {
							if (mouseX >= window.x + ClickWindow.WIDTH - 40
									&& !entry.settings.isEmpty()) {
								String key = expandKey(window, entry);
								if (expanded.contains(key)) {
									expanded.remove(key);
								} else {
									expanded.add(key);
								}
								// The window just grew/shrank - re-pack the
								// grid so windows below shift instead of
								// being overlapped.
								layoutWindows(this.width);
							} else {
								entry.toggle().run();
							}
							return true;
						}
						y += ClickWindow.ROW;
						if (expanded.contains(expandKey(window, entry))) {
							for (DoubleSetting setting : entry.settings) {
								if (mouseY >= y && mouseY < y + ClickWindow.SETTING_ROW && setting.isSlider()) {
									draggingSlider = setting;
									sliderX = window.x + 10;
									sliderWidth = ClickWindow.WIDTH - 20;
									draggingSlider.setFraction((mouseX - sliderX) / (double) sliderWidth);
									return true;
								}
								y += ClickWindow.SETTING_ROW;
							}
						}
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (draggingSlider != null) {
			draggingSlider.setFraction((mouseX - sliderX) / (double) sliderWidth);
			return true;
		}
		for (ClickWindow window : WINDOWS) {
			if (window.dragging) {
				window.x = (int) (mouseX - window.dragOffX);
				window.y = (int) (mouseY - window.dragOffY);
				return true;
			}
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingSlider = null;
		for (ClickWindow window : WINDOWS) {
			window.dragging = false;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchFocused) {
			if (keyCode == 256) { // Esc
				searchFocused = false;
				return true;
			}
			if (keyCode == 259) { // Backspace
				if (!search.isEmpty()) {
					search = search.substring(0, search.length() - 1);
					layoutWindows(this.width);
				}
				return true;
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (searchFocused && chr >= ' ' && chr != 127) {
			search += chr;
			layoutWindows(this.width);
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
