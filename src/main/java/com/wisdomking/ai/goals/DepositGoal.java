package com.wisdomking.ai.goals;

import com.wisdomking.block.NationalChestBlockEntity;
import com.wisdomking.entity.WKNpcEntity;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationRole;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * 納税・納品 Goal（市民・商人・職人）。
 *
 * <p>国庫金庫（National Chest）へ移動し、税収・交易益を国家資金として納める。
 * 国家の資金が増えると兵站（装備配布・土木予算）が回り始める。</p>
 */
public class DepositGoal extends Goal {
	private final WKNpcEntity npc;
	@Nullable
	private BlockPos chestPos;

	public DepositGoal(WKNpcEntity npc) {
		this.npc = npc;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!(npc.level() instanceof ServerLevel) || !npc.taskReady() || npc.getTarget() != null) {
			return false;
		}
		NationRole role = npc.getNationRole();
		if (role.isMilitary() || role == NationRole.MAGE) {
			return false;
		}
		Nation nation = npc.nation();
		if (nation == null) {
			return false;
		}
		chestPos = findChest();
		return chestPos != null;
	}

	@Override
	public boolean canContinueToUse() {
		return chestPos != null && npc.taskReady();
	}

	@Override
	public void tick() {
		if (chestPos == null || !(npc.level() instanceof ServerLevel level)) {
			return;
		}
		if (npc.distanceToSqr(chestPos.getX() + 0.5D, chestPos.getY() + 0.5D, chestPos.getZ() + 0.5D) > 9.0D) {
			npc.getNavigation().moveTo(chestPos.getX() + 0.5D, chestPos.getY(), chestPos.getZ() + 0.5D, 0.9D);
			return;
		}
		Nation nation = npc.nation();
		if (nation == null) {
			chestPos = null;
			return;
		}
		double rate = 0.4D + npc.getNationRole().combatBias() * 0.6D;
		nation.treasury += rate;
		nation.addStock(com.wisdomking.nation.ResourceType.PRECIOUS, 0.01D);
		BlockEntity be = level.getBlockEntity(chestPos);
		if (be instanceof NationalChestBlockEntity chest) {
			chest.setChanged();
		}
		npc.markTaskDone(600);
		chestPos = null;
	}

	@Override
	public void stop() {
		chestPos = null;
	}

	@Nullable
	private BlockPos findChest() {
		if (!(npc.level() instanceof ServerLevel level)) {
			return null;
		}
		Nation nation = npc.nation();
		if (nation == null) {
			return null;
		}
		BlockPos origin = npc.home() != null ? npc.home() : npc.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		int radius = 24;
		for (int dx = -radius; dx <= radius; dx += 4) {
			for (int dz = -radius; dz <= radius; dz += 4) {
				for (int dy = -3; dy <= 6; dy += 3) {
					BlockPos candidate = origin.offset(dx, dy, dz);
					BlockEntity be = level.getBlockEntity(candidate);
					if (be instanceof NationalChestBlockEntity chest && chest.nation() != null
							&& chest.nation().id.equals(nation.id)) {
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
