package com.wisdomking.equipment;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Map;

/**
 * 装備の総合性能評価器。
 *
 * <p>攻撃力・防御力・耐久値・エンチャント・攻撃速度を統合し、
 * NPCが「国庫にある中で最も強い装備」を自動選択するためのスコアを算出する。
 * 属性は ItemStack#forEachModifier から直接読むため、Mod追加装備でも機能する。</p>
 */
public final class EquipmentScorer {
	private EquipmentScorer() {
	}

	/** 攻撃力（属性合計）。 */
	public static double attackDamage(ItemStack stack) {
		return attributeValue(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND);
	}

	/** 攻撃速度。 */
	public static double attackSpeed(ItemStack stack) {
		return attributeValue(stack, Attributes.ATTACK_SPEED, EquipmentSlot.MAINHAND);
	}

	/** 防御力。 */
	public static double armorValue(ItemStack stack) {
		return attributeValue(stack, Attributes.ARMOR, null);
	}

	/** タフネス（防具強度）。 */
	public static double toughness(ItemStack stack) {
		return attributeValue(stack, Attributes.ARMOR_TOUGHNESS, null);
	}

	/** ノックバック耐性。 */
	public static double knockbackResistance(ItemStack stack) {
		return attributeValue(stack, Attributes.KNOCKBACK_RESISTANCE, null);
	}

	private static double attributeValue(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
		if (attribute == null) {
			return 0.0;
		}
		final double[] total = {0.0};
		final java.util.Set<String> seen = new java.util.HashSet<>();
		final java.util.function.BiConsumer<Holder<Attribute>, AttributeModifier> consumer = (attr, modifier) -> {
			try {
				if (attr == null || modifier == null || attr.value() != attribute.value()) {
					return;
				}
				if (!seen.add(modifier.id().toString())) {
					return;
				}
				total[0] += modifier.amount();
			} catch (Throwable ignored) {
				// 個別属性の解析失敗は無視する（他Modの特殊属性対策）
			}
		};
		try {
			if (slot != null) {
				stack.forEachModifier(slot, consumer);
			} else {
				for (EquipmentSlot candidate : EquipmentSlot.values()) {
					stack.forEachModifier(candidate, consumer);
				}
			}
		} catch (Throwable t) {
			return 0.0;
		}
		return total[0];
	}

	/** エンチャントの総合補正。 */
	public static double enchantmentBonus(ItemStack stack) {
		double bonus = 0.0;
		try {
			for (Map.Entry<Holder<Enchantment>, Integer> entry : stack.getEnchantments().entrySet()) {
				bonus += entry.getValue() * 0.9;
			}
		} catch (Throwable ignored) {
			// エンチャント情報が取得できないModアイテムは無補正
		}
		return bonus;
	}

	/** 耐久性（残耐久の割合 0..1）。 */
	public static double durabilityFactor(ItemStack stack) {
		try {
			if (!stack.isDamageableItem()) {
				return 1.0;
			}
			int max = stack.getMaxDamage();
			if (max <= 0) {
				return 1.0;
			}
			return Math.max(0.15, 1.0 - (double) stack.getDamageValue() / max);
		} catch (Throwable t) {
			return 1.0;
		}
	}

	/**
	 * 総合スコア。役割ごとに重み付けを変える。
	 *
	 * @param role 分類済みの役割
	 */
	public static double score(ItemStack stack, CombatRole role) {
		double score = 0.0;
		double durability = durabilityFactor(stack);
		double enchant = enchantmentBonus(stack);
		switch (role) {
			case MELEE -> score = attackDamage(stack) * 2.6 + attackSpeed(stack) * 1.6 + enchant * 0.8;
			case RANGED -> score = attackDamage(stack) * 1.9 + enchant * 1.1 + 2.5;
			case MAGIC -> score = attackDamage(stack) * 1.5 + enchant * 1.6 + 4.0;
			case SHIELD -> score = 3.2 + toughness(stack) + enchant * 0.5;
			case ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET ->
					score = armorValue(stack) * 2.4 + toughness(stack) * 1.3 + enchant * 0.7
							+ knockbackResistance(stack) * 2.0;
			case TOOL -> score = attackDamage(stack) * 0.8 + enchant * 0.3;
			default -> score = 0.5 + enchant * 0.2;
		}
		return score * (0.55 + durability * 0.45);
	}

	public static double score(ItemStack stack) {
		return score(stack, ItemClassifier.classify(stack));
	}

	/** 性能差の説明文（Raphael が提示する比較情報）。 */
	public static String describe(ItemStack stack) {
		CombatRole role = ItemClassifier.classify(stack);
		return String.format("%s: 攻撃 %.1f / 防御 %.1f / 強度 %.1f / エンチャ %.1f / スコア %.1f",
				role.name(), attackDamage(stack), armorValue(stack), toughness(stack),
				enchantmentBonus(stack), score(stack, role));
	}
}
