package com.wisdomking.nation;

import java.util.Locale;

/** 二国間関係の状態。 */
public enum RelationState {
	/** 中立。 */
	NEUTRAL,
	/** 友好（関係値 +60 以上）。 */
	FRIENDLY,
	/** 同盟（条約締結済み）。 */
	ALLIED,
	/** 交易協定。 */
	TRADE_PACT,
	/** 緊張。 */
	TENSE,
	/** 戦争状態。 */
	WAR,
	/** 休戦（停戦条約中）。 */
	TRUCE,
	/** 属国。 */
	VASSAL,
	/** 宗主国。 */
	OVERLORD;

	public String key() {
		return "wisdomking.relation." + name().toLowerCase(Locale.ROOT);
	}

	public static RelationState byName(String name, RelationState fallback) {
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (Throwable t) {
			return fallback;
		}
	}
}
