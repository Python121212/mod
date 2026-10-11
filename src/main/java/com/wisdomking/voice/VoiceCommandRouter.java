package com.wisdomking.voice;

import com.wisdomking.core.WKLog;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.raphael.AnalysisReport;
import com.wisdomking.raphael.RaphaelCore;
import com.wisdomking.raphael.RaphaelRelay;
import com.wisdomking.raphael.Tone;
import com.wisdomking.raphael.Urgency;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/**
 * 音声・テキスト命令の解釈器（音声対話の中核）。
 *
 * <p>マイクから認識された文字列（STT）またはチャット入力を、
 * 国家操作・智慧之王への照会へ振り分ける。日本語・英語の双方に対応する。</p>
 *
 * <p>例:</p>
 * <ul>
 *   <li>「状況を報告せよ」 → 国家情勢解析</li>
 *   <li>「鋼鉄連邦に宣戦布告」 → 宣戦</li>
 *   <li>「講和を結べ」 → 停戦交渉</li>
 *   <li>「装備を配分しろ」 → スマート装備AI実行</li>
 *   <li>「機械を解析せよ」 → 自動化ライン解析</li>
 * </ul>
 */
public final class VoiceCommandRouter {
	private VoiceCommandRouter() {
	}

	/** 命令文字列を解釈して実行する。 */
	public static void handle(ServerPlayer player, String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return;
		}
		String text = rawText.trim();
		String lower = text.toLowerCase(Locale.ROOT);
		NationManager manager = NationManager.get();
		Nation own = manager == null ? null : manager.nationOfPlayer(player.getUUID());
		WKLog.info("voice command from " + player.getName().getString() + ": " + text);

		// --- 照会系 ---
		if (matches(lower, "状況", "報告", "情勢", "report", "status", "situation")) {
			RaphaelCore.reportNation(player);
			return;
		}
		if (matches(lower, "脅威", "危険", "threat", "danger")) {
			RaphaelCore.reportThreats(player);
			return;
		}
		if (matches(lower, "経済", "資源", "economy", "resource")) {
			RaphaelCore.reportEconomy(player);
			return;
		}
		if (matches(lower, "戦況", "戦争の状況", "war status", "front")) {
			RaphaelCore.reportWar(player);
			return;
		}
		if (matches(lower, "解析", "analyze", "scan", "スキャン")) {
			if (matches(lower, "機械", "machine", "ライン", "line", "自動化", "automation")) {
				RaphaelCore.adviseAutomation(player);
			} else if (matches(lower, "mod", "モッド", "mods")) {
				RaphaelCore.scanMods(player);
			} else if (matches(lower, "国家", "nation")) {
				RaphaelCore.reportNation(player);
			} else {
				RaphaelCore.analyzeTarget(player, 8.0);
			}
			return;
		}
		if (matches(lower, "ブリーフィング", "全報告", "briefing", "full report")) {
			RaphaelCore.fullBriefing(player);
			return;
		}

