package com.wisdomking.client.render;

import com.wisdomking.entity.WKNpcEntity;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.Personality;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * NPC レンダラ。
 *
 * <p>プレイヤー型モデルに国家色・役割別の動的スキンを適用する。
 * テクスチャは {@link NpcSkinManager} がその場で生成するため、
 * リソースパック未導入でも紫黒チェッカー（未登録テクスチャ）が出ない。</p>
 */
public class WKNpcRenderer extends HumanoidMobRenderer<WKNpcEntity, HumanoidModel<WKNpcEntity>> {
	private final HumanoidModel<WKNpcEntity> normalModel;
	private final HumanoidModel<WKNpcEntity> slimModel;

	public WKNpcRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		this.normalModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER));
		this.slimModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
		this.addLayer(new HumanoidArmorLayer<>(this,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()));
		this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
	}

	@Override
	public ResourceLocation getTextureLocation(WKNpcEntity entity) {
		try {
			Nation nation = entity.nation();
			int color = nation == null ? 0x8899AA : nation.color;
			NationRole role = entity.getNationRole();
			Personality personality = entity.personality();
			return NpcSkinManager.skin(color, role, personality);
		} catch (Throwable t) {
			return NpcSkinManager.fallback();
		}
	}
}
