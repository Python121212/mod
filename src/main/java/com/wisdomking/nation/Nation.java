package com.wisdomking.nation;

import com.wisdomking.core.WKLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 自律型 NPC 国家。プレイヤー2名はそれぞれ自国の王としてこの国家を所有する。
 *
 * <p>国家は以下を保持する:</p>
 * <ul>
 *   <li>領土（チャンク集合）と首都座標</li>
 *   <li>統治者（国王NPC）と動的王位継承の世代情報</li>
 *   <li>市民台帳（{@link NpcRecord}）と軍力・不満度</li>
 *   <li>資源備蓄・国庫（金）</li>
 *   <li>他国との関係（{@link Relation}）</li>
 *   <li>国家史（chronicle）— Raphael の報告と GUI 表示に使用</li>
 * </ul>
 */
public final class Nation {
	public final UUID id;
	public String name;
	public int color;
	public Ideology ideology;
	/** プレイヤー所有国家か（王が人間プレイヤー）。 */
	public UUID playerKing;

	public WorldPos capital;
	public boolean capitalMaterialized;

	/** 現在の国王 NPC の UUID（動的に交代する）。 */
	public UUID kingNpc;
	public String kingName = "";
	public Personality kingPersonality = Personality.BOLD;
	/** 王位継承の世代（何人目の王か）。 */
	public int dynastyGeneration = 1;

	public final Set<Long> claimedChunks = new HashSet<>();
	public final Map<UUID, Relation> relations = new HashMap<>();
	public final Map<UUID, NpcRecord> subjects = new LinkedHashMap<>();
	public final Map<ResourceType, Double> stock = new EnumMap<>(ResourceType.class);

	/** 国庫（金銭価値）。 */
	public double treasury = 120.0;
	/** 不満度 0..1。高いとクーデター・独立・亡命が発生する。 */
	public float unrest = 0.12f;
	/** 民心（王への支持）0..1。 */
	public float legitimacy = 0.72f;
	/** 軍の士気 0..1。 */
	public float morale = 0.75f;
	/** 技術水準 0..1。自動化・装備品質に影響。 */
	public float technology = 0.25f;

	public long foundedTick;
	public long lastSuccessionTick;
	public long lastWarTick;
	public long lastTradeTick;
	public boolean alive = true;
	/** 属国の場合の宗主国。 */
	public UUID overlord;
	/** 本国が攻撃を受けているか（防衛AIのトリガ）。 */
	public boolean underAttack;

	/** 国家史。Raphael が「《報告》」として読み上げる。 */
	public final List<String> chronicle = new ArrayList<>();
	/** 現在開発中の首都（初期生成時に土木AIが建設する）。 */
	public boolean capitalPendingConstruction = true;

	public Nation(UUID id, String name, int color, Ideology ideology) {
		this.id = id;
		this.name = name;
		this.color = color;
		this.ideology = ideology;
		for (ResourceType type : ResourceType.values()) {
			stock.put(type, 40.0 * type.baseProduction());
		}
	}

	// ------------------------------------------------------------------
	// 市民台帳
	// ------------------------------------------------------------------

	public void addSubject(NpcRecord record) {
		subjects.put(record.id, record);
	}

	public NpcRecord subject(UUID uuid) {
		return subjects.get(uuid);
	}

	public void removeSubject(UUID uuid) {
		subjects.remove(uuid);
	}

	public NpcRecord king() {
		return kingNpc == null ? null : subjects.get(kingNpc);
	}

	public Collection<NpcRecord> subjects() {
		return subjects.values();
	}

	public int population() {
		return subjects.size();
	}

	/** 兵員数。 */
	public int armySize() {
		int count = 0;
		for (NpcRecord rec : subjects.values()) {
			if (rec.role.isMilitary()) {
				count++;
			}
		}
		return count;
	}

	/** 総合戦力（装備・士気・技術補正込み）。 */
	public float militaryPower() {
		float power = 0.0f;
		float techBonus = 1.0f + technology * 0.5f;
		for (NpcRecord rec : subjects.values()) {
			if (rec.role.isMilitary()) {
				power += Math.max(0.5f, rec.power) * (0.6f + rec.loyalty * 0.4f);
			}
		}
		return power * morale * techBonus * ideology.defenseBonus() * 0.5f;
	}

