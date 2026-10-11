package com.wisdomking.block;

import com.wisdomking.core.WKLog;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationFactory;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.NpcRecord;
import com.wisdomking.nation.Personality;
import com.wisdomking.nation.ResourceType;
import com.wisdomking.raphael.RaphaelRelay;
import com.wisdomking.world.AutoCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 施設指示ブロックのブロックエンティティ。
 *
 * <p>施設種別ごとに国家への効果を発揮する:</p>
 * <ul>
 *   <li>兵舎 (BARRACKS): 兵士の徴兵・装備配分</li>
 *   <li>ギルド (GUILD): 市民の転職（工兵・商人・職人・学者）</li>
 *   <li>魔法研究所 (MAGIC_LAB): 秘術資源・魔法Mod連携のスクロール生産</li>
 *   <li>工房 (WORKSHOP): 国庫を入出力としたレシピ自動クラフト（バニラ・全Mod対応）</li>
 *   <li>倉庫 (WAREHOUSE): 備蓄拡張・納品加速</li>
 *   <li>市場 (MARKET): 交易収入の増加</li>
 * </ul>
 */
public class FacilityBlockEntity extends NationBlockEntity {
	private FacilityType facility = FacilityType.WORKSHOP;
	private long lastWorkTick;
	private int producedCount;
	private String lastCrafted = "-";
	/** 直近の施設ログ（GUI・Raphael 報告用）。 */
	private final List<String> log = new ArrayList<>();

	public FacilityBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public void setFacility(FacilityType type) {
		this.facility = type;
		setChanged();
	}

	public FacilityType facility() {
		return facility;
	}

	public int producedCount() {
		return producedCount;
	}

	public String lastCrafted() {
		return lastCrafted;
	}

	public List<String> logLines() {
		return new ArrayList<>(log);
	}

	private void log(String entry) {
		log.add(entry);
		if (log.size() > 10) {
			log.remove(0);
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FacilityBlockEntity be) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		long time = serverLevel.getGameTime();
		long interval = be.facility == FacilityType.WORKSHOP ? 100L : 600L;
		if (time - be.lastWorkTick < interval) {
			return;
		}
		be.lastWorkTick = time;
		be.work(serverLevel);
	}

	/** 施設効果の1サイクル。 */
	private void work(ServerLevel level) {
		NationManager manager = NationManager.get();
		Nation nation = nation();
		if (manager == null || nation == null) {
			return;
		}
		switch (facility) {
			case BARRACKS -> {
				if (nation.armySize() < 8 + nation.claimedChunks.size()) {
					NpcRecord soldier = new NpcRecord(java.util.UUID.randomUUID(),
							NationFactory.randomPersonName(manager.random()), NationRole.SOLDIER,
							Personality.random(manager.random()));
					soldier.power = 1.0f;
					nation.addSubject(soldier);
					producedCount++;
					log("徴兵: " + soldier.name + " が入隊。");
					manager.markDirty();
				}
			}
			case GUILD -> {
				NpcRecord citizen = null;
				for (NpcRecord record : nation.subjects()) {
					if (record.alive && record.role == NationRole.CITIZEN) {
						citizen = record;
						break;
					}
				}
				if (citizen != null) {
					NationRole target = switch (manager.random().nextInt(4)) {
						case 0 -> NationRole.ENGINEER;
						case 1 -> NationRole.MERCHANT;
						case 2 -> NationRole.CRAFTSMAN;
						default -> NationRole.SCHOLAR;
					};
					citizen.role = target;
					producedCount++;
					log("転職: " + citizen.name + " が " + target.name() + " に転向。");
					manager.markDirty();
				}
			}
			case MAGIC_LAB -> {
				nation.addStock(ResourceType.ARCANE, facility.outputRate() * 12.0);
				nation.technology = (float) Math.min(1.0, nation.technology + 0.0008);
				producedCount++;
				log("研究: 秘術資源を精製（技術水準 " + Math.round(nation.technology * 100) + "%）。");
				manager.markDirty();
			}
			case WORKSHOP -> {
				AutoCrafter.CraftResult result = AutoCrafter.tryCraft(level, worldPosition, nation);
				if (result.success()) {
					producedCount++;
					lastCrafted = result.description();
					log("製作物: " + result.description());
				} else if (!result.description().isEmpty() && timeCheck(level)) {
					lastCrafted = result.description();
				}
			}
			case WAREHOUSE -> {
				nation.addStock(ResourceType.STONE, facility.outputRate() * 8.0);
				nation.addStock(ResourceType.MACHINE, facility.outputRate() * 4.0);
				producedCount++;
			}
			case MARKET -> {
				double income = facility.outputRate() * nation.population() * nation.ideology.tradeAptitude();
				nation.treasury += income;
				producedCount++;
				log(String.format("市場収入: +%.1f", income));
				manager.markDirty();
			}
		}
		setChanged();
	}

	private boolean timeCheck(ServerLevel level) {
		return level.getGameTime() % 1200L == 0L;
	}

	/** 周辺の国庫金庫を探す（自動クラフトの入出力）。 */
	@org.jetbrains.annotations.Nullable
	public NationalChestBlockEntity findChest() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-8, -3, -8), worldPosition.offset(8, 3, 8))) {
			if (serverLevel.getBlockEntity(pos) instanceof NationalChestBlockEntity chest) {
				Nation chestNation = chest.nation();
				if (chestNation == null || nationId == null || chestNation.id.equals(nationId)) {
					return chest;
				}
			}
		}
		return null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString("Facility", facility.name());
		tag.putLong("LastWork", lastWorkTick);
		tag.putInt("Produced", producedCount);
		tag.putString("LastCrafted", lastCrafted);
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (String entry : log) {
			list.add(net.minecraft.nbt.StringTag.valueOf(entry));
		}
		tag.put("Log", list);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		facility = FacilityType.byName(tag.getString("Facility"), FacilityType.WORKSHOP);
		lastWorkTick = tag.getLong("LastWork");
		producedCount = tag.getInt("Produced");
		lastCrafted = tag.getString("LastCrafted");
		net.minecraft.nbt.ListTag list = tag.getList("Log", 8);
		for (int i = 0; i < list.size(); i++) {
			log.add(list.getString(i));
		}
	}

	@Override
	public void onLoad() {
		super.onLoad();
		WKLog.safely("facility onLoad", () -> {
			if (level != null && !level.isClientSide) {
				RaphaelRelay.remember(com.wisdomking.raphael.AnalysisReport.of(
						com.wisdomking.raphael.Tone.REPORT,
						"施設 " + facility.name() + " を認識しました。", com.wisdomking.raphael.Urgency.INFO,
						"座標: " + worldPosition.toShortString()));
			}
		});
	}

	@Override
	public CompoundTag describeForHud() {
		CompoundTag tag = super.describeForHud();
		tag.putBoolean("Facility", true);
		tag.putString("FacilityType", facility.name());
		tag.putInt("Produced", producedCount);
		tag.putString("LastCrafted", lastCrafted);
		return tag;
	}
}
