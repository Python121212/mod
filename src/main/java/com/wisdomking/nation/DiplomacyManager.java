package com.wisdomking.nation;

import com.wisdomking.core.WKConfig;
import com.wisdomking.core.WKLog;
import com.wisdomking.raphael.AnalysisReport;
import com.wisdomking.raphael.RaphaelRelay;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 外交。関係値の推移・条約・宣戦布告・属国化を扱う。
 *
 * <p>AI 国家は「理念 × 性格 × 資源不足 × 軍力差」から戦争・交易・同盟を判断する。
 * 資源格差（{@link EconomyManager} のバイオーム由来の生産係数）が侵略の誘因として
 * 直接スコアに寄与する点が地政学シミュレーションの要である。</p>
 */
public final class DiplomacyManager {
	private final NationManager manager;
	private float globalPressure;

	public DiplomacyManager(NationManager manager) {
		this.manager = manager;
	}

	public void noteResourcePressure() {
		globalPressure = Math.min(3.0f, globalPressure + 0.05f);
	}

	/** 理念の相性（0..1、1が最も相性が良い）。 */
	public static float ideologyAffinity(Ideology a, Ideology b) {
		if (a == b) {
			return 0.9f;
		}
		return switch (a) {
			case MILITARISM -> b == THEOCRACY ? 0.7f : (b == MERCANTILISM ? 0.45f : 0.25f);
			case MERCANTILISM -> b == TECHNOCRACY ? 0.75f : (b == SCHOLARSHIP ? 0.7f : 0.4f);
			case ISOLATIONISM -> 0.3f;
			case SCHOLARSHIP -> b == TECHNOCRACY ? 0.9f : 0.5f;
			case THEOCRACY -> b == MILITARISM ? 0.7f : 0.35f;
			case TECHNOCRACY -> b == SCHOLARSHIP ? 0.9f : (b == MERCANTILISM ? 0.75f : 0.5f);
		};
	}

	/** 初期関係を設定する。 */
	public void seedInitialRelations() {
		List<Nation> nations = manager.aliveNations();
		for (int i = 0; i < nations.size(); i++) {
			for (int j = i + 1; j < nations.size(); j++) {
				Nation a = nations.get(i);
				Nation b = nations.get(j);
				float affinity = ideologyAffinity(a.ideology, b.ideology);
				double distance = distance(a, b);
				float proximityPenalty = distance < 600 ? -18.0f : (distance < 1500 ? -6.0f : 6.0f);
				float value = (affinity - 0.5f) * 60.0f + proximityPenalty
						+ (manager.random().nextFloat() - 0.5f) * 25.0f;
				a.relation(b.id).value = value;
				b.relation(a.id).value = value;
			}
		}
		manager.markDirty();
	}

	private double distance(Nation a, Nation b) {
		if (a.capital == null || b.capital == null) {
			return 1200.0;
		}
		if (!a.capital.dimension().equals(b.capital.dimension())) {
			return 4000.0;
		}
		return Math.sqrt(a.capital.pos().distSqr(b.capital.pos()));
	}

	/** 5秒ごとの外交tick。 */
	public void tick() {
		globalPressure = Math.max(0.0f, globalPressure - 0.02f);
		for (Nation nation : manager.aliveNations()) {
			// 関係値の自然収束（理念相性へ向かう）
			for (Nation other : manager.aliveNations()) {
				if (other.id.equals(nation.id)) {
					continue;
				}
				Relation rel = nation.relation(other.id);
				if (rel.state == RelationState.WAR) {
					rel.add(-0.6f);
					continue;
				}
				float target = (ideologyAffinity(nation.ideology, other.ideology) - 0.5f) * 60.0f;
				rel.value += (target - rel.value) * 0.01f;
				if (rel.state == RelationState.TRUCE) {
					rel.treatyTicks -= 100;
					if (rel.treatyTicks <= 0) {
						rel.state = RelationState.NEUTRAL;
					}
				}
				if (rel.state == RelationState.ALLIED) {
					rel.treatyTicks -= 100;
					if (rel.treatyTicks <= 0) {
						rel.state = RelationState.FRIENDLY;
					}
				}
			}
			considerWarDeclaration(nation);
			considerAllegiance(nation);
		}
		// 属国の独立運動
		for (Nation nation : manager.aliveNations()) {
			if (nation.overlord != null && manager.random().nextFloat() < 0.02f) {
				Nation overlord = manager.nation(nation.overlord);
				if (overlord == null) {
					nation.overlord = null;
					continue;
				}
				float overlordStrength = overlord.militaryPower();
				float ownStrength = nation.militaryPower() * 1.35f;
				if (ownStrength > overlordStrength && overlord.unrest > 0.35f) {
					nation.overlord = null;
					overlord.relation(nation.id).state = RelationState.TENSE;
					nation.relation(overlord.id).state = RelationState.TENSE;
					nation.chronicle("独立: 宗主国 " + overlord.name + " の支配を脱した。");
					Component msg = Component.literal("§e[独立] §f" + nation.name + " が " + overlord.name + " の支配から独立した。");
					manager.broadcast(msg);
					RaphaelRelay.event(manager.server(), "nation.independence", nation.name + " / " + overlord.name);
					manager.markDirty();
				}
			}
		}
	}

