package com.wisdomking.nation;

import com.wisdomking.WK;
import com.wisdomking.core.WKConfig;
import com.wisdomking.core.WKLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 国家経済。生産・消費・交易・技術発展を司る。
 *
 * <p>領土のバイオーム構成から資源の生産係数を算出するため、
 * 「砂漠の国は金属に恵まれるが食料が乏しい」といった地域ごとの資源格差が自然発生し、
 * 交易と侵略の誘因となる（地政学シミュレーションの基盤）。</p>
 */
public final class EconomyManager {
	private final NationManager manager;
	/** 国家ごとの生産係数キャッシュ（領土変更時に再計算）。 */
	private final Map<UUID, EnumMap<ResourceType, Double>> productionCache = new HashMap<>();
	private final Map<UUID, Integer> cachedChunkCount = new HashMap<>();

	public EconomyManager(NationManager manager) {
		this.manager = manager;
	}

	/** 生産係数を再計算する。 */
	public void invalidate(Nation nation) {
		productionCache.remove(nation.id);
		cachedChunkCount.remove(nation.id);
	}

	public void invalidateAll() {
		productionCache.clear();
		cachedChunkCount.clear();
	}

	/** その国家の資源別生産係数。バイオーム構成に依存する。 */
	public EnumMap<ResourceType, Double> production(Nation nation) {
		EnumMap<ResourceType, Double> cached = productionCache.get(nation.id);
		if (cached != null && cachedChunkCount.getOrDefault(nation.id, -1) == nation.claimedChunks.size()) {
			return cached;
		}
		EnumMap<ResourceType, Double> factors = new EnumMap<>(ResourceType.class);
		for (ResourceType type : ResourceType.values()) {
			factors.put(type, 1.0);
		}
		ServerLevel level = overworld();
		if (level == null || nation.claimedChunks.isEmpty()) {
			productionCache.put(nation.id, factors);
			cachedChunkCount.put(nation.id, nation.claimedChunks.size());
			return factors;
		}
		List<Long> sample = new ArrayList<>(nation.claimedChunks);
		int step = Math.max(1, sample.size() / 24);
		int counted = 0;
		double[] biomeMix = new double[8]; // forest, desert, ocean, mountain, jungle, swamp, snow, plains
		for (int i = 0; i < sample.size(); i += step) {
			long key = sample.get(i);
			ChunkPos pos = new ChunkPos(key);
			int x = pos.getMinBlockX() + 8;
			int z = pos.getMinBlockZ() + 8;
			try {
				BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 64, z));
				Holder<Biome> biome = level.getBiome(surface);
				if (biome.is(BiomeTags.IS_FOREST)) {
					biomeMix[0]++;
				} else if (biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)) {
					biomeMix[1]++;
				} else if (biome.is(BiomeTags.IS_OCEAN)) {
					biomeMix[2]++;
				} else if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
					biomeMix[3]++;
				} else if (biome.is(BiomeTags.IS_JUNGLE)) {
					biomeMix[4]++;
				} else if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_SNOWY)) {
					biomeMix[5]++;
				} else {
					biomeMix[6]++;
				}
				counted++;
			} catch (Throwable t) {
				WKLog.warn("biome sampling failed: " + t);
			}
		}
		if (counted > 0) {
			for (int i = 0; i < biomeMix.length; i++) {
				biomeMix[i] /= counted;
			}
		}
		// 各資源の生産係数（バイオーム混合比の重み付き和）
		factors.put(ResourceType.FOOD, 0.6 + biomeMix[6] * 1.5 + biomeMix[0] * 0.5 + biomeMix[4] * 0.6 + biomeMix[2] * 0.8);
		factors.put(ResourceType.WOOD, 0.4 + biomeMix[0] * 2.0 + biomeMix[4] * 2.2 + biomeMix[5] * 0.9 + biomeMix[6] * 0.3);
		factors.put(ResourceType.STONE, 0.7 + biomeMix[3] * 1.7 + biomeMix[1] * 1.2 + biomeMix[6] * 0.5);
		factors.put(ResourceType.METAL, 0.3 + biomeMix[3] * 2.1 + biomeMix[1] * 1.4 + biomeMix[5] * 0.8);
		factors.put(ResourceType.PRECIOUS, 0.15 + biomeMix[1] * 2.4 + biomeMix[3] * 1.1 + biomeMix[2] * 0.5);
		factors.put(ResourceType.ARCANE, 0.25 + biomeMix[5] * 1.6 + biomeMix[4] * 1.3 + biomeMix[2] * 0.9);
		factors.put(ResourceType.MACHINE, 0.25 + biomeMix[3] * 1.2 + biomeMix[1] * 1.0 + biomeMix[0] * 0.7);
		factors.put(ResourceType.LUXURY, 0.2 + biomeMix[4] * 1.9 + biomeMix[2] * 1.2 + biomeMix[5] * 0.7);
		productionCache.put(nation.id, factors);
		cachedChunkCount.put(nation.id, nation.claimedChunks.size());
		return factors;
	}

	private ServerLevel overworld() {
		return manager.server().overworld();
	}

	/** 5秒ごとの経済tick。 */
	public void tick() {
		ManagerStats stats = new ManagerStats();
		for (Nation nation : manager.aliveNations()) {
			economyTick(nation, stats);
		}
		if (stats.resourcePoor) {
			// 資源不足の国家は侵略性を高める（外交AIが参照する）
			manager.diplomacy().noteResourcePressure();
		}
		if (manager.nationCount() == 0) {
			WKLog.warn("no nations alive; consider running /wk nation bootstrap");
		}
		if (WK.isModLoaded("create")) {
			// Create 等の機械Mod導入時は工業生産が加算される（BlockEntity 側で加算）
			stats.industrial = true;
		}
	}

	private void economyTick(Nation nation, ManagerStats stats) {
		EnumMap<ResourceType, Double> factors = production(nation);
		double workers = 0.0;
		double soldiers = 0.0;
		double scholars = 0.0;
		double engineers = 0.0;
		for (NpcRecord record : nation.subjects()) {
			switch (record.role) {
				case SOLDIER, GENERAL, MAGE -> soldiers++;
				case SCHOLAR -> scholars++;
				case ENGINEER -> engineers++;
				case CITIZEN, MERCHANT, CRAFTSMAN -> workers++;
				default -> workers += 0.5;
			}
		}
		double laborEfficiency = 0.55 + nation.technology * 0.6 + nation.legitimacy * 0.25;
		double populationFactor = Math.max(1.0, workers * 0.35 + soldiers * 0.05);

		EnumMap<ResourceType, Double> produced = new EnumMap<>(ResourceType.class);
		for (ResourceType type : ResourceType.values()) {
			double producedAmount = type.baseProduction() * factors.get(type) * populationFactor
					* laborEfficiency * (type == ResourceType.FOOD ? 1.0 : 0.65);
			if (type == ResourceType.MACHINE || type == ResourceType.ARCANE) {
				producedAmount *= 1.0 + nation.technology * 1.4;
			}
			nation.addStock(type, producedAmount);
			produced.put(type, producedAmount);
		}

		// 消費（人口 + 軍）
		double population = Math.max(1.0, nation.population() + soldiers * 0.5);
		boolean shortage = false;
		for (ResourceType type : ResourceType.values()) {
			double demand = type.perCapitaDemand() * population * 0.08;
			double available = nation.stock(type);
			if (available < demand) {
				nation.stock.put(type, 0.0);
				if (type.isEssential()) {
					shortage = true;
					nation.unrest = Math.min(1.0f, nation.unrest + 0.012f);
				}
			} else {
				nation.stock.put(type, available - demand);
			}
		}
		if (shortage) {
			stats.resourcePoor = true;
		} else {
			nation.unrest = Math.max(0.0f, nation.unrest - 0.006f);
		}

		// 国庫（税収）
		double tax = workers * 0.35 * (0.7 + nation.legitimacy * 0.6) * (1.0 + nation.ideology.tradeAptitude() * 0.3);
		nation.treasury += tax;

		// 技術発展（学者・工兵・学術理念）
		double research = (scholars * 0.35 + engineers * 0.18) * (nation.ideology == Ideology.SCHOLARSHIP ? 1.7 : 1.0);
		nation.technology = (float) Math.min(1.0, nation.technology + research * 0.00035);

		// 士気は戦争と不満に左右される
		float moraleTarget = nation.atWar() ? 0.65f : 0.85f;
		moraleTarget -= nation.unrest * 0.35f;
		nation.morale += (moraleTarget - nation.morale) * 0.05f;
		nation.morale = Math.max(0.05f, Math.min(1.0f, nation.morale));

		// 正当性（民心）は不満と王の性格で変動
		float legitimacyTarget = 0.85f - nation.unrest * 0.9f + nation.kingPersonality.loyaltyBias() * 0.08f;
		nation.legitimacy += (legitimacyTarget - nation.legitimacy) * 0.03f;
		nation.legitimacy = Math.max(0.0f, Math.min(1.0f, nation.legitimacy));

		// 過剰な資源は隣国との交易で金に換わる（交易収入）
		tradeTick(nation, produced);

		manager.markDirty();
	}

	/** 交易。余剰資源を不足資源と交換し、関係値を改善する。 */
	private void tradeTick(Nation nation, EnumMap<ResourceType, Double> produced) {
		if (nation.lastTradeTick > 0 && manager.server().getTickCount() - nation.lastTradeTick < 600) {
			return;
		}
		nation.lastTradeTick = manager.server().getTickCount();
		Nation partner = null;
		double bestScore = 0.0;
		for (Nation other : manager.aliveNations()) {
			if (other.id.equals(nation.id)) {
				continue;
			}
			Relation rel = nation.relation(other.id);
			if (rel.state == RelationState.WAR) {
				continue;
			}
			double complementarity = complementarity(nation, other);
			double score = complementarity + rel.value * 0.01;
			if (score > bestScore) {
				bestScore = score;
				partner = other;
			}
		}
		if (partner == null || bestScore < 0.4) {
			return;
		}
		// 交易成立: 双方の不足資源を補い、国庫と関係値が増える
		double volume = Math.min(30.0, 6.0 + nation.population() * 0.4) * nation.ideology.tradeAptitude();
		for (ResourceType type : ResourceType.values()) {
			double mine = nation.stock(type);
			double theirs = partner.stock(type);
			if (mine > theirs * 1.6 && mine > 25.0) {
				double amount = Math.min(volume, mine * 0.25);
				nation.consume(type, amount);
				partner.addStock(type, amount * 0.92);
				nation.treasury += amount * price(type) * 0.35;
				partner.treasury -= amount * price(type) * 0.3;
			} else if (theirs > mine * 1.6 && theirs > 25.0) {
				double amount = Math.min(volume, theirs * 0.25);
				partner.consume(type, amount);
				nation.addStock(type, amount * 0.92);
				nation.treasury -= amount * price(type) * 0.3;
				partner.treasury += amount * price(type) * 0.35;
			}
		}
		nation.relation(partner.id).add(1.6f);
		partner.relation(nation.id).add(1.6f);
		manager.markDirty();
	}

	/** 資源の不足度（0=十分、1=完全に不足）。 */
	public double deficit(Nation nation, ResourceType type) {
		double stock = nation.stock(type);
		double need = Math.max(1.0, nation.population() * type.perCapitaDemand());
		return Math.max(0.0, 1.0 - stock / need);
	}

	/** 二国間の補完性（交易の利益）。 */
	public double complementarity(Nation a, Nation b) {
		double total = 0.0;
		for (ResourceType type : ResourceType.values()) {
			total += Math.abs(deficit(a, type) - deficit(b, type));
		}
		return total / ResourceType.values().length * 2.0;
	}

	public double price(ResourceType type) {
		return switch (type) {
			case FOOD -> 1.0;
			case WOOD -> 0.9;
			case STONE -> 0.8;
			case METAL -> 2.2;
			case PRECIOUS -> 5.5;
			case ARCANE -> 3.8;
			case MACHINE -> 3.0;
			case LUXURY -> 2.6;
		};
	}

	/**
	 * 機械Mod・自動化による追加生産。
	 * 施設ブロックや Create の動力設備が稼働している国家は資源ボーナスを得る。
	 */
	public void addIndustrialProduction(Nation nation, ResourceType type, double amount, String source) {
		nation.addStock(type, amount);
		if (amount > 0.5) {
			manager.markDirty();
		}
	}

	private static final class ManagerStats {
		boolean resourcePoor;
		boolean industrial;
	}

	/** デバッグ用: 国家の生産レポート。 */
	public List<String> productionReport(Nation nation) {
		List<String> lines = new ArrayList<>();
		EnumMap<ResourceType, Double> factors = production(nation);
		for (ResourceType type : ResourceType.values()) {
			lines.add(String.format("%s: stock=%.1f factor=%.2f deficit=%.2f", type.name(),
					nation.stock(type), factors.get(type), deficit(nation, type)));
		}
		return lines;
	}

	/** バイオーム構成から「資源に恵まれた資源」を推定する（外交AI用）。 */
	public ResourceType mostAbundant(Nation nation) {
		EnumMap<ResourceType, Double> factors = production(nation);
		ResourceType best = ResourceType.FOOD;
		double bestValue = -1.0;
		for (ResourceType type : ResourceType.values()) {
			double value = factors.get(type) + nation.stock(type) / 50.0;
			if (value > bestValue) {
				bestValue = value;
				best = type;
			}
		}
		return best;
	}

	/** 国家の交易価値（他国から見た魅力）。 */
	public double tradeAttractiveness(Nation nation) {
		double value = nation.treasury * 0.1;
		for (ResourceType type : ResourceType.values()) {
			value += nation.stock(type) * price(type) * 0.05;
		}
		return value;
	}

	/** 便利: 資源ID→ResourceLocation（タグ連携用）。 */
	public static ResourceLocation resourceTag(ResourceType type) {
		return ResourceLocation.fromNamespaceAndPath("c", type.name().toLowerCase(java.util.Locale.ROOT) + "s");
	}

	/** サーバーtick数の取得。 */
	public long tickCount() {
		return manager.server().getTickCount();
	}

	/** 次元ごとの経済活動（将来拡張: ネザー・エンド交易路）。 */
	public int activeDimensions() {
		int count = 0;
		for (ServerLevel level : manager.server().getAllLevels()) {
			if (level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER) {
				count++;
			}
		}
		return count;
	}
}
