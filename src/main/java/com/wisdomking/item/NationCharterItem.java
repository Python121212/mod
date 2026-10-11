package com.wisdomking.item;

import com.wisdomking.block.CapitalCoreBlock;
import com.wisdomking.nation.Ideology;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.WorldPos;
import com.wisdomking.raphael.AnalysisReport;
import com.wisdomking.raphael.RaphaelRelay;
import com.wisdomking.raphael.Tone;
import com.wisdomking.raphael.Urgency;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 建国憲章（Nation Charter）。
 *
 * <p>キャピタル・コアに対して使用すると国家を建国し、人間プレイヤーが王となる。
 * 建国時には領土が宣言され、国家理念が決定される。</p>
 */
public class NationCharterItem extends Item {
	public NationCharterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		PlayerCandidate candidate = new PlayerCandidate(context);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		NationManager manager = NationManager.get();
		if (manager == null || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.PASS;
		}
		Nation existing = manager.nationOfPlayer(player.getUUID());
		if (existing != null) {
			player.displayClientMessage(Component.literal("§e[国家] すでに " + existing.name + " の王です。"), false);
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos();
		boolean isCore = level.getBlockState(pos).getBlock() instanceof CapitalCoreBlock;
		if (!isCore) {
			player.displayClientMessage(Component.literal(
					"§e[国家] キャピタル・コアに対して使用してください。"), false);
			return InteractionResult.SUCCESS;
		}
		String name = generateName(player, manager);
		Ideology ideology = Ideology.values()[manager.random().nextInt(Ideology.values().length)];
		Nation nation = manager.foundPlayerNation(player, name, ideology,
				WorldPos.of(level, pos), 0xE8C547);
		context.getItemInHand().shrink(1);
		RaphaelRelay.announceTo(player, AnalysisReport.of(Tone.SCAN, "建国を確認しました。", Urgency.NOTICE,
				"国名: " + nation.name + " / 理念: " + ideology.name(),
				"首都: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
				"初期領土: " + nation.claimedChunks.size() + " チャンク",
				"《解》人間の王として統治権を掌握しました。臣下は自動的に忠誠を誓います。"), true);
		return InteractionResult.SUCCESS;
	}

	private String generateName(ServerPlayer player, NationManager manager) {
		return player.getName().getString() + "王国";
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.wisdomking.nation_charter.tooltip.1").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.wisdomking.nation_charter.tooltip.2").withStyle(ChatFormatting.GRAY));
	}

	/** 使用コンテキストの薄いラッパ（将来の拡張用）。 */
	private record PlayerCandidate(UseOnContext context) {
	}
}
