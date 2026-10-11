package com.wisdomking.client.voice;

import com.wisdomking.WK;
import com.wisdomking.client.render.NpcInfoCache;
import com.wisdomking.core.WKConfig;
import com.wisdomking.entity.WKNpcEntity;
import com.wisdomking.raphael.Urgency;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * NPC の「声」を演出するクライアント側ディレクター（3D TTS）。
 *
 * <p>近くにいる NPC が一定間隔で役割に応じた台詞をつぶやく。
 * 音量は距離で減衰させるため、どのNPCが話しているかが音で分かる。
 * サーバー権限を持たない純クライアントの演出であり、
 * セリフは言語ファイル（{@code wisdomking.voice.*}）から取得する。</p>
 */
public final class NpcVoiceDirector {
	/** 同じ NPC が再び話すまでの間隔（tick）。 */
	private static final long SPEAK_COOLDOWN = 1400L;
	private static final double RANGE = 10.0D;
	private static final Random RANDOM = new Random();
	private static final Map<Integer, Long> LAST_SPOKE = new HashMap<>();
	private static long lastGlobalSpeak;

	private NpcVoiceDirector() {
	}

	/** クライアント tick から毎tick呼ばれる。 */
	public static void tick(Minecraft client) {
		try {
			if (client == null || client.level == null || client.player == null) {
				return;
			}
			if (!WKConfig.get().voiceEnabled) {
				return;
			}
			long now = client.level.getGameTime();
			if (now - lastGlobalSpeak < 400L) {
				return;
			}
			LocalPlayer player = client.player;
			AABB box = player.getBoundingBox().inflate(RANGE);
			List<WKNpcEntity> nearby = client.level.getEntitiesOfClass(WKNpcEntity.class, box);
			for (WKNpcEntity npc : nearby) {
				Long last = LAST_SPOKE.get(npc.getId());
				if (last != null && now - last < SPEAK_COOLDOWN) {
					continue;
				}
				if (npc.distanceTo(player) > RANGE) {
					continue;
				}
				if (RANDOM.nextFloat() > 0.25F) {
					continue;
				}
				String line = ambientLine(npc);
				if (line == null) {
					continue;
				}
				LAST_SPOKE.put(npc.getId(), now);
				lastGlobalSpeak = now;
				ClientVoiceManager.speakAt(line, Urgency.NOTICE, npc.distanceTo(player));
				// 字幕（音声をOFFにしていても内容が分かるようにする）
				player.displayClientMessage(Component.literal("§7" + nameOf(npc) + "§7: §f" + line), false);
				break;
			}
		} catch (Throwable t) {
			WK.LOG.debug("npc voice director failed: {}", t.toString());
		}
	}

	private static String nameOf(WKNpcEntity npc) {
		NpcInfoCache.Entry info = NpcInfoCache.get(npc.getId());
		if (info != null && !info.name().isEmpty()) {
			return info.name();
		}
		return npc.getName().getString();
	}

	/** 役割・性格に応じた短い台詞。 */
	private static String ambientLine(WKNpcEntity npc) {
		String role = "citizen";
		NpcInfoCache.Entry info = NpcInfoCache.get(npc.getId());
		if (info != null) {
			role = info.role().name().toLowerCase(java.util.Locale.ROOT);
		}
		String key = "wisdomking.voice." + role;
		String line = Component.translatable(key).getString();
		if (line == null || line.isEmpty() || line.equals(key)) {
			line = Component.translatable("wisdomking.voice.citizen").getString();
		}
		return line;
	}

	/** 切断時にキャッシュを破棄する。 */
	public static void clear() {
		LAST_SPOKE.clear();
		lastGlobalSpeak = 0L;
	}

	/** 使用可能かどうかのデバッグ情報。 */
	public static String debugTag() {
		return "npc-voice-cache=" + LAST_SPOKE.size() + "/npcs=" + NpcInfoCache.size();
	}
}
