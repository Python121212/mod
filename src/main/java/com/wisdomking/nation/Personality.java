package com.wisdomking.nation;

import java.util.Locale;
import java.util.Random;

/**
 * NPC の性格。統治者・将軍・市民の行動傾向を決定する。
 * 王位継承時には新国王の性格が国家の理念・外交スタンスを書き換える。
 */
public enum Personality {
	/** 大胆。攻撃的で戦争を厭わない。 */
	BOLD(1.5f, 0.9f, 0.5f),
	/** 慎重。被害を避け防衛的。 */
	CAUTIOUS(0.6f, 1.1f, 1.2f),
	/** 野心家。クーデターを起こしやすい。 */
	AMBITIOUS(1.3f, 1.0f, 0.6f),
	/** 忠義者。既存の主君と国家に忠実。 */
	LOYAL(0.9f, 1.0f, 1.3f),
	/** 強欲。交易と搾取を好む。 */
	GREEDY(1.1f, 1.6f, 0.8f),
	/** 狂信者。理念のために戦う。 */
	ZEALOUS(1.4f, 0.8f, 1.1f),
	/** 慈悲深い。民の不満が上がりにくい。 */
	MERCIFUL(0.7f, 1.0f, 1.05f),
	/** 冷酷。制圧が速いが不満が溜まりやすい。 */
	CRUEL(1.45f, 1.05f, 0.75f);

	private final float aggression;
	private final float greed;
	private final float loyaltyBias;

	Personality(float aggression, float greed, float loyaltyBias) {
		this.aggression = aggression;
		this.greed = greed;
		this.loyaltyBias = loyaltyBias;
	}

	public float aggression() {
		return aggression;
	}

	public float greed() {
		return greed;
	}

	public float loyaltyBias() {
		return loyaltyBias;
	}

	/** クーデター（反逆）を起こす閾値の補正。野心家ほど低い。 */
	public float coupThresholdBias() {
		return switch (this) {
			case AMBITIOUS -> -0.22f;
			case GREEDY -> -0.10f;
			case LOYAL -> 0.30f;
			case MERCIFUL -> 0.16f;
			case CAUTIOUS -> 0.10f;
			default -> 0.0f;
		};
	}

	public String key() {
		return "wisdomking.personality." + name().toLowerCase(Locale.ROOT);
	}

	public static Personality byName(String name, Personality fallback) {
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (Throwable t) {
			return fallback;
		}
	}

	public static Personality random(Random random) {
		Personality[] values = values();
		return values[random.nextInt(values.length)];
	}
}