		// --- 国家操作系 ---
		if (own == null) {
			RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.ALERT,
					"命令を受理できません。あなたは国家に所属していません。", Urgency.NOTICE,
					"建国憲章を使用して国家を設立してください。"), true);
			return;
		}
		if (matches(lower, "宣戦", "戦争を布告", "declare war")) {
			Nation target = findNation(manager, text, own);
			if (target == null) {
				RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.ALERT,
						"対象国家を特定できませんでした。", Urgency.NOTICE,
						"国名を明示してください（例: 「鋼鉄連邦に宣戦布告」）。"), true);
				return;
			}
			manager.diplomacy().declareWar(own, target, "royal_decree");
			RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.REPORT,
					"宣戦布告を執行しました。", Urgency.CRITICAL,
					"対象: " + target.name,
					"我が国戦力: " + String.format("%.1f", own.militaryPower())
							+ " / 相手戦力: " + String.format("%.1f", target.militaryPower()),
					"《告》動員を開始します。兵士は国庫から装備を自動取得します。"), true);
			return;
		}
		if (matches(lower, "講和", "停戦", "和平", "peace", "truce")) {
			Nation target = findNation(manager, text, own);
			for (var war : manager.war().warsOf(own)) {
				Nation enemy = manager.nation(war.enemyOf(own.id));
				if (enemy == null) {
					continue;
				}
				if (target != null && !enemy.id.equals(target.id)) {
					continue;
				}
				manager.diplomacy().makeTruce(own, enemy);
				war.phase = com.wisdomking.nation.War.Phase.ENDED;
				RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.REPORT,
						enemy.name + " との停戦が成立しました。", Urgency.NOTICE,
						"講和条件: 現状維持", "賠償: なし"), true);
				return;
			}
			RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.REPORT, "交戦中の国家がありません。",
					Urgency.INFO), false);
			return;
		}
		if (matches(lower, "同盟", "alliance", "ally")) {
			Nation target = findNation(manager, text, own);
			if (target == null) {
				Nation candidate = manager.diplomacy().bestAllyCandidate(own);
				target = candidate;
			}
			if (target == null) {
				player.displayClientMessage(Component.literal("§e[外交] 同盟候補が見つかりません。"), false);
				return;
			}
			var relation = own.relation(target.id);
			if (relation.value > 25.0f) {
				relation.state = com.wisdomking.nation.RelationState.ALLIED;
				target.relation(own.id).state = com.wisdomking.nation.RelationState.ALLIED;
				relation.treatyTicks = 24000L;
				target.relation(own.id).treatyTicks = 24000L;
				manager.broadcast(Component.literal("§b[同盟] §f" + own.name + " と " + target.name + " が同盟を締結した。"));
				manager.markDirty();
			} else {
				RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.ALERT,
						target.name + " は同盟を拒否しました。", Urgency.INFO,
						"関係値: " + Math.round(relation.value) + "（必要: 25）",
						"交易ポストでの取引により関係値が改善します。"), true);
			}
			return;
		}
		if (matches(lower, "装備", "配分", "equip", "arm", "assign")) {
			int assigned = com.wisdomking.equipment.EquipmentManager.assignToSoldiers(own, player.serverLevel());
			RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.REPORT,
					"スマート装備配分を実行しました。", Urgency.INFO,
					"更新部位数: " + assigned,
					"《解》国庫内の全Mod装備を評価し、兵士ごとに最適な武装を選択しました。"), true);
			return;
		}
		if (matches(lower, "領土", "宣言", "claim", "territory")) {
			int radius = Math.max(1, Math.min(6, extractNumber(lower, 2)));
			int claimed = manager.territory().claimRadius(own, new net.minecraft.world.level.ChunkPos(player.blockPosition()), radius);
			RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.REPORT, "領土を宣言しました。", Urgency.INFO,
					"宣言チャンク: " + claimed + " / 総計 " + own.claimedChunks.size()), true);
			return;
		}
		if (matches(lower, "土木", "道路", "建設", "build", "road")) {
			int queued = com.wisdomking.ai.CivilEngineeringAI.scheduleRoadNetwork(player.serverLevel(), own);
			RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.REPORT, "土木AIへ建設命令を発行しました。", Urgency.INFO,
					"予約タスク: " + queued + " 件",
					"《解》首都と主要施設を接続する道路を自動敷設します。"), true);
			return;
		}
		if (matches(lower, "復興", "修復", "repair", "rebuild")) {
			int queued = com.wisdomking.ai.CivilEngineeringAI.scheduleRepair(player.serverLevel(), own);
			RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.REPORT, "復興作業を予約しました。", Urgency.INFO,
					"対象ブロック: " + queued + " 件"), true);
			return;
		}
		// 不明な命令
		RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.ALERT, "命令を解釈できませんでした。",
				Urgency.INFO, "認識文字列: " + text,
				"使用可能: 状況 / 脅威 / 経済 / 戦況 / 解析 / 宣戦 <国名> / 講和 / 同盟 / 装備 / 領土 / 土木 / 復興"), true);
	}

	private static boolean matches(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	/** テキスト中に含まれる国名から対象国家を特定する。 */
	private static Nation findNation(NationManager manager, String text, Nation self) {
		if (manager == null) {
			return null;
		}
		Nation best = null;
		int bestLength = 0;
		for (Nation nation : manager.aliveNations()) {
			if (nation.id.equals(self.id)) {
				continue;
			}
			if (text.contains(nation.name) && nation.name.length() > bestLength) {
				best = nation;
				bestLength = nation.name.length();
			}
		}
		return best;
	}

	private static int extractNumber(String text, int fallback) {
		int value = 0;
		boolean found = false;
		for (char c : text.toCharArray()) {
			if (Character.isDigit(c)) {
				value = value * 10 + (c - '0');
				found = true;
			} else if (found) {
				break;
			}
		}
		return found ? value : fallback;
	}

	/** 利用可能な命令の一覧（GUI・ヘルプ用）。 */
	public static List<String> helpLines() {
		return List.of(
				"状況 / 報告 — 国家情勢",
				"脅威 — 周辺・国内の脅威解析",
				"経済 — 資源と交易の解析",
				"戦況 — 交戦中の国家との戦況",
				"解析 機械 — 自動化ラインの最適化提案",
				"解析 mod — 全Mod走査",
				"宣戦 <国名> — 宣戦布告",
				"講和 [国名] — 停戦",
				"同盟 [国名] — 同盟締結",
				"装備 — 兵士への装備自動配分",
				"領土 [半径] — 領土宣言",
				"土木 / 復興 — 建設・復旧指示",
				"ブリーフィング — 統合報告");
	}
}
