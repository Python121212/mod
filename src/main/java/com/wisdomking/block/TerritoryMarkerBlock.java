package com.wisdomking.block;

import com.wisdomking.core.WKConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

/**
 * 領土マーカー（国境ブロック）。
 *
 * <p>設置すると周囲のチャンクを自国領土として宣言し、領域保護を有効化する。
 * 領土内では他国プレイヤーによるブロック破壊・設置・爆発が拒否される。</p>
 */
public class TerritoryMarkerBlock extends BaseEntityBlock {
	public TerritoryMarkerBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}


	@Override
	protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(properties -> new TerritoryMarkerBlock(properties));
	}
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TerritoryMarkerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return (tickLevel, pos, tickState, be) -> {
			if (be instanceof TerritoryMarkerBlockEntity marker) {
				TerritoryMarkerBlockEntity.serverTick(tickLevel, pos, tickState, marker);
			}
		};
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide() || !(placer instanceof Player player)) {
			return;
		}
		var manager = com.wisdomking.nation.NationManager.get();
		if (manager == null) {
			return;
		}
		var nation = manager.nationOfPlayer(player.getUUID());
		if (nation == null) {
			player.displayClientMessage(Component.literal("§e[領土] 国家に所属していないため宣言できません。"), false);
			return;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof TerritoryMarkerBlockEntity marker) {
			int claimed = marker.declareTerritory(nation, WKConfig.get().initialClaimRadius);
			player.displayClientMessage(Component.literal("§6[領土] " + nation.name + " が "
					+ claimed + " チャンクを宣言しました（中心: " + pos.getX() + ", " + pos.getZ() + "）"), false);
		}
	}

	@Override
	public InteractionResult useWithoutItem(Level level, Player player, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof TerritoryMarkerBlockEntity marker && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			var nation = marker.nation();
			String owner = nation == null ? "無所属" : nation.name;
			serverPlayer.displayClientMessage(Component.literal(
					"§6[領土] 所有者: " + owner + " / 宣言チャンク: " + marker.claimedCount()
							+ " / 保護: " + (WKConfig.get().protectTerritory ? "有効" : "無効")), false);
		}
		return InteractionResult.SUCCESS;
		}

	@Override
	public ItemInteractionResult useItemOn(ItemStack stack, Level level, Player player, InteractionHand hand,
			BlockHitResult hit) {
		return useWithoutItem(level, player, hit) == InteractionResult.SUCCESS
				? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

}
