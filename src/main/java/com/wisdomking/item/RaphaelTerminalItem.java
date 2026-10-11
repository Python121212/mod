package com.wisdomking.item;

import com.wisdomking.raphael.RaphaelCore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 智慧之王端末（Raphael Terminal）。
 *
 * <p>右クリック: 視線先のブロック・機械・個体を解析する。<br>
 * スニーク＋右クリック: 手に持つアイテムを解析する（Shift 二度押しで全Mod走査）。</p>
 */
public class RaphaelTerminalItem extends Item {
	public RaphaelTerminalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			if (player.isShiftKeyDown()) {
				RaphaelCore.analyzeHeld(serverPlayer);
			} else {
				RaphaelCore.analyzeTarget(serverPlayer, 8.0);
			}
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.wisdomking.raphael_terminal.tooltip.1").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("item.wisdomking.raphael_terminal.tooltip.2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.wisdomking.raphael_terminal.tooltip.3").withStyle(ChatFormatting.DARK_GRAY));
	}
}
