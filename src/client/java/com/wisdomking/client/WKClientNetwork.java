package com.wisdomking.client;

import com.wisdomking.WK;
import com.wisdomking.network.WKPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.CompoundTag;

/**
 * クライアント側ネットワーク処理（{@code CustomPayload} の送受信）。
 *
 * <p>クライアント専用API（{@code ClientPlayNetworking}）はこのクラスに集約し、
 * サーバー側コードと分離する。split source set により、サーバー環境では
 * このクラス自体が読み込まれないため、専用サーバーでもクラッシュしない。</p>
 */
public final class WKClientNetwork {
	private WKClientNetwork() {
	}

	/** 受信ハンドラの登録。 */
	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(WKPayloads.DashboardPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> WisdomKingClient.onDashboard(payload.data())));
		ClientPlayNetworking.registerGlobalReceiver(WKPayloads.RaphaelPayload.TYPE,
				(payload, context) -> context.client().execute(
						() -> WisdomKingClient.onRaphael(payload.data())));
	}

	/** 国家ダッシュボードの表示を要求する。 */
	public static void requestDashboard() {
		send("request_dashboard", "");
	}

	/** 視線先の解析を要求する。 */
	public static void requestAnalyzeTarget() {
		send("analyze_target", "");
	}

	/** 手に持つアイテムの解析を要求する。 */
	public static void requestAnalyzeHeld() {
		send("analyze_held", "");
	}

	/** 任意の操作を送信する。 */
	public static void sendAction(String action, String value) {
		send(action, value);
	}

	private static void send(String action, String value) {
		try {
			CompoundTag tag = new CompoundTag();
			tag.putString("action", action);
			if (value != null && !value.isEmpty()) {
				tag.putString("text", value);
			}
			ClientPlayNetworking.send(new WKPayloads.ActionPayload(tag));
		} catch (Throwable t) {
			WK.LOG.warn("[wisdomking] failed to send action {}: {}", action, t.toString());
		}
	}
}
