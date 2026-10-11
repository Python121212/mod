package com.wisdomking.ai;

import com.wisdomking.core.WKConfig;
import com.wisdomking.core.WKLog;
import com.wisdomking.core.WKTickScheduler;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 自律的地形変更・土木整備AI。
 *
 * <p>国家の首都・施設・国庫を結ぶ道路網を自動敷設し、施設建設予定地を整地し、
 * 戦時には首都周囲へ堀とバリケードを構築、戦後は破壊された地形を復興する。
 * すべての作業は {@link WKTickScheduler} により 1tick あたりの予算内で分割実行され、
 * サーバーのtick落ちを防ぐ。</p>
 */
public final class CivilEngineeringAI {
	/** 1ステップで設置する最大ブロック数。 */
	private static final int BLOCKS_PER_STEP = 8;
	/** 予約ジョブ数（国家ごと）。 */
	private static final int MAX_JOBS = 12;

	private static final Deque<Job> JOBS = new ArrayDeque<>();
	private static int activeJobs;

	private CivilEngineeringAI() {
	}

	/** 土木ジョブ。 */
	public interface Job {
		/** @return 完了したら true */
		boolean step(ServerLevel level, Nation nation);

		String describe();
	}

	/** 道路網の敷設を予約する。 */
	public static int scheduleRoadNetwork(ServerLevel level, Nation nation) {
		if (!WKConfig.get().civilEngineering) {
			return 0;
		}
		BlockPos center = nation.capital == null ? level.getSharedSpawnPos() : nation.capital.pos();
		List<BlockPos> targets = new ArrayList<>();
		// 国庫・施設・交易ポスト（読み込み済みチャンク内）を目的地とする
		for (long key : nation.claimedChunks) {
			ChunkPos chunkPos = new ChunkPos(key);
			if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
				continue;
			}
			for (var entry : level.getChunk(chunkPos.x, chunkPos.z).getBlockEntities().entrySet()) {
				if (entry.getValue() instanceof com.wisdomking.block.NationBlockEntity) {
					targets.add(entry.getKey().immutable());
					if (targets.size() >= 8) {
						break;
					}
				}
			}
		}
		int queued = 0;
		for (BlockPos target : targets) {
			if (queued >= 4) {
				break;
			}
			List<BlockPos> path = planPath(level, center, target);
			if (path.isEmpty()) {
				continue;
			}
			enqueue(new RoadJob(path, nation.id));
			queued++;
		}
		if (queued == 0 && JOBS.size() < MAX_JOBS) {
			// 目的地が無い場合は首都から外周へ環状道路を敷設する
			List<BlockPos> ring = planRing(level, center, 48);
			enqueue(new RoadJob(ring, nation.id));
			queued++;
		}
		nation.chronicle("土木: 道路敷設を " + queued + " 区間予約した。");
		WKLog.info("civil: queued " + queued + " road jobs for " + nation.name);
		return queued;
	}

	/** 戦後の復興を予約する。 */
	public static int scheduleRepair(ServerLevel level, Nation nation) {
		WarDamageJournal journal = journal(nation);
		List<WarDamageJournal.Entry> pending = journal.pending();
		if (pending.isEmpty()) {
			return 0;
		}
		int count = Math.min(pending.size(), 2048);
		enqueue(new RepairJob(pending.subList(0, count), nation.id));
		nation.chronicle("復興: 戦災ブロック " + count + " 件の修復を開始。");
		return count;
	}

	/** 戦時の防衛陣地構築（堀・バリケード）を予約する。 */
	public static int scheduleDefenseLine(ServerLevel level, Nation nation) {
		BlockPos center = nation.capital == null ? level.getSharedSpawnPos() : nation.capital.pos();
		List<BlockPos> moat = new ArrayList<>();
		List<BlockPos> wall = new ArrayList<>();
		int radius = 24;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance > radius || distance < radius - 1.5) {
					continue;
				}
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				BlockPos pos = new BlockPos(x, y, z);
				if (dx % 6 == 0 && dz % 6 == 0) {
					wall.add(pos.above());
				} else {
					moat.add(pos);
				}
			}
		}
		enqueue(new DefenseJob(moat, wall, nation.id));
		nation.chronicle("防衛: 首都周囲に堀とバリケードを構築開始。");
		return moat.size() + wall.size();
	}

	/** 施設建設予定地の整地を予約する。 */
	public static int scheduleLevelArea(ServerLevel level, Nation nation, BlockPos center, int radius) {
		enqueue(new LevelJob(center, radius, nation.id));
		return (radius * 2 + 1) * (radius * 2 + 1);
	}

	private static void enqueue(Job job) {
		if (JOBS.size() >= MAX_JOBS) {
			JOBS.pollFirst();
		}
		JOBS.addLast(job);
	}

	/** サーバーtickから呼ばれ、予算内でジョブを消化する。 */
	public static void tick(ServerLevel level) {
		if (!WKConfig.get().civilEngineering || JOBS.isEmpty()) {
			return;
		}
		int budget = Math.max(1, WKConfig.get().schedulerBudget / BLOCKS_PER_STEP);
		int processed = 0;
		int size = JOBS.size();
		for (int i = 0; i < size && processed < budget; i++) {
			Job job = JOBS.pollFirst();
			if (job == null) {
				break;
			}
			processed++;
			try {
				if (!job.step(level, null)) {
					JOBS.addLast(job);
				}
			} catch (Throwable t) {
				WKLog.warn("civil job failed safely: " + t);
			}
		}
	}

	public static int queuedJobs() {
		return JOBS.size();
	}

	// ------------------------------------------------------------------
	// 経路計画
	// ------------------------------------------------------------------

	/** 地表に沿った直線経路を計画する（段差は階段状に処理）。 */
	public static List<BlockPos> planPath(ServerLevel level, BlockPos from, BlockPos to) {
		List<BlockPos> path = new ArrayList<>();
		int steps = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
		if (steps > WKConfig.get().roadMaxLength * 4) {
			return path;
		}
		for (int i = 0; i <= steps; i++) {
			double t = steps == 0 ? 0.0 : (double) i / steps;
			int x = (int) Math.round(from.getX() + (to.getX() - from.getX()) * t);
			int z = (int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * t);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			path.add(new BlockPos(x, y, z));
		}
		return path;
	}

	/** 首都周囲の環状道路。 */
	public static List<BlockPos> planRing(ServerLevel level, BlockPos center, int radius) {
		List<BlockPos> path = new ArrayList<>();
		for (int angle = 0; angle < 360; angle += 4) {
			double rad = Math.toRadians(angle);
			int x = center.getX() + (int) (Math.cos(rad) * radius);
			int z = center.getZ() + (int) (Math.sin(rad) * radius);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			path.add(new BlockPos(x, y, z));
		}
		return path;
	}

	// ------------------------------------------------------------------
	// ジョブ実装
	// ------------------------------------------------------------------

	/** 道路敷設ジョブ。 */
	private static final class RoadJob implements Job {
		private final List<BlockPos> path;
		private final java.util.UUID nationId;
		private int index;

		RoadJob(List<BlockPos> path, java.util.UUID nationId) {
			this.path = path;
			this.nationId = nationId;
		}

		@Override
		public boolean step(ServerLevel level, @Nullable Nation ignored) {
			Nation nation = NationManager.get() == null ? null : NationManager.get().nation(nationId);
			int placed = 0;
			while (index < path.size() && placed < BLOCKS_PER_STEP) {
				BlockPos pos = path.get(index++);
				placed += buildRoadBlock(level, pos, nation);
			}
			return index >= path.size();
		}

		@Override
		public String describe() {
			return "道路敷設 " + index + "/" + path.size();
		}
	}

	/** 1ブロック分の道路を敷設する。 */
	private static int buildRoadBlock(ServerLevel level, BlockPos surface, @Nullable Nation nation) {
		BlockState state = level.getBlockState(surface);
		int placed = 0;
		if (state.getFluidState().getType() == Fluids.WATER) {
			// 架橋（水面を板で覆う）
			level.setBlockAndUpdate(surface, material(nation, ResourceType.WOOD,
					Blocks.OAK_PLANKS.defaultBlockState()));
			placed++;
		} else if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(Blocks.GRAVEL) /* fallback */) {
			BlockState road = level.getGameTime() % 3 == 0
					? material(nation, ResourceType.STONE, Blocks.STONE_BRICKS.defaultBlockState())
					: material(nation, ResourceType.STONE, Blocks.COBBLESTONE.defaultBlockState());
			level.setBlockAndUpdate(surface, road);
			placed++;
		} else if (state.isAir()) {
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, surface.getX(), surface.getZ()) - 1;
			BlockPos ground = new BlockPos(surface.getX(), y, surface.getZ());
			level.setBlockAndUpdate(ground, material(nation, ResourceType.STONE,
					Blocks.COBBLESTONE.defaultBlockState()));
			placed++;
		}
		// 上空の障害物（葉・草）を除去し、明かりを設置する
		BlockPos above = surface.above();
		BlockState aboveState = level.getBlockState(above);
		if (aboveState.is(BlockTags.LEAVES) || aboveState.is(BlockTags.REPLACEABLE)) {
			level.removeBlock(above, false);
			placed++;
		}
		if (placed > 0 && (surface.getX() % 8 == 0 || surface.getZ() % 8 == 0)
				&& level.getBlockState(surface.above()).isAir()) {
			level.setBlockAndUpdate(surface.above(), Blocks.TORCH.defaultBlockState());
		}
		return placed;
	}

	private static BlockState material(@Nullable Nation nation, ResourceType type, BlockState fallback) {
		if (nation == null) {
			return fallback;
		}
		if (nation.stock(type) > 1.0) {
			nation.addStock(type, -1.0);
			NationManager manager = NationManager.get();
			if (manager != null) {
				manager.markDirty();
			}
			return fallback;
		}
		return fallback;
	}

	/** 戦災復興ジョブ。 */
	private static final class RepairJob implements Job {
		private final List<WarDamageJournal.Entry> entries;
		private final java.util.UUID nationId;
		private int index;

		RepairJob(List<WarDamageJournal.Entry> entries, java.util.UUID nationId) {
			this.entries = entries;
			this.nationId = nationId;
		}

		@Override
		public boolean step(ServerLevel level, @Nullable Nation ignored) {
			Nation nation = NationManager.get() == null ? null : NationManager.get().nation(nationId);
			int placed = 0;
			while (index < entries.size() && placed < BLOCKS_PER_STEP) {
				WarDamageJournal.Entry entry = entries.get(index++);
				BlockPos pos = entry.pos();
				if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
					continue;
				}
				BlockState current = level.getBlockState(pos);
				if (current.isAir() || current.getFluidState().getType() == Fluids.WATER
						|| current.is(BlockTags.REPLACEABLE)) {
					level.setBlockAndUpdate(pos, WarDamageJournal.resolve(entry.blockId()));
					placed++;
				}
			}
			if (nation != null && index >= entries.size()) {
				journal(nation).clear();
				nation.chronicle("復興: 戦災復興が完了した。");
			}
			return index >= entries.size();
		}

		@Override
		public String describe() {
			return "復興 " + index + "/" + entries.size();
		}
	}

	/** 防衛陣地構築ジョブ（堀の掘削 + バリケード設置）。 */
	private static final class DefenseJob implements Job {
		private final List<BlockPos> moat;
		private final List<BlockPos> wall;
		private final java.util.UUID nationId;
		private int moatIndex;
		private int wallIndex;

		DefenseJob(List<BlockPos> moat, List<BlockPos> wall, java.util.UUID nationId) {
			this.moat = moat;
			this.wall = wall;
			this.nationId = nationId;
		}

		@Override
		public boolean step(ServerLevel level, @Nullable Nation ignored) {
			int placed = 0;
			while (moatIndex < moat.size() && placed < BLOCKS_PER_STEP) {
				BlockPos pos = moat.get(moatIndex++);
				if (level.getBlockState(pos).getBlock() != Blocks.AIR) {
					level.removeBlock(pos, false);
					placed++;
				}
			}
			while (wallIndex < wall.size() && placed < BLOCKS_PER_STEP) {
				BlockPos pos = wall.get(wallIndex++);
				if (level.getBlockState(pos).canBeReplaced()) {
					level.setBlockAndUpdate(pos, Blocks.OAK_FENCE.defaultBlockState());
					placed++;
				}
			}
			return moatIndex >= moat.size() && wallIndex >= wall.size();
		}

		@Override
		public String describe() {
			return "防衛構築 堀 " + moatIndex + "/" + moat.size() + " 壁 " + wallIndex + "/" + wall.size();
		}
	}

	/** 整地ジョブ。 */
	private static final class LevelJob implements Job {
		private final BlockPos center;
		private final int radius;
		private final java.util.UUID nationId;
		private int dx = -1;
		private int dz;

		LevelJob(BlockPos center, int radius, java.util.UUID nationId) {
			this.center = center;
			this.radius = radius;
			this.nationId = nationId;
		}

		@Override
		public boolean step(ServerLevel level, @Nullable Nation ignored) {
			int placed = 0;
			while (placed < BLOCKS_PER_STEP) {
				if (dx < -radius) {
					return true;
				}
				if (dz > radius) {
					dz = -radius;
					dx++;
					continue;
				}
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				dz++;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				int targetY = center.getY();
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				if (y > targetY) {
					level.removeBlock(new BlockPos(x, y - 1, z), false);
					placed++;
				} else if (y < targetY) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState());
					placed++;
				}
			}
			return false;
		}

		@Override
		public String describe() {
			return "整地 dx=" + dx + " dz=" + dz;
		}
	}

	// ------------------------------------------------------------------
	// 戦災記録
	// ------------------------------------------------------------------

	private static final java.util.Map<java.util.UUID, WarDamageJournal> JOURNALS = new java.util.HashMap<>();

	public static WarDamageJournal journal(Nation nation) {
		return JOURNALS.computeIfAbsent(nation.id, key -> new WarDamageJournal());
	}

	public static List<String> jobSummary() {
		List<String> list = new ArrayList<>();
		for (Job job : JOBS) {
			list.add(job.describe());
		}
		return list;
	}

	public static void reset() {
		JOBS.clear();
		activeJobs = 0;
	}
}
