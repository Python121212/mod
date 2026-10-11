package com.wisdomking.block;

import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.ResourceType;
import com.wisdomking.world.ResourceMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 国庫金庫（ナショナル・チェスト）のブロックエンティティ。
 *
 * <p>国家の備蓄拠点。NPCが生産物を自動納品し、兵士が装備を自動選択して持ち出す。
 * 資源カテゴリごとに内容を集計し、国家経済（{@link Nation#stock}）へ反映する。</p>
 */
public class NationalChestBlockEntity extends NationBlockEntity implements Container {
	public static final int SLOTS = 27;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private long lastDepositTick;

	public NationalChestBlockEntity(BlockPos pos, BlockState state) {
		super(com.wisdomking.core.WKBlockEntities.NATIONAL_CHEST, pos, state);
	}

	/** NPCやホッパーからの納品を受け入れる（資源として国家備蓄へ計上）。 */
	public boolean deposit(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		ItemStack remaining = stack.copy();
		for (int i = 0; i < SLOTS && !remaining.isEmpty(); i++) {
			ItemStack slot = items.get(i);
			if (slot.isEmpty()) {
				items.set(i, remaining.copy());
				remaining = ItemStack.EMPTY;
			} else if (ItemStack.isSameItemSameComponents(slot, remaining)) {
				int space = slot.getMaxStackSize() - slot.getCount();
				int move = Math.min(space, remaining.getCount());
				slot.grow(move);
				remaining.shrink(move);
			}
		}
		if (remaining.getCount() != stack.getCount()) {
			recordResource(stack.copyWithCount(stack.getCount() - remaining.getCount()));
			setChanged();
			return true;
		}
		return false;
	}

	/** 国庫に納品されたアイテムを国家備蓄（資源）へ反映する。 */
	private void recordResource(ItemStack deposited) {
		Nation nation = nation();
		if (nation == null) {
			return;
		}
		ResourceType type = ResourceMapper.resourceFor(deposited);
		if (type != null) {
			nation.addStock(type, ResourceMapper.valueOf(deposited, type));
			NationManager manager = NationManager.get();
			if (manager != null) {
				manager.markDirty();
			}
		}
	}

	/** 国家備蓄から資源を引き出す（建設・交易の支払い）。 */
	public boolean withdrawResource(ResourceType type, double amount) {
		Nation nation = nation();
		if (nation == null) {
			return false;
		}
		return nation.consume(type, amount);
	}

	/** 指定国家の所有物かどうか。 */
	public boolean nationIdEquals(com.wisdomking.nation.Nation nation) {
		if (nation == null) {
			return true;
		}
		return nationId == null || nationId.equals(nation.id);
	}

	/** 在庫のうち戦闘装備を抽出する（装備配分AIが使用）。 */
	public ItemStack extractBestCombatItem(net.minecraft.world.entity.EquipmentSlot slot, double minScore) {
		ItemStack best = ItemStack.EMPTY;
		int bestIndex = -1;
		double bestScore = minScore;
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			com.wisdomking.equipment.CombatRole role = com.wisdomking.equipment.ItemClassifier.classify(stack);
			if (com.wisdomking.equipment.ItemClassifier.slotFor(role) != slot) {
				continue;
			}
			double score = com.wisdomking.equipment.EquipmentScorer.score(stack, role);
			if (score > bestScore) {
				bestScore = score;
				best = stack;
				bestIndex = i;
			}
		}
		if (bestIndex >= 0) {
			ItemStack result = best.copy();
			items.set(bestIndex, ItemStack.EMPTY);
			setChanged();
			return result;
		}
		return ItemStack.EMPTY;
	}

	/** 内容の資源サマリ。 */
	public String summarize() {
		int used = 0;
		double value = 0.0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				used++;
				ResourceType type = ResourceMapper.resourceFor(stack);
				if (type != null) {
					value += ResourceMapper.valueOf(stack, type);
				}
			}
		}
		return String.format("%d/%d スロット / 資源換算 %.1f", used, SLOTS, value);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, NationalChestBlockEntity be) {
		long time = level.getGameTime();
		if (time % 200L != 0L) {
			return;
		}
		be.lastDepositTick = time;
	}

	// --- Container 実装 ---

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = ContainerHelper.removeItem(items, slot, amount);
		if (!result.isEmpty()) {
			setChanged();
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return level != null && level.getBlockEntity(worldPosition) == this
				&& player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
				worldPosition.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clearContent() {
		items.clear();
		setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return true;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		tag.putLong("LastDeposit", lastDepositTick);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		ContainerHelper.loadAllItems(tag, items, registries);
		lastDepositTick = tag.getLong("LastDeposit");
	}

	@Override
	public CompoundTag describeForHud() {
		CompoundTag tag = super.describeForHud();
		tag.putBoolean("NationalChest", true);
		tag.putString("Contents", summarize());
		return tag;
	}

	@Override
	public void setChanged() {
		super.setChanged();
	}
}
