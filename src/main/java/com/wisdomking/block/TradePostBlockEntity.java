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

import java.util.ArrayList;
import java.util.List;

/**
 * 貿易ポスト（市場ターミナル）のブロックエンティティ。
 *
 * <p>他国や商人NPCが訪れ、全自動でアイテム交易を行う。市場に持ち込まれたアイテムは
 * 資源カテゴリへ換算され、不足資源は購入、余剰資源は売却されて国庫（金）が増減する。
 * 交易のたびに相手国との関係値が改善するため、外交の実利拠点となる。</p>
 */
public class TradePostBlockEntity extends NationBlockEntity implements Container {
	public static final int SLOTS = 9;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private final List<String> tradeLog = new ArrayList<>();
	private double profitAccumulated;
	private int tradeCount;

	public TradePostBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, TradePostBlockEntity be) {
		long time = level.getGameTime();
		if (time % 200L != 0L) {
			return;
		}
		be.tradeTick();
	}

	/** 自動交易の1サイクル。 */
	public void tradeTick() {
		Nation nation = nation();
		NationManager manager = NationManager.get();
		if (nation == null || manager == null) {
			return;
		}
		// 1. 市場在庫を資源へ換算
		double income = 0.0;
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			ResourceType type = ResourceMapper.resourceFor(stack);
			if (type == null) {
				continue;
			}
			double value = ResourceMapper.valueOf(stack, type);
			nation.addStock(type, value * 0.5);
			nation.treasury += value * 0.25;
			income += value * 0.75;
			items.set(i, ItemStack.EMPTY);
		}

		// 2. 余剰資源の売却と不足資源の購入
		for (ResourceType type : ResourceType.values()) {
			double deficit = manager.economy().deficit(nation, type);
			double stock = nation.stock(type);
			double price = manager.economy().price(type);
			if (deficit > 0.5 && nation.treasury > price * 8.0) {
				double buy = Math.min(24.0, nation.treasury / Math.max(0.5, price));
				nation.addStock(type, buy);
				nation.treasury -= buy * price;
				income -= buy * price;
			} else if (stock > 120.0) {
				double sell = (stock - 100.0) * 0.4;
				nation.addStock(type, -sell);
				nation.treasury += sell * price;
				income += sell * price;
			}
		}

		// 3. 交易相手の選定と関係改善
		Nation partner = pickPartner(manager, nation);
		if (partner != null) {
			double bonus = 2.5 * nation.ideology.tradeAptitude();
			nation.treasury += bonus;
			nation.relation(partner.id).add(0.8f);
			partner.relation(nation.id).add(0.8f);
			log(String.format("取引: %s と交易成立（収益 %.1f）", partner.name, bonus));
			income += bonus;
		}

		if (income != 0.0) {
			profitAccumulated += income;
			tradeCount++;
			setChanged();
			manager.markDirty();
		}
	}

	private Nation pickPartner(NationManager manager, Nation nation) {
		Nation best = null;
		float bestValue = -1000.0f;
		for (Nation other : manager.aliveNations()) {
			if (other.id.equals(nation.id) || nation.atWarWith(other.id)) {
				continue;
			}
			float value = nation.relation(other.id).value + (float) manager.economy().complementarity(nation, other) * 12.0f;
			if (value > bestValue) {
				bestValue = value;
				best = other;
			}
		}
		return best;
	}

	private void log(String entry) {
		tradeLog.add(entry);
		if (tradeLog.size() > 12) {
			tradeLog.remove(0);
		}
	}

	/** プレイヤー・NPCが持ち込んだアイテムを市場在庫へ入れる。 */
	public boolean depositFrom(ItemStack stack) {
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
			setChanged();
			return true;
		}
		return false;
	}

	public List<String> tradeLog() {
		return new ArrayList<>(tradeLog);
	}

	public double profitAccumulated() {
		return profitAccumulated;
	}

	public int tradeCount() {
		return tradeCount;
	}

	// --- Container ---

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
		setChanged();
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
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		tag.putDouble("Profit", profitAccumulated);
		tag.putInt("Trades", tradeCount);
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (String entry : tradeLog) {
			list.add(net.minecraft.nbt.StringTag.valueOf(entry));
		}
		tag.put("Log", list);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		ContainerHelper.loadAllItems(tag, items, registries);
		profitAccumulated = tag.getDouble("Profit");
		tradeCount = tag.getInt("Trades");
		net.minecraft.nbt.ListTag list = tag.getList("Log", 8);
		for (int i = 0; i < list.size(); i++) {
			tradeLog.add(list.getString(i));
		}
	}

	@Override
	public CompoundTag describeForHud() {
		CompoundTag tag = super.describeForHud();
		tag.putBoolean("TradePost", true);
		tag.putInt("Trades", tradeCount);
		tag.putDouble("Profit", profitAccumulated);
		return tag;
	}
}
