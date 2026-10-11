package com.wisdomking.client.dev;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * TEMPORARY client-side compile probe (deleted before release): validates 1.21.1 client APIs
 * (HUD callback signature, screen layout, keybinds, renderer registration, packets).
 */
public final class ClientProbe {
	private ClientProbe() {
	}

	@SuppressWarnings("unused")
	private static void probeHud() {
		HudRenderCallback.EVENT.register((guiGraphics, tickCounter) -> {
			int width = guiGraphics.guiWidth();
			int height = guiGraphics.guiHeight();
			float delta = tickCounter.getGameTimeDeltaPartialTick(false);
			guiGraphics.fill(0, 0, width, 12, 0x80000000);
			guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("probe"), 4, 2, 0xFFFFFFFF);
			boolean hidden = Minecraft.getInstance().options.hideGui;
			if (delta < 0.0f || hidden) {
				return;
			}
		});
	}

	@SuppressWarnings("unused")
	private static void probeKeybind() {
		KeyMapping mapping = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.wisdomking.voice", GLFW.GLFW_KEY_V, "key.categories.wisdomking"));
		while (mapping.consumeClick()) {
			// probe
		}
	}

	/** 画面の雛形（ダッシュボードで使う API を一通り通す）。 */
	public static class ProbeScreen extends Screen {
		private int scroll;

		public ProbeScreen() {
			super(Component.literal("probe"));
		}

		@Override
		protected void init() {
			int cx = this.width / 2;
			this.addRenderableWidget(Button.builder(Component.literal("tab"), button -> this.scroll = 0)
					.bounds(cx - 100, 24, 60, 18)
					.build());
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			this.renderBackground(graphics, mouseX, mouseY, partialTick);
			super.render(graphics, mouseX, mouseY, partialTick);
			graphics.fill(10, 10, this.width - 10, this.height - 10, 0xC0101010);
			graphics.drawString(this.font, this.title, 12, 12, 0xFFD700);
			graphics.drawString(this.font, "scroll=" + scroll, 12, 24, 0xFFFFFF);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
			this.scroll = Math.max(0, this.scroll - (int) scrollY);
			return true;
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}

	@SuppressWarnings("unused")
	private static void probeRenderer(EntityRendererProvider.Context context) {
		EntityRendererRegistry.register(null, ctx -> new HumanoidMobRenderer<com.wisdomking.dev.Probe.ProbeNpc,
				HumanoidModel<com.wisdomking.dev.Probe.ProbeNpc>>(ctx,
				new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f) {
			@Override
			public net.minecraft.resources.ResourceLocation getTextureLocation(com.wisdomking.dev.Probe.ProbeNpc entity) {
				return net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
			}

			{
				this.addLayer(new HumanoidArmorLayer<>(this,
						new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
						new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
				this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
			}
		});
	}

	@SuppressWarnings("unused")
	private static void probePacketSend(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
