package com.wisdomking.core;

import com.wisdomking.WK;
import com.wisdomking.ai.CivilEngineeringAI;
import com.wisdomking.nation.INationMember;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.SuccessionEngine;
import com.wisdomking.nation.WorldPos;
import com.wisdomking.raphael.AutomationAdvisor;
import com.wisdomking.raphael.RaphaelCore;
import com.wisdomking.world.AutoCrafter;
import com.wisdomking.world.TerritoryProtection;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.Nullable;

/**
 * Fabric API イベントの登録。
 *
 * <p>他Modとの競合を避けるため、可能な限り標準イベントを使用する方針を採る
 * （Mixin は爆発保護の補完のみ）。</p>
 */
public final class WKEvents {
	private WKEvents() {
	}

	public static void register() {
		// --- サーバーライフサイクル ---
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				NationManager manager = NationManager.init(server);
				WKLog.info("NationManager initialised: " + manager.nationCount() + " nations");
			} catch (Throwable t) {
				WKLog.error("failed to initialise nation system", t);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WKLog.safely("server stopping", () -> {
				NationManager manager = NationManager.get();
				if (manager != null) {
					manager.save();
					manager.exportJson();
				}
				NationManager.shutdown();
				AutomationAdvisor.reset();
				AutoCrafter.reset();
				CivilEngineeringAI.reset();
				com.wisdomking.entity.NpcSpawner.reset();
			});
		});

		// --- 毎tick: 国家シミュレーション + 土木AI + 受動解析 ---
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			NationManager manager = NationManager.get();
			if (manager == null) {
				return;
			}
			try {
				manager.tick();
				CivilEngineeringAI.tick(server.overworld());
				com.wisdomking.entity.NpcSpawner.tick(server.overworld());
				RaphaelCore.passiveTick(server);
			} catch (Throwable t) {
				WKLog.error("nation tick failed", t);
			}
		});

		// --- 接続・切断 ---
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				WKLog.safely("player join", () -> RaphaelCore.greet(handler.getPlayer())));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				WKLog.safely("player disconnect", () -> {
					NationManager manager = NationManager.get();
					if (manager != null) {
						manager.exportJson();
					}
				}));

		// --- NPC・国王の死亡（動的王位継承のトリガ） ---
		ServerLivingEntityEvents.AFTER_DEATH.register(WKEvents::onDeath);

		// --- 領域保護（Fabric 標準イベント） ---
		TerritoryProtection.register();
	}

	/** 死亡イベント。NPCの台帳更新と王位継承を行う。 */
	private static void onDeath(LivingEntity entity, DamageSource source) {
		NationManager manager = NationManager.get();
		if (manager == null || !(entity instanceof INationMember member)) {
			return;
		}
		Nation nation = manager.nation(member.getNationId());
		if (nation == null) {
			return;
		}
		NationRole role = member.getNationRole();
		SuccessionEngine.Cause cause = classifyCause(source);
		if (role == NationRole.KING) {
			manager.succession().notifyKingEntityDeath(nation, entity.getUUID(), cause, killerNation(source));
		} else {
			var record = nation.subject(entity.getUUID());
			if (record != null) {
				record.alive = false;
				record.lastPos = WorldPos.of(entity);
				record.lastSeenTick = manager.server().getTickCount();
			}
			manager.markDirty();
		}
	}

	private static SuccessionEngine.Cause classifyCause(DamageSource source) {
		var attacker = source.getEntity();
		if (attacker instanceof ServerPlayer) {
			return SuccessionEngine.Cause.SLAIN_BY_PLAYER;
		}
		if (attacker instanceof INationMember) {
			return SuccessionEngine.Cause.BATTLE;
		}
		if (attacker == null && !source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)) {
			return SuccessionEngine.Cause.ILLNESS;
		}
		return SuccessionEngine.Cause.UNKNOWN;
	}

	@Nullable
	private static java.util.UUID killerNation(DamageSource source) {
		NationManager manager = NationManager.get();
		if (manager == null) {
			return null;
		}
		var attacker = source.getEntity();
		if (attacker instanceof ServerPlayer player) {
			return manager.nationIdOfPlayer(player);
		}
		if (attacker instanceof INationMember member) {
			return member.getNationId();
		}
		return null;
	}

	/** デバッグ用の識別子。 */
	public static String tag() {
		return WK.MOD_ID + ":events";
	}
}
