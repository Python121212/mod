package com.wisdomking.nation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 領土（チャンク所有権）と領域保護の管理。
 *
 * <p>「領土マーカー」「キャピタル・コア」が周囲のチャンクを自国領土として宣言する。
 * 保護されたチャンクでは他国のプレイヤー／NPCによるブロック破壊・設置・爆発を拒否する。</p>
 */
public final class TerritoryManager {
	private final NationManager manager;
	/** チャンク(long) -> 国家ID の逆引きインデックス。 */
	private final Map<Long, UUID> chunkIndex = new HashMap<>();

	public TerritoryManager(NationManager manager) {
		this.manager = manager;
	}

	public static long chunkKey(BlockPos pos) {
		return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
	}

	public static long chunkKey(ChunkPos pos) {
		return ChunkPos.asLong(pos.x, pos.z);
	}

	/** インデックスを国家データから再構築する（ロード直後・領土変更時に呼ぶ）。 */
	public void rebuild() {
		chunkIndex.clear();
		for (Nation nation : manager.nations()) {
			if (!nation.alive) {
				continue;
			}
			for (long key : nation.claimedChunks) {
				UUID owner = chunkIndex.get(key);
				// 先着優先。ただし属国より宗主国を優先しない（マーカー設置者が実効支配）。
				if (owner == null) {
					chunkIndex.put(key, nation.id);
				}
			}
		}
	}

	@Nullable
	public UUID ownerOf(long chunkKey) {
		return chunkIndex.get(chunkKey);
	}

	@Nullable
	public Nation nationAt(Level level, BlockPos pos) {
		UUID owner = ownerOf(chunkKey(pos));
		return owner == null ? null : manager.nation(owner);
	}

	@Nullable
	public Nation nationAt(Entity entity) {
		return nationAt(entity.level(), entity.blockPosition());
	}

	/** 指定チャンクを国家領土として宣言する。 */
	public void claim(Nation nation, ChunkPos pos) {
		long key = chunkKey(pos);
		UUID current = chunkIndex.get(key);
		if (current != null && !current.equals(nation.id)) {
			Nation previous = manager.nation(current);
			if (previous != null) {
				previous.claimedChunks.remove(key);
			}
		}
		nation.claimedChunks.add(key);
		chunkIndex.put(key, nation.id);
		manager.markDirty();
	}

	/** 中心チャンクから半径 {@code radius} チャンクを宣言する。 */
	public int claimRadius(Nation nation, ChunkPos center, int radius) {
		int claimed = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				claim(nation, new ChunkPos(center.x + dx, center.z + dz));
				claimed++;
			}
		}
		return claimed;
	}

	public void releaseChunk(Nation nation, ChunkPos pos) {
		long key = chunkKey(pos);
		nation.claimedChunks.remove(key);
		if (nation.id.equals(chunkIndex.get(key))) {
			chunkIndex.remove(key);
		}
		manager.markDirty();
	}

	/** 国家滅亡時に全領土を解放する。 */
	public void releaseAll(Nation nation) {
		for (long key : new HashSet<>(nation.claimedChunks)) {
			if (nation.id.equals(chunkIndex.get(key))) {
				chunkIndex.remove(key);
			}
		}
		nation.claimedChunks.clear();
		manager.markDirty();
	}

	/** 国家の領土を勝者へ移譲する（戦争の結果）。 */
	public int transferAll(Nation from, Nation to) {
		int moved = 0;
		for (long key : new HashSet<>(from.claimedChunks)) {
			from.claimedChunks.remove(key);
			to.claimedChunks.add(key);
			chunkIndex.put(key, to.id);
			moved++;
		}
		manager.markDirty();
		return moved;
	}

	/** その座標が保護対象か（所有者が存在するか）。 */
	public boolean isProtected(Level level, BlockPos pos) {
		return ownerOf(chunkKey(pos)) != null;
	}

	/**
	 * 指定プレイヤーが座標を改変してよいかを判定する。
	 *
	 * @return 改変可能なら true
	 */
	public boolean canModify(Level level, BlockPos pos, @Nullable Player player) {
		UUID ownerId = ownerOf(chunkKey(pos));
		if (ownerId == null) {
			return true; // 無所属地は自由
		}
		Nation owner = manager.nation(ownerId);
		if (owner == null || !owner.alive) {
			return true;
		}
		if (player == null) {
			return false;
		}
		if (player.isCreative() && player.hasPermissions(2)) {
			return true; // OP は保護を無視できる（管理用途）
		}
		// 自国領土・同盟国領土・宗主国領土は改変可能
		UUID playerNation = manager.nationIdOfPlayer(player);
		if (playerNation == null) {
			return false;
		}
		if (playerNation.equals(owner.id)) {
			return true;
		}
		Relation rel = manager.nation(playerNation) == null ? null : manager.nation(playerNation).relations.get(owner.id);
		if (rel == null) {
			return false;
		}
		return rel.state == RelationState.ALLIED || rel.state == RelationState.OVERLORD
				|| rel.state == RelationState.VASSAL || rel.state == RelationState.TRUCE;
	}

	/** 国家の首都座標（未設定なら null）。 */
	@Nullable
	public BlockPos capitalOf(Nation nation) {
		return nation.capital == null ? null : nation.capital.pos();
	}

	/** チャンク所有者マップのスナップショット（デバッグ・GUI用）。 */
	public Map<Long, UUID> snapshot() {
		return new HashMap<>(chunkIndex);
	}

	public Set<Long> chunksOf(UUID nation) {
		Nation nation1 = manager.nation(nation);
		return nation1 == null ? Set.of() : new HashSet<>(nation1.claimedChunks);
	}
}
