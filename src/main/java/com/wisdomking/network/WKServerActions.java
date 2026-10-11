package com.wisdomking.network;

import com.wisdomking.core.WKLog;
import com.wisdomking.nation.Ideology;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.WorldPos;
import com.wisdomking.raphael.RaphaelCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * クライアントから送信された操作（{@link WKPayloads.ActionPayload}）を検証して実行する。
 *
 * <p>すべての操作はサーバー側で権限・所属国家を再検証する（クライアントを信用しない）。
 * 例外は握りつぶし、不正なペイロードでサーバーが落ちないようにする。</p>
 */
public final class WKServerActions {
	public static final String ACTION_REQUEST_DASHBOARD = "request_dashboard";
	public static final String ACTION_ANALYZE_TARGET = "analyze_target";
	public static final String ACTION_ANALYZE_HELD = "analyze_held";
	public static final String ACTION_SCAN_MODS = "scan_mods";
	public static final String ACTION_NATION_REPORT = "nation_report";
	public static final String ACTION_WAR_REPORT = "war_report";
	public static final String ACTION_ECONOMY_REPORT = "economy_report";
	public static final String ACTION_THREAT_REPORT = "threat_report";
	public static final String ACTION_AUTOMATION_ADVICE = "automation_advice";
	public static final String ACTION_FULL_BRIEFING = "full_briefing";
	public static final String ACTION_FOUND_NATION = "found_nation";
	public static final String ACTION_DECLARE_WAR = "declare_war";
	public static final String ACTION_PROPOSE_PEACE = "propose_peace";
	public static final String ACTION_FORM_ALLIANCE = "form_alliance";
	public static final String ACTION_CLAIM_TERRITORY = "claim_territory";
	public static final String ACTION_VOICE_COMMAND = "voice_command";
	public static final String ACTION_ASSIGN_EQUIPMENT = "assign_equipment";

	private WKServerActions() {
	}

	public static void handle(ServerPlayer player, CompoundTag data) {
		try {
			String action = data.getString("action");
			switch (action) {
				case ACTION_REQUEST_DASHBOARD -> WKPayloads.sendDashboard(player);
				case ACTION_ANALYZE_TARGET -> RaphaelCore.analyzeTarget(player, 8.0);
				case ACTION_ANALYZE_HELD -> RaphaelCore.analyzeHeld(player);
				case ACTION_SCAN_MODS -> RaphaelCore.scanMods(player);
				case ACTION_NATION_REPORT -> RaphaelCore.reportNation(player);
				case ACTION_WAR_REPORT -> RaphaelCore.reportWar(player);
				case ACTION_ECONOMY_REPORT -> RaphaelCore.reportEconomy(player);
				case ACTION_THREAT_REPORT -> RaphaelCore.reportThreats(player);
				case ACTION_AUTOMATION_ADVICE -> RaphaelCore.adviseAutomation(player);
				case ACTION_FULL_BRIEFING -> RaphaelCore.fullBriefing(player);
				case ACTION_FOUND_NATION -> foundNation(player, data);
				case ACTION_DECLARE_WAR -> declareWar(player, data);
				case ACTION_PROPOSE_PEACE -> proposePeace(player, data);
				case ACTION_FORM_ALLIANCE -> formAlliance(player, data);
				case ACTION_CLAIM_TERRITORY -> claimTerritory(player, data);
				case ACTION_VOICE_COMMAND -> com.wisdomking.voice.VoiceCommandRouter.handle(player, data.getString("text"));
				case ACTION_ASSIGN_EQUIPMENT -> WKLog.safely("assign equipment",
						() -> WKServerActions.assignEquipment(player, data));
				default -> WKLog.warn("unknown action payload: " + action);
			}
			WKPayloads.sendDashboard(player);
		} catch (Throwable t) {
			WKLog.warn("action payload failed safely: " + t);
		}
	}

