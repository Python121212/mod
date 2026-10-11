package com.wisdomking.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 戦災記録（War Damage Journal）。
 *
 * <p>戦争中に破壊されたブロックの本来の状態を記録しておき、
 * 戦後の復興AIが「元の姿」へ復旧できるようにする。
 * 記録は国家単位で保持され、復興の優先順位（首都→施設→道路→宅地）に用いる。</p>
 */
public final class WarDamageJournal {
	public static final int LIMIT = 4096;

	/** 破壊された座標 -> 元のブロック状態。 */
	private final Map<Long, Entry> entries = new LinkedHashMap<>();

	public record Entry(BlockPos pos, String blockId, int priority) {
	}

	/** 破壊を記録する。 */
	public void record(BlockPos pos, BlockState original, int priority) {
		if (entries.size() >= LIMIT) {
			return;
		}
		ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(original.getBlock());
		entries.put(pos.asLong(), new Entry(pos.immutable(), id == null ? "minecraft:stone" : id.toString(), priority));
	}

	/** 復興対象を優先度順に取り出す。 */
	public java.util.List<Entry> pending() {
		java.util.List<Entry> list = new java.util.ArrayList<>(entries.values());
		list.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
		return list;
	}

	public int size() {
		return entries.size();
	}

	public void remove(BlockPos pos) {
		entries.remove(pos.asLong());
	}

	public void clear() {
		entries.clear();
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Entry entry : entries.values()) {
			CompoundTag child = new CompoundTag();
			child.putInt("X", entry.pos().getX());
			child.putInt("Y", entry.pos().getY());
			child.putInt("Z", entry.pos().getZ());
			child.putString("Block", entry.blockId());
			child.putInt("Priority", entry.priority());
			list.add(child);
		}
		tag.put("Entries", list);
		return tag;
	}

	public void load(CompoundTag tag) {
		entries.clear();
		ListTag list = tag.getList("Entries", 10);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag child = list.getCompound(i);
			BlockPos pos = new BlockPos(child.getInt("X"), child.getInt("Y"), child.getInt("Z"));
			entries.put(pos.asLong(), new Entry(pos, child.getString("Block"), child.getInt("Priority")));
		}
	}

	/** ブロックIDから状態を復元する。 */
	public static BlockState resolve(String blockId) {
		try {
			ResourceLocation id = ResourceLocation.parse(blockId);
			Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
			if (block != null && block != Blocks.AIR) {
				return block.defaultBlockState();
			}
		} catch (Throwable ignored) {
			// 未知のブロックは石で代替する
		}
		return Blocks.STONE.defaultBlockState();
	}
}
