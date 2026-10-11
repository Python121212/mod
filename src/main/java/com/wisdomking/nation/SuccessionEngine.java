package com.wisdomking.nation;

import com.wisdomking.core.WKConfig;
import com.wisdomking.core.WKLog;
import com.wisdomking.raphael.RaphaelRelay;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 動的王位継承システム。
 *
 * <p>国王NPCが戦死・暗殺・クーデター・病死などで失われると、
 * 生存する臣下の中から「人望 × 戦力 × 忠誠 × 役職権威 × 性格」の
 * 重み付きスコアで新国王が選出される。選出結果は予測不能であり、
 * 新国王の性格に応じて国家理念・外交スタンス・侵略目標が変化する。</p>
 *
 * <p>不満度が閾値を超えている場合、野心家の臣下がクーデターを起こし、
 * 最悪の場合は内戦（国家分裂・新国家の建国）へ発展する。</p>
 */
public final class SuccessionEngine {
	private final NationManager manager;

	public SuccessionEngine(NationManager manager) {
		this.manager = manager;
	}

	/** 継承原因。 */
	public enum Cause {
		/** 戦死 */
		BATTLE,
		/** 暗殺 */
		ASSASSINATION,
		/** クーデター */
		COUP,
		/** 病死・老衰 */
		ILLNESS,
		/** プレイヤーによる討伐 */
		SLAIN_BY_PLAYER,
		/** 不明 */
		UNKNOWN
	}

	/**
	 * 国王の死亡イベント。新国王の選出と政策変更を実行する。
	 *
	 * @param nation 対象国家
	 * @param kingRecord 死亡した国王の台帳レコード
	 * @param cause 継承原因
	 * @param killerNationId 殺害者側の国家（あれば）
	 */
	public void onLeaderDeath(Nation nation, NpcRecord kingRecord, Cause cause, @Nullable UUID killerNationId) {
		if (!nation.alive) {
			return;
		}
		kingRecord.alive = false;
		int generation = nation.dynastyGeneration;
		nation.chronicle("王位継承: 第" + generation + "代 " + kingRecord.name + " が " + causeName(cause) + "により崩御。");
		nation.legitimacy = Math.max(0.0f, nation.legitimacy - (cause == Cause.COUP ? 0.25f : 0.15f));
		nation.unrest = Math.min(1.0f, nation.unrest + (cause == Cause.COUP ? 0.15f : 0.08f));

		NpcRecord successor = selectSuccessor(nation, kingRecord);
		if (successor == null) {
			// 後継者不在 → 国家崩壊（無政府状態）
			manager.dissolveNation(nation, "後継者不在による国家崩壊");
			return;
		}
		crown(nation, successor, cause, generation + 1, killerNationId);
	}

