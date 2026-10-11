package com.wisdomking.equipment;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import java.util.Locale;

/**
 * 全Mod対応の装備自動判定器（Universal Item Classifier）。
 *
 * <p>判定は以下の順に多段的に行い、未知のModアイテムでも可能な限り分類する:</p>
 * <ol>
 *   <li>バニラ / 既知の {@link Item} 具象クラス（SwordItem, BowItem, ArmorItem 等）</li>
 *   <li>共通タグ（{@code c:weapons} / {@code c:armors} / {@code c:bows} / {@code minecraft:swords} 等）</li>
 *   <li>属性値（攻撃力・防御力の属性モディファイア）</li>
 *   <li>識別子の命名ヒューリスティクス（gun, rifle, staff, wand, spear, helmet 等）</li>
 * </ol>
 *
 * <p>これにより Create の工具、Iron's Spells の魔法杖、銃器Modの小火器なども
 * NPCが「使える道具」として認識できる。</p>
 */
public final class ItemClassifier {
	// 共通タグ（Fabric / NeoForge 共通の c: 名前空間）
	public static final TagKey<Item> C_WEAPONS = commonTag("weapons");
	public static final TagKey<Item> C_SWORDS = commonTag("swords");
	public static final TagKey<Item> C_AXES = commonTag("axes");
	public static final TagKey<Item> C_BOWS = commonTag("bows");
	public static final TagKey<Item> C_SPEARS = commonTag("spears");
	public static final TagKey<Item> C_ARMORS = commonTag("armors");
	public static final TagKey<Item> C_HELMETS = commonTag("helmets");
	public static final TagKey<Item> C_CHESTPLATES = commonTag("chestplates");
	public static final TagKey<Item> C_LEGGINGS = commonTag("leggings");
	public static final TagKey<Item> C_BOOTS = commonTag("boots");
	public static final TagKey<Item> C_SHIELDS = commonTag("shields");
	public static final TagKey<Item> C_RANGED_WEAPONS = commonTag("ranged_weapons");
	public static final TagKey<Item> C_MAGIC = commonTag("magic");
	public static final TagKey<Item> C_TOOLS = commonTag("tools");

	private static final String[] MELEE_HINTS = {
			"sword", "katana", "blade", "dagger", "spear", "halberd", "glaive", "mace", "hammer",
			"battleaxe", "scimitar", "saber", "claymore", "rapier", "scythe", "axe", "lance", "club"
	};
	private static final String[] RANGED_HINTS = {
			"gun", "rifle", "pistol", "musket", "cannon", "bow", "crossbow", "launcher", "slingshot",
			"blowgun", "dart", "javelin", "thrower", "ballista"
	};
	private static final String[] MAGIC_HINTS = {
			"staff", "wand", "scepter", "sceptre", "spell", "scroll", "tome", "grimoire", "orb",
			"rod", "focus", "crystal", "runic", "arcane", "magic", "mana", "rune"
	};
	private static final String[] ARMOR_HINTS = {
			"helmet", "helm", "hat", "hood", "crown", "chestplate", "cuirass", "leggings", "greaves",
			"boots", "sandals", "gauntlet", "plate", "armor", "robe"
	};
	private static final String[] SHIELD_HINTS = {"shield", "buckler", "aegis", "targe"};
	private static final String[] TOOL_HINTS = {
			"pickaxe", "shovel", "hoe", "wrench", "screwdriver", "drill", "saw", "mallet", "pliers",
			"spanner", "hammer", "chisel", "tool"
	};

	private ItemClassifier() {
	}

	public static TagKey<Item> commonTag(String path) {
		return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
	}

	public static TagKey<Item> tag(String namespace, String path) {
		return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
	}

	/** 多段判定による分類。 */
	public static CombatRole classify(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return CombatRole.UNKNOWN;
		}
		Item item = stack.getItem();
		String path = idPath(stack);

