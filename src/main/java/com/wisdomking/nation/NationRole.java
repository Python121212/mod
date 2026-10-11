package com.wisdomking.nation;

import java.util.Locale;

/** NPC の役職。国家運営・戦争・生産の役割を決定する。 */
public enum NationRole {
	/** 国王。国家の中枢。死亡すると王位継承が発生する。 */
	KING(0.0f, 2.2f),
	/** 王子・王女。継承権を持つ。 */
	PRINCE(0.0f, 1.6f),
	/** 将軍。軍を率いる。 */
	GENERAL(0.0f, 2.0f),
	/** 兵士。戦闘と防衛を担う。 */
	SOLDIER(0.0f, 1.0f),
	/** 魔法兵。属性魔法を扱う。 */
	MAGE(0.0f, 1.4f),
	/** 工兵。土木・陣地構築を担う。 */
	ENGINEER(0.0f, 0.8f),
	/** 市民。生産・納税を担う。 */
	CITIZEN(0.0f, 0.4f),
	/** 商人。交易を担う。 */
	MERCHANT(0.0f, 0.6f),
	/** 職人。クラフトを担う。 */
	CRAFTSMAN(0.0f, 0.7f),
	/** 学者。研究を担う。 */
	SCHOLAR(0.0f, 0.5f);

	private final float combatBias;
	private final float authority;

	NationRole(float combatBias, float authority) {
		this.combatBias = combatBias;
		this.authority = authority;
	}

	public float combatBias() {
		return combatBias;
	}

	/** 発言力。継承順位やクーデター成功率に影響する。 */
	public float authority() {
		return authority;
	}

	public boolean isMilitary() {
		return this == SOLDIER || this == MAGE || this == GENERAL;
	}

	public boolean isLeaderClass() {
		return this == KING || this == PRINCE || this == GENERAL;
	}

	public String key() {
		return "wisdomking.role." + name().toLowerCase(Locale.ROOT);
	}

	public static NationRole byName(String name, NationRole fallback) {
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (Throwable t) {
			return fallback;
		}
	}
}
