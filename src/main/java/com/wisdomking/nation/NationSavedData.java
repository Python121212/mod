package com.wisdomking.nation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 国家データの永続化（NBT）。
 *
 * <p>ワールドの {@code data/wisdomking_nations.dat} に保存される。
 * JSON 形式でのエクスポート（{@link NationManager#exportJson()}）も併設し、
 * サーバー管理者が国家情勢を外部ツールで解析できるようにしている。</p>
 */
public final class NationSavedData extends SavedData {
	public static final String NAME = "wisdomking_nations";
	public static final int DATA_VERSION = 1;

	private CompoundTag payload = new CompoundTag();

	public NationSavedData() {
	}

	public CompoundTag payload() {
		return payload;
	}

	public void setPayload(CompoundTag tag) {
		this.payload = tag;
		setDirty();
	}

	public static NationSavedData create() {
		return new NationSavedData();
	}

	public static NationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		NationSavedData data = new NationSavedData();
		data.payload = tag.contains("State") ? tag.getCompound("State") : new CompoundTag();
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putInt("Version", DATA_VERSION);
		tag.put("State", payload);
		return tag;
	}

	public static SavedData.Factory<NationSavedData> factory() {
		return new SavedData.Factory<>(NationSavedData::create, NationSavedData::load, NAME);
	}
}
