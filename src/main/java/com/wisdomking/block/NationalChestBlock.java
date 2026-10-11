package com.wisdomking.block;

import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.raphael.ItemAnalyzer;
import com.wisdomking.raphael.RaphaelRelay;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 国庫金庫（ナショナル・チェスト）。
 *
 * <p>資源・他Modの武器防具を全自動で納品・保管する国家の金庫。
 * 兵士はここから最良の装備を自動選択し、工房はここを入出力として自動クラフトを行う。</p>
 */
public class NationalChestBlock extends BaseEntityBlock {
	public NationalChestBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new NationalChestBlockEntity(com.wisdomking.core.WKBlockEntities.NATIONAL_CHEST, pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return (tickLevel, pos, tickState, be) -> {
			if (be instanceof NationalChestBlockEntity chest) {
				NationalChestBlockEntity.serverTick(tickLevel, pos, tickState, chest);
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
		if (be instanceof NationalChestBlockEntity chest) {
			chest.assignIfAbsent(nation);
			if (nation != null) {
				player.displayClientMessage(Component.literal(
						"§6[国庫] " + nation.name + " の国庫金庫を設置しました。"), false);
			}
		}
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof NationalChestBlockEntity chest) || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.isEmpty() && !player.isShiftKeyDown()) {
			// 手に持ったアイテムを国庫へ納品する
			boolean deposited = chest.deposit(held.copy());
			if (deposited) {
				held.setCount(0);
				serverPlayer.displayClientMessage(Component.literal("§6[国庫] 納品しました。"), true);
			} else {
				serverPlayer.displayClientMessage(Component.literal("§e[国庫] 満杯です。"), true);
			}
			return InteractionResult.SUCCESS;
		}
		// 空手: 在庫解析 + 国庫GUI
		List<ItemStack> contents = new ArrayList<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			contents.add(chest.getItem(i));
		}
		RaphaelRelay.remember(ItemAnalyzer.analyzeInventory(contents));
		serverPlayer.displayClientMessage(Component.literal("§6[国庫] " + chest.summarize()), false);
		com.wisdomking.network.WKNetwork.openDashboard(serverPlayer);
		return InteractionResult.SUCCESS;
	}
}
