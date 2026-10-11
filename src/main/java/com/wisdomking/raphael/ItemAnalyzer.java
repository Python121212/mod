package com.wisdomking.raphael;

import com.wisdomking.equipment.CombatRole;
import com.wisdomking.equipment.EquipmentScorer;
import com.wisdomking.equipment.ItemClassifier;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.ResourceType;
import com.wisdomking.world.ResourceMapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * アイテム・機械部品の解析器（アイテム解析）。
 *
 * <p>プレイヤーが視認またはインベントリに保持したアイテムについて、
 * 分類・性能・用途・資源換算・Mod由来情報を提示する。
 * Create の歯車や工業Modの部品も「どの生産チェーンに寄与するか」を推定して助言する。</p>
 */
public final class ItemAnalyzer {
	private ItemAnalyzer() {
	}

	/** アイテム単体の解析レポート。 */
	public static AnalysisReport analyze(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return AnalysisReport.of(Tone.ANALYSIS, "解析対象が空です。アイテムを手に持つか、視線を合わせてください。",
					Urgency.INFO, "対象: なし");
		}
		ResourceLocation id = ItemClassifier.itemId(stack);
		CombatRole role = ItemClassifier.classify(stack);
		String modName = ModScanner.displayName(id.getNamespace());
		double score = EquipmentScorer.score(stack, role);

