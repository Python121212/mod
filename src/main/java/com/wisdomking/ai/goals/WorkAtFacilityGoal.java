package com.wisdomking.ai.goals;

import com.wisdomking.block.FacilityBlockEntity;
import com.wisdomking.entity.WKNpcEntity;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.ResourceType;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * 施設勤務 Goal。
 *
 * <p>兵舎・ギルド・魔法研究所・自動工房などの施設へ出勤し、生産を後押しする。
 * 施設ブロック自体の {@code serverTick} が機械的な生産を担い、
 * この Goal は「人手」として追加の生産・不満低減を担当する。</p>
 */
public class WorkAtFacilityGoal extends Goal {
	private final WKNpcEntity npc;
	@Nullable
	private BlockPos facilityPos;
	private int workTicks;

	public WorkAtFacilityGoal(WKNpcEntity npc) {
		this.npc = npc;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(npc.level() instanceof ServerLevel) || !npc.taskReady() || npc.getTarget() != null) {
			return false;
		}
		NationRole role = npc.getNationRole();
		if (role.isMilitary() || role == NationRole.MAGE && npc.getRandom().nextBoolean()) {
			return false;
		}
		Nation nation = npc.nation();
		if (nation == null) {
			return false;
		}
		facilityPos = findFacility();
		return facilityPos != null;
	}

	@Override
	public boolean canContinueToUse() {
		return facilityPos != null && npc.taskReady() && workTicks < 400;
	}

	@Override
	public void start() {
		workTicks = 0;
	}

	@Override
	public void tick() {
		if (facilityPos == null || !(npc.level() instanceof ServerLevel level)) {
			return;
		}
		if (npc.distanceToSqr(facilityPos.getX() + 0.5D, facilityPos.getY() + 0.5D,
				facilityPos.getZ() + 0.5D) > 16.0D) {
			npc.getNavigation().moveTo(facilityPos.getX() + 0.5D, facilityPos.getY(),
					facilityPos.getZ() + 0.5D, 1.0D);
			return;
		}
		npc.getNavigation().stop();
		workTicks++;
		if (workTicks % 40 != 0) {
			return;
		}
		Nation nation = npc.nation();
		BlockEntity be = level.getBlockEntity(facilityPos);
		if (nation == null || !(be instanceof FacilityBlockEntity facility)) {
			facilityPos = null;
			return;
		}
		// 人手による生産ブースト（施設種別ごとの資源）
		ResourceType type = switch (facility.facility()) {
			case BARRACKS -> ResourceType.METAL;
			case GUILD -> ResourceType.LUXURY;
			case MAGIC_LAB -> ResourceType.ARCANE;
			case WORKSHOP -> ResourceType.MACHINE;
			case FARM -> ResourceType.FOOD;
			case MARKET -> ResourceType.LUXURY;
		};
		double amount = 0.05D + npc.getNationRole().combatBias() * 0.05D;
		nation.addStock(type, amount);
		nation.unrest = Math.max(0.0F, nation.unrest - 0.002F);
		if (workTicks >= 400) {
			npc.markTaskDone(200);
		}
	}

	@Override
	public void stop() {
		facilityPos = null;
	}

	/** 国家の施設を近傍から探す（未ロード・他国施設は対象外）。 */
	@Nullable
	private BlockPos findFacility() {
		ServerLevel level = npc.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		if (level == null) {
			return null;
		}
		Nation nation = npc.nation();
		if (nation == null) {
			return null;
		}
		BlockPos origin = npc.home() != null ? npc.home() : npc.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		int radius = 32;
		for (int dx = -radius; dx <= radius; dx += 4) {
			for (int dz = -radius; dz <= radius; dz += 4) {
				for (int dy = -4; dy <= 4; dy += 4) {
					BlockPos candidate = origin.offset(dx, dy, dz);
					BlockEntity be = level.getBlockEntity(candidate);
					if (be instanceof FacilityBlockEntity facility && facility.nation() != null
							&& facility.nation().id.equals(nation.id)) {
						double distance = npc.distanceToSqr(candidate.getX() + 0.5D, candidate.getY() + 0.5D,
								candidate.getZ() + 0.5D);
						if (distance < bestDistance) {
							bestDistance = distance;
							best = candidate.immutable();
						}
					}
				}
			}
		}
		return best;
	}
}
