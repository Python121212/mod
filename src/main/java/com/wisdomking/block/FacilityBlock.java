package com.wisdomking.block;

import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

import javax.annotation.Nullable;

/**
 * 施設指示ブロック（兵舎・ギルド・魔法研究所・工房・倉庫・市場）。
 * 施設種別は設置時に使用したアイテム／スニーク操作で切り替える。
 */
public class FacilityBlock extends BaseEntityBlock {
	public FacilityBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FacilityBlockEntity(com.wisdomking.core.WKBlockEntities.FACILITY, pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return (tickLevel, pos, tickState, be) -> {
			if (be instanceof FacilityBlockEntity facility) {
				FacilityBlockEntity.serverTick(tickLevel, pos, tickState, facility);
			}
		};
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide() || !(placer instanceof Player player)) {
			return;
		}
		NationManager manager = NationManager.get();
		Nation nation = manager == null ? null : manager.nationOfPlayer(player.getUUID());
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof FacilityBlockEntity facility) {
			facility.assignIfAbsent(nation);
			facility.setFacility(FacilityType.WORKSHOP);
			player.displayClientMessage(Component.literal(
					"§6[施設] 工房として登録しました。スニーク＋右クリックで施設種別を切り替えられます。"), false);
		}
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof FacilityBlockEntity facility) || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			FacilityType[] values = FacilityType.values();
			FacilityType next = values[(facility.facility().ordinal() + 1) % values.length];
			facility.setFacility(next);
			serverPlayer.displayClientMessage(Component.literal(
					"§6[施設] 種別を " + next.name() + " に変更しました。"), false);
			return InteractionResult.SUCCESS;
		}
		serverPlayer.displayClientMessage(Component.literal(String.format(
				"§6[施設] %s / 生産回数 %d / 直近: %s", facility.facility().name(),
				facility.producedCount(), facility.lastCrafted())), false);
		for (String line : facility.logLines()) {
			serverPlayer.displayClientMessage(Component.literal("§7  └ " + line), false);
		}
		return InteractionResult.SUCCESS;
	}
}
