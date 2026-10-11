package com.wisdomking.client.render;

import com.wisdomking.client.WKClientNetwork;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.Personality;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC スキンの動的生成。
 *
 * <p>国家色 × 役割 の組み合わせでスキンテクスチャをその場で生成し、
 * テクスチャマネージャへ登録する。これにより「役割・国家色ごとの動的スキン」を
 * リソースパックに依存せず実現し、未登録テクスチャ（紫黒チェッカー）を完全に排除する。</p>
 *
 * <p>生成は 64x64 のプレイヤー形式スキン。髪・肌・服・役割マーカー（肩章）を
 * 手続き的に描画する。生成済みテクスチャはキャッシュされる。</p>
 */
public final class NpcSkinManager {
	private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
	private static final ResourceLocation FALLBACK = ResourceLocation.fromNamespaceAndPath("minecraft",
			"textures/entity/player/wide/steve.png");

	private NpcSkinManager() {
	}

	/** 国家色と役割からスキン識別子を作る。 */
	public static String key(int nationColor, NationRole role, Personality personality) {
		return Integer.toHexString(nationColor & 0xFFFFFF) + "_" + role.name().toLowerCase()
				+ "_" + personality.name().toLowerCase();
	}

	/** スキンテクスチャを取得する（無ければ生成する）。 */
	public static ResourceLocation skin(int nationColor, NationRole role, Personality personality) {
		String key = key(nationColor, role, personality);
		ResourceLocation cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		try {
			ResourceLocation location = ResourceLocation.fromNamespaceAndPath("wisdomking",
					"dynamic/npc_" + key);
			DynamicTexture texture = new DynamicTexture(64, 64, true);
			NativeDrawing.drawSkin(texture.getPixels(), nationColor, role, personality);
			texture.upload();
			Minecraft.getInstance().getTextureManager().register(location, texture);
			CACHE.put(key, location);
			return location;
		} catch (Throwable t) {
			// 動的生成に失敗しても必ず既存テクスチャを返す（未登録テクスチャを出さない）
			com.wisdomking.WK.LOG.debug("dynamic skin generation failed: " + t);
			return FALLBACK;
		}
	}

	/** 国家安全保障上のフォールバック（未使用時も保持）。 */
	public static ResourceLocation fallback() {
		return FALLBACK;
	}

	/** キャッシュを破棄する（リソース再読込時）。 */
	public static void clear() {
		CACHE.clear();
	}

	/**
	 * 手続き的スキン描画。{@code NativeImage} は 64x64 RGBA。
	 */
	static final class NativeDrawing {
		private NativeDrawing() {
		}

		static void drawSkin(com.mojang.blaze3d.platform.NativeImage image, int nationColor, NationRole role,
				Personality personality) {
			int skinTone = 0xFFE0AC69;
			int hair = 0xFF3B2A1A;
			int cloth = 0xFF000000 | (nationColor & 0xFFFFFF);
			int clothDark = darken(cloth, 0.7F);
			int accent = accentFor(role);
			int eye = 0xFF2B2B2B;

			// 全体を服色で塗る
			fill(image, 0, 0, 64, 64, cloth);
			// 頭（8x8x8 の展開）: 頭部前面/側面/上面
			fill(image, 8, 0, 16, 8, hair);
			fill(image, 8, 8, 16, 16, skinTone); // 顔
			fill(image, 0, 8, 8, 16, skinTone);
			fill(image, 16, 8, 24, 16, skinTone);
			fill(image, 24, 8, 32, 16, skinTone);
			fill(image, 8, 0, 16, 2, hair);
			// 目
			fill(image, 10, 11, 12, 12, eye);
			fill(image, 13, 11, 15, 12, eye);
			// 身体
			fill(image, 20, 20, 28, 32, clothDark);
			fill(image, 16, 20, 20, 32, cloth);
			fill(image, 28, 20, 32, 32, cloth);
			fill(image, 32, 20, 40, 32, clothDark);
			// 役割マーカー（胸の徽章・肩章）
			fill(image, 22, 22, 26, 26, accent);
			fill(image, 17, 21, 19, 23, accent);
			fill(image, 29, 21, 31, 23, accent);
			// 脚
			fill(image, 8, 16, 16, 32, clothDark);
			fill(image, 16, 16, 24, 32, clothDark);
			fill(image, 24, 16, 32, 32, clothDark);
			fill(image, 32, 16, 40, 32, clothDark);
			// 腕・脚の下半分は素足/手袋
			fill(image, 40, 16, 56, 32, cloth);
			fill(image, 44, 16, 52, 20, skinTone);
			fill(image, 0, 16, 16, 32, skinTone);
			fill(image, 16, 48, 48, 64, clothDark);
			fill(image, 16, 48, 48, 52, skinTone);
			// 性格によるアクセント（帽子/マント）
			if (personality == Personality.CRUEL || personality == Personality.ZEALOUS) {
				fill(image, 8, 0, 16, 1, 0xFF7A1010);
			} else if (personality == Personality.MERCIFUL || personality == Personality.LOYAL) {
				fill(image, 8, 0, 16, 1, 0xFFE8E8E8);
			}
			if (role == NationRole.KING) {
				// 王冠（金色）
				fill(image, 8, 0, 16, 2, 0xFFFFD24A);
			}
		}

		private static void fill(com.mojang.blaze3d.platform.NativeImage image, int x0, int y0, int x1, int y1,
				int argb) {
			for (int y = y0; y < y1 && y < image.getHeight(); y++) {
				for (int x = x0; x < x1 && x < image.getWidth(); x++) {
					image.setPixelRGBA(x, y, argb);
				}
			}
		}

		private static int darken(int argb, float factor) {
			int a = (argb >>> 24) & 0xFF;
			int r = (int) (((argb >> 16) & 0xFF) * factor);
			int g = (int) (((argb >> 8) & 0xFF) * factor);
			int b = (int) ((argb & 0xFF) * factor);
			return (a << 24) | (r << 16) | (g << 8) | b;
		}

		private static int accentFor(NationRole role) {
			return switch (role) {
				case KING, PRINCE -> 0xFFFFD24A;
				case GENERAL -> 0xFFB03A2E;
				case SOLDIER -> 0xFF5D6D7E;
				case MAGE -> 0xFF8E44AD;
				case ENGINEER -> 0xFFE67E22;
				case MERCHANT -> 0xFF27AE60;
				case CRAFTSMAN -> 0xFFA04000;
				case SCHOLAR -> 0xFF2E86C1;
				case CITIZEN -> 0xFF95A5A6;
			};
		}
	}

	/** ネットワーク層との接続点（将来の同期用）。 */
	public static String debugTag() {
		return WKClientNetwork.debugTag();
	}
}