	/** AI の宣戦判断。 */
	private void considerWarDeclaration(Nation nation) {
		if (nation.atWar()) {
			return;
		}
		long now = manager.server().getTickCount();
		if (now - nation.lastWarTick < WKConfig.get().warCooldownTicks) {
			return;
		}
		if (nation.playerKing != null) {
			return; // プレイヤー国家の宣戦は人間が決める
		}
		float aggression = nation.ideology.aggression() * nation.kingPersonality.aggression();
		double need = 0.0;
		for (ResourceType type : ResourceType.values()) {
			need += manager.economy().deficit(nation, type) * (type.isEssential() ? 1.5 : 0.7);
		}
		need = need / ResourceType.values().length + globalPressure * 0.15;
		float power = nation.militaryPower();

		Nation bestTarget = null;
		double bestScore = 0.0;
		for (Nation other : manager.aliveNations()) {
			if (other.id.equals(nation.id) || other.id.equals(nation.overlord)) {
				continue;
			}
			Relation rel = nation.relation(other.id);
			if (rel.state == RelationState.ALLIED || rel.state == RelationState.VASSAL
					|| rel.state == RelationState.OVERLORD) {
				continue;
			}
			if (rel.state == RelationState.TRUCE && rel.treatyTicks > 0) {
				continue;
			}
			double strengthRatio = power / Math.max(1.0f, other.militaryPower());
			double wealth = manager.economy().tradeAttractiveness(other);
			double distancePenalty = Math.max(0.25, 1.0 - distance(nation, other) / 3000.0);
			double score = strengthRatio * 1.6 + need * 2.2 + aggression * 1.4
					+ (-rel.value) * 0.012 + wealth * 0.01 + other.unrest * 1.1;
			score *= distancePenalty;
			if (other.playerKing != null) {
				score *= 1.15; // 人間の国家は魅力的な標的（ただし報復が怖い）
			}
			if (score > bestScore) {
				bestScore = score;
				bestTarget = other;
			}
		}
		if (bestTarget != null && bestScore > 3.4) {
			String casusBelli = need > 0.5 ? "resource" : (aggression > 1.6 ? "conquest" : "border");
			declareWar(nation, bestTarget, casusBelli);
		}
	}

	/** 同盟・従属の提案判断。 */
	private void considerAllegiance(Nation nation) {
		if (nation.overlord != null || nation.atWar()) {
			return;
		}
		Nation ally = null;
		float bestValue = 55.0f;
		for (Nation other : manager.aliveNations()) {
			if (other.id.equals(nation.id)) {
				continue;
			}
			Relation rel = nation.relation(other.id);
			if (rel.value > bestValue && rel.state != RelationState.WAR) {
				bestValue = rel.value;
				ally = other;
			}
		}
		if (ally != null && nation.relation(ally.id).state != RelationState.ALLIED
				&& manager.random().nextFloat() < 0.05f) {
			Relation rel = nation.relation(ally.id);
			rel.state = RelationState.ALLIED;
			rel.treatyTicks = 24000L;
			ally.relation(nation.id).state = RelationState.ALLIED;
			ally.relation(nation.id).treatyTicks = 24000L;
			manager.broadcast(Component.literal("§b[同盟] §f" + nation.name + " と " + ally.name + " が同盟を結んだ。"));
			RaphaelRelay.event(manager.server(), "nation.alliance", nation.name + " / " + ally.name);
			manager.markDirty();
		}
	}

