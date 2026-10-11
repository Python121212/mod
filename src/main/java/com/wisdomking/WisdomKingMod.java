package com.wisdomking;

import com.wisdomking.core.WKCommands;
import com.wisdomking.core.WKConfig;
import com.wisdomking.core.WKEvents;
import com.wisdomking.core.WKLog;
import com.wisdomking.core.WKRegistry;
import com.wisdomking.network.WKNetwork;
import com.wisdomking.network.WKPayloads;
import com.wisdomking.raphael.ChatSink;
import com.wisdomking.raphael.RaphaelRelay;

/**
 * 智慧之王（Wisdom King）Mod のメインエントリポイント。
 *
 * <p>Fabric 1.21.1 / Java 21。以下を初期化する:</p>
 * <ol>
 *   <li>構成ファイル（{@code config/wisdomking.json}）</li>
 *   <li>ブロック・アイテム・ブロックエンティティの登録</li>
 *   <li>ネットワーク（{@link WKPayloads} のペイロード型 + サーバー受信ハンドラ）</li>
 *   <li>Fabric イベント（国家tick・土木AI・領域保護・死亡イベント）</li>
 *   <li>コマンド（{@code /wisdomking}, {@code /wk}）</li>
 *   <li>智慧之王の配送シンク（チャット + 音声・GUI連携）</li>
 * </ol>
 */
public class WisdomKingMod implements net.fabricmc.api.ModInitializer {
	public static final String MOD_ID = WK.MOD_ID;

	@Override
	public void onInitialize() {
		WKLog.info("《解析完了》智慧之王システム、初期化を開始します。");
		WKConfig.get();
		WKRegistry.init();
		WKNetwork.init();
		WKLog.safely("server payload receivers", WKPayloads::registerServer);
		WKEvents.register();
		WKCommands.register();
		// 標準チャット配送 + クライアント連携（音声/GUI）シンク
		RaphaelRelay.install(ChatSink.INSTANCE);
		RaphaelRelay.install(new com.wisdomking.raphael.NetworkSink());
		WKLog.info("《告》智慧之王、稼働状態に入りました。検出Mod数: "
				+ com.wisdomking.raphael.ModScanner.modCount());
	}

	public static net.minecraft.resources.ResourceLocation id(String path) {
		return WK.id(path);
	}
}
