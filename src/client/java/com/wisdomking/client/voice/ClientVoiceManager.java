package com.wisdomking.client.voice;

import com.wisdomking.WK;
import com.wisdomking.core.WKConfig;
import com.wisdomking.raphael.Urgency;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * クライアント側の音声入出力マネージャ（STT / TTS）。
 *
 * <p>構成:</p>
 * <ul>
 *   <li><b>STT（音声入力）</b>: {@link TargetDataLine} でマイク音声を取得し、
 *       設定された認識エンドポイント（Whisper系HTTP API）へ送信して文字列を得る。
 *       認識結果は {@code ActionPayload} としてサーバーへ送られ、
 *       {@link com.wisdomking.voice.VoiceCommandRouter} が命令として解釈する。</li>
 *   <li><b>TTS（音声出力）</b>: 内蔵の手続き的ボイス合成（オフライン・追加依存なし）と、
 *       外部TTSエンジン（HTTP）の二系統。どちらも {@link SourceDataLine} で再生する。
 *       失敗時は字幕表示のみに縮退し、クライアントを落とさない。</li>
 * </ul>
 *
 * <p>Simple Voice Chat など音声Modが導入されている環境では、それらの音声経路と
 * 併用しても競合しない（本Modは独自の音声ストリームを使用する）。</p>
 */
