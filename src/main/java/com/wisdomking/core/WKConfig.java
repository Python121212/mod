package com.wisdomking.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wisdomking.WK;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 智慧之王システムの構成ファイル（{@code config/wisdomking.json}）。
 *
 * <p>国家数・戦争パラメータ・解析範囲・音声入出力の設定を保持する。
 * 読み込み失敗時は既定値で動作を継続する（クラッシュさせない）。</p>
 */
public final class WKConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static WKConfig instance;

	// --- 国家システム ---
	/** 初期国家数（5以上を推奨）。 */
	public int initialNations = 6;
	/** 初期人口（台帳NPC数）。 */
	public int initialPopulation = 28;
	/** 首都の初期領土半径（チャンク）。 */
	public int initialClaimRadius = 3;
	/** 国家ごとのNPC実体化上限。 */
	public int npcEntityCap = 48;
	/** NPCを実体化する距離（プレイヤーからのブロック数）。 */
	public double npcMaterializeRadius = 96.0;
	/** 拠点がチャンク未ロードでも台帳を維持するか。 */
	public boolean persistentNpcLedger = true;

	// --- 戦争・外交 ---
	public int warCooldownTicks = 12000;
	public int truceTicks = 24000;
	public int warScoreThreshold = 70;
	public int maxWarTicks = 120000;
	public float coupUnrestThreshold = 0.6f;
	public boolean aiWarsEnabled = true;

	// --- 土木AI ---
	public boolean civilEngineering = true;
	/** 1tickあたりのブロック操作予算。 */
	public int schedulerBudget = 64;
	/** 道路敷設の最大長。 */
	public int roadMaxLength = 96;
	/** 土木AIが動作するプレイヤー近傍距離。 */
	public double civilEngineeringRadius = 128.0;

	// --- 智慧之王（解析） ---
	/** 機械走査の半径。 */
	public int analysisRadius = 24;
	public boolean passiveAnalysis = true;
	public int passiveAnalysisIntervalTicks = 1200;
	public boolean speakNotices = true;
	/** チャット標識を付与するか（仕様により既定 true）。 */
	public boolean toneTokens = true;

	// --- 音声（STT/TTS） ---
	public boolean voiceEnabled = true;
	/** TTS: "procedural"（内蔵・オフライン） / "http"（外部エンジン） / "off" */
	public String ttsMode = "procedural";
	public String ttsEndpoint = "";
	public String ttsVoice = "raphael";
	public float voiceVolume = 0.9f;
	public double voiceDistance = 32.0;
	/** STT: マイク入力による命令認識。 */
	public boolean sttEnabled = false;
	public String sttMode = "http";
	public String sttEndpoint = "";
	public String sttLanguage = "ja";
	public double sttSilenceThreshold = 0.06;

	// --- 保護 ---
	public boolean protectTerritory = true;
	public boolean protectFromExplosions = true;

	// --- デバッグ ---
	public boolean debug = false;

	public static WKConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(WK.MOD_ID + ".json");
	}

	public static WKConfig load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				WKConfig config = GSON.fromJson(json, WKConfig.class);
				if (config != null) {
					return config;
				}
			} catch (Throwable t) {
				WKLog.warn("wisdomking config unreadable, falling back to defaults: " + t);
			}
		}
		WKConfig config = new WKConfig();
		config.save();
		return config;
	}

	public void save() {
		try {
			Path path = path();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			WKLog.warn("failed to write wisdomking config: " + e.getMessage());
		}
	}

	public static void reload() {
		instance = load();
	}
}
