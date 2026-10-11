package com.wisdomking.world;

import com.wisdomking.block.NationalChestBlockEntity;
import com.wisdomking.core.WKLog;
import com.wisdomking.nation.Nation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全Mod対応の自動クラフタ。
 *
 * <p>サーバーの {@link net.minecraft.world.item.crafting.RecipeManager} から
 * 作業台レシピ（{@link RecipeType#CRAFTING}）を取得して判定するため、
 * バニラ・Create・工業Mod・魔法Modなど、あらゆるModのクラフトレシピが自動的に対象になる。
 * 施設（工房）ブロックが国庫金庫を入出力として素材を消費し、完成品を国庫へ納品する。</p>
 */
public final class AutoCrafter {
	/** 1回の判定で試すレシピ上限（サーバー負荷対策）。 */
	private static final int MAX_RECIPE_TESTS = 600;
	/** 同一tickで連続クラフトしないためのクールダウン。 */
	private static final Map<Long, Long> LAST_CRAFT = new LinkedHashMap<>();

	private AutoCrafter() {
	}

	/** クラフト結果。 */
	public record CraftResult(boolean success, String description) {
		public static CraftResult idle(String reason) {
			return new CraftResult(false, reason);
		}
	}

	/** 工房付近の国庫金庫を用いて1回クラフトを試みる。 */
	public static CraftResult tryCraft(ServerLevel level, BlockPos workshopPos, Nation nation) {
		long key = level.dimension().location().hashCode() * 31L + workshopPos.asLong();
		long now = level.getGameTime();
		Long last = LAST_CRAFT.get(key);
		if (last != null && now - last < 60L) {
			return CraftResult.idle("");
		}
		NationalChestBlockEntity chest = findChest(level, workshopPos);
		if (chest == null) {
			return CraftResult.idle("国庫金庫が見つかりません（施設から8ブロック以内に設置してください）。");
		}
		if (!chest.nationIdEquals(nation)) {
			return CraftResult.idle("金庫の所有国家が一致しません。");
		}
		try {
			return craft(level, chest, nation);
		} catch (Throwable t) {
			WKLog.warn("auto craft failed safely: " + t);
			return CraftResult.idle("クラフト判定を安全にスキップしました。");
		}
	}

	@Nullable
	private static NationalChestBlockEntity findChest(ServerLevel level, BlockPos pos) {
		for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-8, -3, -8), pos.offset(8, 3, 8))) {
			if (level.getBlockEntity(candidate) instanceof NationalChestBlockEntity chest) {
				return chest;
			}
		}
		return null;
	}

	private static CraftResult craft(ServerLevel level, NationalChestBlockEntity chest, Nation nation) {
		// 国庫から素材候補を集める（種類ごとに1個ずつ、最大9種）
		List<Integer> sourceSlots = new ArrayList<>();
		List<ItemStack> candidates = new ArrayList<>();
		for (int i = 0; i < chest.getContainerSize() && candidates.size() < 9; i++) {
			ItemStack stack = chest.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			sourceSlots.add(i);
			candidates.add(stack.copyWithCount(1));
		}
		if (candidates.size() < 2) {
			return CraftResult.idle("素材不足（国庫に2種類以上の素材が必要です）。");
		}

		// 候補素材から 3x3 グリッドの組み合わせを生成（1x1 ～ 3x3 の形を試す）
		var recipeManager = level.getRecipeManager();
		List<RecipeHolder<CraftingRecipe>> recipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
		int tested = 0;
		ItemStack[] grid = new ItemStack[9];
		for (int i = 0; i < 9; i++) {
			grid[i] = ItemStack.EMPTY;
		}
		// まず「同じ素材を最大数並べる」「異なる素材を並べる」の2パターンを試す
		List<List<ItemStack>> patterns = new ArrayList<>();
		patterns.add(candidates);
		List<ItemStack> filled = new ArrayList<>();
		for (ItemStack candidate : candidates) {
			ItemStack full = candidate.copy();
			full.setCount(Math.min(full.getMaxStackSize(), 9));
			filled.add(full);
		}
		patterns.add(filled);

		for (List<ItemStack> pattern : patterns) {
			for (int i = 0; i < grid.length; i++) {
				grid[i] = i < pattern.size() ? pattern.get(i) : ItemStack.EMPTY;
			}
			CraftingInput input = CraftingInput.of(3, 3, List.of(grid));
			for (RecipeHolder<CraftingRecipe> holder : recipes) {
				if (tested++ > MAX_RECIPE_TESTS) {
					return CraftResult.idle("レシピ探索上限に達しました。");
				}
				CraftingRecipe recipe = holder.value();
				try {
					if (!recipe.matches(input, level)) {
						continue;
					}
					ItemStack result = recipe.assemble(input, level.registryAccess());
					if (result.isEmpty()) {
						continue;
					}
					// 素材を消費して成果物を国庫へ納品する
					if (!consume(chest, grid)) {
						continue;
					}
					ItemStack remaining = chest.deposit(result.copy());
					String description = result.getCount() + " × " + result.getHoverName().getString();
					if (!remaining.isEmpty()) {
						WKLog.warn("auto craft output overflow: " + description);
					}
					LAST_CRAFT.put(level.dimension().location().hashCode() * 31L + chest.getBlockPos().asLong(),
							level.getGameTime());
					return new CraftResult(true, description + " を生産（レシピ: " + holder.id() + "）");
				} catch (Throwable t) {
					// 個別レシピの失敗は無視して次を試す（他Modの特殊レシピ対策）
				}
			}
		}
		return CraftResult.idle("現在の国庫素材で作成可能なレシピがありません。");
	}

	/** グリッド1枚分の素材を金庫から消費する。 */
	private static boolean consume(NationalChestBlockEntity chest, ItemStack[] grid) {
		// 必要数の集計
		Map<ItemStack, Integer> needs = new LinkedHashMap<>();
		for (ItemStack stack : grid) {
			if (stack.isEmpty()) {
				continue;
			}
			boolean merged = false;
			for (Map.Entry<ItemStack, Integer> entry : needs.entrySet()) {
				if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
					entry.setValue(entry.getValue() + 1);
					merged = true;
					break;
				}
			}
			if (!merged) {
				needs.put(stack.copyWithCount(1), 1);
			}
		}
		// 在庫確認
		for (Map.Entry<ItemStack, Integer> need : needs.entrySet()) {
			int available = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack stack = chest.getItem(i);
				if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, need.getKey())) {
					available += stack.getCount();
				}
			}
			if (available < need.getValue()) {
				return false;
			}
		}
		// 消費
		for (Map.Entry<ItemStack, Integer> need : needs.entrySet()) {
			int remaining = need.getValue();
			for (int i = 0; i < chest.getContainerSize() && remaining > 0; i++) {
				ItemStack stack = chest.getItem(i);
				if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, need.getKey())) {
					continue;
				}
				int take = Math.min(remaining, stack.getCount());
				stack.shrink(take);
				if (stack.isEmpty()) {
					chest.setItem(i, ItemStack.EMPTY);
				}
				remaining -= take;
			}
		}
		chest.setChanged();
		return true;
	}

	/** クラフト可能レシピの総数（Raphael 報告用）。 */
	public static int recipeCount(ServerLevel level) {
		try {
			return level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).size();
		} catch (Throwable t) {
			return 0;
		}
	}

	/** 対応するModの識別子（レシピ名の名前空間から判定）。 */
	public static String modOfRecipe(RecipeHolder<?> holder) {
		return holder.id().getNamespace();
	}

	/** サーバー停止時のクリア。 */
	public static void reset() {
		LAST_CRAFT.clear();
	}

}
