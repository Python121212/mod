package com.wisdomking.raphael;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/** 標準チャット配送シンク。常に有効な最終フォールバック。 */
public final class ChatSink implements RaphaelSink {
	public static final ChatSink INSTANCE = new ChatSink();

	private ChatSink() {
	}

	@Override
	public void deliver(ServerPlayer player, AnalysisReport report, boolean speak, @Nullable UUID nationId) {
		RaphaelRelay.chatDelivery(player, report);
	}

	@Override
	public String name() {
		return "chat";
	}
}
