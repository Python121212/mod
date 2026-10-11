package com.wisdomking.nation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;

/** 次元つき座標。国家の首都位置などを保持する。 */
public record WorldPos(ResourceLocation dimension, BlockPos pos) {

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Dim", dimension.toString());
		tag.putInt("X", pos.getX());
		tag.putInt("Y", pos.getY());
		tag.putInt("Z", pos.getZ());
		return tag;
	}

	public static WorldPos load(CompoundTag tag) {
		ResourceLocation dim;
		try {
			dim = ResourceLocation.parse(tag.getString("Dim"));
		} catch (Throwable t) {
			dim = net.minecraft.world.level.Level.OVERWORLD.location();
		}
		return new WorldPos(dim, new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")));
	}

	/** 該当次元の ServerLevel を取得（未ロードなら null）。 */
	public ServerLevel level(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().location().equals(dimension)) {
				return level;
			}
		}
		return null;
	}

	public static WorldPos of(net.minecraft.world.level.Level level, BlockPos pos) {
		return new WorldPos(level.dimension().location(), pos.immutable());
	}

	public static WorldPos of(net.minecraft.world.entity.Entity entity) {
		return new WorldPos(entity.level().dimension().location(), entity.blockPosition());
	}
}
