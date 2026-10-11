package com.wisdomking.raphael;

import com.wisdomking.WK;
import com.wisdomking.core.WKLog;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NpcRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * 智慧之王の発言配送ハブ。
 *
 * <p>国家イベント（継承・宣戦・停戦・クーデター）や解析レポートを、
 * チャット・HUD・音声（TTS）へ同時に流すための単一窓口である。
 * 直近のレポートは {@link #history()} に保持され、国家ダッシュボードGUIで参照できる。</p>
 */
public final class RaphaelRelay {
	private static final Deque<AnalysisReport> HISTORY = new LinkedList<>();
	private static final List<RaphaelSink> SINKS = new ArrayList<>();
	private static final int HISTORY_LIMIT = 64;

	private RaphaelRelay() {
	}

	/** 追加シンク（音声・GUI連携）を登録する。 */
	public static synchronized void install(RaphaelSink sink) {
		SINKS.add(sink);
		WKLog.info("Raphael sink installed: " + sink.name());
	}

	public static synchronized void uninstallAll() {
		SINKS.clear();
	}

	public static synchronized List<AnalysisReport> history() {
		return new ArrayList<>(HISTORY);
	}

	public static synchronized void remember(AnalysisReport report) {
		HISTORY.addFirst(report);
		while (HISTORY.size() > HISTORY_LIMIT) {
			HISTORY.removeLast();
		}
	}

	/** 全プレイヤーへ送る。 */
	public static void announce(MinecraftServer server, AnalysisReport report) {
		announce(server, report, null, report.urgency() == Urgency.CRITICAL);
	}

	/** 指定国家の構成員へ送る。 */
	public static void announceTo(MinecraftServer server, AnalysisReport report, Nation nation) {
		announce(server, report, nation == null ? null : nation.id, report.urgency() == Urgency.CRITICAL);
	}

	/** プレイヤー単体へ送る。 */
	public static void announceTo(ServerPlayer player, AnalysisReport report, boolean speak) {
		deliverTo(player, report, speak, null);
	}

	private static void announce(MinecraftServer server, AnalysisReport report, @Nullable UUID nationId, boolean speak) {
		remember(report);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (nationId != null) {
				NationManager manager = NationManager.get();
				if (manager == null) {
					continue;
				}
				UUID playerNation = manager.nationIdOfPlayer(player);
				if (playerNation == null || !playerNation.equals(nationId)) {
					continue;
				}
			}
			deliverTo(player, report, speak, nationId);
		}
		if (server.getPlayerList().getPlayers().isEmpty()) {
			WKLog.info("[raphael] " + report.oneLine());
		}
	}

	private static void deliverTo(ServerPlayer player, AnalysisReport report, boolean speak, @Nullable UUID nationId) {
		if (!speak) {
			// それでも GUI / HUD 用にシンクへ流す（読み上げなし）
			for (RaphaelSink sink : snapshotSinks()) {
				try {
					sink.deliver(player, report, false, nationId);
				} catch (Throwable t) {
					WKLog.warn("raphael sink failed: " + sink.name() + " / " + t);
				}
			}
			return;
		}
		for (RaphaelSink sink : snapshotSinks()) {
			try {
				sink.deliver(player, report, true, nationId);
			} catch (Throwable t) {
				WKLog.warn("raphael sink failed: " + sink.name() + " / " + t);
			}
		}
	}

	/** チャット配送（標準シンク）。 */
	public static void chatDelivery(ServerPlayer player, AnalysisReport report) {
		for (Component line : report.toChatLines()) {
			player.displayClientMessage(line, false);
		}
	}

	private static synchronized List<RaphaelSink> snapshotSinks() {
		return new ArrayList<>(SINKS);
	}

	// ------------------------------------------------------------------
	// 定型イベント（国家システムから呼ばれる）
	// ------------------------------------------------------------------

	/** 一般的なイベント報告。key は翻訳キーの一部（GUI・音声の分類に使う）。 */
	public static void event(MinecraftServer server, String key, String detail) {
		AnalysisReport report = AnalysisReport.of(Tone.REPORT, key, Urgency.NOTICE,
				detail, "詳細は国家ダッシュボードを参照してください。");
		announce(server, report);
	}

	/** 王位継承の報告（性格・政策変化を解析として付す）。 */
	public static void succession(MinecraftServer server, Nation nation, NpcRecord successor, String cause,
			String policyText, @Nullable UUID killerNationId) {
		RaphaelSink chat = (player, report, speak, nationId) -> chatDelivery(player, report);
		AnalysisReport report = AnalysisReport.of(Tone.ANALYSIS,
				"王位継承を検出。" + nation.name + " 第" + nation.dynastyGeneration + "代国王 " + successor.name
						+ "（性格: " + successor.personality.name() + "）が即位しました。",
				Urgency.CRITICAL,
				"継承原因: " + cause,
				policyText,
				"人望 " + Math.round(successor.popularity * 100) + "% / 戦力 "
						+ String.format("%.2f", successor.power) + " / 忠誠 100%",
				"予測: " + successor.personality.key() + " の傾向により外交スタンスが変化します。");
		announceTo(server, report, nation);
		// 他国にも伝わる（情報は漏れる）
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			NationManager manager = NationManager.get();
			if (manager == null) {
				continue;
			}
			UUID playerNation = manager.nationIdOfPlayer(player);
			if (playerNation == null || !playerNation.equals(nation.id)) {
				player.displayClientMessage(Component.literal("§7[情報] " + nation.name + " で王位継承が発生した。"), false);
			}
		}
	}

	/** 戦況報告。 */
	public static void warReport(MinecraftServer server, com.wisdomking.nation.War war, Nation attacker,
			Nation defender, String battleEvent) {
		int score = war.score;
		String state = score > 25 ? "攻撃側優勢" : score < -25 ? "防衛側優勢" : "拮抗";
		AnalysisReport report = AnalysisReport.of(Tone.REPORT,
				"戦況解析: " + attacker.name + " vs " + defender.name + " — " + state
						+ "（スコア " + score + "）",
				score > 25 || score < -25 ? Urgency.NOTICE : Urgency.INFO,
				battleEvent,
				"攻撃側戦力 " + String.format("%.1f", attacker.militaryPower())
						+ " / 防衛側戦力 " + String.format("%.1f", defender.militaryPower()),
				"戦争局面: " + war.phase.name());
		announce(server, report);
	}

	/** 機械・自動化ラインの推奨（Create等）。 */
	public static void advice(ServerPlayer player, String headline, List<String> details) {
		AnalysisReport report = new AnalysisReport(Tone.ALERT, headline, details, Urgency.NOTICE);
		remember(report);
		deliverTo(player, report, true, null);
	}

	/** Mod 解析の完了報告。 */
	public static void scanComplete(ServerPlayer player, String headline, List<String> details) {
		AnalysisReport report = new AnalysisReport(Tone.SCAN, headline, details, Urgency.INFO);
		remember(report);
		deliverTo(player, report, false, null);
	}

	public static String modTag() {
		return WK.MOD_ID + ":raphael";
	}
}
