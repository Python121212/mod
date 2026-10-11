package com.wisdomking.entity;

import com.wisdomking.WK;
import com.wisdomking.ai.goals.BuildRoadGoal;
import com.wisdomking.ai.goals.CombatGoal;
import com.wisdomking.ai.goals.DepositGoal;
import com.wisdomking.ai.goals.PatrolGoal;
import com.wisdomking.ai.goals.WorkAtFacilityGoal;
import com.wisdomking.equipment.CombatRole;
import com.wisdomking.equipment.ItemClassifier;
import com.wisdomking.nation.INationMember;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.NpcRecord;
import com.wisdomking.nation.Personality;
import com.wisdomking.nation.WorldPos;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 自律 NPC（市民・兵士・技師・商人・魔術師）。
 *
 * <p>国家（{@link Nation}）の構成員 {@link NpcRecord} と 1:1 で対応し、
 * 装備・攻撃スタイル・役割行動（土木・生産・交易・警備）をすべて動的に決定する。
 * 装備は国庫から取り出した「その時点で最強の品」であり、武器種に応じて
 * {@link CombatRole.AttackStyle} を切り替えるため、他Modの武器でも動作する。</p>
 *
 * <p>死亡処理は {@link com.wisdomking.core.WKEvents} が {@link INationMember} 経由で行う。
 * 王の死亡時には動的王位継承（{@link com.wisdomking.nation.SuccessionEngine}）が走る。</p>
 */
public class WKNpcEntity extends PathfinderMob implements RangedAttackMob, INationMember {
	private static final String NBT_NATION = "WisdomKingNation";
	private static final String NBT_NPC = "WisdomKingNpc";
	private static final String NBT_HOME = "WisdomKingHome";
	private static final String NBT_TASK = "WisdomKingTaskTick";

	@Nullable
	private UUID nationId;
	@Nullable
	private UUID npcId;
	@Nullable
	private BlockPos home;
	private int taskCooldown;
	private CombatRole.AttackStyle attackStyle = CombatRole.AttackStyle.MELEE;

