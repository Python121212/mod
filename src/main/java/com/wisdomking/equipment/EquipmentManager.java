package com.wisdomking.equipment;

import com.wisdomking.block.NationalChestBlockEntity;
import com.wisdomking.core.WKLog;
import com.wisdomking.nation.INationMember;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NpcRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * スマート装備AI の配備担当。
 *
 * <p>国庫金庫にある武器・防具から、兵士ごとに「最も総合性能が高い装備」を選び、
 * 装備スロットへ自動適用する。Mod追加装備であっても
 * {@link ItemClassifier} と {@link EquipmentScorer} により適切に評価される。</p>
 */
public final class EquipmentManager {
	private EquipmentManager() {
	}

	/** 指定国家の兵士に装備を配分する。 */
	public static int assignToSoldiers(Nation nation, ServerLevel level) {
		int assigned = 0;
		try {
			List<NationalChestBlockEntity> chests = findChests(nation, level);
			if (chests.isEmpty()) {
				return 0;
			}
			AABB area = new AABB(-30000000, level.getMinBuildHeight(), -30000000,
					30000000, level.getMaxBuildHeight(), 30000000);
			List<Mob> candidates = new ArrayList<>();
			for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
					nationBoundingBox(level, nation))) {
				if (entity instanceof Mob mob && entity instanceof INationMember member
						&& member.belongsTo(nation.id) && member.getNationRole().isMilitary()) {
					candidates.add(mob);
				}
			}
			for (Mob mob : candidates) {
				assigned += equip(mob, nation, chests);
				if (assigned > 64) {
					break;
				}
			}
		} catch (Throwable t) {
			WKLog.warn("equipment assignment failed safely: " + t);
		}
		return assigned;
	}

	/** 国家の首都周辺を配備対象領域とする。 */
	private static AABB nationBoundingBox(ServerLevel level, Nation nation) {
		BlockPos center = nation.capital == null ? level.getSharedSpawnPos() : nation.capital.pos();
		return new AABB(center).inflate(256.0, 128.0, 256.0);
	}

	/** 国庫金庫を探す（首都周辺 + 読み込み済みチャンク）。 */
	public static List<NationalChestBlockEntity> findChests(Nation nation, ServerLevel level) {
		List<NationalChestBlockEntity> chests = new ArrayList<>();
		for (long chunkKey : nation.claimedChunks) {
			net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(chunkKey);
			if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
				continue;
			}
			net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
			for (var entry : chunk.getBlockEntities().entrySet()) {
				if (entry.getValue() instanceof NationalChestBlockEntity chest
						&& chest.nationIdEquals(nation)) {
					chests.add(chest);
				}
			}
		}
		return chests;
	}

	/** 個体1体への装備配分。 */
	public static int equip(Mob mob, Nation nation, List<NationalChestBlockEntity> chests) {
		int changed = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
				EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack current = mob.getItemBySlot(slot);
			double currentScore = current.isEmpty() ? -1.0 : EquipmentScorer.score(current);
			for (NationalChestBlockEntity chest : chests) {
				ItemStack candidate = chest.extractBestCombatItem(slot, currentScore);
				if (!candidate.isEmpty()) {
					if (!current.isEmpty()) {
						chest.deposit(current);
					}
					mob.setItemSlot(slot, candidate);
					mob.setDropChance(slot, 0.0f);
					currentScore = EquipmentScorer.score(candidate);
					changed++;
				}
			}
		}
		if (changed > 0) {
			NpcRecord record = nation.subject(mob.getUUID());
			if (record != null) {
				record.power += changed * 0.15f;
				record.clamp();
				NationManager manager = NationManager.get();
				if (manager != null) {
					manager.markDirty();
				}
			}
		}
		return changed;
	}

	/** 兵士の装備状況レポート（Raphael・GUI 用）。 */
	public static List<String> describeLoadout(Mob mob) {
		List<String> lines = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR && slot != EquipmentSlot.MAINHAND) {
				continue;
			}
			ItemStack stack = mob.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			CombatRole role = ItemClassifier.classify(stack);
			lines.add(String.format("%s: %s [%s / スコア %.2f]", slot.getName(),
					stack.getHoverName().getString(), role.name(), EquipmentScorer.score(stack, role)));
		}
		return lines;
	}
}
