package com.wisdomking.block;

import com.wisdomking.nation.ResourceType;

import java.util.Locale;

/**
 * 施設種別。施設指示ブロックに付与され、NPCの転職・自動クラフト・研究を有効化する。
 */
public enum FacilityType {
	/** 兵舎: 兵士の徴兵・装備配分。 */
	BARRACKS(ResourceType.METAL, 0.05),
	/** ギルド: 市民の転職・職人育成。 */
	GUILD(ResourceType.WOOD, 0.04),
	/** 魔法研究所: 魔法スクロール・秘術資源の生産。 */
	MAGIC_LAB(ResourceType.ARCANE, 0.06),
	/** 工房: レシピ自動クラフト（全Mod対応）。 */
	WORKSHOP(ResourceType.MACHINE, 0.07),
	/** 倉庫: 備蓄容量の拡張と納品の加速。 */
	WAREHOUSE(ResourceType.STONE, 0.05),
	/** 市場: 交易収入の増加。 */
	MARKET(ResourceType.LUXURY, 0.06);

	private final ResourceType focus;
	private final double outputRate;

	FacilityType(ResourceType focus, double outputRate) {
		this.focus = focus;
		this.outputRate = outputRate;
	}

	public ResourceType focus() {
		return focus;
	}

	public double outputRate() {
		return outputRate;
	}

	public String key() {
		return "wisdomking.facility." + name().toLowerCase(Locale.ROOT);
	}

	public static FacilityType byName(String name, FacilityType fallback) {
		try {
			return valueOf(name);
		} catch (Throwable t) {
			return fallback;
		}
	}
}