		List<String> details = new ArrayList<>();
		details.add("登録ID: " + id + "（Mod: " + modName + "）");
		details.add("分類: " + role.name() + " / 総合評価スコア " + String.format("%.2f", score));
		if (role.isWeapon() || role.isArmor() || role == CombatRole.SHIELD) {
			details.add(String.format("攻撃 %.1f / 防御 %.1f / 強度 %.1f / エンチャント補正 %.1f / 耐久残 %d%%",
					EquipmentScorer.attackDamage(stack), EquipmentScorer.armorValue(stack),
					EquipmentScorer.toughness(stack), EquipmentScorer.enchantmentBonus(stack),
					Math.round(EquipmentScorer.durabilityFactor(stack) * 100)));
			details.add("戦闘スタイル: " + role.style().name() + "（NPC兵士は自動で間合いと攻撃手順を切り替えます）");
		} else {
			details.add("攻撃属性なし / スタック上限 " + stack.getMaxStackSize()
					+ " / 耐久 " + (stack.isDamageableItem() ? String.valueOf(stack.getMaxDamage()) : "無限"));
		}
		String tags = describeTags(stack);
		if (!tags.isEmpty()) {
			details.add("タグ: " + tags);
		}
		Rarity rarity = stack.getRarity();
		details.add("希少度: " + rarity.name() + " / エンチャント可能: " + stack.isEnchantable());
		ResourceType resource = ResourceMapper.resourceFor(stack);
		if (resource != null) {
			details.add("資源分類: " + resource.name() + "（国家備蓄に計上されます）");
			details.add("概算価値: " + String.format("%.2f", ResourceMapper.valueOf(stack, resource)));
		}
		details.add("推奨: " + recommend(stack, role));
		details.add(advice(stack, role));
		return new AnalysisReport(Tone.ANALYSIS,
				stack.getHoverName().getString() + " の解析結果", details, Urgency.INFO);
	}

	/** Mod固有の運用アドバイス。 */
	private static String advice(ItemStack stack, CombatRole role) {
		String namespace = ItemClassifier.idNamespace(stack);
		String path = ItemClassifier.idPath(stack);
		if (namespace.equals("create") || namespace.startsWith("create")) {
			if (path.contains("cogwheel") || path.contains("gear")) {
				return "Create 動力系: 歯車は回転伝達の中核です。大歯車と小歯車の比でトルクと速度を交換できます。";
			}
			if (path.contains("shaft")) {
				return "Create 動力系: シャフトは回転軸の延長に使用します。動力源から機械までの距離が長いほど損失が増えます。";
			}
			if (path.contains("belt")) {
				return "Create 動力系: ベルトは入力側と出力側の回転数を同調させます。搬送にも転用可能です。";
			}
			if (path.contains("water_wheel") || path.contains("windmill") || path.contains("sail")) {
				return "Create 動力源: 水流・風力は無燃料で安定した回転を供給します。負荷（ストレス）超過に注意してください。";
			}
			if (path.contains("steam_engine") || path.contains("engine")) {
				return "Create 動力源: 蒸気機関は高出力を供給しますが、燃料と水の供給線が必要です。";
			}
			return "Create 部品: 動力網（キネティック・ネットワーク）の一部として稼働します。";
		}
		if (role == CombatRole.MAGIC) {
			return "魔法系統: 魔法研究所で同系統スクロールを自動生産できます（魔法Mod導入時）。";
		}
		if (role == CombatRole.RANGED) {
			return "遠距離兵装: 弾薬・矢の供給を国庫に確保してください。";
		}
		if (role.isArmor()) {
			return "防具: 兵舎の装備配分により、最も防御力の高い個体から優先配備されます。";
		}
		if (role == CombatRole.BUILDING) {
			return "建材: 土木AIが道路・陣地構築に自動で消費します。";
		}
		return "汎用品: 国家備蓄へ納品するか、施設の自動クラフト原料として利用できます。";
	}

	private static String recommend(ItemStack stack, CombatRole role) {
		Nation nation = null;
		return switch (role) {
			case MELEE -> "近接兵（" + NationRole.SOLDIER.name() + "）に最適。将軍には追加で " + Math.round(EquipmentScorer.score(stack) * 0.6) + " の指揮補正が入ります。";
			case RANGED -> "射手・弩兵向け。高所陣地との組み合わせが有効です。";
			case MAGIC -> "魔法兵・学者向け。魔法研究所の研究対象になります。";
			case SHIELD -> "前衛兵の盾として配備。弓兵の被害を軽減します。";
			case ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET -> "該当スロットの兵士装備として自動配分されます。";
			case TOOL -> "工兵の土木作業効率を上昇させます。";
			case FOOD -> "市民の糧食。兵站（" + ResourceType.FOOD.name() + "）に計上されます。";
			case BUILDING -> "土木AIの建材ストックに加わります。";
			default -> "汎用素材として国庫へ納品可能です。";
		};
	}

	private static String describeTags(ItemStack stack) {
		List<String> tags = new ArrayList<>();
		try {
			stack.getTags().limit(6).forEach((TagKey<Item> tag) ->
					tags.add(tag.location().toString()));
		} catch (Throwable ignored) {
			// タグ取得に失敗しても解析は継続する
		}
		return String.join(", ", tags);
	}

	/** 手に持つアイテムの解析（スロット指定）。 */
	public static AnalysisReport analyzeHeld(net.minecraft.world.entity.LivingEntity entity,
			net.minecraft.world.InteractionHand hand) {
		ItemStack stack = entity.getItemInHand(hand);
		return analyze(stack);
	}

	/** インベントリ全体の装備価値解析（国庫・兵站評価）。 */
	public static AnalysisReport analyzeInventory(Iterable<ItemStack> stacks) {
		double bestWeapon = 0.0;
		double bestArmor = 0.0;
		int combatItems = 0;
		int total = 0;
		double value = 0.0;
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			total += stack.getCount();
			CombatRole role = ItemClassifier.classify(stack);
			double score = EquipmentScorer.score(stack, role);
			ResourceType resource = ResourceMapper.resourceFor(stack);
			if (resource != null) {
				value += ResourceMapper.valueOf(stack, resource) * stack.getCount();
			}
			if (role.isWeapon()) {
				bestWeapon = Math.max(bestWeapon, score);
				combatItems += stack.getCount();
			} else if (role.isArmor()) {
				bestArmor = Math.max(bestArmor, score);
				combatItems += stack.getCount();
			}
		}
		return AnalysisReport.of(Tone.ANALYSIS,
				"在庫解析: " + total + " 個のアイテムを検出", Urgency.INFO,
				String.format("最良武装スコア %.2f / 最良防具スコア %.2f", bestWeapon, bestArmor),
				"戦闘装備 " + combatItems + " 点",
				String.format("資源換算価値 %.1f", value));
	}
}
