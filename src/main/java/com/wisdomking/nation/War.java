package com.wisdomking.nation;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 国家間戦争。宣戦布告から降伏・併合までの状態を保持する。
 *
 * <p>戦争は「キャピタル・コアの破壊／占領」「戦争スコアの閾値到達」「軍隊の全滅」で終結する。</p>
 */
public final class War {
	public enum Phase {
		/** 宣戦布告直後の動員期間。 */
		MOBILIZING,
		/** 交戦中。 */
		ACTIVE,
		/** 停戦交渉中。 */
		CEASEFIRE_TALKS,
		/** 終結。 */
		ENDED
	}

	public final UUID attacker;
	public final UUID defender;
	public final long startTick;
	public Phase phase = Phase.MOBILIZING;
	/** 正なら攻撃側優勢 (スコアは -100..100)。 */
	public int score;
	/** この戦争の目的（略奪・領土割譲・属国化・滅亡）。 */
	public String casusBelli = "border";
	/** 戦争イベント履歴（Raphael の戦況報告・GUI 表示に使う）。 */
	public final List<String> events = new ArrayList<>();
	public boolean capitalCaptured;
	public long lastBattleTick;

	public War(UUID attacker, UUID defender, long startTick) {
		this.attacker = attacker;
		this.defender = defender;
		this.startTick = startTick;
	}

	public UUID enemyOf(UUID nation) {
		return nation.equals(attacker) ? defender : attacker;
	}

	public boolean involves(UUID nation) {
		return attacker.equals(nation) || defender.equals(nation);
	}

	public void log(String event) {
		events.add(event);
		if (events.size() > 40) {
			events.remove(0);
		}
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Attacker", attacker.toString());
		tag.putString("Defender", defender.toString());
		tag.putLong("Start", startTick);
		tag.putString("Phase", phase.name());
		tag.putInt("Score", score);
		tag.putString("CB", casusBelli);
		tag.putBoolean("CapitalCaptured", capitalCaptured);
		tag.putLong("LastBattle", lastBattleTick);
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (String e : events) {
			list.add(net.minecraft.nbt.StringTag.valueOf(e));
		}
		tag.put("Events", list);
		return tag;
	}

	public static War load(CompoundTag tag) {
		UUID a;
		UUID d;
		try {
			a = UUID.fromString(tag.getString("Attacker"));
			d = UUID.fromString(tag.getString("Defender"));
		} catch (Throwable t) {
			return null;
		}
		War war = new War(a, d, tag.getLong("Start"));
		try {
			war.phase = Phase.valueOf(tag.getString("Phase"));
		} catch (Throwable t) {
			war.phase = Phase.ACTIVE;
		}
		war.score = tag.getInt("Score");
		war.casusBelli = tag.getString("CB");
		war.capitalCaptured = tag.getBoolean("CapitalCaptured");
		war.lastBattleTick = tag.getLong("LastBattle");
		net.minecraft.nbt.ListTag list = tag.getList("Events", 8);
		for (int i = 0; i < list.size(); i++) {
			war.events.add(list.getString(i));
		}
		return war;
	}
}
