package com.wisdomking.entity;

import com.wisdomking.core.WKConfig;
import com.wisdomking.core.WKLog;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.NpcRecord;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC の実体化（Materialization）管理。
 *
 * <p>国家の構成員（{@link NpcRecord}）は常にシミュレーションされているが、実際の
 * エンティティとして世界に存在するのはプレイヤー周辺の一部だけである。
 * これにより「1000体の国家」でもサーバー負荷を一定に保ちつつ、
 * 98% の世界が自律的に動いているように見せられる。</p>
 *
 * <p>遠方の NPC は実体化を解除（デスポーン）し、台帳（{@code NpcRecord}）へ
 * 位置・忠誠度を書き戻してから消す。再接近時に再実体化される。</p>
 */
public final class NpcSpawner {
	private static final Map<UUID, Long> LAST_SPAWN = new ConcurrentHashMap<>();

	private NpcSpawner() {
	}

	/** 毎tick処理（間引いて実行される）。 */
	public static void tick(ServerLevel level) {
		try {
			NationManager manager = NationManager.get();
			if (manager == null) {
				return;
			}
			long time = level.getGameTime();
			if (time % 40L != 0L) {
				return;
			}
			despawnDistant(level);
			materializeNearPlayers(level, manager);
		} catch (Throwable t) {
			WKLog.error("npc spawner tick failed", t);
		}
	}

	/** プレイヤー周辺の国家構成員を実体化する。 */
	private static void materializeNearPlayers(ServerLevel level, NationManager manager) {
		double radius = WKConfig.get().npcMaterializeRadius;
		int cap = WKConfig.get().npcEntityCap;
		int alive = WKNpcEntities.countAll(level);
		if (alive >= cap) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			java.util.UUID nationId = manager.nationIdOfPlayer(player);
			Nation nation = nationId == null ? null : manager.nation(nationId);
			if (nation == null || !nation.alive) {
				continue;
			}
			int budget = Math.max(1, (cap - alive) / 4 + 1);
			for (NpcRecord record : nation.subjects()) {
				if (budget <= 0 || alive >= cap) {
					break;
				}
				if (!record.alive || isMaterialized(level, record.id)) {
					continue;
				}
				Long last = LAST_SPAWN.get(record.id);
				long now = level.getGameTime();
				if (last != null && now - last < 200L) {
					continue;
				}
				double distance = record.lastPos == null ? 0.0
						: Math.sqrt(record.lastPos.pos().distSqr(player.blockPosition()));
				if (record.lastPos != null && distance > radius) {
					continue;
				}
				if (WKNpcEntities.materialize(level, nation, record) != null) {
					LAST_SPAWN.put(record.id, now);
					budget--;
					alive++;
				}
			}
		}
	}

	/** プレイヤーから遠ざかった NPC を台帳へ書き戻して消す。 */
	private static void despawnDistant(ServerLevel level) {
		double radius = WKConfig.get().npcMaterializeRadius * 1.6D;
		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof WKNpcEntity npc)) {
				continue;
			}
			NpcRecord record = npc.record();
			if (record != null) {
				record.lastPos = npc.worldPos();
				record.lastSeenTick = level.getGameTime();
			}
			if (level.getNearestPlayer(npc, radius) == null) {
				npc.discard();
			}
		}
	}

	/** 指定レコードが既に実体化されているか。 */
	public static boolean isMaterialized(ServerLevel level, UUID recordId) {
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof WKNpcEntity npc && recordId.equals(npc.getNpcId())) {
				return true;
			}
		}
		return false;
	}

	/** 国家の首都周辺へ人口を一括実体化する（建国直後の初期配置）。 */
	public static int materializeCapital(ServerLevel level, Nation nation, int count) {
		int spawned = 0;
		int index = 0;
		for (NpcRecord record : nation.subjects()) {
			if (spawned >= count || index++ > 64) {
				break;
			}
			if (!record.alive) {
				continue;
			}
			if (WKNpcEntities.materialize(level, nation, record) != null) {
				spawned++;
			}
		}
		return spawned;
	}

	/** 役割ごとの実体数（Raphael の報告用）。 */
	public static Map<NationRole, Integer> census(ServerLevel level) {
		Map<NationRole, Integer> map = new HashMap<>();
		try {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof WKNpcEntity npc) {
					map.merge(npc.getNationRole(), 1, Integer::sum);
				}
			}
		} catch (Throwable ignored) {
			// 集計失敗は無視
		}
		return map;
	}

	/** 状態を初期化する（サーバー停止時）。 */
	public static void reset() {
		LAST_SPAWN.clear();
	}
}
