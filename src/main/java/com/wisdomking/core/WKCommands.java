package com.wisdomking.core;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.wisdomking.WK;
import com.wisdomking.ai.CivilEngineeringAI;
import com.wisdomking.nation.Ideology;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.WorldPos;
import com.wisdomking.raphael.RaphaelCore;
import com.wisdomking.raphael.RaphaelRelay;
import com.wisdomking.voice.VoiceCommandRouter;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 運用コマンド（{@code /wisdomking} / {@code /wk}）。
 *
 * <p>国家の確認・建国・外交操作、智慧之王への照会、土木AIへの指示、
 * 管理者向けデバッグ操作を提供する。</p>
 */
public final class WKCommands {
	private WKCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			var root = Commands.literal("wisdomking")
					.requires(source -> source.hasPermission(0))
					// /wk nation ...
					.then(Commands.literal("nation")
							.then(Commands.literal("list").executes(WKCommands::listNations))
							.then(Commands.literal("info").executes(WKCommands::nationInfo))
							.then(Commands.literal("found")
									.then(Commands.argument("name", StringArgumentType.greedyString())
											.executes(WKCommands::foundNation)))
							.then(Commands.literal("claim")
									.then(Commands.argument("radius", IntegerArgumentType.integer(1, 8))
											.executes(WKCommands::claim)))
							.then(Commands.literal("subjects").executes(WKCommands::subjects))
							.then(Commands.literal("diplomacy").executes(WKCommands::diplomacy)))
					// /wk raphael ...
					.then(Commands.literal("raphael")
							.then(Commands.literal("scan").executes(WKCommands::scanMods))
							.then(Commands.literal("nation").executes(WKCommands::nationReport))
							.then(Commands.literal("war").executes(WKCommands::warReport))
							.then(Commands.literal("economy").executes(WKCommands::economyReport))
							.then(Commands.literal("threats").executes(WKCommands::threatReport))
							.then(Commands.literal("automation").executes(WKCommands::automationAdvice))
							.then(Commands.literal("briefing").executes(WKCommands::briefing))
							.then(Commands.literal("history").executes(WKCommands::history)))
					// /wk voice <text>
					.then(Commands.literal("voice")
							.then(Commands.argument("text", StringArgumentType.greedyString())
									.executes(ctx -> {
										ServerPlayer player = ctx.getSource().getPlayerOrException();
										VoiceCommandRouter.handle(player, StringArgumentType.getString(ctx, "text"));
										return 1;
									}))
							.then(Commands.literal("help").executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								for (String line : VoiceCommandRouter.helpLines()) {
									player.displayClientMessage(Component.literal("§7" + line), false);
								}
								return 1;
							})))
					// /wk civil ...
					.then(Commands.literal("civil")
							.then(Commands.literal("roads").executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								Nation nation = requireNation(player);
								if (nation == null) {
									return 0;
								}
								int queued = CivilEngineeringAI.scheduleRoadNetwork(player.serverLevel(), nation);
								ctx.getSource().sendSuccess(() -> Component.literal(
										"道路敷設を " + queued + " 区間予約しました（待機ジョブ "
												+ CivilEngineeringAI.queuedJobs() + "）"), false);
								return 1;
							}))
							.then(Commands.literal("defense").executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								Nation nation = requireNation(player);
								if (nation == null) {
									return 0;
								}
								int blocks = CivilEngineeringAI.scheduleDefenseLine(player.serverLevel(), nation);
								ctx.getSource().sendSuccess(() -> Component.literal(
										"防衛陣地構築を予約しました（対象 " + blocks + " ブロック）"), false);
								return 1;
							}))
							.then(Commands.literal("repair").executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								Nation nation = requireNation(player);
								if (nation == null) {
									return 0;
								}
								int count = CivilEngineeringAI.scheduleRepair(player.serverLevel(), nation);
								ctx.getSource().sendSuccess(() -> Component.literal(
										"復興対象 " + count + " ブロックを予約しました"), false);
								return 1;
							}))
							.then(Commands.literal("status").executes(ctx -> {
								ctx.getSource().sendSuccess(() -> Component.literal(
										"土木ジョブ待機数: " + CivilEngineeringAI.queuedJobs()), false);
								for (String line : CivilEngineeringAI.jobSummary()) {
									ctx.getSource().sendSuccess(() -> Component.literal("§7  └ " + line), false);
								}
								return 1;
							})))
					// /wk admin ...
					.then(Commands.literal("admin")
							.requires(source -> source.hasPermission(2))
							.then(Commands.literal("export").executes(ctx -> {
								NationManager manager = NationManager.get();
								if (manager != null) {
									manager.save();
									manager.exportJson();
								}
								ctx.getSource().sendSuccess(() -> Component.literal(
										"国家データを保存・エクスポートしました"), false);
								return 1;
							}))
							.then(Commands.literal("bootstrap").executes(ctx -> {
								NationManager manager = NationManager.get();
								if (manager == null) {
									return 0;
								}
								ctx.getSource().sendSuccess(() -> Component.literal(
										"現在の国家数: " + manager.nationCount()), false);
								return 1;
							}))
							.then(Commands.literal("tick").executes(ctx -> {
								NationManager manager = NationManager.get();
								if (manager != null) {
									manager.tick();
								}
								return 1;
							}))
							.then(Commands.literal("war")
									.then(Commands.argument("target", StringArgumentType.word())
											.executes(ctx -> {
												NationManager manager = NationManager.get();
												Nation target = manager == null ? null
														: manager.nation(java.util.UUID.fromString(StringArgumentType.getString(ctx, "target")));
												ServerPlayer player = ctx.getSource().getPlayerOrException();
												Nation own = requireNation(player);
												if (manager == null || target == null || own == null) {
													return 0;
												}
												manager.diplomacy().declareWar(own, target, "admin");
												return 1;
											})))
							.then(Commands.literal("ideology")
									.then(Commands.argument("ideology", StringArgumentType.word())
											.executes(ctx -> {
												ServerPlayer player = ctx.getSource().getPlayerOrException();
												Nation own = requireNation(player);
												if (own == null) {
													return 0;
												}
												own.ideology = Ideology.byName(StringArgumentType.getString(ctx, "ideology"), own.ideology);
												ctx.getSource().sendSuccess(() -> Component.literal(
														"理念を " + own.ideology.name() + " に変更しました"), false);
												return 1;
											}))))
					;
			dispatcher.register(root);
			dispatcher.register(Commands.literal("wk").redirect(dispatcher.getRoot().getChild("wisdomking")));
		});
	}

	private static Nation requireNation(ServerPlayer player) {
		NationManager manager = NationManager.get();
		Nation nation = manager == null ? null : manager.nationOfPlayer(player.getUUID());
		if (nation == null) {
			player.displayClientMessage(Component.literal("§e[wisdomking] 国家に所属していません。"), false);
		}
		return nation;
	}

	private static int listNations(CommandContext<CommandSourceStack> ctx) {
		NationManager manager = NationManager.get();
		if (manager == null) {
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§6[国家一覧] 総数 " + manager.nationCount()), false);
		for (Nation nation : manager.aliveNations()) {
			ctx.getSource().sendSuccess(() -> Component.literal(String.format(
					"§f%s §7(%s / 王: %s / 人口 %d / 兵力 %d / 戦力 %.1f / 領土 %d)%s",
					nation.name, nation.ideology.name(), nation.kingName, nation.population(),
					nation.armySize(), nation.militaryPower(), nation.claimedChunks.size(),
					nation.atWar() ? " §c[交戦中]" : "")), false);
		}
		return 1;
	}

	private static int nationInfo(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		RaphaelCore.reportNation(player);
		return 1;
	}

	private static int foundNation(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		NationManager manager = NationManager.get();
		if (manager == null || manager.nationOfPlayer(player.getUUID()) != null) {
			ctx.getSource().sendFailure(Component.literal("すでに国家に所属しています。"));
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "name");
		manager.foundPlayerNation(player, name, Ideology.MERCANTILISM,
				WorldPos.of(player.level(), player.blockPosition()), 0xE8C547);
		return 1;
	}

	private static int claim(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Nation nation = requireNation(player);
		if (nation == null) {
			return 0;
		}
		int radius = IntegerArgumentType.getInteger(ctx, "radius");
		int claimed = NationManager.require().territory().claimRadius(nation,
				new net.minecraft.world.level.ChunkPos(player.blockPosition()), radius);
		ctx.getSource().sendSuccess(() -> Component.literal("領土を " + claimed + " チャンク宣言しました"), false);
		return 1;
	}

	private static int subjects(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Nation nation = requireNation(player);
		if (nation == null) {
			return 0;
		}
		List<String> lines = RaphaelCore.subjectLines(nation);
		ctx.getSource().sendSuccess(() -> Component.literal("§6[臣下] " + lines.size() + " 名"), false);
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal("§7" + line), false);
		}
		return 1;
	}

	private static int diplomacy(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Nation nation = requireNation(player);
		if (nation == null) {
			return 0;
		}
		for (String line : RaphaelCore.diplomacyLines(nation)) {
			ctx.getSource().sendSuccess(() -> Component.literal("§7" + line), false);
		}
		return 1;
	}

	private static int scanMods(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		RaphaelCore.scanMods(ctx.getSource().getPlayerOrException());
		return 1;
	}

	private static int nationReport(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		RaphaelCore.reportNation(ctx.getSource().getPlayerOrException());
		return 1;
	}

	private static int warReport(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		RaphaelCore.reportWar(ctx.getSource().getPlayerOrException());
		return 1;
	}

	private static int economyReport(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		RaphaelCore.reportEconomy(ctx.getSource().getPlayerOrException());
		return 1;
	}

	private static int threatReport(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		RaphaelCore.reportThreats(ctx.getSource().getPlayerOrException());
		return 1;
	}

	private static int automationAdvice(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		RaphaelCore.adviseAutomation(ctx.getSource().getPlayerOrException());
		return 1;
	}

	private static int briefing(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		RaphaelCore.fullBriefing(ctx.getSource().getPlayerOrException());
		return 1;
	}

	private static int history(CommandContext<CommandSourceStack> ctx) {
		int index = 0;
		for (var report : RaphaelRelay.history()) {
			if (index++ > 15) {
				break;
			}
			ctx.getSource().sendSuccess(() -> Component.literal("§7" + report.oneLine()), false);
		}
		return 1;
	}

	/** コマンド名（デバッグ用）。 */
	public static String rootName() {
		return WK.MOD_ID;
	}
}
