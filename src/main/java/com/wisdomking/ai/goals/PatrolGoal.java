package com.wisdomking.ai.goals;

import com.wisdomking.entity.WKNpcEntity;
import com.wisdomking.nation.Nation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * 警備 Goal（将軍・兵士）。
 *
 * <p>国家の領土（チャンク）から巡回地点を選び、そこまで移動する。
 * 戦時には敵の接近を索敵し、交戦中の相手がいれば CombatGoal へ制御を譲る。</p>
 */
public class PatrolGoal extends Goal {
	private final WKNpcEntity npc;
	@Nullable
	private BlockPos destination;

	public PatrolGoal(WKNpcEntity npc) {
		this.npc = npc;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!(npc.level() instanceof ServerLevel) || !npc.taskReady() || npc.getTarget() != null) {
			return false;
		}
		if (!npc.getNationRole().isMilitary()) {
			return false;
		}
		Nation nation = npc.nation();
		if (nation == null || nation.claimedChunks.isEmpty()) {
			return false;
		}
		List<Long> chunks = new java.util.ArrayList<>(nation.claimedChunks);
		for (int attempt = 0; attempt < 8; attempt++) {
			long key = chunks.get(npc.getRandom().nextInt(chunks.size()));
			BlockPos candidate = new BlockPos(net.minecraft.world.level.ChunkPos.getX(key) * 16 + 8, 64,
					net.minecraft.world.level.ChunkPos.getZ(key) * 16 + 8);
			// 他国チャンクへは巡回しない（自国・属国領域のみ）
			if (!nation.claimedChunks.contains(key)) {
				continue;
			}
			destination = candidate;
			break;
		}
		return destination != null;
	}

	@Override
	public boolean canContinueToUse() {
		return destination != null && npc.taskReady() && npc.getTarget() == null
				&& npc.distanceToSqr(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D) > 25.0D;
	}

	@Override
	public void start() {
		if (destination != null && npc.level() instanceof ServerLevel level) {
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
					destination.getX(), destination.getZ());
			destination = new BlockPos(destination.getX(), y, destination.getZ());
			npc.getNavigation().moveTo(destination.getX() + 0.5D, y, destination.getZ() + 0.5D, 0.9D);
		}
	}

	@Override
	public void tick() {
		if (destination == null) {
			return;
		}
		if (npc.distanceToSqr(destination.getX() + 0.5D, destination.getY() + 0.5D,
				destination.getZ() + 0.5D) < 36.0D) {
			npc.markTaskDone(300);
			destination = null;
		}
	}

	@Override
	public void stop() {
		destination = null;
	}
}
