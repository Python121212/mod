package com.wisdomking.nation;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 国家に属する NPC の台帳レコード。
 *
 * <p>エンティティが未ロードでも国家の人口・軍力・継承候補を把握できるようにするため、
 * 実体とは別に最小限の情報を保持する（世界の98%がNPCであるという設定を、
 * 実際にロードされている個体群と台帳の二層構造で表現する）。</p>
 */
public final class NpcRecord {
	public final UUID id;
	public String name;
	public NationRole role;
	public Personality personality;
	/** 忠誠度 0..1。低いと反乱・亡命の原因になる。 */
	public float loyalty;
	/** 個人戦力（装備込みの総合値）。 */
	public float power;
	/** 民心（その人物の人気）0..1。継承選出に影響。 */
	public float popularity;
	public WorldPos lastPos;
	public long lastSeenTick;
	public boolean alive = true;
	/** 継承順位の計算に使う年齢（tick）。 */
	public long birthTick;

	public NpcRecord(UUID id, String name, NationRole role, Personality personality) {
		this.id = id;
		this.name = name;
		this.role = role;
		this.personality = personality;
		this.loyalty = 0.7f + personality.loyaltyBias() * 0.3f;
		this.popularity = 0.5f;
		this.power = role.authority();
	}

	public void clamp() {
		loyalty = Math.max(0.0f, Math.min(1.0f, loyalty));
		popularity = Math.max(0.0f, Math.min(1.0f, popularity));
		power = Math.max(0.0f, power);
	}

	/** 継承候補としての総合スコア。 */
	public float successionScore() {
		return popularity * 2.2f + power * 1.4f + loyalty * 1.0f
				+ role.authority() * 1.6f + personality.aggression() * 0.4f;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Id", id.toString());
		tag.putString("Name", name == null ? "" : name);
		tag.putString("Role", role.name());
		tag.putString("Personality", personality.name());
		tag.putFloat("Loyalty", loyalty);
		tag.putFloat("Power", power);
		tag.putFloat("Popularity", popularity);
		tag.putBoolean("Alive", alive);
		tag.putLong("LastSeen", lastSeenTick);
		tag.putLong("Birth", birthTick);
		if (lastPos != null) {
			tag.put("Pos", lastPos.save());
		}
		return tag;
	}

	public static NpcRecord load(CompoundTag tag) {
		UUID id;
		try {
			id = UUID.fromString(tag.getString("Id"));
		} catch (Throwable t) {
			id = UUID.randomUUID();
		}
		NpcRecord rec = new NpcRecord(id, tag.getString("Name"),
				NationRole.byName(tag.getString("Role"), NationRole.CITIZEN),
				Personality.byName(tag.getString("Personality"), Personality.LOYAL));
		rec.loyalty = tag.getFloat("Loyalty");
		rec.power = tag.getFloat("Power");
		rec.popularity = tag.getFloat("Popularity");
		rec.alive = !tag.contains("Alive") || tag.getBoolean("Alive");
		rec.lastSeenTick = tag.getLong("LastSeen");
		rec.birthTick = tag.getLong("Birth");
		if (tag.contains("Pos")) {
			try {
				rec.lastPos = WorldPos.load(tag.getCompound("Pos"));
			} catch (Throwable ignored) {
				rec.lastPos = null;
			}
		}
		rec.clamp();
		return rec;
	}
}
