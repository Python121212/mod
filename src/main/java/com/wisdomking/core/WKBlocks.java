package com.wisdomking.core;

import com.wisdomking.WK;
import com.wisdomking.block.CapitalCoreBlock;
import com.wisdomking.block.FacilityBlock;
import com.wisdomking.block.NationalChestBlock;
import com.wisdomking.block.TerritoryMarkerBlock;
import com.wisdomking.block.TradePostBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家運営ブロック群の登録。
 *
 * <p>すべて {@code wisdomking:} 名前空間で登録され、他Modと衝突しない。
 * テクスチャは {@code assets/wisdomking/textures/block/*} に 1:1 で対応付けられており、
 * 欠落時は自動生成の代替テクスチャへフォールバックする（Missing Texture 防止）。</p>
 */
public final class WKBlocks {
	public static final List<Block> ALL = new ArrayList<>();

	public static CapitalCoreBlock CAPITAL_CORE;
	public static TerritoryMarkerBlock TERRITORY_MARKER;
	public static NationalChestBlock NATIONAL_CHEST;
	public static TradePostBlock TRADE_POST;
	public static FacilityBlock FACILITY;

	private WKBlocks() {
	}

	private static <T extends Block> T register(String name, T block) {
		Registry.register(BuiltInRegistries.BLOCK, WK.id(name), block);
		ALL.add(block);
		return block;
	}

	public static void init() {
		CAPITAL_CORE = register("capital_core", new CapitalCoreBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.GOLD)
				.strength(25.0f, 1200.0f)
				.sound(SoundType.METAL)
				.lightLevel(state -> 12)
				.pushReaction(PushReaction.BLOCK)
				.requiresCorrectToolForDrops()));

		TERRITORY_MARKER = register("territory_marker", new TerritoryMarkerBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.STONE)
				.strength(6.0f, 12.0f)
				.sound(SoundType.STONE)
				.lightLevel(state -> 7)
				.pushReaction(PushReaction.BLOCK)));

		NATIONAL_CHEST = register("national_chest", new NationalChestBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.WOOD)
				.strength(5.0f)
				.sound(SoundType.WOOD)
				.pushReaction(PushReaction.BLOCK)));

		TRADE_POST = register("trade_post", new TradePostBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_ORANGE)
				.strength(4.0f)
				.sound(SoundType.WOOD)
				.pushReaction(PushReaction.BLOCK)));

		FACILITY = register("facility", new FacilityBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_LIGHT_BLUE)
				.strength(5.0f)
				.sound(SoundType.STONE)
				.pushReaction(PushReaction.BLOCK)));
	}
}