		// --- 1. 具象クラス判定（最も確実） ---
		if (item instanceof SwordItem) {
			return CombatRole.MELEE;
		}
		if (item instanceof ShieldItem) {
			return CombatRole.SHIELD;
		}
		if (item instanceof BowItem || item instanceof CrossbowItem) {
			return CombatRole.RANGED;
		}
		if (item instanceof TridentItem) {
			return isMagicNamed(path) ? CombatRole.MAGIC : CombatRole.MELEE;
		}
		if (item instanceof ArmorItem armor) {
			return switch (armor.getEquipmentSlot()) {
				case HEAD -> CombatRole.ARMOR_HEAD;
				case CHEST -> CombatRole.ARMOR_CHEST;
				case LEGS -> CombatRole.ARMOR_LEGS;
				case FEET -> CombatRole.ARMOR_FEET;
				default -> CombatRole.ARMOR_CHEST;
			};
		}
		if (item instanceof ArrowItem) {
			return CombatRole.PROJECTILE;
		}
		if (item instanceof HoeItem) {
			return CombatRole.TOOL;
		}

		// --- 2. タグ判定（Mod間共通の取り決め） ---
		CombatRole tagRole = classifyByTags(stack);
		if (tagRole != CombatRole.UNKNOWN) {
			return tagRole;
		}

		// --- 3. 属性値判定（攻撃力・防御力を持つなら武器／防具） ---
		CombatRole attributeRole = classifyByAttributes(stack);
		if (attributeRole != CombatRole.UNKNOWN) {
			return attributeRole;
		}

		// --- 4. 命名ヒューリスティクス（最後の砦） ---
		CombatRole hintRole = classifyByName(path);
		if (hintRole != CombatRole.UNKNOWN) {
			return hintRole;
		}

