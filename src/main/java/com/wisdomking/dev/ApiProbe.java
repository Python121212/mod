package com.wisdomking.dev;

import com.wisdomking.WK;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 開発用 API 検証ファイル（一時的）。
 *
 * <p>1.21.1 の公式マッピングにおける API シグネチャを1回のビルドで検証するための
 * プローブである。実装が安定した時点で削除する。</p>
 */
public final class ApiProbe {
	private ApiProbe() {
	}

	/** エンティティ型の登録と属性登録。 */
	public static EntityType<ProbeMob> probeEntityType() {
		EntityType<ProbeMob> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, WK.id("api_probe"),
				EntityType.Builder.of(ProbeMob::new, MobCategory.CREATURE)
						.sized(0.6f, 1.95f)
						.clientTrackingRange(10)
						.build("api_probe"));
		FabricDefaultAttributeRegistry.register(type, ProbeMob.createAttributes());
		return type;
	}

	/** 属性ビルダー。 */
	public static AttributeSupplier.Builder probeAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.ARMOR, 1.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	/** Goal 群（AI）の生成と登録。 */
	public static void probeGoals(ProbeMob mob) {
		mob.goalSelector.addGoal(0, new FloatGoal(mob));
		mob.goalSelector.addGoal(2, new MeleeAttackGoal(mob, 1.1, true));
		mob.goalSelector.addGoal(3, new RangedAttackGoal(mob, 1.0, 30, 12.0f));
		mob.goalSelector.addGoal(6, new RandomStrollGoal(mob, 0.8));
		mob.goalSelector.addGoal(7, new LookAtPlayerGoal(mob, Player.class, 8.0f));
		mob.targetSelector.addGoal(1, new HurtByTargetGoal(mob));
		mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
	}

	/** エンティティ生成・装備・移動などの基本操作。 */
	public static void probeEntityOps(ServerLevel level, ProbeMob mob) {
		Entity spawned = mob.getType().create(level);
		if (spawned != null) {
			spawned.setPos(mob.getX(), mob.getY(), mob.getZ());
			level.addFreshEntity(spawned);
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(true);
		mob.setCustomName(Component.literal("probe"));
		mob.setCustomNameVisible(true);
		mob.getNavigation().moveTo(mob.getX() + 4, mob.getY(), mob.getZ(), 1.0);
		mob.getLookControl().setLookAt(mob.getX() + 1, mob.getY(), mob.getZ());
		double value = mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(value + 1.0);
		EquipmentSlot.Type slotType = EquipmentSlot.CHEST.getType();
		boolean humanoid = slotType == EquipmentSlot.Type.HUMANOID_ARMOR;
		ResourceLocation dimension = level.dimension().location();
		AABB box = AABB.ofSize(mob.position(), 8.0, 4.0, 8.0);
		int nearby = level.getEntitiesOfClass(ProbeMob.class, box).size();
		WK.LOG.debug("probe {} {} {} {} {}", value, humanoid, dimension, nearby, mob.getBbHeight());
	}

	/** ブロックエンティティ・コンテナ・NBT 系。 */
	public static void probeContainer(Container container) {
		SimpleContainer simple = new SimpleContainer(27);
		ItemStack stack = simple.getItem(0);
		stack.getMaxStackSize();
		simple.setChanged();
		container.getContainerSize();
	}

	/** レシピ・クラフト系。 */
	public static void probeRecipes(ServerLevel level) {
		var recipes = level.getRecipeManager()
				.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING);
		for (var holder : recipes) {
			var recipe = holder.value();
			var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3,
					java.util.List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
							ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
							ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
			if (recipe.matches(input, level)) {
				ItemStack result = recipe.assemble(input, level.registryAccess());
				WK.LOG.debug("probe recipe {} -> {}", holder.id(), result);
			}
		}
	}

	/** 進捗（Advancement）の付与。 */
	public static void probeAdvancement(ServerPlayer player) {
		var holder = player.server.getAdvancements().get(WK.id("root"));
		if (holder != null) {
			player.getAdvancements().award(holder, "manual");
		}
	}

	/** メニュー（GUI）型の登録。 */
	public static void probeMenuType() {
		net.minecraft.world.inventory.MenuType<net.minecraft.world.inventory.AbstractContainerMenu> type =
				new net.minecraft.world.inventory.MenuType<>((syncId, inventory) -> null,
						net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
		Registry.register(BuiltInRegistries.MENU, WK.id("api_probe_menu"), type);
	}

	/** ブロック座標・チャンク関連。 */
	public static void probePositions(ServerLevel level, BlockPos pos) {
		var chunkPos = new net.minecraft.world.level.ChunkPos(pos);
		long key = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
		var chunk = level.getChunk(chunkPos.x, chunkPos.z);
		int blockEntities = chunk.getBlockEntities().size();
		boolean loaded = level.hasChunk(chunkPos.x, chunkPos.z);
		int height = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
				pos.getX(), pos.getZ());
		var holder = level.getBiome(pos);
		boolean forest = holder.is(net.minecraft.tags.BiomeTags.IS_FOREST);
		level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
		level.removeBlock(pos, false);
		WK.LOG.debug("probe pos {} {} {} {} {}", key, blockEntities, loaded, height, forest);
	}

	/** アイテム・属性・エンチャント。 */
	public static void probeItems(ItemStack stack, LivingEntity entity) {
		var modifiers = stack.getAttributeModifiers();
		for (var entry : modifiers.modifiers()) {
			var attribute = entry.attribute();
			var modifier = entry.modifier();
			double amount = modifier.amount();
			var operation = modifier.operation();
			WK.LOG.debug("probe attr {} {} {}", attribute, amount, operation);
		}
		for (var entry : stack.getEnchantments().entrySet()) {
			WK.LOG.debug("probe enchant {} {}", entry.getKey(), entry.getValue());
		}
		stack.hurtAndBreak(1, entity, EquipmentSlot.MAINHAND);
		var food = stack.getFoodProperties(null);
		var rarity = stack.getRarity();
		int maxDamage = stack.getMaxDamage();
		var hover = stack.getHoverName();
		stack.getTags().forEach(tag -> WK.LOG.debug("tag {}", tag.location()));
		WK.LOG.debug("probe item {} {} {} {}", food, rarity, maxDamage, hover);
	}

	/** ダメージソース関連。 */
	public static void probeDamage(Entity entity) {
		var source = entity.damageSources().generic();
		boolean drowning = source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING);
		Entity attacker = source.getEntity();
		WK.LOG.debug("probe damage {} {} {}", drowning, attacker, source.type());
	}

	/** 検証用のダミーMob。 */
	public static class ProbeMob extends PathfinderMob implements RangedAttackMob {
		public ProbeMob(EntityType<? extends ProbeMob> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder createAttributes() {
			return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
		}

		@Override
		public void performRangedAttack(LivingEntity target, float velocity) {
			// 検証用: 実際の発射処理は実装側で行う
		}

		@Override
		public boolean canAttack(LivingEntity target) {
			return target instanceof Player && super.canAttack(target);
		}

		public void probeHands() {
			setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}

		public void probeSpawn(ServerLevel level) {
			// 自然スポーン判定用のフック
			finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()),
					net.minecraft.world.entity.MobSpawnType.NATURAL, null);
		}
	}
}
