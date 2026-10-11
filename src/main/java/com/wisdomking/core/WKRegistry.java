package com.wisdomking.core;

/** すべての登録処理を一括実行するエントリポイント。 */
public final class WKRegistry {
	private WKRegistry() {
	}

	public static void init() {
		WKBlocks.init();
		WKItems.init();
		WKBlockEntities.init();
		com.wisdomking.entity.WKNpcEntities.init();
	}
}
