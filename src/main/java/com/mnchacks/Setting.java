package com.mnchacks;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.util.math.MathHelper;

/**
 * Settings for the Click GUI expandable rows: booleans flip on click,
 * sliders set on click/drag across the bar.
 */
public abstract class Setting {

	private final String name;

	protected Setting(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public abstract String getValueText();

	public abstract boolean isSlider();

	public abstract double getFraction();

	public abstract void setFraction(double fraction);

	public abstract void onClick();

	public static final class Bool extends Setting {

		private final BooleanSupplier get;
		private final Runnable flip;

		public Bool(String name, BooleanSupplier get, Runnable flip) {
			super(name);
			this.get = get;
			this.flip = flip;
		}

		@Override
		public String getValueText() {
			return get.getAsBoolean() ? "ON" : "OFF";
		}

		@Override
		public boolean isSlider() {
			return false;
		}

		@Override
		public double getFraction() {
			return 0.0D;
		}

		@Override
		public void setFraction(double fraction) {
		}

		@Override
		public void onClick() {
			flip.run();
		}
	}

	public static final class Slider extends Setting {

		private final DoubleSupplier get;
		private final DoubleConsumer set;
		private final double min;
		private final double max;

		public Slider(String name, double min, double max, DoubleSupplier get, DoubleConsumer set) {
			super(name);
			this.min = min;
			this.max = max;
			this.get = get;
			this.set = set;
		}

		@Override
		public String getValueText() {
			return String.format(java.util.Locale.ROOT, "%.1f", get.getAsDouble());
		}

		@Override
		public boolean isSlider() {
			return true;
		}

		@Override
		public double getFraction() {
			return MathHelper.clamp((get.getAsDouble() - min) / (max - min), 0.0D, 1.0D);
		}

		@Override
		public void setFraction(double fraction) {
			set.accept(MathHelper.clamp(min + MathHelper.clamp(fraction, 0.0D, 1.0D) * (max - min), min, max));
		}

		@Override
		public void onClick() {
		}
	}
}