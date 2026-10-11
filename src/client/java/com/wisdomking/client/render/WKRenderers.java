package com.wisdomking.client.render;

import com.wisdomking.WK;
import com.wisdomking.entity.WKNpcEntities;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** クライアント側レンダラ登録。 */
public final class WKRenderers {
	private WKRenderers() {
	}

	public static void init() {
		try {
			if (WKNpcEntities.WK_NPC != null) {
				EntityRendererRegistry.register(WKNpcEntities.WK_NPC, WKNpcRenderer::new);
				WK.LOG.info("《解》NPC レンダラを登録しました。");
			}
		} catch (Throwable t) {
			WK.LOG.warn("failed to register npc renderer: " + t);
		}
	}
}
