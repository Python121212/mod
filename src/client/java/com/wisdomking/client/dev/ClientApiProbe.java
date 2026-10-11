package com.wisdomking.client.dev;

import com.wisdomking.WK;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

/**
 * クライアント側 API 検証ファイル（一時的）。
 *
 * <p>エンティティレンダラ・HUD・GUI の 1.21.1 におけるシグネチャを検証する。</p>
 */
public final class ClientApiProbe {
	private ClientApiProbe() {
	}

	/** 人型Mobレンダラの構築。 */
	public static HumanoidMobRenderer<ProbeHumanoid, HumanoidModel<ProbeHumanoid>> probeRenderer(
			EntityRendererProvider.Context context) {
		HumanoidModel<ProbeHumanoid> base = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER));
		HumanoidMobRenderer<ProbeHumanoid, HumanoidModel<ProbeHumanoid>> renderer =
				new HumanoidMobRenderer<>(context, base, 0.5f) {
					@Override
					public ResourceLocation getTextureLocation(ProbeHumanoid entity) {
						return WK.id("textures/entity/probe.png");
					}
				};
		renderer.addLayer(new HumanoidArmorLayer<>(renderer,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()));
		renderer.addLayer(new ItemInHandLayer<>(renderer, context.getItemInHandRenderer()));
		return renderer;
	}

	/** レンダラ登録。 */
	public static void probeRegistration() {
		EntityRendererRegistry.register(ApiProbeAccess.entityType(), ClientApiProbe::probeRenderer);
	}

	/** HUD 描画コールバックのシグネチャ検証。 */
	public static void probeHud(GuiGraphics graphics, DeltaTracker tracker) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		graphics.fill(0, 0, 10, 10, 0xFF000000);
		graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
				Component.literal("probe"), 0, 0, 0xFFFFFF, true);
		WK.LOG.debug("hud {} {}", width, height, tracker.getGameTimeDeltaTicks());
	}

	/** 画面の背景描画シグネチャ検証。 */
	public static void probeScreen(net.minecraft.client.gui.screens.Screen screen, GuiGraphics graphics,
			int mouseX, int mouseY, float partialTick) {
		screen.renderBackground(graphics, mouseX, mouseY, partialTick);
		screen.mouseScrolled(mouseX, mouseY, 0.0, 1.0);
	}

	/** 検証用のダミー人型Mob。 */
	public static class ProbeHumanoid extends PathfinderMob {
		public ProbeHumanoid(net.minecraft.world.entity.EntityType<? extends ProbeHumanoid> type,
				net.minecraft.world.level.Level level) {
			super(type, level);
		}

		@Override
		public boolean isPushable() {
			return true;
		}

		public LivingEntity asLiving() {
			return this;
		}
	}

	/** 検証用のアクセサ（エンティティ型の参照）。 */
	public static final class ApiProbeAccess {
		private static net.minecraft.world.entity.EntityType<?> entityType;

		private ApiProbeAccess() {
		}

		public static void setEntityType(net.minecraft.world.entity.EntityType<?> type) {
			entityType = type;
		}

		@SuppressWarnings("unchecked")
		public static net.minecraft.world.entity.EntityType<ProbeHumanoid> entityType() {
			return (net.minecraft.world.entity.EntityType<ProbeHumanoid>) entityType;
		}
	}
}
