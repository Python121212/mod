package com.wisdomking.network;

import com.wisdomking.core.WKLog;
import net.minecraft.server.level.ServerPlayer;

/**
 * クライアント／サーバー間通信の窓口（Fabric 1.21.1 の {@code CustomPayload}）。
 *
 * <p>Step 5 で実装されるペイロード群の登録・送信を一括して扱う。
 * ネットワーク層が未初期化の場合でも各システムが安全に動作するよう、
 * すべてのメソッドは失敗時に静かに縮退する。</p>
 */
public final class WKNetwork {
	private static boolean registered;

	private WKNetwork() {
	}

	/** ペイロード型の登録（サーバー・クライアント共通）。 */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		WKLog.safely("network init", () -> WKPayloads.registerCommon());
	}

	/** 国家ダッシュボードの表示要求（サーバー→クライアント）。 */
	public static void openDashboard(ServerPlayer player) {
		WKLog.safely("open dashboard", () -> WKPayloads.sendDashboard(player));
		player.displayClientMessage(net.minecraft.network.chat.Component.literal(
				"§7[wisdomking] 国家ダッシュボード（GUI）はクライアント連携時に表示されます。"), false);
	}

	/** 智慧之王の発言をクライアントへ送る（音声・HUD用）。 */
	public static void sendRaphaelMessages(ServerPlayer player) {
		WKLog.safely("raphael sync", () -> WKPayloads.sendRaphaelHistory(player));
	}
}