	public WKNpcEntity(EntityType<? extends WKNpcEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 2;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.30D)
				.add(Attributes.ATTACK_DAMAGE, 2.0D)
				.add(Attributes.ATTACK_SPEED, 3.0D)
				.add(Attributes.FOLLOW_RANGE, 32.0D)
				.add(Attributes.ARMOR, 2.0D);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new CombatGoal(this));
		this.goalSelector.addGoal(2, new BuildRoadGoal(this));
		this.goalSelector.addGoal(3, new WorkAtFacilityGoal(this));
		this.goalSelector.addGoal(4, new PatrolGoal(this));
		this.goalSelector.addGoal(5, new DepositGoal(this));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
	}

	// ------------------------------------------------------------------
	// 国家関連（INationMember 実装）
	// ------------------------------------------------------------------

	@Override
	@Nullable
	public UUID getNationId() {
		return nationId;
	}

	@Override
	public NationRole getNationRole() {
		return role();
	}

	@Nullable
	public UUID getNpcId() {
		return npcId;
	}

	@Nullable
	public Nation nation() {
		NationManager manager = NationManager.get();
		return manager == null || nationId == null ? null : manager.nation(nationId);
	}

	@Nullable
	public NpcRecord record() {
		Nation nation = nation();
		return nation == null || npcId == null ? null : nation.subject(npcId);
	}

	public NationRole role() {
		NpcRecord record = record();
		return record == null ? NationRole.CITIZEN : record.role;
	}

	public Personality personality() {
		NpcRecord record = record();
		return record == null ? Personality.LOYAL : record.personality;
	}

	@Nullable
	public BlockPos home() {
		return home;
	}

	public void setHome(@Nullable BlockPos pos) {
		this.home = pos == null ? null : pos.immutable();
	}

	/** 国家と構成員レコードを関連付ける（スポーン時・再読込時に呼ばれる）。 */
	public void bind(Nation nation, @Nullable NpcRecord record, @Nullable BlockPos homePos) {
		this.nationId = nation.id;
		this.npcId = record == null ? null : record.id;
		if (homePos != null) {
			this.home = homePos.immutable();
		}
		this.applyRoleAttributes();
		String name = record == null ? "住人" : record.name;
		this.setCustomName(Component.literal(name + " §7[" + role().key() + "]"));
		this.setCustomNameVisible(false);
	}

	private void applyRoleAttributes() {
		NationRole role = role();
		try {
			double speed = 0.28D + role.combatBias() * 0.05D;
			if (getAttribute(Attributes.MOVEMENT_SPEED) != null) {
				getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
			}
			if (getAttribute(Attributes.ATTACK_DAMAGE) != null) {
				getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D + role.combatBias() * 2.5D);
			}
			if (getAttribute(Attributes.MAX_HEALTH) != null) {
				double health = 16.0D + role.combatBias() * 8.0D;
				getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
				setHealth((float) health);
			}
			if (getAttribute(Attributes.ARMOR) != null) {
				getAttribute(Attributes.ARMOR).setBaseValue(1.0D + role.combatBias() * 3.0D);
			}
		} catch (Throwable t) {
			WK.LOG.debug("npc attribute tuning skipped: " + t);
		}
	}

	// ------------------------------------------------------------------
	// 装備と攻撃スタイル
	// ------------------------------------------------------------------

	/** 装備から攻撃スタイルを再判定する（武器を持ち替えるたびに呼ぶ）。 */
	public void refreshAttackStyle() {
		try {
			ItemStack main = this.getMainHandItem();
			if (main.isEmpty()) {
				attackStyle = CombatRole.AttackStyle.MELEE;
				return;
			}
			attackStyle = ItemClassifier.classify(main).style();
		} catch (Throwable t) {
			attackStyle = CombatRole.AttackStyle.MELEE;
		}
	}

	public CombatRole.AttackStyle attackStyle() {
		return attackStyle;
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
		super.setItemSlot(slot, stack);
		if (slot == EquipmentSlot.MAINHAND) {
			refreshAttackStyle();
		}
	}

	/** 弓・クロスボウ・魔法（投射物）による攻撃。 */
	@Override
	public void performRangedAttack(LivingEntity target, float velocity) {
		try {
			ItemStack ammo = this.getProjectile(this.getMainHandItem());
			if (ammo.isEmpty()) {
				ammo = new ItemStack(Items.ARROW);
			}
			Arrow arrow = new Arrow(this.level(), this, ammo, null);
			double dx = target.getX() - this.getX();
			double dy = target.getEyeY() - arrow.getY() - 0.2D;
			double dz = target.getZ() - this.getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, 12.0F - this.level().getDifficulty().getId() * 2.0F);
			if (!this.level().isClientSide()) {
				this.level().addFreshEntity(arrow);
			}
			this.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F,
					1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
		} catch (Throwable t) {
			WK.LOG.debug("ranged attack fallback: " + t);
		}
	}

	// ------------------------------------------------------------------
	// 毎tickの役割行動
	// ------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		if (taskCooldown > 0) {
			taskCooldown--;
		}
		if (this.tickCount % 200 == 0) {
			scanForEnemyTarget();
			refreshAttackStyle();
		}
		if (this.tickCount % 600 == 0 && home != null && this.distanceToSqr(
				home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D) > 6400.0D) {
			this.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
		}
	}

	/** 敵対国家の兵・プレイヤーを索敵してターゲットに設定する（交戦中の軍人のみ）。 */
	private void scanForEnemyTarget() {
		try {
			Nation myNation = nation();
			if (myNation == null || !role().isMilitary()) {
				return;
			}
			Player nearest = this.level().getNearestPlayer(this, 24.0D);
			if (nearest != null) {
				Nation other = NationManager.get() == null ? null
						: NationManager.get().nationOfPlayer(nearest.getUUID());
				boolean hostile = other != null && !other.id.equals(myNation.id) && myNation.atWarWith(other.id);
				if (!hostile || !this.canAttack(nearest)) {
					nearest = null;
				}
			}
			if (nearest != null) {
				this.setTarget(nearest);
				return;
			}
			for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(20.0D))) {
				if (entity instanceof WKNpcEntity other && other != this && this.canAttack(other)) {
					Nation otherNation = other.nation();
					if (otherNation != null && myNation.atWarWith(otherNation.id)) {
						this.setTarget(other);
						return;
					}
				}
			}
		} catch (Throwable t) {
			WK.LOG.debug("npc target scan skipped: " + t);
		}
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor levelAccessor, DifficultyInstance difficulty,
			MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
		this.setPersistenceRequired();
		return super.finalizeSpawn(levelAccessor, difficulty, spawnType, spawnGroupData);
	}

	/** 装備は国庫所有のため、死亡時に世界へドロップしない（国家資産の保全）。 */
	@Override
	protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,
			net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		try {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND
						|| slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
					this.setItemSlot(slot, ItemStack.EMPTY);
				}
			}
		} catch (Throwable t) {
			WK.LOG.debug("npc loot cleanup skipped: " + t);
		}
	}

	// ------------------------------------------------------------------
	// 役割行動の管理
	// ------------------------------------------------------------------

	public void setTaskCooldown(int ticks) {
		this.taskCooldown = Math.max(0, ticks);
	}

	public boolean taskReady() {
		return taskCooldown <= 0;
	}

	public void markTaskDone(int cooldownTicks) {
		this.taskCooldown = Math.max(0, cooldownTicks);
	}

	@Nullable
	public WorldPos worldPos() {
		return WorldPos.of(this);
	}

	// ------------------------------------------------------------------
	// 永続化
	// ------------------------------------------------------------------

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (nationId != null) {
			tag.putUUID(NBT_NATION, nationId);
		}
		if (npcId != null) {
			tag.putUUID(NBT_NPC, npcId);
		}
		if (home != null) {
			tag.putLong(NBT_HOME, home.asLong());
		}
		tag.putInt(NBT_TASK, taskCooldown);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		try {
			nationId = tag.hasUUID(NBT_NATION) ? tag.getUUID(NBT_NATION) : null;
			npcId = tag.hasUUID(NBT_NPC) ? tag.getUUID(NBT_NPC) : null;
			home = tag.contains(NBT_HOME) ? BlockPos.of(tag.getLong(NBT_HOME)) : null;
			taskCooldown = tag.getInt(NBT_TASK);
			this.setPersistenceRequired();
			this.applyRoleAttributes();
		} catch (Throwable t) {
			WK.LOG.warn("npc load failed: " + t);
		}
	}
}
