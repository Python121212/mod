package com.wisdomking.raphael;

import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * 智慧之王の発言を配送する先（シンク）。
 *
 * <p>標準ではチャットのみ。クライアント側の TTS / HUD / 3D ボイス機能は
 * ネットワーク層（Step 5・6）がこのシンクを差し替えることで有効化される。
 * この設計により、音声Modが未導入の環境でも一切クラッシュせず縮退動作する。</p>
 */
public interface RaphaelSink {
	/**
	 * レポートを配送する。
	 *
	 * @param player 送信先プレイヤー
	 * @param report レポート
	 * @param speak 音声読み上げを行うか
	 * @param nationId 対象国家（紐付く国家の構成員のみに送る場合に使用、null なら全体）
	 */
	void deliver(ServerPlayer player, AnalysisReport report, boolean speak, @Nullable UUID nationId);

	/** シンクの名前（デバッグ用）。 */
	default String name() {
		return getClass().getSimpleName();
	}
}
