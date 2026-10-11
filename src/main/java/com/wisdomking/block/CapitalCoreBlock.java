package com.wisdomking.block;

import com.wisdomking.core.WKLog;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.raphael.RaphaelCore;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

/**
 * キャピタル・コア（首都中核）。
 *
 * <p>国家の中枢ブロック。破壊されると戦争の勝敗が確定し、
 * 国家は降伏・属国化・滅亡する。</p>
 */
public class CapitalCoreBlock extends BaseEntityBlock {
	public CapitalCoreBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}


	@Override
	protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(properties -> new CapitalCoreBlock(properties));
	}
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CapitalCoreBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return (tickLevel, pos, tickState, be) -> {
			if (be instanceof CapitalCoreBlockEntity core) {
				CapitalCoreBlockEntity.serverTick(tickLevel, pos, tickState, core);
			}
		};
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide()) {
			return;
		}
		NationManager manager = NationManager.get();
		if (manager == null || !(placer instanceof Player player)) {
			return;
		}
		Nation nation = manager.nationOfPlayer(player.getUUID());
		if (nation != null) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof CapitalCoreBlockEntity core) {
				core.registerAsCapital(nation);
				player.displayClientMessage(Component.literal(
						"§6[国家] " + nation.name + " の首都中核を設置しました。"), false);
			}
		} else {
			player.displayClientMessage(Component.literal(
					"§e[国家] 無所属です。建国憲章（Nation Charter）を使用すると建国できます。"), false);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof CapitalCoreBlockEntity core && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			Nation nation = core.nation();
			if (nation == null) {
				NationManager manager = NationManager.get();
				Nation playerNation = manager == null ? null : manager.nationOfPlayer(player.getUUID());
				if (playerNation != null) {
					core.registerAsCapital(playerNation);
					serverPlayer.displayClientMessage(Component.literal(
							"§6[国家] " + playerNation.name + " の首都をこの位置に移転しました。"), false);
				} else {
					serverPlayer.displayClientMessage(Component.literal(
							"§e[国家] 建国憲章を使用してください。"), false);
				}
			} else {
				// 国家情勢の解析（智慧之王）。GUI はネットワーク層から開く。
				RaphaelCore.reportNation(serverPlayer);
				com.wisdomking.network.WKNetwork.openDashboard(serverPlayer);
			}
		}
		return InteractionResult.SUCCESS;
		}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		return useWithoutItem(state, level, pos, player, hit) == InteractionResult.SUCCESS
				? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}


	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		BlockState result = super.playerWillDestroy(level, pos, state, player);
		if (level.isClientSide()) {
			return result;
		}
		BlockEntity be = level.getBlockEntity(pos);
		NationManager manager = NationManager.get();
		if (manager == null || !(be instanceof CapitalCoreBlockEntity core)) {
			return result;
		}
		Nation defender = core.nation();
		if (defender == null) {
			return result;
		}
		Nation attacker = manager.nationOfPlayer(player.getUUID());
		if (attacker != null && attacker.id.equals(defender.id)) {
			// 自国による解体（移転）は戦争扱いにしない
			defender.capitalMaterialized = false;
			return result;
		}
		WKLog.info("capital core of " + defender.name + " destroyed by "
				+ player.getName().getString());
		if (level instanceof ServerLevel serverLevel) {
			manager.war().onCapitalCoreLost(defender, attacker);
			serverLevel.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					4.0f, Level.ExplosionInteraction.NONE);
		}
		return result;
	}
}