	private static void foundNation(ServerPlayer player, CompoundTag data) {
		NationManager manager = NationManager.get();
		if (manager == null || manager.nationOfPlayer(player.getUUID()) != null) {
			return;
		}
		String name = data.getString("name");
		if (name.isEmpty()) {
			name = player.getName().getString() + "王国";
		}
		Ideology ideology;
		try {
			ideology = Ideology.valueOf(data.getString("ideology"));
		} catch (Throwable t) {
			ideology = Ideology.MERCANTILISM;
		}
		manager.foundPlayerNation(player, name, ideology, WorldPos.of(player.level(), player.blockPosition()),
				0xE8C547);
	}

	private static void declareWar(ServerPlayer player, CompoundTag data) {
		NationManager manager = NationManager.get();
		if (manager == null) {
			return;
		}
		Nation own = manager.nationOfPlayer(player.getUUID());
		Nation target = manager.nation(parseUuid(data.getString("target")));
		if (own == null || target == null || own.id.equals(target.id)) {
			return;
		}
		manager.diplomacy().declareWar(own, target, "player_decree");
	}

	private static void proposePeace(ServerPlayer player, CompoundTag data) {
		NationManager manager = NationManager.get();
		if (manager == null) {
			return;
		}
		Nation own = manager.nationOfPlayer(player.getUUID());
		Nation target = manager.nation(parseUuid(data.getString("target")));
		if (own == null || target == null) {
			return;
		}
		if (!own.atWarWith(target.id)) {
			return;
		}
		// 相手の戦力が優勢なら講和を拒否される（外交の実利）
		float ownPower = own.militaryPower();
		float enemyPower = target.militaryPower();
		boolean accepted = ownPower >= enemyPower * 0.9f || own.treasury > target.treasury * 1.5;
		if (accepted) {
			manager.diplomacy().makeTruce(own, target);
			for (var war : manager.war().warsOf(own)) {
				if (war.involves(target.id)) {
					war.phase = com.wisdomking.nation.War.Phase.ENDED;
				}
			}
		} else {
			player.displayClientMessage(net.minecraft.network.chat.Component.literal(
					"§e[外交] " + target.name + " は講和を拒否しました。戦力差を覆すか賠償が必要です。"), false);
		}
	}

	private static void formAlliance(ServerPlayer player, CompoundTag data) {
		NationManager manager = NationManager.get();
		if (manager == null) {
			return;
		}
		Nation own = manager.nationOfPlayer(player.getUUID());
		Nation target = manager.nation(parseUuid(data.getString("target")));
		if (own == null || target == null) {
			return;
		}
		var relation = own.relation(target.id);
		boolean accepted = relation.value > 25.0f;
		if (accepted) {
			relation.state = com.wisdomking.nation.RelationState.ALLIED;
			target.relation(own.id).state = com.wisdomking.nation.RelationState.ALLIED;
			relation.treatyTicks = 24000L;
			target.relation(own.id).treatyTicks = 24000L;
			manager.broadcast(net.minecraft.network.chat.Component.literal(
					"§b[同盟] §f" + own.name + " と " + target.name + " が同盟を締結した。"));
			manager.markDirty();
		} else {
			player.displayClientMessage(net.minecraft.network.chat.Component.literal(
					"§e[外交] " + target.name + " は同盟を拒否しました（関係値 "
							+ Math.round(relation.value) + " / 必要 25）。"), false);
		}
	}

	private static void claimTerritory(ServerPlayer player, CompoundTag data) {
		NationManager manager = NationManager.get();
		if (manager == null) {
			return;
		}
		Nation own = manager.nationOfPlayer(player.getUUID());
		if (own == null) {
			return;
		}
		int radius = Math.max(1, Math.min(6, data.getInt("radius")));
		manager.territory().claimRadius(own, new net.minecraft.world.level.ChunkPos(player.blockPosition()), radius);
	}

	private static void assignEquipment(ServerPlayer player, CompoundTag data) {
		// 装備配分GUIからの要求（国庫から最良装備を該当NPCへ配備）
		var manager = NationManager.get();
		if (manager == null) {
			return;
		}
		Nation nation = manager.nationOfPlayer(player.getUUID());
		if (nation == null) {
			return;
		}
		com.wisdomking.equipment.EquipmentManager.assignToSoldiers(nation, player.serverLevel());
	}

	private static java.util.UUID parseUuid(String text) {
		try {
			return java.util.UUID.fromString(text);
		} catch (Throwable t) {
			return null;
		}
	}
}
