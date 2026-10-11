package com.wisdomking.network;

import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.NpcRecord;
import com.wisdomking.nation.Relation;
import com.wisdomking.nation.ResourceType;
import com.wisdomking.raphael.RaphaelRelay;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家ダッシュボード用データの構築。
 *
 * <p>国家情報・臣下リスト・外交一覧・資源備蓄・智慧之王の解析レポートを
 * 単一の NBT へ集約して送信する。クライアントは受信した NBT をそのまま GUI に描画する。</p>
 */
public final class WKDashboardData {
	private WKDashboardData() {
	}

	public static CompoundTag build(ServerPlayer player) {
		CompoundTag root = new CompoundTag();
		NationManager manager = NationManager.get();
		if (manager == null) {
			root.putBoolean("valid", false);
			root.putString("message", "国家データが未初期化です。");
			return root;
		}
		Nation nation = manager.nationOfPlayer(player.getUUID());
		if (nation == null) {
			root.putBoolean("valid", false);
			root.putString("message", "国家に所属していません。建国憲章で国家を設立してください。");
			root.putInt("nationCount", manager.nationCount());
			root.put("world", worldOverview(manager));
			return root;
		}
		root.putBoolean("valid", true);
		root.putString("name", nation.name);
		root.putString("ideology", nation.ideology.name());
		root.putString("king", nation.kingName);
		root.putString("kingPersonality", nation.kingPersonality.name());
		root.putInt("generation", nation.dynastyGeneration);
		root.putInt("population", nation.population());
		root.putInt("army", nation.armySize());
		root.putFloat("militaryPower", nation.militaryPower());
		root.putFloat("unrest", nation.unrest);
		root.putFloat("legitimacy", nation.legitimacy);
		root.putFloat("morale", nation.morale);
		root.putFloat("technology", nation.technology);
		root.putDouble("treasury", nation.treasury);
		root.putInt("chunks", nation.claimedChunks.size());
		root.putInt("color", nation.color);
		root.putBoolean("atWar", nation.atWar());
		root.putInt("nationCount", manager.nationCount());

		CompoundTag stock = new CompoundTag();
		for (ResourceType type : ResourceType.values()) {
			stock.putDouble(type.name(), nation.stock(type));
			stock.putDouble(type.name() + "_deficit", manager.economy().deficit(nation, type));
		}
		root.put("stock", stock);

		// 臣下リスト（継承スコア順）
		ListTag subjects = new ListTag();
		List<NpcRecord> records = new ArrayList<>(nation.subjects());
		records.sort((a, b) -> Float.compare(b.successionScore(), a.successionScore()));
		int count = 0;
		for (NpcRecord record : records) {
			if (!record.alive) {
				continue;
			}
			CompoundTag tag = new CompoundTag();
			tag.putString("name", record.name);
			tag.putString("role", record.role.name());
			tag.putString("personality", record.personality.name());
			tag.putFloat("loyalty", record.loyalty);
			tag.putFloat("popularity", record.popularity);
			tag.putFloat("power", record.power);
			tag.putBoolean("isKing", record.role == NationRole.KING);
			tag.putFloat("successionScore", record.successionScore());
			tag.putString("id", record.id.toString());
			subjects.add(tag);
			if (++count > 60) {
				break;
			}
		}
		root.put("subjects", subjects);

		// 外交一覧
		ListTag diplomacy = new ListTag();
		for (Nation other : manager.aliveNations()) {
			if (other.id.equals(nation.id)) {
				continue;
			}
			Relation relation = nation.relation(other.id);
			CompoundTag tag = new CompoundTag();
			tag.putString("name", other.name);
			tag.putString("id", other.id.toString());
			tag.putString("state", relation.state.name());
			tag.putFloat("value", relation.value);
			tag.putFloat("power", other.militaryPower());
			tag.putFloat("ratio", other.militaryPower() / Math.max(1.0f, nation.militaryPower()));
			tag.putString("ideology", other.ideology.name());
			tag.putString("king", other.kingName);
			tag.putBoolean("atWar", relation.state == com.wisdomking.nation.RelationState.WAR);
			diplomacy.add(tag);
		}
		root.put("diplomacy", diplomacy);

		// 戦況
		ListTag wars = new ListTag();
		for (var war : manager.war().warsOf(nation)) {
			Nation enemy = manager.nation(war.enemyOf(nation.id));
			CompoundTag tag = new CompoundTag();
			tag.putString("enemy", enemy == null ? "不明" : enemy.name);
			boolean attacker = war.attacker.equals(nation.id);
			tag.putInt("score", attacker ? war.score : -war.score);
			tag.putString("phase", war.phase.name());
			tag.putString("casus", war.casusBelli);
			tag.putBoolean("capitalCaptured", war.capitalCaptured);
			wars.add(tag);
		}
		root.put("wars", wars);

		// 国家史
		ListTag history = new ListTag();
		for (String entry : nation.recentChronicle(12)) {
			history.add(net.minecraft.nbt.StringTag.valueOf(entry));
		}
		root.put("chronicle", history);

		// 智慧之王レポート
		ListTag reports = new ListTag();
		int reportCount = 0;
		for (var report : RaphaelRelay.history()) {
			reports.add(report.save());
			if (++reportCount > 12) {
				break;
			}
		}
		root.put("reports", reports);
		root.put("world", worldOverview(manager));
		return root;
	}

	/** 世界全体の国家一覧（地政学ビュー）。 */
	private static ListTag worldOverview(NationManager manager) {
		ListTag list = new ListTag();
		for (Nation nation : manager.aliveNations()) {
			CompoundTag tag = new CompoundTag();
			tag.putString("name", nation.name);
			tag.putString("king", nation.kingName);
			tag.putString("ideology", nation.ideology.name());
			tag.putInt("population", nation.population());
			tag.putInt("chunks", nation.claimedChunks.size());
			tag.putFloat("power", nation.militaryPower());
			tag.putFloat("unrest", nation.unrest);
			tag.putBoolean("atWar", nation.atWar());
			tag.putBoolean("player", nation.playerKing != null);
			tag.putBoolean("vassal", nation.overlord != null);
			list.add(tag);
		}
		return list;
	}
}
