package com.wisdomking.ai.goals;

import com.wisdomking.entity.WKNpcEntity;
import com.wisdomking.equipment.CombatRole;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * NPC の戦闘 Goal。
 *
 * <p>装備から判定した {@link CombatRole.AttackStyle} に従い、近接（斬撃）と
 * 遠距離（射撃・魔法）を自動で切り替える。他Modの武器を装備しても
 * 分類結果（{@code ItemClassifier}）に応じて挙動が変わるのが特徴。</p>
 */
public class CombatGoal extends Goal {
	private final WKNpcEntity npc;
	private int cooldown;

	public CombatGoal(WKNpcEntity npc) {
		this.npc = npc;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = npc.getTarget();
		if (target == null || !target.isAlive() || !npc.canAttack(target)) {
			return false;
		}
		// 軍人は交戦中の敵を、非軍人は自分を攻撃した相手のみ迎撃する
		if (npc.getNationRole().isMilitary()) {
			return true;
		}
		return npc.getLastHurtByMob() == target;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = npc.getTarget();
		return target != null && target.isAlive() && npc.canAttack(target)
				&& npc.distanceToSqr(target) < 48.0D * 48.0D;
	}

	@Override
	public void stop() {
		npc.getNavigation().stop();
	}

	@Override
	public void tick() {
		LivingEntity target = npc.getTarget();
		if (target == null) {
			return;
		}
		npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double distance = npc.distanceTo(target);
		CombatRole.AttackStyle style = npc.attackStyle();
		if (style == CombatRole.AttackStyle.RANGED || style == CombatRole.AttackStyle.MAGIC) {
			if (distance < 6.0D) {
				npc.getNavigation().moveTo(npc.getX() - (target.getX() - npc.getX()),
						npc.getY(), npc.getZ() - (target.getZ() - npc.getZ()), 1.1D);
			} else if (distance > 14.0D) {
				npc.getNavigation().moveTo(target, 1.1D);
			} else {
				npc.getNavigation().stop();
			}
			if (cooldown <= 0 && distance < 20.0D) {
				npc.performRangedAttack(target, 1.0F);
				cooldown = 24 + npc.getRandom().nextInt(12);
			}
		} else {
			npc.getNavigation().moveTo(target, 1.2D);
			if (distance < 2.6D && cooldown <= 0) {
				npc.swing(InteractionHand.MAIN_HAND);
				npc.doHurtTarget(target);
				cooldown = 18;
			}
		}
		if (cooldown > 0) {
			cooldown--;
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}
}
