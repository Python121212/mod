package com.wisdomking.nation;

import java.util.Locale;

/**
 * 国家理念。外交スタンス・侵略性・生産ボーナスを決定する。
 * 動的王位継承により国王が交代すると、新国王の性格に応じて理念が変化しうる。
 */
public enum Ideology {
	/**  militarism: 軍事優先。侵略性が最も高い。 */
	MILITARISM(1.6f, 0.7f, 1.25f),
	/** mercantilism: 交易優先。交易収入が大きい。 */
	MERCANTILISM(0.8f, 1.6f, 1.0f),
	/** isolationism: 鎖国。防衛力が高く外交は消極的。 */
	ISOLATIONISM(0.6f, 0.6f, 1.4f),
	/** scholarship: 学術。魔法・研究・自動化に強い。 */
	SCHOLARSHIP(0.9f, 1.1f, 1.1f),
	/** theocracy: 神権。民の忠誠が高く不満が溜まりにくい。 */
	THEOCRACY(1.0f, 0.9f, 1.2f),
	/** technocracy: 技術官僚。工業・土木・機械化に強い。 */
	TECHNOCRACY(1.1f, 1.2f, 1.15f);

	private final float aggression;
	private final float tradeAptitude;
	private final float defenseBonus;

	Ideology(float aggression, float tradeAptitude, float defenseBonus) {
		this.aggression = aggression;
		this.tradeAptitude = tradeAptitude;
		this.defenseBonus = defenseBonus;
	}

	public float aggression() {
		return aggression;
	}

	public float tradeAptitude() {
		return tradeAptitude;
	}

	public float defenseBonus() {
		return defenseBonus;
	}

	public String key() {
		return "wisdomking.ideology." + name().toLowerCase(Locale.ROOT);
	}

	public static Ideology byName(String name, Ideology fallback) {
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (Throwable t) {
			return fallback;
		}
	}
}
