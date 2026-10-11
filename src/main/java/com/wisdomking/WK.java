package com.wisdomking;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 智慧之王 (Wisdom King) プロジェクト共通定数・ユーティリティ。
 *
 * <p>このクラスは他Modとの名前空間衝突を避けるための唯一の ID 生成窓口である。
 * すべての {@link ResourceLocation} は {@link #id(String)} を経由して生成すること。</p>
 */
public final class WK {
	/** このModの名前空間。他Modの識別子と絶対に混在させない。 */
	public static final String MOD_ID = "wisdomking";
	public static final String MOD_NAME = "Wisdom King";

	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	private WK() {
	}

	/** {@code wisdomking:path} 形式の識別子を生成する。 */
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	/** 他Modが導入されているかを安全に判定する（Soft-Dependency 用）。 */
	public static boolean isModLoaded(String modId) {
		try {
			return FabricLoader.getInstance().isModLoaded(modId);
		} catch (Throwable t) {
			return false;
		}
	}

	/** クラスパス上にクラスが存在するかを安全に判定する（リフレクション連携用）。 */
	public static boolean hasClass(String className) {
		try {
			Class.forName(className, false, WK.class.getClassLoader());
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}
