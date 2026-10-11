package com.wisdomking.client;

import com.wisdomking.WK;
import com.wisdomking.client.gui.NationDashboardScreen;
import com.wisdomking.client.voice.ClientVoiceManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.CompoundTag;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * クライアントエントリポイント。
 *
 * <p>国家ダッシュボードGUI、智慧之王 HUD、音声入出力（STT/TTS）を初期化する。</p>
 */
public class WisdomKingClient implements ClientModInitializer {
	public static final String KEY_CATEGORY = "key.categories.wisdomking";

	public static KeyMapping dashKey;
	public static KeyMapping voiceKey;
	public static KeyMapping analyzeKey;

	/** サーバーから受信した最新のダッシュボードデータ。 */
	private static CompoundTag lastDashboard;
	/** サーバーから受信した最新の智慧之王レポート。 */
	private static CompoundTag lastRaphael;

	public static CompoundTag dashboard() {
		return lastDashboard;
	}

	public static CompoundTag raphael() {
		return lastRaphael;
	}

	@Override
	public void onInitializeClient() {
		WK.LOG.info("[wisdomking] client initialising");
		com.wisdomking.client.WKClientNetwork.register();

		dashKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.wisdomking.dashboard",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, KEY_CATEGORY));
		analyzeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.wisdomking.analyze",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY));
		voiceKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.wisdomking.voice",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KEY_CATEGORY));

		ClientVoiceManager.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (dashKey.consumeClick()) {
				if (lastDashboard == null || !lastDashboard.getBoolean("valid")) {
					WKClientNetwork.requestDashboard();
				}
				if (lastDashboard != null) {
					client.setScreen(new NationDashboardScreen(lastDashboard));
				}
			}
			while (analyzeKey.consumeClick()) {
				WKClientNetwork.requestAnalyzeTarget();
			}
			while (voiceKey.consumeClick()) {
				ClientVoiceManager.togglePushToTalk();
			}
			ClientVoiceManager.tick(client);
		});
	}

	/** サーバーから受信: 国家ダッシュボード。 */
	public static void onDashboard(CompoundTag data) {
		lastDashboard = data;
	}

	/** サーバーから受信: 智慧之王レポート（チャット・HUD・音声）。 */
	public static void onRaphael(CompoundTag data) {
		lastRaphael = data;
		boolean speak = data.getBoolean("Speak");
		com.wisdomking.raphael.AnalysisReport report = com.wisdomking.raphael.AnalysisReport.load(data);
		com.wisdomking.client.raphael.RaphaelHud.push(report);
		if (speak) {
			ClientVoiceManager.speak(report.spokenText(), report.urgency());
		}
	}
}
