package com.wisdomking.ai.goals;

import com.wisdomking.ai.CivilEngineeringAI;
import com.wisdomking.entity.WKNpcEntity;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationRole;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * 技師・職人による土木作業 Goal。
 *
 * <p>国家の土木ジョブキューから最寄りの作業地点を取り出し、そこへ移動して
 * 実際に道路敷設・整地・防衛陣地構築を進める。NPC が労働力として参加することで
 * 「自律的な土木 AI」が住民の行動として可視化される。</p>
 */
public class BuildRoadGoal extends Goal {
	private final WKNpcEntity npc;
	@Nullable
	private BlockPos site;

	public BuildRoadGoal(WKNpcEntity npc) {
		this.npc = npc;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!(npc.level() instanceof ServerLevel level) || !npc.taskReady()) {
			return false;
		}
		NationRole role = npc.getNationRole();
		if (role != NationRole.ENGINEER && role != NationRole.CRAFTSMAN && role != NationRole.CITIZEN) {
			return false;
		}
		Nation nation = npc.nation();
		if (nation == null || CivilEngineeringAI.queuedJobs() <= 0) {
			return false;
		}
		site = CivilEngineeringAI.nearestWorkSite(nation, npc.blockPosition());
		return site != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (site == null || !npc.taskReady()) {
			return false;
		}
		Nation nation = npc.nation();
		return nation != null && npc.distanceToSqr(site.getX() + 0.5D, site.getY() + 0.5D,
				site.getZ() + 0.5D) > 4.0D;
	}

	@Override
	public void start() {
		if (site != null) {
			npc.getNavigation().moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, 1.0D);
		}
	}

	@Override
	public void tick() {
		if (site == null) {
			return;
		}
		if (!(npc.level() instanceof ServerLevel level)) {
			return;
		}
		Nation nation = npc.nation();
		if (nation == null) {
			return;
		}
		if (npc.distanceToSqr(site.getX() + 0.5D, site.getY() + 0.5D, site.getZ() + 0.5D) <= 9.0D) {
			boolean worked = CivilEngineeringAI.workAt(level, nation, npc.blockPosition());
			npc.markTaskDone(worked ? 40 : 120);
			npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		} else {
			npc.getNavigation().moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, 1.0D);
		}
	}

	@Override
	public void stop() {
		site = null;
	}
}
