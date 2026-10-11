package com.wisdomking.raphael;

/** 報告の緊急度。チャット配色・TTS の読み上げ優先度・HUD 表示の扱いを決める。 */
public enum Urgency {
	/** 通常の情報。 */
	INFO,
	/** 注意喚起。 */
	NOTICE,
	/** 重大（侵攻・首都危機・クーデター等）。音声で必ず読み上げる。 */
	CRITICAL;

	public static Urgency byName(String name, Urgency fallback) {
		try {
			return valueOf(name);
		} catch (Throwable t) {
			return fallback;
		}
	}
}
