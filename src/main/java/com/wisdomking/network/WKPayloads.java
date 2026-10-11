package com.wisdomking.network;

import com.wisdomking.WK;
import com.wisdomking.core.WKLog;
import com.wisdomking.raphael.RaphaelRelay;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 智慧之王／国家システムの通信ペイロード定義。
 *
 * <p>1.21.1 の {@link CustomPacketPayload} と {@link StreamCodec} を使用し、
 * 複雑な状態は NBT（{@link CompoundTag}）へ集約して受け渡す。
 * これにより ペイロード数を最小限に抑えつつ、国家データ・解析レポート・
 * 音声命令のすべてを同期できる。</p>
 */
public final class WKPayloads {
	/** サーバー → クライアント: 国家ダッシュボードの全データ。 */
	public record DashboardPayload(CompoundTag data) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<DashboardPayload> TYPE =
				new CustomPacketPayload.Type<>(WK.id("dashboard"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DashboardPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, DashboardPayload::data,
						DashboardPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** サーバー → クライアント: 智慧之王の発言（チャット・HUD・TTS）。 */
	public record RaphaelPayload(CompoundTag data) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<RaphaelPayload> TYPE =
				new CustomPacketPayload.Type<>(WK.id("raphael"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RaphaelPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, RaphaelPayload::data,
						RaphaelPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** クライアント → サーバー: 智慧之王への照会・国家操作・音声命令。 */
	public record ActionPayload(CompoundTag data) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ActionPayload> TYPE =
				new CustomPacketPayload.Type<>(WK.id("action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, ActionPayload::data,
						ActionPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** サーバー → クライアント: 視界内 NPC の外見・装備情報（スキン/HUD 用）。 */
	public record NpcSyncPayload(CompoundTag data) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<NpcSyncPayload> TYPE =
				new CustomPacketPayload.Type<>(WK.id("npc_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, NpcSyncPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, NpcSyncPayload::data,
						NpcSyncPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private WKPayloads() {
	}

	/** ペイロード型の登録（サーバー・クライアント共通）。 */
	public static void registerCommon() {
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
				.register(DashboardPayload.TYPE, DashboardPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
				.register(RaphaelPayload.TYPE, RaphaelPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
				.register(ActionPayload.TYPE, ActionPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
				.register(NpcSyncPayload.TYPE, NpcSyncPayload.CODEC);
		WKLog.info("payload types registered");
	}

	/** サーバー側でプレイヤーからの操作を受信する。 */
	public static void registerServer() {
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				ActionPayload.TYPE, (payload, context) -> context.server().execute(
						() -> WKServerActions.handle(context.player(), payload.data())));
	}

	public static void sendDashboard(ServerPlayer player) {
		try {
			CompoundTag data = WKDashboardData.build(player);
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new DashboardPayload(data));
		} catch (Throwable t) {
			WKLog.warn("dashboard payload failed: " + t);
		}
	}

	public static void sendRaphaelHistory(ServerPlayer player) {
		try {
			ListTag list = new ListTag();
			for (var report : RaphaelRelay.history()) {
				list.add(report.save());
			}
			CompoundTag tag = new CompoundTag();
			tag.put("Reports", list);
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new RaphaelPayload(tag));
		} catch (Throwable t) {
			WKLog.warn("raphael payload failed: " + t);
		}
	}

	public static void sendRaphael(ServerPlayer player, com.wisdomking.raphael.AnalysisReport report, boolean speak) {
		try {
			CompoundTag tag = report.save();
			tag.putBoolean("Speak", speak);
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new RaphaelPayload(tag));
		} catch (Throwable t) {
			WKLog.warn("raphael payload failed: " + t);
		}
	}

	public static ResourceLocation dashboardId() {
		return DashboardPayload.TYPE.id();
	}
}
