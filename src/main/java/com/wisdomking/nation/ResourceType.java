package com.wisdomking.nation;

import java.util.Locale;

/**
 * 国家が扱う資源カテゴリ。地域ごとの気候・地形によって生産量が偏るため、
 * 資源格差が交易・侵略の誘因となる（地政学シミュレーションの中核）。
 */
public enum ResourceType {
	FOOD(1.0, 0.35),
	WOOD(1.0, 0.25),
	STONE(1.0, 0.20),
	METAL(0.6, 0.30),
	PRECIOUS(0.25, 0.12),
	ARCANE(0.35, 0.22),
	MACHINE(0.5, 0.28),
	LUXURY(0.4, 0.08);

	private final double baseProduction;
	private final double perCapitaDemand;

	ResourceType(double baseProduction, double perCapitaDemand) {
		this.baseProduction = baseProduction;
		this.perCapitaDemand = perCapitaDemand;
	}

	public double baseProduction() {
		return baseProduction;
	}

	public double perCapitaDemand() {
		return perCapitaDemand;
	}

	/** 生活必需品か（不足すると不満が急増する）。 */
	public boolean isEssential() {
		return this == FOOD || this == WOOD || this == STONE;
	}

	public String key() {
		return "wisdomking.resource." + name().toLowerCase(Locale.ROOT);
	}

	public static ResourceType byName(String name, ResourceType fallback) {
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (Throwable t) {
			return fallback;
		}
	}
}
