package com.wisdomking.nation;

import com.wisdomking.core.WKConfig;
import com.wisdomking.raphael.RaphaelRelay;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 戦争の進行管理。
 *
 * <p>戦争は以下の要因で推移する:</p>
 * <ul>
 *   <li>戦力比（{@link Nation#militaryPower()}）と士気・技術</li>
 *   <li>実戦（NPC兵士同士の交戦・プレイヤーの戦闘）による戦争スコア変動</li>
 *   <li>キャピタル・コアの破壊／占領（即時降伏）</li>
 * </ul>
 *
 * <p>終結条件: 戦争スコアが ±{@link WKConfig#warScoreThreshold} に到達、
 * または首都陥落。結果として領土割譲・賠償金・属国化・滅亡のいずれかが適用される。</p>
 */
public final class WarManager {
	private final NationManager manager;
	private final List<War> wars = new ArrayList<>();

	public WarManager(NationManager manager) {
		this.manager = manager;
	}

	public List<War> activeWars() {
		return wars;
	}

	public void clear() {
		wars.clear();
	}

	public void restore(War war) {
		wars.add(war);
	}

	/** 宣戦布告時の戦争オブジェクト生成。 */
	public War startWar(Nation attacker, Nation defender, String casusBelli) {
		War war = new War(attacker.id, defender.id, manager.server().getTickCount());
		war.casusBelli = casusBelli;
		war.phase = War.Phase.MOBILIZING;
		war.log("宣戦布告: " + casusBelli);
		wars.add(war);
		manager.markDirty();
		return war;
	}

	@Nullable
	public War warBetween(Nation a, Nation b) {
		for (War war : wars) {
			if (war.phase != War.Phase.ENDED && war.involves(a.id) && war.involves(b.id)) {
				return war;
			}
		}
		return null;
	}

	public List<War> warsOf(Nation nation) {
		List<War> out = new ArrayList<>();
		for (War war : wars) {
			if (war.phase != War.Phase.ENDED && war.involves(nation.id)) {
				out.add(war);
			}
		}
		return out;
	}

	/** 毎tickの戦争進行。 */
	public void tick() {
		if (wars.isEmpty()) {
			return;
		}
		List<War> finished = new ArrayList<>();
		for (War war : wars) {
			if (war.phase == War.Phase.ENDED) {
				finished.add(war);
				continue;
			}
			Nation attacker = manager.nation(war.attacker);
			Nation defender = manager.nation(war.defender);
			if (attacker == null || defender == null || !attacker.alive || !defender.alive) {
				war.phase = War.Phase.ENDED;
				finished.add(war);
				continue;
			}
			stepWar(war, attacker, defender);
		}
		wars.removeAll(finished);
		if (!finished.isEmpty()) {
			manager.markDirty();
		}
	}

	private void stepWar(War war, Nation attacker, Nation defender) {
		long now = manager.server().getTickCount();
		if (war.phase == War.Phase.MOBILIZING && now - war.startTick > 1200L) {
			war.phase = War.Phase.ACTIVE;
			war.log("動員完了、交戦状態へ移行。");
			RaphaelRelay.event(manager.server(), "war.active", attacker.name + " vs " + defender.name);
		}

		// 戦力比による戦争スコアの推移（抽象的解決）
		float attackerPower = attacker.militaryPower();
		float defenderPower = defender.militaryPower() * defender.ideology.defenseBonus()
				* (defender.underAttack ? 1.0f : 1.15f);
		double ratio = attackerPower / Math.max(1.0f, attackerPower + defenderPower);
		double drift = (ratio - 0.5) * 2.0; // -1..1
		int scoreDelta = (int) Math.round(drift * 0.6);
		war.score = Math.max(-100, Math.min(100, war.score + scoreDelta));

		// 兵力消耗（長期戦は双方の軍が削られる）
		if (now % 400L == 0L) {
			attrition(attacker, 0.012f + (float) Math.max(0.0, -drift) * 0.02f);
			attrition(defender, 0.012f + (float) Math.max(0.0, drift) * 0.02f);
			war.lastBattleTick = now;
			if (manager.random().nextFloat() < 0.35f) {
				String event = describeBattle(attacker, defender, drift);
				war.log(event);
				RaphaelRelay.warReport(manager.server(), war, attacker, defender, event);
			}
		}

		// 終結判定
		if (war.capitalCaptured) {
			resolve(war, attacker, defender, drift >= 0 ? attacker : defender);
			return;
		}
		if (war.score >= WKConfig.get().warScoreThreshold) {
			resolve(war, attacker, defender, attacker);
		} else if (war.score <= -WKConfig.get().warScoreThreshold) {
			resolve(war, attacker, defender, defender);
		} else if (now - war.startTick > WKConfig.get().maxWarTicks) {
			// 長期化: 講和（現状維持）
			war.phase = War.Phase.CEASEFIRE_TALKS;
			war.log("戦争が長期化し、講和交渉が開始された。");
			RaphaelRelay.event(manager.server(), "war.stalemate", attacker.name + " vs " + defender.name);
			manager.diplomacy().makeTruce(attacker, defender);
			war.phase = War.Phase.ENDED;
		}
	}

	private String describeBattle(Nation attacker, Nation defender, double drift) {
		boolean attackerWins = drift >= 0;
		Nation winner = attackerWins ? attacker : defender;
		Nation loser = attackerWins ? defender : attacker;
		String[] battlefield = {"国境の平原", "丘陵の要塞線", "河川渡河点", "森林の隘路", "首都外郭"};
		String place = battlefield[manager.random().nextInt(battlefield.length)];
		return "会戦: " + place + " にて " + winner.name + " が " + loser.name
				+ " を撃退（戦力比 " + String.format("%.2f", (double) winner.militaryPower()
				/ Math.max(1.0f, loser.militaryPower())) + "）";
	}

	private void attrition(Nation nation, float ratio) {
		int removed = 0;
		for (NpcRecord record : new ArrayList<>(nation.subjects())) {
			if (!record.role.isMilitary() || !record.alive) {
				continue;
			}
			if (manager.random().nextFloat() < ratio) {
				record.alive = false;
				record.lastSeenTick = manager.server().getTickCount();
				removed++;
				if (removed > 6) {
					break;
				}
			}
		}
		if (removed > 0) {
			nation.unrest = Math.min(1.0f, nation.unrest + 0.01f * removed);
			manager.markDirty();
		}
	}

	/**
	 * 戦争の終結と戦後処理。
	 *
	 * @param victor 勝利国
	 */
	public void resolve(War war, Nation attacker, Nation defender, Nation victor) {
		Nation loser = victor.id.equals(attacker.id) ? defender : attacker;
		war.phase = War.Phase.ENDED;
		war.log("終結: " + victor.name + " の勝利。");
		manager.diplomacy().makeTruce(attacker, defender);

		// 賠償金
		double indemnity = Math.max(0.0, loser.wealth() * 0.22);
		loser.treasury = Math.max(0.0, loser.treasury - indemnity);
		victor.treasury += indemnity;

		// 領土割譲（敗者の領土の 25%）
		int transferCount = Math.max(1, (int) (loser.claimedChunks.size() * 0.25));
		int moved = 0;
		for (long key : new ArrayList<>(loser.claimedChunks)) {
			if (moved >= transferCount) {
				break;
			}
			loser.claimedChunks.remove(key);
			victor.claimedChunks.add(key);
			moved++;
		}
		manager.territory().rebuild();
		manager.economy().invalidate(loser);
		manager.economy().invalidate(victor);

		// 軍の壊滅判定 → 属国化 or 滅亡
		boolean crushed = loser.militaryPower() < Math.max(2.0f, victor.militaryPower() * 0.25f);
		boolean capitalLost = war.capitalCaptured;
		String outcome;
		if (capitalLost && crushed) {
			// 完全併合: 滅亡
			victor.treasury += loser.treasury;
			victor.treasury += 0.0;
			for (NpcRecord record : loser.subjects()) {
				record.alive = false;
			}
			manager.dissolveNation(loser, victor.name + " による首都陥落・完全併合");
			outcome = "滅亡";
		} else if (capitalLost || (crushed && war.score >= 85)) {
			manager.diplomacy().makeVassal(victor, loser);
			outcome = "属国化";
		} else {
			outcome = "領土割譲と賠償";
		}

		victor.chronicle("戦勝: " + loser.name + " に勝利（" + outcome + "）。");
		if (loser.alive) {
			loser.chronicle("敗戦: " + victor.name + " に敗北（" + outcome + "）。");
			loser.underAttack = false;
		}
		manager.broadcast(Component.literal("§e[終戦] §f" + victor.name + " が " + loser.name
				+ " との戦争に勝利した。（" + outcome + " / 賠償 " + String.format("%.0f", indemnity) + "）"));
		RaphaelRelay.event(manager.server(), "war.ended", victor.name + " -> " + loser.name + " [" + outcome + "]");
		manager.markDirty();
	}

	/** キャピタル・コアが破壊・占領されたときの即時処理。 */
	public void onCapitalCoreLost(Nation defender, @Nullable Nation attacker) {
		defender.capitalMaterialized = false;
		if (attacker == null) {
			defender.chronicle("首都陥落: 首都中核が失われた（原因不明）。");
			return;
		}
		defender.chronicle("首都陥落: " + attacker.name + " により首都中核が破壊された。");
		attacker.chronicle("戦果: " + defender.name + " の首都中核を破壊した。");
		War war = warBetween(attacker, defender);
		if (war != null) {
			war.capitalCaptured = true;
			war.score = Math.max(war.score, 80);
			war.log("首都陥落: " + attacker.name + " が " + defender.name + " の首都を制圧。");
		} else {
			// 戦争状態でない場合は強制降伏（奇襲）
			declareAndResolve(attacker, defender);
		}
		manager.markDirty();
	}

	private void declareAndResolve(Nation attacker, Nation defender) {
		War war = startWar(attacker, defender, "capital");
		war.phase = War.Phase.ACTIVE;
		war.capitalCaptured = true;
		war.score = 85;
		resolve(war, attacker, defender, attacker);
	}

	/** 実戦（NPC戦闘）による戦争スコア加算。 */
	public void addCombatScore(Nation winner, Nation looser, int amount) {
		War war = warBetween(winner, looser);
		if (war == null) {
			return;
		}
		boolean winnerIsAttacker = war.attacker.equals(winner.id);
		war.score = Math.max(-100, Math.min(100, war.score + (winnerIsAttacker ? amount : -amount)));
		manager.markDirty();
	}

	/** 国家が受けている戦争の要約（Raphael 戦況報告用）。 */
	public List<String> warSummary(Nation nation) {
		List<String> lines = new ArrayList<>();
		for (War war : warsOf(nation)) {
			Nation other = manager.nation(war.enemyOf(nation.id));
			if (other == null) {
				continue;
			}
			boolean attacker = war.attacker.equals(nation.id);
			int ownScore = attacker ? war.score : -war.score;
			lines.add(String.format("%s との戦争: 優勢度 %d (%s) / 局面 %s",
					other.name, ownScore, ownScore >= 0 ? "優勢" : "劣勢", war.phase.name()));
		}
		return lines;
	}
}
