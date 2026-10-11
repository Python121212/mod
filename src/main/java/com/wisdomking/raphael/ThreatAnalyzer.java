package com.wisdomking.raphael;

import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.NpcRecord;
import com.wisdomking.nation.ResourceType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 脅威解析器。国家・戦況・周辺環境の危機をリアルタイムに評価する。
 *
 * <p>解析対象:</p>
 * <ul>
 *   <li>敵国の侵攻（敵国家軍の接近と戦力比）</li>
 *   <li>他Modの強力な敵Mob（バニラ非ネームスペース含む）の出現</li>
 *   <li>国庫の資源不足・不満度上昇・クーデターの兆候</li>
 *   <li>首都（キャピタル・コア）の防衛状況</li>
 * </ul>
 */
public final class ThreatAnalyzer {
	private ThreatAnalyzer() {
	}

	/** プレイヤー周辺の脅威レポート。 */
	public static AnalysisReport analyze(ServerPlayer player) {
		NationManager manager = NationManager.get();
		Nation nation = manager == null ? null : manager.nationOfPlayer(player.getUUID());
		ServerLevel level = (ServerLevel) player.level();
		List<String> details = new ArrayList<>();
		List<String> criticalItems = new ArrayList<>();

		// --- 1. 周辺の敵対Mob ---
		Map<String, Integer> hostileByType = new LinkedHashMap<>();
		double hostileThreat = 0.0;
		try {
			AABB area = player.getBoundingBox().inflate(48.0);
			for (Entity entity : level.getEntitiesOfClass(Entity.class, area)) {
				if (!(entity instanceof LivingEntity living) || !(entity instanceof Enemy)) {
					continue;
				}
				var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
				String key = id == null ? "unknown" : id.toString();
				hostileByType.merge(key, 1, Integer::sum);
				double health = living.getMaxHealth();
				double attack = living instanceof Mob mob
						? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 2.0;
				hostileThreat += health * 0.5 + attack * 2.0;
			}
		} catch (Throwable t) {
			details.add("周辺Mob走査に失敗しました（安全にスキップ）。");
		}
		details.add(String.format("周辺48mの敵対Mob: %d 体 / 脅威指数 %.1f", hostileByType.values().stream().mapToInt(Integer::intValue).sum(), hostileThreat));
		hostileByType.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.limit(4)
				.forEach(entry -> {
					boolean modded = !entry.getKey().startsWith("minecraft:");
					details.add("  - " + entry.getKey() + " ×" + entry.getValue() + (modded ? "（他ModのMob）" : ""));
				});
		if (hostileThreat > 60.0) {
			criticalItems.add("周辺の敵対勢力が濃密です。守備隊の増強を推奨します。");
		}
		if (hostileByType.keySet().stream().anyMatch(key -> !key.startsWith("minecraft:")) && hostileThreat > 30.0) {
			criticalItems.add("他Modの敵対Mobを検出。対策装備（耐性・属性防御）の配備を推奨します。");
		}

		// --- 2. 国家の内部リスク ---
		if (nation != null) {
			if (nation.unrest > 0.45f) {
				criticalItems.add(String.format("不満度が %.0f%% に達しています。食料と治安の回復が急務です。",
						nation.unrest * 100));
			}
			float coupRisk = coupRisk(nation);
			if (coupRisk > 0.35f) {
				criticalItems.add(String.format("クーデターの兆候を検出（リスク %.0f%%）。要注意人物が存在します。",
						coupRisk * 100));
				NpcRecord plotter = mostLikelyPlotter(nation);
				if (plotter != null) {
					details.add("要注意人物: " + plotter.name + "（" + plotter.role.name()
							+ " / 忠誠 " + Math.round(plotter.loyalty * 100) + "% / 性格 " + plotter.personality.name() + "）");
				}
			}
			List<String> shortages = new ArrayList<>();
			for (ResourceType type : ResourceType.values()) {
				double deficit = manager.economy().deficit(nation, type);
				if (deficit > 0.6) {
					shortages.add(type.name() + String.format("(%.0f%%不足)", deficit * 100));
				}
			}
			if (!shortages.isEmpty()) {
				details.add("資源不足: " + String.join(", ", shortages));
				criticalItems.add("資源不足が国家の不満を押し上げています。交易または占領による解決を検討してください。");
			}
			// --- 3. 敵国の脅威 ---
			List<Nation> threats = manager.diplomacy().threatsTo(nation);
			if (!threats.isEmpty()) {
				Nation top = threats.get(0);
				double ratio = top.militaryPower() / Math.max(1.0f, nation.militaryPower());
				details.add(String.format("軍事的脅威: %s（戦力比 %.2f / %s）", top.name, ratio,
						nation.atWarWith(top.id) ? "交戦中" : "緊張関係"));
				if (ratio > 1.3) {
					criticalItems.add(top.name + " が優勢です。同盟の締結または防衛陣地の構築を推奨します。");
				}
			}
			// --- 4. 戦況 ---
			List<String> wars = manager.war().warSummary(nation);
			if (!wars.isEmpty()) {
				details.addAll(wars);
			}
		} else {
			details.add("あなたは未所属です。建国憲章を使用するか首都中核を制圧して国家を掌握してください。");
		}

		if (!criticalItems.isEmpty()) {
			details.add("推奨行動:");
			for (String item : criticalItems) {
				details.add("  - " + item);
			}
		}
		return new AnalysisReport(Tone.ALERT,
				hostileThreat > 60.0 ? "脅威解析: 危険" : "脅威解析: " + (criticalItems.isEmpty() ? "平穏" : "警戒"),
				details, criticalItems.isEmpty() ? Urgency.INFO : Urgency.CRITICAL);
	}

	/** クーデターリスクの推定 0..1。 */
	public static float coupRisk(Nation nation) {
		if (nation == null || nation.playerKing != null) {
			return 0.0f;
		}
		float risk = Math.max(0.0f, nation.unrest - 0.3f) * 1.6f;
		float loyaltyFactor = 1.0f - nation.averageLoyalty();
		risk += loyaltyFactor * 0.7f;
		risk += (1.0f - nation.legitimacy) * 0.5f;
		NpcRecord plotter = mostLikelyPlotter(nation);
		if (plotter != null) {
			risk += plotter.personality.aggression() * 0.12f + plotter.power * 0.04f;
		}
		return Math.min(1.0f, Math.max(0.0f, risk));
	}

	/** 最も危険な人物（野心家・低忠誠・高戦力）。 */
	@Nullable
	public static NpcRecord mostLikelyPlotter(Nation nation) {
		NpcRecord best = null;
		float bestScore = 0.0f;
		for (NpcRecord record : nation.subjects()) {
			if (!record.alive || record.role == NationRole.KING) {
				continue;
			}
			float score = (1.0f - record.loyalty) * 2.0f + record.power * 0.6f
					+ record.personality.aggression() * 0.5f;
			if (score > bestScore) {
				bestScore = score;
				best = record;
			}
		}
		return best;
	}

	/** 指名した人物の危険度（GUI の臣下リストで使用）。 */
	public static List<NpcRecord> rankedByRisk(Nation nation) {
		List<NpcRecord> list = new ArrayList<>(nation.subjects());
		list.removeIf(record -> !record.alive || record.role == NationRole.KING);
		list.sort(Comparator.comparingDouble((NpcRecord record) ->
				-((1.0f - record.loyalty) * 2.0f + record.power * 0.6f)));
		return list;
	}
}