		if (item instanceof AxeItem || item instanceof DiggerItem) {
			return CombatRole.TOOL;
		}
		if (item instanceof BlockItem) {
			return CombatRole.BUILDING;
		}
		if (stack.get(net.minecraft.core.component.DataComponents.FOOD) != null) {
			return CombatRole.FOOD;
		}
		if (item == Items.STICK || item == Items.IRON_INGOT || item == Items.GOLD_INGOT
				|| item == Items.DIAMOND || item == Items.NETHERITE_INGOT) {
			return CombatRole.MATERIAL;
		}
		return CombatRole.UTILITY;
	}

	private static CombatRole classifyByTags(ItemStack stack) {
		if (stack.is(C_WEAPONS) || stack.is(C_SWORDS) || stack.is(C_AXES) || stack.is(C_SPEARS)) {
			return stack.is(C_AXES) ? CombatRole.MELEE : CombatRole.MELEE;
		}
		if (stack.is(C_RANGED_WEAPONS) || stack.is(C_BOWS)) {
			return CombatRole.RANGED;
		}
		if (stack.is(C_SHIELDS)) {
			return CombatRole.SHIELD;
		}
		if (stack.is(C_MAGIC)) {
			return CombatRole.MAGIC;
		}
		if (stack.is(C_HELMETS)) {
			return CombatRole.ARMOR_HEAD;
		}
		if (stack.is(C_CHESTPLATES) || stack.is(C_ARMORS)) {
			return CombatRole.ARMOR_CHEST;
		}
		if (stack.is(C_LEGGINGS)) {
			return CombatRole.ARMOR_LEGS;
		}
		if (stack.is(C_BOOTS)) {
			return CombatRole.ARMOR_FEET;
		}
		// バニラタグ
		if (stack.is(ItemTags.SWORDS)) {
			return CombatRole.MELEE;
		}
		if (stack.is(ItemTags.BOW_ENCHANTABLE)) {
			return CombatRole.RANGED;
		}
		if (stack.is(ItemTags.HEAD_ARMOR)) {
			return CombatRole.ARMOR_HEAD;
		}
		if (stack.is(ItemTags.CHEST_ARMOR)) {
			return CombatRole.ARMOR_CHEST;
		}
		if (stack.is(ItemTags.LEG_ARMOR)) {
			return CombatRole.ARMOR_LEGS;
		}
		if (stack.is(ItemTags.FOOT_ARMOR)) {
			return CombatRole.ARMOR_FEET;
		}
		if (stack.is(C_TOOLS) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)) {
			return CombatRole.TOOL;
		}
		return CombatRole.UNKNOWN;
	}

	private static CombatRole classifyByAttributes(ItemStack stack) {
		double attack = EquipmentScorer.attackDamage(stack);
		double armor = EquipmentScorer.armorValue(stack);
		if (armor >= 1.0) {
			String path = idPath(stack);
			if (path.contains("helmet") || path.contains("helm")) {
				return CombatRole.ARMOR_HEAD;
			}
			if (path.contains("legging") || path.contains("greave")) {
				return CombatRole.ARMOR_LEGS;
			}
			if (path.contains("boot") || path.contains("feet")) {
				return CombatRole.ARMOR_FEET;
			}
			return CombatRole.ARMOR_CHEST;
		}
		if (attack > 1.5) {
			String path = idPath(stack);
			if (isMagicNamed(path)) {
				return CombatRole.MAGIC;
			}
			if (isRangedNamed(path)) {
				return CombatRole.RANGED;
			}
			return CombatRole.MELEE;
		}
		return CombatRole.UNKNOWN;
	}

	private static CombatRole classifyByName(String path) {
		if (path.isEmpty()) {
			return CombatRole.UNKNOWN;
		}
		String lower = path.toLowerCase(Locale.ROOT);
		if (containsAny(lower, SHIELD_HINTS)) {
			return CombatRole.SHIELD;
		}
		if (containsAny(lower, ARMOR_HINTS)) {
			if (lower.contains("helmet") || lower.contains("helm") || lower.contains("hood")
					|| lower.contains("hat") || lower.contains("crown")) {
				return CombatRole.ARMOR_HEAD;
			}
			if (lower.contains("legging") || lower.contains("greave")) {
				return CombatRole.ARMOR_LEGS;
			}
			if (lower.contains("boot") || lower.contains("sandal")) {
				return CombatRole.ARMOR_FEET;
			}
			return CombatRole.ARMOR_CHEST;
		}
		if (isMagicNamed(lower)) {
			return CombatRole.MAGIC;
		}
		if (isRangedNamed(lower)) {
			return CombatRole.RANGED;
		}
		if (containsAny(lower, MELEE_HINTS)) {
			return CombatRole.MELEE;
		}
		if (containsAny(lower, TOOL_HINTS)) {
			return CombatRole.TOOL;
		}
		return CombatRole.UNKNOWN;
	}

	private static boolean isMagicNamed(String path) {
		return containsAny(path, MAGIC_HINTS);
	}

	private static boolean isRangedNamed(String path) {
		return containsAny(path, RANGED_HINTS);
	}

	private static boolean containsAny(String path, String[] hints) {
		for (String hint : hints) {
			if (path.contains(hint)) {
				return true;
			}
		}
		return false;
	}

	/** アイテムの登録名（{@code namespace:path}）。 */
	public static ResourceLocation itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	public static String idPath(ItemStack stack) {
		ResourceLocation id = itemId(stack);
		return id == null ? "" : id.getPath();
	}

	public static String idNamespace(ItemStack stack) {
		ResourceLocation id = itemId(stack);
		return id == null ? "minecraft" : id.getNamespace();
	}

	/** 装備スロットへの割当。防具以外は手に持つ。 */
	public static EquipmentSlot slotFor(CombatRole role) {
		return switch (role) {
			case ARMOR_HEAD -> EquipmentSlot.HEAD;
			case ARMOR_CHEST -> EquipmentSlot.CHEST;
			case ARMOR_LEGS -> EquipmentSlot.LEGS;
			case ARMOR_FEET -> EquipmentSlot.FEET;
			default -> EquipmentSlot.MAINHAND;
		};
	}

	public static boolean isWeapon(ItemStack stack) {
		return classify(stack).isWeapon();
	}

	public static boolean isArmor(ItemStack stack) {
		return classify(stack).isArmor();
	}

	public static boolean isCombatItem(ItemStack stack) {
		CombatRole role = classify(stack);
		return role.isWeapon() || role.isArmor() || role == CombatRole.SHIELD;
	}
}
