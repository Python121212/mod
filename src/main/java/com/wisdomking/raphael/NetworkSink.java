package com.wisdomking.raphael;

import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * ネットワーク配送シンク。
 *
 * <p>チャット（{@link ChatSink}）に加えて、クライアントへペイロードを送信し、
 * GUI の解析ログ・HUD 表示・音声合成（TTS）を有効化する。
 * ネットワーク層が利用できない場合でも例外を投げずに縮退する。</p>
 */
public final class NetworkSink implements RaphaelSink {
	@Override
	public void deliver(ServerPlayer player, AnalysisReport report, boolean speak, @Nullable UUID nationId) {
		try {
			com.wisdomking.network.WKPayloads.sendRaphael(player, report, speak);
		} catch (Throwable ignored) {
			// ネットワーク未初期化時はチャットのみで継続する
		}
	}

	@Override
	public String name() {
		return "network";
	}
}
