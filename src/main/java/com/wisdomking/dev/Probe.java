package com.wisdomking.dev;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * TEMPORARY compile probe (deleted before release).
 *
 * Only exists so that a single CI run validates every uncertain 1.21.1 signature used by the
 * real implementation. Nothing here is registered or executed.
 */
public final class Probe {
	private Probe() {
	}

	// ---- item registration ----
	@SuppressWarnings("unused")
	private static Item probeItem() {
		Item.Properties props = new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant();
		Item item = new Item(props) {
			@Override
			public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
				return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
			}

			@Override
			public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
				return InteractionResult.SUCCESS;
			}
		};
		return net.minecraft.core.Registry.register(BuiltInRegistries.ITEM,
				ResourceLocation.fromNamespaceAndPath("wisdomking", "probe"), item);
	}

	// ---- creative tab ----
	@SuppressWarnings("unused")
	private static CreativeModeTab probeTab() {
		return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
				.title(Component.translatable("itemGroup.wisdomking"))
				.icon(() -> new ItemStack(Items.GOLDEN_HELMET))
				.displayItems((params, output) -> output.accept(Items.GOLDEN_HELMET))
				.build();
	}

	// ---- saved data ----
	@SuppressWarnings("unused")
	private static void probeSavedData(ServerLevel level) {
		Supplier<ProbeData> ctor = ProbeData::new;
		BiFunction<CompoundTag, net.minecraft.core.HolderLookup.Provider, ProbeData> loader = ProbeData::load;
		SavedData.Factory<ProbeData> factory = new SavedData.Factory<ProbeData>(ctor, loader, null);
		ProbeData data = level.getDataStorage().computeIfAbsent(factory, "wisdomking_probe");
		data.setDirty();
	}

	/** 保存データのサンプル。 */
	public static final class ProbeData extends SavedData {
		public static ProbeData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
			return new ProbeData();
		}

		@Override
		public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
			return tag;
		}
	}

	// ---- mob / entity ----
	@SuppressWarnings("unused")
	private static EntityType<ProbeNpc> probeEntityType() {
		EntityType<ProbeNpc> type = EntityType.Builder.of(ProbeNpc::new, MobCategory.MISC)
				.sized(0.6f, 1.8f)
				.clientTrackingRange(10)
				.updateInterval(3)
				.build("wisdomking:probe_npc");
		FabricDefaultAttributeRegistry.register(type, ProbeNpc.createAttributes());
		return net.minecraft.core.Registry.register(BuiltInRegistries.ENTITY_TYPE,
				ResourceLocation.fromNamespaceAndPath("wisdomking", "probe_npc"), type);
	}

	/** 歩行型 NPC の最小実装（本実装の雛形）。 */
	public static class ProbeNpc extends PathfinderMob {
		public ProbeNpc(EntityType<? extends ProbeNpc> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder createAttributes() {
			return Mob.createMobAttributes()
					.add(Attributes.MAX_HEALTH, 20.0)
					.add(Attributes.MOVEMENT_SPEED, 0.28)
					.add(Attributes.ATTACK_DAMAGE, 3.0)
					.add(Attributes.FOLLOW_RANGE, 24.0)
					.add(Attributes.ARMOR, 2.0);
		}

		@Override
		protected void registerGoals() {
			this.goalSelector.addGoal(0, new FloatGoal(this));
			this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
			this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
			this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
			this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
			this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
			this.goalSelector.addGoal(3, new Goal() {
				@Override
				public boolean canUse() {
					return false;
				}
			});
		}

		@Override
		public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
			super.setItemSlot(slot, stack);
		}

		@Override
		public boolean hurt(DamageSource source, float amount) {
			if (source.getEntity() instanceof Player player) {
				this.setTarget(player);
			}
			return super.hurt(source, amount);
		}
	}

	// ---- block entity / container ----
	@SuppressWarnings("unused")
	private static BlockEntityType<ProbeBlockEntity> probeBeType(BaseEntityBlock block) {
		return BlockEntityType.Builder.of(ProbeBlockEntity::new, block).build(null);
	}

	/** コンテナ付きブロックエンティティの雛形。 */
	public static class ProbeBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements Container {
		private final SimpleContainer container = new SimpleContainer(27);

		public ProbeBlockEntity(BlockPos pos, BlockState state) {
			super(null, pos, state);
		}

		@Override
		public int getContainerSize() {
			return container.getContainerSize();
		}

		@Override
		public boolean isEmpty() {
			return container.isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return container.getItem(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			return container.removeItem(slot, amount);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return container.removeItemNoUpdate(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			container.setItem(slot, stack);
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			container.clearContent();
		}

		@Override
		protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
			super.saveAdditional(tag, registries);
			net.minecraft.world.ContainerHelper.saveAllItems(tag, containize(), registries);
		}

		@Override
		protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
			super.loadAdditional(tag, registries);
			net.minecraft.world.ContainerHelper.loadAllItems(tag, containize(), registries);
		}

		private net.minecraft.core.NonNullList<ItemStack> containize() {
			net.minecraft.core.NonNullList<ItemStack> list = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
			for (int i = 0; i < 27; i++) {
				list.set(i, container.getItem(i));
			}
			return list;
		}
	}

	// ---- recipes ----
	@SuppressWarnings("unused")
	private static ItemStack probeRecipe(Level level, ItemStack[] grid) {
		RecipeManager manager = level.getRecipeManager();
		CraftingInput input = CraftingInput.of(3, 3, List.of(grid));
		return manager.getRecipeFor(RecipeType.CRAFTING, input, level)
				.map(holder -> holder.value().assemble(input, level.registryAccess()))
				.orElse(ItemStack.EMPTY);
	}

	@SuppressWarnings("unused")
	private static void probeBlockEntityApis(Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof ProbeBlockEntity be) {
			CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
			Vec3 center = Vec3.atCenterOf(pos);
			BlockState state = level.getBlockState(pos);
			boolean replaceable = state.canBeReplaced();
			MapCodec<? extends BaseEntityBlock> codec = null; // 型のみ確認
			CompoundTag copy = tag.copy();
			be.loadWithComponents(copy, level.registryAccess());
			be.setChanged();
			LivingEntity living = null;
			if (living != null) {
				be.saveToItem(new ItemStack(Items.CHEST), level.registryAccess());
			}
		}
	}

	@SuppressWarnings("unused")
	private static void probeMisc(Level level, Player player, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean food = stack.get(net.minecraft.core.component.DataComponents.FOOD) != null;
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			double amount = modifier.amount();
			attribute.value().getDescriptionId();
		});
		int maxDamage = stack.getMaxDamage();
		int damage = stack.getDamageValue();
		stack.setDamageValue(damage);
		stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		stack.getTags().forEach(tagKey -> tagKey.location().toString());
		stack.getEnchantments().keySet().forEach(holder -> holder.unwrapKey().ifPresent(key -> key.location()));
		ItemInteractionResult air = ItemInteractionResult.SUCCESS;
		ItemInteractionResult air2 = ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		InteractionResult use = InteractionResult.SUCCESS;
		String name = player.getGameProfile().getName();
		Vec3 look = player.getLookAngle();
		boolean b = hit.getBlockPos() != null && use != null && air != null && air2 != null && food
				&& maxDamage >= 0 && !name.isEmpty() && look != null && b == false;
	}
}
