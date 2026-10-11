package com.wisdomking.core;

import com.wisdomking.WK;
import com.wisdomking.block.CapitalCoreBlockEntity;
import com.wisdomking.block.FacilityBlockEntity;
import com.wisdomking.block.NationalChestBlockEntity;
import com.wisdomking.block.TerritoryMarkerBlockEntity;
import com.wisdomking.block.TradePostBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** ブロックエンティティ型の登録。 */
public final class WKBlockEntities {
	public static BlockEntityType<CapitalCoreBlockEntity> CAPITAL_CORE;
	public static BlockEntityType<TerritoryMarkerBlockEntity> TERRITORY_MARKER;
	public static BlockEntityType<NationalChestBlockEntity> NATIONAL_CHEST;
	public static BlockEntityType<TradePostBlockEntity> TRADE_POST;
	public static BlockEntityType<FacilityBlockEntity> FACILITY;

	private WKBlockEntities() {
	}

	public static void init() {
		CAPITAL_CORE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, WK.id("capital_core"),
				BlockEntityType.Builder.of(CapitalCoreBlockEntity::new, WKBlocks.CAPITAL_CORE).build());

		TERRITORY_MARKER = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, WK.id("territory_marker"),
				BlockEntityType.Builder.of(TerritoryMarkerBlockEntity::new, WKBlocks.TERRITORY_MARKER).build());

		NATIONAL_CHEST = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, WK.id("national_chest"),
				BlockEntityType.Builder.of(NationalChestBlockEntity::new, WKBlocks.NATIONAL_CHEST).build());

		TRADE_POST = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, WK.id("trade_post"),
				BlockEntityType.Builder.of(TradePostBlockEntity::new, WKBlocks.TRADE_POST).build());

		FACILITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, WK.id("facility"),
				BlockEntityType.Builder.of(FacilityBlockEntity::new, WKBlocks.FACILITY).build());
	}
}