	/** 宣戦布告。 */
	public void declareWar(Nation attacker, Nation defender, String casusBelli) {
		Relation rel = attacker.relation(defender.id);
		Relation back = defender.relation(attacker.id);
		rel.state = RelationState.WAR;
		back.state = RelationState.WAR;
		rel.value = Math.min(rel.value, -60.0f);
		back.value = Math.min(back.value, -60.0f);
		rel.warScore = 0;
		rel.warStartTick = manager.server().getTickCount();
		attacker.lastWarTick = rel.warStartTick;
		defender.underAttack = true;
		attacker.chronicle("宣戦: " + defender.name + " へ宣戦布告（事由: " + casusBelli + "）。");
		defender.chronicle("被宣戦: " + attacker.name + " から宣戦布告を受けた。");
		manager.war().startWar(attacker, defender, casusBelli);
		Component msg = Component.literal("§4[宣戦] §f" + attacker.name + " が " + defender.name + " に宣戦布告した。（"
				+ translateCasus(casusBelli) + "）");
		manager.broadcast(msg);
		RaphaelRelay.event(manager.server(), "war.declared",
				attacker.name + " -> " + defender.name + " [" + casusBelli + "]");
		manager.markDirty();
	}

	private String translateCasus(String casus) {
		return switch (casus) {
			case "resource" -> "資源の欠乏";
			case "conquest" -> "領土拡張";
			case "revenge" -> "旧怨";
			case "capital" -> "首都攻防";
			default -> "国境紛争";
		};
	}

	/** 停戦の成立。 */
	public void makeTruce(Nation a, Nation b) {
		Relation rel = a.relation(b.id);
		Relation back = b.relation(a.id);
		rel.state = RelationState.TRUCE;
		back.state = RelationState.TRUCE;
		long duration = WKConfig.get().truceTicks;
		rel.treatyTicks = duration;
		back.treatyTicks = duration;
		rel.warScore = 0;
		back.warScore = 0;
		a.underAttack = false;
		b.underAttack = false;
		manager.broadcast(Component.literal("§a[停戦] §f" + a.name + " と " + b.name + " が停戦条約を締結した。"));
		RaphaelRelay.event(manager.server(), "war.truce", a.name + " / " + b.name);
		manager.markDirty();
	}

	/** 属国化。 */
	public void makeVassal(Nation overlord, Nation vassal) {
		vassal.overlord = overlord.id;
		vassal.relation(overlord.id).state = RelationState.VASSAL;
		overlord.relation(vassal.id).state = RelationState.OVERLORD;
		vassal.chronicle("属国化: " + overlord.name + " の宗主権を認めた。");
		overlord.chronicle("宗主権: " + vassal.name + " を属国とした。");
		manager.broadcast(Component.literal("§6[従属] §f" + vassal.name + " は " + overlord.name + " の属国となった。"));
		RaphaelRelay.event(manager.server(), "nation.vassalized", overlord.name + " <- " + vassal.name);
		manager.markDirty();
	}

	/** 攻撃的な国家のリスト（Raphael の脅威解析で使用）。 */
	public List<Nation> threatsTo(Nation nation) {
		List<Nation> out = new ArrayList<>();
		for (Nation other : manager.aliveNations()) {
			if (other.id.equals(nation.id)) {
				continue;
			}
			Relation rel = nation.relation(other.id);
			boolean hostile = rel.state == RelationState.WAR || rel.state == RelationState.TENSE
					|| rel.value < -25.0f;
			double strength = other.militaryPower() / Math.max(1.0f, nation.militaryPower());
			if (hostile && strength > 0.6) {
				out.add(other);
			}
		}
		out.sort(Comparator.comparingDouble((Nation n) -> -n.militaryPower() / Math.max(1.0f, nation.militaryPower())));
		return out;
	}

	/** 最有力の同盟候補。 */
	@Nullable
	public Nation bestAllyCandidate(Nation nation) {
		Nation best = null;
		float bestValue = -200.0f;
		for (Nation other : manager.aliveNations()) {
			if (other.id.equals(nation.id) || nation.atWarWith(other.id)) {
				continue;
			}
			Relation rel = nation.relation(other.id);
			float value = rel.value + ideologyAffinity(nation.ideology, other.ideology) * 20.0f;
			if (value > bestValue) {
				bestValue = value;
				best = other;
			}
		}
		return best;
	}

	/** デバッグ: 二国間関係の要約。 */
	public String describe(Nation a, Nation b) {
		Relation rel = a.relation(b.id);
		return String.format("%s vs %s: %s value=%.1f warScore=%d treaty=%d",
				a.name, b.name, rel.state, rel.value, rel.warScore, rel.treatyTicks);
	}

	public void debugLog() {
		List<Nation> nations = manager.aliveNations();
		for (int i = 0; i < nations.size(); i++) {
			for (int j = i + 1; j < nations.size(); j++) {
				WKLog.info(describe(nations.get(i), nations.get(j)));
			}
		}
	}
}
