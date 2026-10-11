package com.wisdomking.equipment;

import java.util.Locale;

/**
 * 汎用装備分類（Universal Item Classifier）の分類結果。
 *
 * <p>バニラ・他Modを問わず、あらゆる武器・防具・道具をこの役割へ自動分類し、
 * NPC兵士の装備選択と攻撃スタイル切り替えに用いる。</p>
 */
public enum CombatRole {
	MELEE,
	RANGED,
	MAGIC,
	SHIELD,
	ARMOR_HEAD,
	ARMOR_CHEST,
	ARMOR_LEGS,
	ARMOR_FEET,
	TOOL,
	FOOD,
	PROJECTILE,
	BUILDING,
	MATERIAL,
	UTILITY,
	UNKNOWN;

	public boolean isWeapon() {
		return this == MELEE || this == RANGED || this == MAGIC;
	}

	public boolean isArmor() {
		return this == ARMOR_HEAD || this == ARMOR_CHEST || this == ARMOR_LEGS || this == ARMOR_FEET;
	}

	public String key() {
		return "wisdomking.role." + name().toLowerCase(Locale.ROOT);
	}

	/** NPC の攻撃スタイル。 */
	public AttackStyle style() {
		return switch (this) {
			case MELEE -> AttackStyle.MELEE;
			case RANGED -> AttackStyle.RANGED;
			case MAGIC -> AttackStyle.MAGIC;
			default -> AttackStyle.NONE;
		};
	}

	public enum AttackStyle {
		MELEE,
		RANGED,
		MAGIC,
		NONE
	}
}
