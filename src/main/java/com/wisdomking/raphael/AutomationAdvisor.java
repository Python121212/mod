package com.wisdomking.raphael;

import com.wisdomking.core.WKLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自動化ライン最適化アドバイザ。
 *
 * <p>周辺のブロックエンティティを走査して「動力源／消費機械／保管庫」の構成比を測り、
 * 稼働状態の変化（NBT ハッシュ）から停止機械を検出して、具体的な増設・改修を提案する。
 * 例:「《告》動力不足を検出。水車の増設を推奨します」</p>
 *
 * <p>この解析は Mod の API に依存しないため、Create・Mekanism・Immersive Engineering など
 * どの機械Modでも動作する。</p>
 */
public final class AutomationAdvisor {
	/** 機械ごとの前回活動ハッシュ。 */
	private static final Map<String, Integer> LAST_HASH = new ConcurrentHashMap<>();
	private static final Map<String, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** 停止と見なすまでの経過tick数（10秒）。 */
	private static final long IDLE_THRESHOLD = 200L;

	private AutomationAdvisor() {
	}

	/** 周辺の自動化ラインを解析し、助言レポートを返す。 */
	public static AnalysisReport advise(ServerLevel level, BlockPos center, int radius) {
		long now = level.getGameTime();
		Map<String, int[]> familyStats = new LinkedHashMap<>(); // family -> [power, consumer, storage, other]
		List<String> idleMachines = new ArrayList<>();
		List<String> saturatedMachines = new ArrayList<>();
		int scanned = 0;
		int withInventory = 0;

		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
				center.offset(radius, radius, radius))) {
			BlockEntity be;
			try {
				be = level.getBlockEntity(pos);
			} catch (Throwable t) {
				continue;
			}
			if (be == null) {
				continue;
			}
			scanned++;
			String className = be.getClass().getName();
			var blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
			String path = blockId == null ? "" : blockId.getPath();
			String namespace = blockId == null ? "minecraft" : blockId.getNamespace();
			MachineAnalyzer.Family family = MachineAnalyzer.classifyFamily(className, path);
			int[] stats = familyStats.computeIfAbsent(namespace + "|" + family.name(), key -> new int[4]);
			if (MachineAnalyzer.isPowerSource(className, path)) {
				stats[0]++;
			} else if (MachineAnalyzer.isConsumer(className, path)) {
				stats[1]++;
			} else if (family == MachineAnalyzer.Family.STORAGE) {
				stats[2]++;
			} else {
				stats[3]++;
			}

			// 稼働変化の追跡
			String key = level.dimension().location() + "@" + pos.asLong();
			int hash = MachineAnalyzer.activityHash(be, level);
			Integer previous = LAST_HASH.put(key, hash);
			Long previousTick = LAST_TICK.put(key, now);
			if (previous != null && previousTick != null) {
				boolean unchanged = previous == hash;
				long elapsed = now - previousTick;
				if (unchanged && elapsed > IDLE_THRESHOLD && (MachineAnalyzer.isConsumer(className, path))) {
					idleMachines.add(String.format("%s (%d,%d,%d)", path, pos.getX(), pos.getY(), pos.getZ()));
				}
			}
			if (be instanceof net.minecraft.world.Container container) {
				withInventory++;
				int filled = 0;
				for (int i = 0; i < container.getContainerSize(); i++) {
					if (!container.getItem(i).isEmpty()) {
						filled++;
					}
				}
				if (filled == container.getContainerSize() && filled > 0) {
					saturatedMachines.add(String.format("%s (%d,%d,%d)", path, pos.getX(), pos.getY(), pos.getZ()));
				}
			}
		}

		List<String> details = new ArrayList<>();
		int totalPower = 0;
		int totalConsumers = 0;
		for (Map.Entry<String, int[]> entry : familyStats.entrySet()) {
			int[] stats = entry.getValue();
			totalPower += stats[0];
			totalConsumers += stats[1];
			details.add(String.format("%s [%s]: 動力源 %d / 消費機械 %d / 保管 %d / その他 %d",
					ModScanner.displayName(entry.getKey().split("\\|")[0]),
					entry.getKey().split("\\|")[1], stats[0], stats[1], stats[2], stats[3]));
		}

		List<String> advisories = new ArrayList<>();
		if (totalConsumers > 0 && totalPower == 0) {
			advisories.add("動力不足を検出。水車・風車・蒸気機関などの動力源増設を推奨します。");
		} else if (totalConsumers > totalPower * 4) {
			advisories.add("負荷過多。動力源 1 あたり消費機械 " + (totalConsumers / Math.max(1, totalPower))
					+ " 基は過密です。動力網の分割を推奨します。");
		}
		if (!idleMachines.isEmpty()) {
			advisories.add("稼働停止 " + idleMachines.size() + " 基: 入力・出力経路または燃料供給を点検してください。");
			details.add("停止機械: " + String.join(", ", idleMachines.subList(0, Math.min(4, idleMachines.size()))));
		}
		if (!saturatedMachines.isEmpty()) {
			advisories.add("出力先飽和 " + saturatedMachines.size() + " 基: 搬出経路の増設を推奨します。");
		}
		if (advisories.isEmpty()) {
			advisories.add("自動化ラインは安定稼働しています。現状の構成を維持してください。");
		}
		details.add("走査対象ブロックエンティティ: " + scanned + "（うちインベントリ保持 " + withInventory + "）");
		if (ModScanner.hasMechanicalMods()) {
			details.add("機械系Modを検出。キネティック・ネットワークのストレス余裕を確保してください。");
		}
		details.addAll(advisories);
		if (scanned == 0) {
			details.add("《告》周辺に機械が検出されませんでした。走査半径を広げるか近づいて再実行してください。");
		}
		return new AnalysisReport(Tone.ALERT,
				"自動化ライン解析: 動力源 " + totalPower + " / 消費機械 " + totalConsumers,
				details, advisories.get(0).startsWith("動力不足") ? Urgency.NOTICE : Urgency.INFO);
	}

	/** 機械記憶をクリアする（サーバー停止時）。 */
	public static void reset() {
		LAST_HASH.clear();
		LAST_TICK.clear();
		WKLog.info("automation advisor memory cleared");
	}
}
