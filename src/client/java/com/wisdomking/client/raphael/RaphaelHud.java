package com.wisdomking.client.raphael;

import com.wisdomking.raphael.AnalysisReport;
import com.wisdomking.raphael.Tone;
import com.wisdomking.raphael.Urgency;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 智慧之王 HUD オーバーレイ。
 *
 * <p>画面右下に最新の解析レポート（標識 + 見出し）を一定時間表示し、
 * 音声読み上げ中の字幕としても機能する。緊急（CRITICAL）は赤、警告は金、
 * 解析結果は水色で表示する。</p>
 */
public final class RaphaelHud {
	private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
	private static final int MAX_ENTRIES = 4;
	private static final long DISPLAY_TICKS = 200L;

	private RaphaelHud() {
	}

	private record Entry(AnalysisReport report, long expireTick) {
	}

	public static void init() {
		HudRenderCallback.EVENT.register(RaphaelHud::onRender);
	}

	/** レポートをHUDへ積む。 */
	public static synchronized void push(AnalysisReport report) {
		long now = System.currentTimeMillis() / 50L;
		ENTRIES.addLast(new Entry(report, now + DISPLAY_TICKS));
		while (ENTRIES.size() > MAX_ENTRIES) {
			ENTRIES.removeFirst();
		}
	}

	private static synchronized void onRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		long now = System.currentTimeMillis() / 50L;
		ENTRIES.removeIf(entry -> entry.expireTick() < now);
		if (ENTRIES.isEmpty()) {
			return;
		}
		int y = graphics.guiHeight() - 60;
		for (Entry entry : ENTRIES) {
			int color = switch (entry.report().urgency()) {
				case CRITICAL -> 0xFF5555;
				case NOTICE -> 0xFFD070;
				default -> 0x80D0FF;
			};
			String text = entry.report().tone().token() + " " + entry.report().headline();
			int width = client.font.width(text);
			int x = graphics.guiWidth() - width - 8;
			graphics.fill(x - 4, y - 2, graphics.guiWidth() - 2, y + 10, 0x80000000);
			graphics.drawString(client.font, text, x, y, color, true);
			y -= 14;
		}
	}

	/** 表示中のレポート数。 */
	public static int size() {
		return ENTRIES.size();
	}

	/** 字幕として最新の発言を取得する。 */
	public static String latestSubtitle() {
		Entry entry = ENTRIES.peekLast();
		return entry == null ? "" : entry.report().spokenText();
	}
}
