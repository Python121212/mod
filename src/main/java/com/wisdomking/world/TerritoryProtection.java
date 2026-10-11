package com.wisdomking.world;

import com.wisdomking.WK;
import com.wisdomking.ai.CivilEngineeringAI;
import com.wisdomking.core.WKConfig;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.raphael.AnalysisReport;
import com.wisdomking.raphael.RaphaelRelay;
import com.wisdomking.raphael.Tone;
import com.wisdomking.raphael.Urgency;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 領域保護の実装。
 *
 * <p>Fabric API の標準イベントのみで完結させ（{@code PlayerBlockBreakEvents},
 * {@code AttackBlockCallback}, {@code UseBlockCallback}）、Mixin は爆発処理の
 * 補完にのみ使用する。これにより他Modとの競合を最小化する。</p>
 */
public final class TerritoryProtection {
	/** 保護違反の通知回数を抑制する。 */
	private static final Map<UUID, Long> LAST_DENY = new HashMap<>();

	private TerritoryProtection() {
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!WKConfig.get().protectTerritory || level.isClientSide()) {
				return true;
			}
			boolean allowed = canModify(level, pos, player);
			if (!allowed) {
				notifyDenied(player, pos, "破壊");
			} else {
				recordPotentialDamage(level, player, pos, state);
			}
			return allowed;
		});

		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (!WKConfig.get().protectTerritory || level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (!canModify(level, pos, player)) {
				notifyDenied(player, pos, "攻撃");
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!WKConfig.get().protectTerritory || level.isClientSide()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			if (!canModify(level, pos, player)) {
				notifyDenied(player, pos, "設置・使用");
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	/** 改変可否の判定。 */
	public static boolean canModify(Level level, BlockPos pos, Player player) {
		NationManager manager = NationManager.get();
		if (manager == null) {
			return true;
		}
		return manager.territory().canModify(level, pos, player);
	}

	/** 戦争中の破壊を記録する（復興AIの材料）。 */
	private static void recordPotentialDamage(Level level, Player player, BlockPos pos, BlockState state) {
		NationManager manager = NationManager.get();
		if (manager == null) {
			return;
		}
		Nation owner = manager.territory().nationAt(level, pos);
		if (owner == null || !owner.atWar()) {
			return;
		}
		CivilEngineeringAI.journal(owner).record(pos, state, 2);
	}

	private static void notifyDenied(Player player, BlockPos pos, String action) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		long now = serverPlayer.level().getGameTime();
		Long last = LAST_DENY.get(serverPlayer.getUUID());
		if (last != null && now - last < 60L) {
			return;
		}
		LAST_DENY.put(serverPlayer.getUUID(), now);
		NationManager manager = NationManager.get();
		Nation owner = manager == null ? null : manager.territory().nationAt(serverPlayer.level(), pos);
		String ownerName = owner == null ? "他国" : owner.name;
		serverPlayer.displayClientMessage(Component.literal(
				"§c[領域] " + ownerName + " の領土では" + action + "が制限されています。"), true);
		RaphaelRelay.announceTo(serverPlayer, AnalysisReport.of(Tone.ALERT,
				"領域保護により" + action + "を拒否しました。", Urgency.INFO,
				"所有者: " + ownerName,
				"座標: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
				"交戦状態であれば宣戦布告後に占領が可能です。"), false);
	}

	/** 爆発による保護ブロックの破壊を判定する（Mixin から呼ばれる）。 */
	public static boolean allowExplosionAt(Level level, BlockPos pos) {
		if (!WKConfig.get().protectTerritory || !WKConfig.get().protectFromExplosions) {
			return true;
		}
		NationManager manager = NationManager.get();
		if (manager == null) {
			return true;
		}
		Nation owner = manager.territory().nationAt(level, pos);
		if (owner == null) {
			return true;
		}
		// 交戦中の国家の領土では爆発による破壊を許可する（戦争の実効性を保つ）
		return owner.atWar();
	}

	/** 起動ログ用。 */
	public static String describe() {
		return "TerritoryProtection[" + WK.MOD_ID + "] active=" + WKConfig.get().protectTerritory;
	}
}
