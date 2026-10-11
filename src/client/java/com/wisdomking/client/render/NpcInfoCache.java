package com.wisdomking.client.render;

import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.Personality;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * サーバーから受信した NPC の外見・装備情報（クライアント側キャッシュ）。
 *
 * <p>国家色・役割・性格はエンティティ同期に含まれないため、
 * {@code NpcSync} ペイロードで受け取りここに保持する。
 * 動的スキン生成と HUD 表示の両方がこのキャッシュを参照する。</p>
 */
public final class NpcInfoCache {
	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

	private NpcInfoCache() {
	}

	/** 1体分の情報。 */
	public record Entry(int entityId, String name, String nation, int color, NationRole role,
			Personality personality, String style, float hp, float maxHp, String weapon, String armor,
			boolean king) {
	}

	/** ペイロードを適用する。 */
	public static void apply(CompoundTag tag) {
		Map<Integer, Entry> updated = new HashMap<>();
		ListTag list = tag.getList("Npcs", CompoundTag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			int id = entry.getInt("Id");
			updated.put(id, new Entry(id,
					entry.getString("Name"),
					entry.getString("Nation"),
					entry.getInt("Color"),
					parseRole(entry.getString("Role")),
					parsePersonality(entry.getString("Personality")),
					entry.getString("Style"),
					entry.getFloat("Hp"),
					entry.getFloat("MaxHp"),
					entry.getString("Weapon"),
					entry.getString("Armor"),
					entry.getBoolean("King")));
		}
		synchronized (ENTRIES) {
			ENTRIES.clear();
			ENTRIES.putAll(updated);
		}
	}

	@Nullable
	public static Entry get(int entityId) {
		synchronized (ENTRIES) {
			return ENTRIES.get(entityId);
		}
	}

	public static int size() {
		synchronized (ENTRIES) {
			return ENTRIES.size();
		}
	}

	public static void clear() {
		synchronized (ENTRIES) {
			ENTRIES.clear();
		}
	}

	private static NationRole parseRole(String name) {
		try {
			return NationRole.valueOf(name);
		} catch (Throwable t) {
			return NationRole.CITIZEN;
		}
	}

	private static Personality parsePersonality(String name) {
		try {
			return Personality.valueOf(name);
		} catch (Throwable t) {
			return Personality.CAUTIOUS;
		}
	}
}
