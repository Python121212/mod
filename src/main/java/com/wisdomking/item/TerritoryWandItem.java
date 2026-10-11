package com.wisdomking.item;

import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.raphael.AnalysisReport;
import com.wisdomking.raphael.RaphaelRelay;
import com.wisdomking.raphael.Tone;
import com.wisdomking.raphael.Urgency;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 領土測量杖（Territory Wand）。
 *
 * <p>右クリックで現在地のチャンク群を自国領土として宣言する。
 * スニーク＋右クリックで現在のチャンクの所有国を照会する。</p>
 */
public class TerritoryWandItem extends Item {
	private static final int CLAIM_RADIUS = 2;

	public TerritoryWandItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.success(stack);
		}
		NationManager manager = NationManager.get();
		Nation nation = manager == null ? null : manager.nationOfPlayer(player.getUUID());
		if (nation == null) {
			serverPlayer.displayClientMessage(Component.literal("§e[領土] 国家に所属していません。"), false);
			return InteractionResultHolder.success(stack);
		}
		if (player.isShiftKeyDown()) {
			Nation owner = manager.territory().nationAt(level, player.blockPosition());
			String ownerName = owner == null ? "無所属地" : owner.name;
			serverPlayer.displayClientMessage(Component.literal("§6[領土] 現在地の所有者: " + ownerName), false);
			return InteractionResultHolder.success(stack);
		}
		int claimed = manager.territory().claimRadius(nation, new ChunkPos(player.blockPosition()), CLAIM_RADIUS);
		RaphaelRelay.announceTo(serverPlayer, AnalysisReport.of(Tone.REPORT,
				"領土を宣言しました。", Urgency.INFO,
				"国家: " + nation.name,
				"宣言チャンク: " + claimed + "（半径 " + CLAIM_RADIUS + "）",
				"総領土: " + nation.claimedChunks.size() + " チャンク",
				"領域保護: " + (com.wisdomking.core.WKConfig.get().protectTerritory ? "有効" : "無効")), false);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		NationManager manager = NationManager.get();
		if (manager == null || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.PASS;
		}
		Nation nation = manager.nationOfPlayer(player.getUUID());
		if (nation == null) {
			return InteractionResult.PASS;
		}
		BlockPos pos = context.getClickedPos();
		int claimed = manager.territory().claimRadius(nation, new ChunkPos(pos), CLAIM_RADIUS);
		player.displayClientMessage(Component.literal("§6[領土] " + nation.name + " が "
				+ claimed + " チャンクを宣言（総計 " + nation.claimedChunks.size() + "）"), false);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.wisdomking.territory_wand.tooltip.1").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("item.wisdomking.territory_wand.tooltip.2").withStyle(ChatFormatting.GRAY));
	}
}
