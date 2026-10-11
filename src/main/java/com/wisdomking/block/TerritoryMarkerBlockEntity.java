package com.wisdomking.block;

import com.wisdomking.core.WKConfig;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** 領土マーカーのブロックエンティティ。宣言チャンク数と所有者を保持する。 */
public class TerritoryMarkerBlockEntity extends NationBlockEntity {
	private int claimedCount;
	private int radius = 2;

	public TerritoryMarkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** 領土を宣言する。 */
	public int declareTerritory(Nation nation, int radius) {
		this.radius = Math.max(1, radius);
		setNation(nation);
		NationManager manager = NationManager.get();
		if (manager == null) {
			return 0;
		}
		this.claimedCount = manager.territory().claimRadius(nation, new ChunkPos(worldPosition), this.radius);
		manager.markDirty();
		setChanged();
		return claimedCount;
	}

	public int claimedCount() {
		return claimedCount;
	}

	@Override
	public void onLoad() {
		super.onLoad();
		NationManager manager = NationManager.get();
		Nation nation = nation();
		if (manager != null && nation != null && level != null && !level.isClientSide) {
			this.claimedCount = manager.territory().claimRadius(nation, new ChunkPos(worldPosition), radius);
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, TerritoryMarkerBlockEntity be) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		long time = serverLevel.getGameTime();
		if (time % 400L != 0L) {
			return;
		}
		Nation nation = be.nation();
		NationManager manager = NationManager.get();
		if (nation == null || manager == null) {
			return;
		}
		// 戦時に破壊された場合の再宣言（マーカーが健在なら領土は維持される）
		manager.territory().claimRadius(nation, new ChunkPos(pos), be.radius);
		be.claimedCount = (be.radius * 2 + 1) * (be.radius * 2 + 1);
		if (WKConfig.get().protectTerritory && time % 2400L == 0L) {
			be.setChanged();
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Radius", radius);
		tag.putInt("Claimed", claimedCount);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		radius = Math.max(1, tag.getInt("Radius"));
		claimedCount = tag.getInt("Claimed");
	}

	@Override
	public CompoundTag describeForHud() {
		CompoundTag tag = super.describeForHud();
		tag.putBoolean("Territory", true);
		tag.putInt("Claimed", claimedCount);
		tag.putInt("Radius", radius);
		return tag;
	}
}
