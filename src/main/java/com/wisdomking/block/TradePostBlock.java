package com.wisdomking.block;

import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.raphael.AnalysisReport;
import com.wisdomking.raphael.RaphaelRelay;
import com.wisdomking.raphael.Tone;
import com.wisdomking.raphael.Urgency;
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

/** 貿易ポスト（市場ターミナル）。商人NPCと他国が全自動で交易する拠点。 */
public class TradePostBlock extends BaseEntityBlock {
	public TradePostBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TradePostBlockEntity(com.wisdomking.core.WKBlockEntities.TRADE_POST, pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return (tickLevel, pos, tickState, be) -> {
			if (be instanceof TradePostBlockEntity post) {
				TradePostBlockEntity.serverTick(tickLevel, pos, tickState, post);
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
		if (be instanceof TradePostBlockEntity post) {
			post.assignIfAbsent(nation);
		}
		if (nation != null) {
			player.displayClientMessage(Component.literal("§6[交易] " + nation.name
					+ " の市場ターミナルを開設しました。手持ちのアイテムを納品すると自動取引が始まります。"), false);
		}
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof TradePostBlockEntity post) || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.isEmpty()) {
			if (post.depositFrom(held)) {
				serverPlayer.displayClientMessage(Component.literal("§6[交易] 市場へ納品しました。"), true);
			} else {
				serverPlayer.displayClientMessage(Component.literal("§e[交易] 市場在庫が満杯です。"), true);
			}
			return InteractionResult.SUCCESS;
		}
		Nation nation = post.nation();
		List<String> details = new ArrayList<>();
		details.add("取引回数: " + post.tradeCount() + " / 累計収益: " + String.format("%.1f", post.profitAccumulated()));
		details.addAll(post.tradeLog());
		if (nation != null) {
			NationManager manager = NationManager.get();
			details.add("国庫: " + String.format("%.1f", nation.treasury));
			if (manager != null) {
				details.add("最も豊富: " + manager.economy().mostAbundant(nation).name());
			}
		}
		RaphaelRelay.announceTo(serverPlayer, new AnalysisReport(Tone.REPORT,
				"市場ターミナル報告", details, Urgency.INFO), false);
		return InteractionResult.SUCCESS;
	}
}