public final class ClientVoiceManager {
	private static final ExecutorService AUDIO_POOL = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "wisdomking-audio");
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicBoolean RECORDING = new AtomicBoolean(false);
	private static final AtomicBoolean SPEAKING = new AtomicBoolean(false);
	private static TargetDataLine microphone;
	private static long recordStartTick;

	private ClientVoiceManager() {
	}

	public static void init() {
		WK.LOG.info("[wisdomking] voice manager ready (tts={}, stt={})",
				WKConfig.get().ttsMode, WKConfig.get().sttEnabled ? "enabled" : "disabled");
	}

	/** プッシュ・トゥ・トークの開始／終了。 */
	public static void togglePushToTalk() {
		if (RECORDING.get()) {
			stopRecordingAndRecognize();
		} else {
			startRecording();
		}
	}

	private static void startRecording() {
		if (!WKConfig.get().sttEnabled) {
			message("§e[音声] STTは無効です。config/wisdomking.json の sttEnabled を true にし、"
					+ "sttEndpoint を設定してください（または /wk voice <命令> を使用）。");
			return;
		}
		try {
			AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			if (!AudioSystem.isLineSupported(info)) {
				message("§c[音声] マイク入力が利用できません。");
				return;
			}
			microphone = (TargetDataLine) AudioSystem.getLine(info);
			microphone.open(format);
			microphone.start();
			RECORDING.set(true);
			recordStartTick = System.currentTimeMillis() / 50L;
			message("§b[音声] 録音中... もう一度 V キーで送信します。");
		} catch (Throwable t) {
			RECORDING.set(false);
			message("§c[音声] マイクを開けませんでした: " + t.getMessage());
		}
	}

	private static void stopRecordingAndRecognize() {
		RECORDING.set(false);
		TargetDataLine line = microphone;
		microphone = null;
		if (line == null) {
			return;
		}
		AUDIO_POOL.submit(() -> {
			try {
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				line.stop();
				byte[] chunk = new byte[4096];
				int read;
				line.flush();
				// 録音バッファを読み切る
				while ((read = line.read(chunk, 0, chunk.length)) > 0 && buffer.size() < 48000 * 4) {
					buffer.write(chunk, 0, read);
					if (buffer.size() > 0 && line.available() == 0) {
						break;
					}
				}
				line.close();
				byte[] audio = buffer.toByteArray();
				if (audio.length < 3200) {
					message("§e[音声] 録音が短すぎます。");
					return;
				}
				String text = recognize(audio);
				if (text == null || text.isBlank()) {
					message("§e[音声] 認識結果が得られませんでした。");
					return;
				}
				message("§b[音声] 認識: " + text);
				com.wisdomking.client.WKClientNetwork.sendAction("voice_command", text);
			} catch (Throwable t) {
				message("§c[音声] 認識処理に失敗しました: " + t.getMessage());
			}
		});
	}

	/** 認識エンドポイントへ WAV を送信して文字列を得る。 */
	private static String recognize(byte[] pcm) {
		String endpoint = WKConfig.get().sttEndpoint;
		if (endpoint == null || endpoint.isBlank()) {
			return null;
		}
		try {
			byte[] wav = toWav(pcm, 16000, 1, 16);
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
					.timeout(Duration.ofSeconds(30))
					.header("Content-Type", "audio/wav")
					.POST(HttpRequest.BodyPublishers.ofByteArray(wav))
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				WK.LOG.warn("STT endpoint returned {}", response.statusCode());
				return null;
			}
			return extractTextField(response.body());
		} catch (Throwable t) {
			WK.LOG.warn("STT request failed: {}", t.toString());
			return null;
		}
	}

	/** 応答JSONから "text" フィールドを簡易抽出する。 */
	private static String extractTextField(String json) {
		int index = json.indexOf("\"text\"");
		if (index < 0) {
			return json.trim();
		}
		int colon = json.indexOf(':', index);
		int start = json.indexOf('"', colon + 1);
		StringBuilder builder = new StringBuilder();
		for (int i = start + 1; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c == '\\' && i + 1 < json.length()) {
				builder.append(json.charAt(++i));
				continue;
			}
			if (c == '"') {
				break;
			}
			builder.append(c);
		}
		return builder.toString();
	}

	// ------------------------------------------------------------------
	// TTS
	// ------------------------------------------------------------------

	/** 智慧之王の発話を再生する（内声・距離減衰なし）。 */
	public static void speak(String text, Urgency urgency) {
		speakInternal(text, urgency, 1.0F);
	}

	/**
	 * NPC の発話を距離減衰つきで再生する（3D音声相当）。
	 *
	 * <p>NPC の位置とプレイヤーの距離に応じて音量を下げることで、
	 * 立体感のある「その場の声」を再現する。Simple Voice Chat などの
	 * 音声Modとは独立した経路（自前の SourceDataLine）を使うため競合しない。</p>
	 */
	public static void speakAt(String text, Urgency urgency, double distance) {
		double range = Math.max(2.0D, WKConfig.get().voiceDistance);
		float gain = (float) Math.max(0.15D, Math.min(1.0D, 1.0D - (distance / range)));
		speakInternal(text, urgency, gain);
	}

	private static void speakInternal(String text, Urgency urgency, float gain) {
		if (text == null || text.isBlank() || !WKConfig.get().voiceEnabled) {
			return;
		}
		String mode = WKConfig.get().ttsMode;
		if ("off".equalsIgnoreCase(mode)) {
			return;
		}
		if ("http".equalsIgnoreCase(mode) && WKConfig.get().ttsEndpoint != null
				&& !WKConfig.get().ttsEndpoint.isBlank()) {
			AUDIO_POOL.submit(() -> speakHttp(text, urgency, gain));
		} else {
			AUDIO_POOL.submit(() -> speakProcedural(text, urgency, gain));
		}
	}

	/** 外部TTSエンジンから音声を取得して再生する。 */
	private static void speakHttp(String text, Urgency urgency, float gain) {
		try {
			String endpoint = WKConfig.get().ttsEndpoint;
			String body = "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
					+ "\",\"voice\":\"" + WKConfig.get().ttsVoice + "\"}";
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
					.timeout(Duration.ofSeconds(20))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
					.build();
			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() / 100 != 2) {
				speakProcedural(text, urgency, gain);
				return;
			}
			byte[] audio = response.body();
			final byte[] pcm;
			final int rate;
			final int channels;
			if (isWav(audio)) {
				pcm = wavData(audio);
				rate = wavSampleRate(audio);
				channels = wavChannels(audio);
			} else {
				// 生PCM（16bit LE, 22050Hz mono）とみなす
				pcm = audio;
				rate = 22050;
				channels = 1;
			}
			playPcm(pcm, rate, channels, gain);
		} catch (Throwable t) {
			WK.LOG.warn("HTTP TTS failed, falling back to procedural voice: {}", t.toString());
			speakProcedural(text, urgency, gain);
		}
	}

	/**
	 * 内蔵の手続き的ボイス合成。
	 *
	 * <p>外部エンジンが無い環境でも「それらしい声」を出すため、
	 * 文字ごとに基本周波数とフォルマント（倍音構成）を割り当てた
	 * 短い音節列を合成する。日本語のモーラ数に応じてピッチを変化させる。</p>
	 */
	private static void speakProcedural(String text, Urgency urgency, float gain) {
		try {
			int sampleRate = 22050;
			double basePitch = switch (urgency) {
				case CRITICAL -> 132.0;
				case NOTICE -> 146.0;
				default -> 158.0;
			};
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			String stripped = text.replace("《", "").replace("》", "");
			int syllables = 0;
			for (int i = 0; i < stripped.length(); i++) {
				char c = stripped.charAt(i);
				if (Character.isWhitespace(c) || c == '、' || c == '。') {
					appendSilence(out, sampleRate, 40);
					continue;
				}
				double pitch = basePitch * (1.0 + ((c % 7) - 3) * 0.018);
				int durationMs = 70 + (c % 5) * 12;
				appendVoice(out, sampleRate, pitch, durationMs,
						c % 3 == 0 ? 0.5 : (c % 3 == 1 ? 0.35 : 0.45));
				syllables++;
				if (syllables > 220) {
					break;
				}
			}
			byte[] pcm = out.toByteArray();
			if (pcm.length == 0) {
				return;
			}
			playPcm(pcm, sampleRate, 1, gain);
		} catch (Throwable t) {
			WK.LOG.warn("procedural TTS failed safely: {}", t.toString());
		}
	}

	/** 16bit LE PCM に音量ゲインを適用する（3D音声の距離減衰）。 */
	private static byte[] applyGain(byte[] pcm, float gain) {
		byte[] out = new byte[pcm.length];
		for (int i = 0; i + 1 < pcm.length; i += 2) {
			int sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
			int scaled = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample * gain)));
			out[i] = (byte) (scaled & 0xFF);
			out[i + 1] = (byte) ((scaled >> 8) & 0xFF);
		}
		return out;
	}

	private static void appendSilence(ByteArrayOutputStream out, int sampleRate, int ms) {
		int count = sampleRate * ms / 1000;
		for (int i = 0; i < count; i++) {
			out.write(0);
			out.write(0);
		}
	}

	private static void appendVoice(ByteArrayOutputStream out, int sampleRate, double pitch, int ms, double brightness) {
		int count = sampleRate * ms / 1000;
		for (int i = 0; i < count; i++) {
			double t = (double) i / sampleRate;
			double envelope = Math.min(1.0, i / (sampleRate * 0.008))
					* Math.min(1.0, (count - i) / (sampleRate * 0.02));
			double value = Math.sin(2 * Math.PI * pitch * t)
					+ brightness * Math.sin(2 * Math.PI * pitch * 2 * t)
					+ 0.25 * Math.sin(2 * Math.PI * pitch * 3 * t)
					+ 0.08 * (Math.random() - 0.5);
			double sample = value * envelope * 0.22 * WKConfig.get().voiceVolume;
			short s = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample * Short.MAX_VALUE));
			out.write(s & 0xFF);
			out.write((s >> 8) & 0xFF);
		}
	}

	private static void playPcm(byte[] pcm, int sampleRate, int channels, float gain) {
		if (gain < 0.99F) {
			pcm = applyGain(pcm, gain);
		}
		if (SPEAKING.getAndSet(true)) {
			return;
		}
		try {
			AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			if (!AudioSystem.isLineSupported(info)) {
				WK.LOG.warn("no audio output available for TTS");
				return;
			}
			try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
				line.open(format);
				line.start();
				line.write(pcm, 0, pcm.length);
				line.drain();
				line.stop();
			}
		} catch (Throwable t) {
			WK.LOG.warn("TTS playback failed: {}", t.toString());
		} finally {
			SPEAKING.set(false);
		}
	}

	/** クライアントtick（録音の自動停止など）。 */
	public static void tick(Minecraft client) {
		if (RECORDING.get()) {
			long now = System.currentTimeMillis() / 50L;
			if (now - recordStartTick > 200L) { // 10秒で自動終了
				stopRecordingAndRecognize();
			}
		}
	}

	private static void message(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(text), false);
		}
	}

	// --- WAV ユーティリティ ---

	private static byte[] toWav(byte[] pcm, int sampleRate, int channels, int bits) {
		byte[] header = new byte[44];
		int dataSize = pcm.length;
		int byteRate = sampleRate * channels * bits / 8;
		writeAscii(header, 0, "RIFF");
		writeInt(header, 4, 36 + dataSize);
		writeAscii(header, 8, "WAVE");
		writeAscii(header, 12, "fmt ");
		writeInt(header, 16, 16);
		writeShort(header, 20, (short) 1);
		writeShort(header, 22, (short) channels);
		writeInt(header, 24, sampleRate);
		writeInt(header, 28, byteRate);
		writeShort(header, 32, (short) (channels * bits / 8));
		writeShort(header, 34, (short) bits);
		writeAscii(header, 36, "data");
		writeInt(header, 40, dataSize);
		byte[] wav = new byte[44 + dataSize];
		System.arraycopy(header, 0, wav, 0, 44);
		System.arraycopy(pcm, 0, wav, 44, dataSize);
		return wav;
	}

	private static void writeAscii(byte[] target, int offset, String value) {
		for (int i = 0; i < value.length(); i++) {
			target[offset + i] = (byte) value.charAt(i);
		}
	}

	private static void writeInt(byte[] target, int offset, int value) {
		target[offset] = (byte) (value & 0xFF);
		target[offset + 1] = (byte) ((value >> 8) & 0xFF);
		target[offset + 2] = (byte) ((value >> 16) & 0xFF);
		target[offset + 3] = (byte) ((value >> 24) & 0xFF);
	}

	private static void writeShort(byte[] target, int offset, short value) {
		target[offset] = (byte) (value & 0xFF);
		target[offset + 1] = (byte) ((value >> 8) & 0xFF);
	}

	private static boolean isWav(byte[] data) {
		return data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F';
	}

	private static int readInt(byte[] data, int offset) {
		return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
				| ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
	}

	private static int readShort(byte[] data, int offset) {
		return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
	}

	private static int wavSampleRate(byte[] data) {
		return data.length > 28 ? readInt(data, 24) : 22050;
	}

	private static int wavChannels(byte[] data) {
		return data.length > 24 ? Math.max(1, readShort(data, 22)) : 1;
	}

	private static byte[] wavData(byte[] data) {
		// "data" チャンクを探索する
		for (int i = 12; i + 8 < data.length; ) {
			String id = new String(data, i, 4, StandardCharsets.US_ASCII);
			int size = readInt(data, i + 4);
			if ("data".equals(id)) {
				int start = i + 8;
				int length = Math.min(size, data.length - start);
				byte[] out = new byte[length];
				System.arraycopy(data, start, out, 0, length);
				return out;
			}
			i += 8 + size + (size % 2);
		}
		return new byte[0];
	}
}