	/** 平均忠誠度。 */
	public float averageLoyalty() {
		if (subjects.isEmpty()) {
			return 0.5f;
		}
		float sum = 0.0f;
		for (NpcRecord rec : subjects.values()) {
			sum += rec.loyalty;
		}
		return sum / subjects.size();
	}

	// ------------------------------------------------------------------
	// 外交
	// ------------------------------------------------------------------

	public Relation relation(UUID other) {
		return relations.computeIfAbsent(other, Relation::new);
	}

	public boolean atWar() {
		for (Relation rel : relations.values()) {
			if (rel.state == RelationState.WAR) {
				return true;
			}
		}
		return false;
	}

	public boolean atWarWith(UUID other) {
		Relation rel = relations.get(other);
		return rel != null && rel.state == RelationState.WAR;
	}

	// ------------------------------------------------------------------
	// 資源
	// ------------------------------------------------------------------

	public double stock(ResourceType type) {
		return stock.getOrDefault(type, 0.0);
	}

	public void addStock(ResourceType type, double amount) {
		stock.merge(type, amount, Double::sum);
	}

	public boolean consume(ResourceType type, double amount) {
		double current = stock(type);
		if (current < amount) {
			return false;
		}
		stock.put(type, current - amount);
		return true;
	}

	/** 資源の総合評価（交易・外交判断に使用）。 */
	public double wealth() {
		double total = treasury;
		for (ResourceType type : ResourceType.values()) {
			total += stock(type) * (type == ResourceType.PRECIOUS ? 2.5 : 1.0);
		}
		return total;
	}

	// ------------------------------------------------------------------
	// 史実記録
	// ------------------------------------------------------------------

	public void chronicle(String entry) {
		chronicle.add(entry);
		if (chronicle.size() > 200) {
			chronicle.remove(0);
		}
	}

	public List<String> recentChronicle(int max) {
		int size = chronicle.size();
		List<String> out = new ArrayList<>();
		for (int i = Math.max(0, size - max); i < size; i++) {
			out.add(chronicle.get(i));
		}
		return out;
	}

	// ------------------------------------------------------------------
	// シリアライズ
	// ------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Id", id.toString());
		tag.putString("Name", name);
		tag.putInt("Color", color);
		tag.putString("Ideology", ideology.name());
		if (playerKing != null) {
			tag.putString("PlayerKing", playerKing.toString());
		}
		if (capital != null) {
			tag.put("Capital", capital.save());
		}
		tag.putBoolean("CapitalBuilt", capitalMaterialized);
		if (kingNpc != null) {
			tag.putString("KingNpc", kingNpc.toString());
		}
		tag.putString("KingName", kingName);
		tag.putString("KingPersonality", kingPersonality.name());
		tag.putInt("Generation", dynastyGeneration);
		tag.putLong("Founded", foundedTick);
		tag.putLong("LastSuccession", lastSuccessionTick);
		tag.putLong("LastWar", lastWarTick);
		tag.putLong("LastTrade", lastTradeTick);
		tag.putFloat("Unrest", unrest);
		tag.putFloat("Legitimacy", legitimacy);
		tag.putFloat("Morale", morale);
		tag.putFloat("Technology", technology);
		tag.putDouble("Treasury", treasury);
		tag.putBoolean("Alive", alive);
		tag.putBoolean("PendingBuild", capitalPendingConstruction);
		if (overlord != null) {
			tag.putString("Overlord", overlord.toString());
		}

		long[] chunks = new long[claimedChunks.size()];
		int i = 0;
		for (long l : claimedChunks) {
			chunks[i++] = l;
		}
		tag.putLongArray("Claims", chunks);

		CompoundTag stockTag = new CompoundTag();
		for (Map.Entry<ResourceType, Double> entry : stock.entrySet()) {
			stockTag.putDouble(entry.getKey().name(), entry.getValue());
		}
		tag.put("Stock", stockTag);

