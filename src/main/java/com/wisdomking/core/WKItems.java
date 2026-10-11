package com.wisdomking.core;

import com.wisdomking.WK;
import com.wisdomking.item.NationCharterItem;
import com.wisdomking.item.RaphaelTerminalItem;
import com.wisdomking.item.TerritoryWandItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;

/** アイテム登録（ツール・ブロックアイテム・クリエイティブタブ）。 */
public final class WKItems {
	public static final List<Item> ALL = new ArrayList<>();

	public static Item CAPITAL_CORE;
	public static Item TERRITORY_MARKER;
	public static Item NATIONAL_CHEST;
	public static Item TRADE_POST;
	public static Item FACILITY;
	public static Item RAPHAEL_TERMINAL;
	public static Item NATION_CHARTER;
	public static Item TERRITORY_WAND;
	public static CreativeModeTab TAB;

	private WKItems() {
	}

	private static <T extends Item> T register(String name, T item) {
		Registry.register(BuiltInRegistries.ITEM, WK.id(name), item);
		ALL.add(item);
		return item;
	}

	public static void init() {
		CAPITAL_CORE = register("capital_core", new BlockItem(WKBlocks.CAPITAL_CORE,
				new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
		TERRITORY_MARKER = register("territory_marker", new BlockItem(WKBlocks.TERRITORY_MARKER,
				new Item.Properties().rarity(Rarity.UNCOMMON)));
		NATIONAL_CHEST = register("national_chest", new BlockItem(WKBlocks.NATIONAL_CHEST,
				new Item.Properties().rarity(Rarity.UNCOMMON)));
		TRADE_POST = register("trade_post", new BlockItem(WKBlocks.TRADE_POST,
				new Item.Properties().rarity(Rarity.UNCOMMON)));
		FACILITY = register("facility", new BlockItem(WKBlocks.FACILITY,
				new Item.Properties().rarity(Rarity.UNCOMMON)));

		RAPHAEL_TERMINAL = register("raphael_terminal", new RaphaelTerminalItem(
				new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
		NATION_CHARTER = register("nation_charter", new NationCharterItem(
				new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
		TERRITORY_WAND = register("territory_wand", new TerritoryWandItem(
				new Item.Properties().stacksTo(1)));

		TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, WK.id("main"),
				CreativeModeTab.builder(CreativeModeTab.Row.TOP, 7)
						.title(Component.translatable("itemGroup.wisdomking"))
						.icon(() -> new ItemStack(CAPITAL_CORE))
						.displayItems((parameters, output) -> {
							output.accept(CAPITAL_CORE);
							output.accept(TERRITORY_MARKER);
							output.accept(NATIONAL_CHEST);
							output.accept(TRADE_POST);
							output.accept(FACILITY);
							output.accept(RAPHAEL_TERMINAL);
							output.accept(NATION_CHARTER);
							output.accept(TERRITORY_WAND);
						})
						.build());
	}
}
