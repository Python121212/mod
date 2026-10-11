package com.wisdomking.block;

import com.wisdomking.core.WKConfig;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.WorldPos;
import com.wisdomking.raphael.RaphaelRelay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * キャピタル・コア（首都中核）のブロックエンティティ。
 *
 * <p>国家の中枢であり、破壊・占領されると国家は降伏・滅亡・属国化する。
 * 定期的に領土を再宣言し、戦時に Raphael へ危機報告を行う。</p>
 */
public class CapitalCoreBlockEntity extends NationBlockEntity {
	public static final int CLAIM_RADIUS = 4;

	public CapitalCoreBlockEntity(BlockPos pos, BlockState state) {
		super(com.wisdomking.core.WKBlockEntities.CAPITAL_CORE, pos, state);
	}

	/** 国家の首都として登録する。 */
	public void registerAsCapital(Nation nation) {
		setNation(nation);
		nation.capital = WorldPos.of(level, worldPosition);
		nation.capitalMaterialized = true;
		NationManager manager = NationManager.get();
		if (manager != null) {
			manager.territory().claimRadius(nation, new ChunkPos(worldPosition), CLAIM_RADIUS);
			manager.markDirty();
		}
		setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CapitalCoreBlockEntity be) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		long time = serverLevel.getGameTime();
		if (time % 200L != 0L) {
			return;
		}
		Nation nation = be.nation();
		NationManager manager = NationManager.get();
		if (nation == null || manager == null) {
			return;
		}
		// 領土の再宣言（破壊されたマーカー等の復旧）
		manager.territory().claimRadius(nation, new ChunkPos(pos), CLAIM_RADIUS);
		be.lastSyncTick = time;
		// 戦時中の首都防衛報告
		if (nation.atWar() && time % 1200L == 0L) {
			RaphaelRelay.announceTo(serverLevel.getServer(), com.wisdomking.raphael.AnalysisReport.of(
					com.wisdomking.raphael.Tone.ALERT,
					"首都 " + nation.name + " の中核が攻撃下にあります。",
					com.wisdomking.raphael.Urgency.CRITICAL,
					"座標: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
					"首都陥落は即時の降伏・滅亡を意味します。防衛隊の配備を推奨します。"), nation);
		}
		be.setChanged();
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("Capital", true);
	}

	/** 防衛力（周辺のNPC兵士数）を数える。 */
	public int defendersNearby() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return 0;
		}
		int count = 0;
		for (net.minecraft.world.entity.Entity entity : serverLevel.getEntities((net.minecraft.world.entity.Entity) null,
				net.minecraft.world.phys.AABB.ofSize(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition), 48, 32, 48))) {
			if (entity instanceof com.wisdomking.nation.INationMember member
					&& member.belongsTo(nationId) && member.getNationRole().isMilitary()) {
				count++;
			}
		}
		return count;
	}

	@Override
	public CompoundTag describeForHud() {
		CompoundTag tag = super.describeForHud();
		tag.putBoolean("Capital", true);
		tag.putInt("Defenders", defendersNearby());
		tag.putString("Protection", WKConfig.get().protectTerritory ? "保護中" : "無効");
		return tag;
	}
}
