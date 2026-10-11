package com.wisdomking.world;

import com.wisdomking.nation.ResourceType;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * アイテム → 国家資源カテゴリの換算器。
 *
 * <p>Mod追加アイテムであっても、識別子・タグ・クラスから資源カテゴリを推定し、
 * 国庫への納品・交易価格・経済シミュレーションに反映する。
 * これにより未知のModアイテムでも国家経済に寄与させることができる。</p>
 */
public final class ResourceMapper {
	private ResourceMapper() {
	}

	private static final String[] METAL_HINTS = {
			"ingot", "nugget", "plate", "sheet", "rod", "wire", "gear", "cogwheel", "shaft",
			"copper", "iron", "steel", "bronze", "tin", "lead", "silver", "aluminium", "aluminum",
			"nickel", "zinc", "tungsten", "titanium", "brass", "ferrous"
	};
	private static final String[] PRECIOUS_HINTS = {
			"diamond", "emerald", "gold", "ruby", "sapphire", "amethyst", "topaz", "opal", "jade",
			"coin", "gem", "crystal_shard", "treasure", "jewel", "platinum", "iridium"
	};
	private static final String[] FOOD_HINTS = {
			"bread", "meat", "beef", "pork", "chicken", "fish", "apple", "carrot", "potato", "wheat",
			"rice", "soup", "stew", "pie", "cheese", "berry", "fruit", "food", "cooked", "raw_"
	};
	private static final String[] ARCANE_HINTS = {
			"scroll", "rune", "mana", "arcane", "essence", "spell", "soul", "ink", "glyph", "crystal",
			"cauldron", "ritual", "totem", "enchanted", "magic"
	};
	private static final String[] MACHINE_HINTS = {
			"circuit", "machine", "engine", "motor", "piston", "bearing", "gearbox", "conveyor",
			"belt", "turbine", "generator", "coil", "capacitor", "processor", "chip", "printed",
			"factory", "plant", "pump", "valve", "pipe", "cable", "fluix", "logic"
	};
	private static final String[] WOOD_HINTS = {
			"log", "wood", "plank", "sapling", "stick", "lumber", "timber", "bamboo", "board"
	};
	private static final String[] STONE_HINTS = {
			"stone", "cobble", "brick", "sand", "gravel", "clay", "granite", "diorite", "andesite",
			"deepslate", "basalt", "concrete", "glass", "terracotta", "slate", "marble", "limestone"
	};
	private static final String[] LUXURY_HINTS = {
			"wool", "carpet", "silk", "dye", "paint", "velvet", "tapestry", "cushion", "lantern",
			"chandelier", "statue", "banner", "crown", "tiara", "throne"
	};

	/** アイテムを資源カテゴリへ変換する（該当なしは null）。 */
	@Nullable
	public static ResourceType resourceFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String path = path(stack).toLowerCase(Locale.ROOT);
		String namespace = namespace(stack);

		if (stack.getFoodProperties(null) != null || containsAny(path, FOOD_HINTS)) {
			return ResourceType.FOOD;
		}
		if (containsAny(path, ARCANE_HINTS) || isMagicNamespace(namespace)) {
			return ResourceType.ARCANE;
		}
		if (containsAny(path, MACHINE_HINTS) || isMechanicalNamespace(namespace)) {
			return ResourceType.MACHINE;
		}
		if (containsAny(path, PRECIOUS_HINTS) || stack.is(ItemTags.BEACON_PAYMENT_ITEMS)) {
			return ResourceType.PRECIOUS;
		}
		if (containsAny(path, METAL_HINTS)) {
			return ResourceType.METAL;
		}
		if (containsAny(path, WOOD_HINTS) || stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)) {
			return ResourceType.WOOD;
		}
		if (containsAny(path, STONE_HINTS) || stack.is(ItemTags.STONE_CRAFTING_MATERIALS)) {
			return ResourceType.STONE;
		}
		if (containsAny(path, LUXURY_HINTS) || stack.is(ItemTags.WOOL)) {
			return ResourceType.LUXURY;
		}
		if (stack.getItem() instanceof BlockItem) {
			return ResourceType.STONE;
		}
		return null;
	}

	public static double valueOf(ItemStack stack, ResourceType type) {
		if (type == null) {
			return 0.0;
		}
		double base = switch (type) {
			case FOOD -> 0.8;
			case WOOD -> 0.7;
			case STONE -> 0.6;
			case METAL -> 1.8;
			case PRECIOUS -> 4.5;
			case ARCANE -> 3.2;
			case MACHINE -> 2.6;
			case LUXURY -> 2.2;
		};
		return base * Math.max(1, stack.getCount()) * (1.0 + rarityBonus(stack));
	}

	private static double rarityBonus(ItemStack stack) {
		return switch (stack.getRarity()) {
			case UNCOMMON -> 0.2;
			case RARE -> 0.5;
			case EPIC -> 1.0;
			default -> 0.0;
		};
	}

	private static boolean isMagicNamespace(String namespace) {
		return namespace.contains("spell") || namespace.contains("magic") || namespace.contains("arcane")
				|| namespace.contains("mana") || namespace.equals("irons_spellbooks");
	}

	private static boolean isMechanicalNamespace(String namespace) {
		return namespace.startsWith("create") || namespace.contains("machine") || namespace.contains("tech")
				|| namespace.equals("mekanism") || namespace.equals("ae2") || namespace.equals("immersiveengineering");
	}

	private static boolean containsAny(String path, String[] hints) {
		for (String hint : hints) {
			if (path.contains(hint)) {
				return true;
			}
		}
		return false;
	}

	private static String path(ItemStack stack) {
		var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id == null ? "" : id.getPath();
	}

	private static String namespace(ItemStack stack) {
		var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id == null ? "minecraft" : id.getNamespace();
	}
}