		ListTag relList = new ListTag();
		for (Relation rel : relations.values()) {
			relList.add(rel.save());
		}
		tag.put("Relations", relList);

		ListTag subList = new ListTag();
		for (NpcRecord rec : subjects.values()) {
			subList.add(rec.save());
		}
		tag.put("Subjects", subList);

		ListTag hist = new ListTag();
		for (String entry : chronicle) {
			hist.add(net.minecraft.nbt.StringTag.valueOf(entry));
		}
		tag.put("Chronicle", hist);
		return tag;
	}

	public static Nation load(CompoundTag tag) {
		UUID id;
		try {
			id = UUID.fromString(tag.getString("Id"));
		} catch (Throwable t) {
			WKLog.warn("nation record without valid id, generating new one");
			id = UUID.randomUUID();
		}
		Nation nation = new Nation(id, tag.getString("Name"), tag.getInt("Color"),
				Ideology.byName(tag.getString("Ideology"), Ideology.MERCANTILISM));
		if (tag.contains("PlayerKing")) {
			try {
				nation.playerKing = UUID.fromString(tag.getString("PlayerKing"));
			} catch (Throwable ignored) {
				nation.playerKing = null;
			}
		}
		if (tag.contains("Capital")) {
			try {
				nation.capital = WorldPos.load(tag.getCompound("Capital"));
			} catch (Throwable ignored) {
				nation.capital = null;
			}
		}
		nation.capitalMaterialized = tag.getBoolean("CapitalBuilt");
		if (tag.contains("KingNpc")) {
			try {
				nation.kingNpc = UUID.fromString(tag.getString("KingNpc"));
			} catch (Throwable ignored) {
				nation.kingNpc = null;
			}
		}
		nation.kingName = tag.getString("KingName");
		nation.kingPersonality = Personality.byName(tag.getString("KingPersonality"), Personality.BOLD);
		nation.dynastyGeneration = Math.max(1, tag.getInt("Generation"));
		nation.foundedTick = tag.getLong("Founded");
		nation.lastSuccessionTick = tag.getLong("LastSuccession");
		nation.lastWarTick = tag.getLong("LastWar");
		nation.lastTradeTick = tag.getLong("LastTrade");
		nation.unrest = tag.getFloat("Unrest");
		nation.legitimacy = tag.getFloat("Legitimacy");
		nation.morale = tag.getFloat("Morale");
		nation.technology = tag.getFloat("Technology");
		nation.treasury = tag.getDouble("Treasury");
		nation.alive = !tag.contains("Alive") || tag.getBoolean("Alive");
		nation.capitalPendingConstruction = tag.getBoolean("PendingBuild");
		if (tag.contains("Overlord")) {
			try {
				nation.overlord = UUID.fromString(tag.getString("Overlord"));
			} catch (Throwable ignored) {
				nation.overlord = null;
			}
		}
		for (long chunk : tag.getLongArray("Claims")) {
			nation.claimedChunks.add(chunk);
		}
		CompoundTag stockTag = tag.getCompound("Stock");
		for (ResourceType type : ResourceType.values()) {
			if (stockTag.contains(type.name())) {
				nation.stock.put(type, stockTag.getDouble(type.name()));
			}
		}
		ListTag relList = tag.getList("Relations", 10);
		for (int i = 0; i < relList.size(); i++) {
			Relation rel = Relation.load(relList.getCompound(i));
			nation.relations.put(rel.other, rel);
		}
		ListTag subList = tag.getList("Subjects", 10);
		for (int i = 0; i < subList.size(); i++) {
			NpcRecord rec = NpcRecord.load(subList.getCompound(i));
			nation.subjects.put(rec.id, rec);
		}
		ListTag hist = tag.getList("Chronicle", 8);
		for (int i = 0; i < hist.size(); i++) {
			nation.chronicle.add(hist.getString(i));
		}
		return nation;
	}

	@Override
	public String toString() {
		return "Nation[" + name + " pop=" + population() + " army=" + armySize()
				+ " chunks=" + claimedChunks.size() + " ideology=" + ideology + "]";
	}
}
