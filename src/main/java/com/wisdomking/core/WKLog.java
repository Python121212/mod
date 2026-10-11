package com.wisdomking.core;

import com.wisdomking.WK;

/** ログ出力ヘルパ。例外を握りつぶしてクラッシュを防ぐ安全設計のための窓口。 */
public final class WKLog {
	private WKLog() {
	}

	public static void info(String msg) {
		WK.LOG.info(msg);
	}

	public static void warn(String msg) {
		WK.LOG.warn(msg);
	}

	public static void error(String msg, Throwable t) {
		WK.LOG.error(msg, t);
	}

	/**
	 * 失敗しても安全に継続する処理を実行する。
	 *
	 * <p>他Mod連携（Create / Iron's Spells / Simple Voice Chat 等）は
	 * 導入されていない環境でクラッシュしてはならないため、必ずこのヘルパを経由する。</p>
	 */
	public static void safely(String what, Runnable action) {
		try {
			action.run();
		} catch (Throwable t) {
			WK.LOG.warn("[wisdomking] optional integration '{}' skipped: {}", what, t.toString());
		}
	}
}
