package com.wisdomking.raphael;

import net.minecraft.ChatFormatting;

/**
 * 智慧之王（ラファエル）の発言冒頭に付与される宣告標識。
 *
 * <p>仕様により、すべての発言は以下のいずれかで始まる:</p>
 * <ul>
 *   <li>《告》 — 警告・脅威・推奨（WARNING）</li>
 *   <li>《解》 — 解析・解釈（ANALYSIS）</li>
 *   <li>《解析完了》 — 走査・調査の完了報告（SCAN）</li>
 *   <li>《報告》 — 事実・戦況・国家情勢の報告（REPORT）</li>
 * </ul>
 */
public enum Tone {
	/** 警告・推奨。 */
	ALERT("wisdomking.raphael.tone.alert", "《告》", ChatFormatting.GOLD),
	/** 解析結果。 */
	ANALYSIS("wisdomking.raphael.tone.analysis", "《解》", ChatFormatting.AQUA),
	/** 解析完了。 */
	SCAN("wisdomking.raphael.tone.scan", "《解析完了》", ChatFormatting.LIGHT_PURPLE),
	/** 報告。 */
	REPORT("wisdomking.raphael.tone.report", "《報告》", ChatFormatting.WHITE);

	private final String langKey;
	private final String token;
	private final ChatFormatting color;

	Tone(String langKey, String token, ChatFormatting color) {
		this.langKey = langKey;
		this.token = token;
		this.color = color;
	}

	public String langKey() {
		return langKey;
	}

	/** 実際にチャットへ表示する標識。 */
	public String token() {
		return token;
	}

	public ChatFormatting color() {
		return color;
	}

	public static Tone byName(String name, Tone fallback) {
		try {
			return valueOf(name);
		} catch (Throwable t) {
			return fallback;
		}
	}
}