	/** 継承者を選出する（スコア上位 + わずかなランダム性で予測不能にする）。 */
	@Nullable
	public NpcRecord selectSuccessor(Nation nation, @Nullable NpcRecord exclude) {
		List<NpcRecord> candidates = new ArrayList<>();
		for (NpcRecord record : nation.subjects()) {
			if (!record.alive || record.equals(exclude) || record.role == NationRole.KING) {
				continue;
			}
			// 継承権のない階級は原則として対象外（ただしクーデター後の実力者選出はありうる）
			if (record.role == NationRole.CITIZEN || record.role == NationRole.SOLDIER) {
				continue;
			}
			candidates.add(record);
		}
		if (candidates.isEmpty()) {
			// 非常時: 兵士・市民からも実力者が擁立される
			for (NpcRecord record : nation.subjects()) {
				if (record.alive && !record.equals(exclude)) {
					candidates.add(record);
				}
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		candidates.sort(Comparator.comparingDouble(NpcRecord::successionScore).reversed());
		int pool = Math.min(3, candidates.size());
		return candidates.get(manager.random().nextInt(pool));
	}

	/** 即位。性格に基づく政策転換と Raphael による公表。 */
	public void crown(Nation nation, NpcRecord successor, Cause cause, int generation, @Nullable UUID killerNationId) {
		successor.role = NationRole.KING;
		successor.power = Math.max(successor.power, 2.2f);
		successor.popularity = Math.min(1.0f, successor.popularity + 0.12f);
		successor.loyalty = 1.0f;
		nation.kingNpc = successor.id;
		nation.kingName = successor.name;
		Ideology previous = nation.ideology;
		nation.kingPersonality = successor.personality;
		nation.dynastyGeneration = generation;
		nation.lastSuccessionTick = manager.server().getTickCount();
		nation.legitimacy = Math.min(1.0f, nation.legitimacy + 0.1f);

		// 性格 → 理念のドリフト（政策変化）
		Ideology next = driftIdeology(nation.ideology, successor.personality);
		boolean policyChanged = next != previous;
		nation.ideology = next;
		nation.chronicle("即位: 第" + generation + "代国王 " + successor.name + " (" + successor.personality.name()
				+ ") が即位" + (policyChanged ? "。理念が " + previous.name() + " から " + next.name() + " へ転換。" : "。"));
		announceSuccession(nation, successor, cause, generation, policyChanged, previous, next, killerNationId);
		manager.markDirty();
	}

	private Ideology driftIdeology(Ideology current, Personality personality) {
		float roll = manager.random().nextFloat();
		if (roll > 0.55f) {
			return current; // 55% は現状維持
		}
		return switch (personality) {
			case BOLD, CRUEL -> Ideology.MILITARISM;
			case AMBITIOUS -> manager.random().nextBoolean() ? Ideology.MILITARISM : Ideology.TECHNOCRACY;
			case GREEDY -> Ideology.MERCANTILISM;
			case ZEALOUS -> Ideology.THEOCRACY;
			case CAUTIOUS, MERCIFUL -> Ideology.ISOLATIONISM;
			case LOYAL -> current;
		};
	}

	private void announceSuccession(Nation nation, NpcRecord successor, Cause cause, int generation,
			boolean policyChanged, Ideology previous, Ideology next, @Nullable UUID killerNationId) {
		String causeText = causeName(cause);
		String policyText = policyChanged
				? "国家理念が「" + previous.name() + "」から「" + next.name() + "」へ変更されました。"
				: "国家理念「" + next.name() + "」は維持されます。";
		Component msg = Component.literal("§6[王位継承] §f" + nation.name + " 第" + generation + "代国王 "
				+ successor.name + " 即位（" + causeText + " / 性格: " + successor.personality.name() + "）");
		manager.broadcast(msg);
		manager.broadcast(Component.literal("§7  └ " + policyText));
		RaphaelRelay.succession(manager.server(), nation, successor, causeText, policyText, killerNationId);
	}

	private String causeName(Cause cause) {
		return switch (cause) {
			case BATTLE -> "戦死";
			case ASSASSINATION -> "暗殺";
			case COUP -> "クーデター";
			case ILLNESS -> "病死";
			case SLAIN_BY_PLAYER -> "討伐";
			case UNKNOWN -> "不慮の事故";
		};
	}

	/** 20秒ごとの定期的な王権チェック（クーデター・崩御・分裂）。 */
	public void periodicTick() {
		for (Nation nation : manager.aliveNations()) {
			if (nation.playerKing != null) {
				// プレイヤー国家: NPC宰相の死亡のみ処理し、王位はプレイヤーが保持する
				continue;
			}
			NpcRecord king = nation.king();
			if (king == null || !king.alive) {
				NpcRecord fallback = new NpcRecord(UUID.randomUUID(),
						NationFactory.randomPersonName(manager.random()), NationRole.KING, Personality.random(manager.random()));
				nation.addSubject(fallback);
				WKLog.warn("nation " + nation.name + " had no living king; emergency coronation held");
				crown(nation, fallback, Cause.UNKNOWN, nation.dynastyGeneration + 1, null);
				continue;
			}
			checkCoup(nation, king);
			checkNaturalDeath(nation, king);
			// 不満度が高いと忠誠が低下する
			float loyaltyDrift = (0.5f - nation.unrest) * 0.05f;
			for (NpcRecord record : nation.subjects()) {
				record.power += record.role.isMilitary() ? 0.002f : 0.0f;
				if (record.role != NationRole.KING) {
					record.loyalty += loyaltyDrift;
					record.clamp();
				}
			}
			// 属国は宗主国の圧力で不満が上がる
			if (nation.overlord != null) {
				nation.unrest = Math.min(1.0f, nation.unrest + 0.004f);
			}
			manager.markDirty();
		}
	}

	/** クーデター判定。 */
	private void checkCoup(Nation nation, NpcRecord king) {
		if (nation.unrest < WKConfig.get().coupUnrestThreshold) {
			return;
		}
		NpcRecord plotter = null;
		float bestScore = 0.0f;
		for (NpcRecord record : nation.subjects()) {
			if (!record.alive || record.role == NationRole.KING) {
				continue;
			}
			float threshold = WKConfig.get().coupUnrestThreshold + record.personality.coupThresholdBias()
					- record.power * 0.03f;
			if (nation.unrest < threshold && record.personality != Personality.AMBITIOUS) {
				continue;
			}
			float score = record.power * 1.4f + (1.0f - record.loyalty) * 2.0f
					+ record.personality.aggression() * 0.6f + (record.role == NationRole.GENERAL ? 1.2f : 0.0f);
			if (score > bestScore && score > 2.4f) {
				bestScore = score;
				plotter = record;
			}
		}
		if (plotter == null || manager.random().nextFloat() > 0.25f) {
			return;
		}
		// クーデター発生
		nation.chronicle("クーデター: " + plotter.name + " が " + king.name + " を廃した。");
		manager.broadcast(Component.literal("§4[クーデター] §f" + nation.name + " で " + plotter.name
				+ " が " + king.name + " を廃位した。"));
		RaphaelRelay.event(manager.server(), "succession.coup", nation.name + " / " + plotter.name);
		onLeaderDeath(nation, king, Cause.COUP, null);

		// 内戦: 不満度が極端に高い場合は分裂して新国家が建国される
		if (nation.unrest > 0.75f && nation.claimedChunks.size() > 12 && manager.random().nextFloat() < 0.5f) {
			civilWar(nation);
		}
	}

	/** 内戦による国家分裂（動的国家数変動の中核）。 */
	public void civilWar(Nation parent) {
		List<NpcRecord> rebels = new ArrayList<>();
		for (NpcRecord record : parent.subjects()) {
			if (record.alive && record.loyalty < 0.45f && record.role != NationRole.KING) {
				rebels.add(record);
			}
		}
		if (rebels.isEmpty()) {
			return;
		}
		rebels.sort(Comparator.comparingDouble(NpcRecord::successionScore).reversed());
		NpcRecord leader = rebels.get(0);
		WorldPos capital = parent.capital;
		Nation rebelsNation = NationFactory.secede(parent, leader, capital, manager.random());
		// 反乱軍は人口と領土の一部を引き連れる
		int movedSubjects = 0;
		for (NpcRecord record : rebels) {
			if (movedSubjects >= 12) {
				break;
			}
			parent.removeSubject(record.id);
			record.loyalty = 0.9f;
			rebelsNation.addSubject(record);
			movedSubjects++;
		}
		int movedChunks = 0;
		int targetChunks = Math.max(4, parent.claimedChunks.size() / 3);
		for (long key : new ArrayList<>(parent.claimedChunks)) {
			if (movedChunks >= targetChunks) {
				break;
			}
			parent.claimedChunks.remove(key);
			rebelsNation.claimedChunks.add(key);
			movedChunks++;
		}
		manager.register(rebelsNation);
		manager.territory().rebuild();
		manager.economy().invalidate(parent);
		manager.economy().invalidate(rebelsNation);
		manager.diplomacy().declareWar(rebelsNation, parent, "rebellion");
		manager.broadcast(Component.literal("§c[内戦] §f" + parent.name + " が分裂し、" + rebelsNation.name
				+ " が " + leader.name + " を王として独立した。"));
		RaphaelRelay.event(manager.server(), "nation.civil_war",
				parent.name + " -> " + rebelsNation.name);
		manager.markDirty();
	}

	/** 老衰・病死。 */
	private void checkNaturalDeath(Nation nation, NpcRecord king) {
		long age = manager.server().getTickCount() - king.birthTick;
		float chance = 0.00012f + Math.max(0.0f, (age - 480000.0f) / 2400000.0f) * 0.004f;
		if (manager.random().nextFloat() < chance) {
			onLeaderDeath(nation, king, Cause.ILLNESS, null);
		}
	}

	/** 実体側（NPC国王エンティティ）の死亡通知。 */
	public void notifyKingEntityDeath(Nation nation, UUID entityId, Cause cause, @Nullable UUID killerNation) {
		NpcRecord record = nation.subject(entityId);
		if (record == null) {
			return;
		}
		if (nation.kingNpc != null && nation.kingNpc.equals(entityId)) {
			onLeaderDeath(nation, record, cause, killerNation);
		} else {
			record.alive = false;
			record.lastSeenTick = manager.server().getTickCount();
			manager.markDirty();
		}
	}
}
