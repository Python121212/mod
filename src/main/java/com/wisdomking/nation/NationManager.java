package com.wisdomking.nation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wisdomking.WK;
import com.wisdomking.core.WKConfig;
import com.wisdomking.core.WKLog;
import com.wisdomking.core.WKTickScheduler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 国家運営の中枢。{@link Nation} の集合と、その上で動く各サブシステムを統括する。
 *
 * <p>サブシステム:</p>
 * <ul>
 *   <li>{@link TerritoryManager} — 領土・領域保護</li>
 *   <li>{@link EconomyManager} — 生産・消費・交易・資源格差</li>
 *   <li>{@link DiplomacyManager} — 関係値・条約・宣戦布告</li>
 *   <li>{@link WarManager} — 戦争進行・降伏判定・併合</li>
 *   <li>{@link SuccessionEngine} — 動的王位継承・クーデター・独立</li>
 * </ul>
 */
public final class NationManager {
	private static NationManager instance;

	private final MinecraftServer server;
	private final Map<UUID, Nation> nations = new LinkedHashMap<>();
	/** プレイヤーUUID -> 所属国家UUID。 */
	private final Map<UUID, UUID> playerNations = new HashMap<>();
	private final Random random = new Random();
	private final WKTickScheduler scheduler = new WKTickScheduler();

	private final TerritoryManager territory;
	private final EconomyManager economy;
	private final DiplomacyManager diplomacy;
	private final WarManager war;
	private final SuccessionEngine succession;

	private long tickCounter;
	private boolean dirty;

	private NationManager(MinecraftServer server) {
		this.server = server;
		this.territory = new TerritoryManager(this);
		this.economy = new EconomyManager(this);
		this.diplomacy = new DiplomacyManager(this);
		this.war = new WarManager(this);
		this.succession = new SuccessionEngine(this);
	}

	// ------------------------------------------------------------------
	// ライフサイクル
	// ------------------------------------------------------------------

	public static NationManager init(MinecraftServer server) {
		NationManager manager = new NationManager(server);
		manager.load();
		instance = manager;
		return manager;
	}

	@Nullable
	public static NationManager get() {
		return instance;
	}

	public static NationManager require() {
		NationManager manager = instance;
		if (manager == null) {
			throw new IllegalStateException("NationManager is not initialised");
		}
		return manager;
	}

	public static void shutdown() {
		if (instance != null) {
			instance.save();
			instance = null;
		}
	}

	public MinecraftServer server() {
		return server;
	}

	public Random random() {
		return random;
	}

	public WKTickScheduler scheduler() {
		return scheduler;
	}

	public TerritoryManager territory() {
		return territory;
	}

	public EconomyManager economy() {
		return economy;
	}

	public DiplomacyManager diplomacy() {
		return diplomacy;
	}

	public WarManager war() {
		return war;
	}

	public SuccessionEngine succession() {
		return succession;
	}

	// ------------------------------------------------------------------
	// 国家アクセス
	// ------------------------------------------------------------------

	public Collection<Nation> nations() {
		return nations.values();
	}

	public List<Nation> aliveNations() {
		List<Nation> list = new ArrayList<>();
		for (Nation nation : nations.values()) {
			if (nation.alive) {
				list.add(nation);
			}
		}
		return list;
	}

	@Nullable
	public Nation nation(@Nullable UUID id) {
		return id == null ? null : nations.get(id);
	}

	public int nationCount() {
		return aliveNations().size();
	}

	public void register(Nation nation) {
		nations.put(nation.id, nation);
		for (Nation other : nations.values()) {
			if (!other.id.equals(nation.id)) {
				nation.relation(other.id);
				other.relation(nation.id);
			}
		}
		markDirty();
	}

	@Nullable
	public Nation nationOfPlayer(UUID playerId) {
		return nation(playerNations.get(playerId));
	}

	@Nullable
	public Nation nationIdOfPlayerEntity(Entity entity) {
		return entity instanceof net.minecraft.world.entity.player.Player ? nationOfPlayer(entity.getUUID()) : null;
	}

	@Nullable
	public UUID nationIdOfPlayer(Entity player) {
		UUID nationId = playerNations.get(player.getUUID());
		if (nationId == null) {
			return null;
		}
		Nation nation = nations.get(nationId);
		return nation != null && nation.alive ? nationId : null;
	}

	public void setPlayerNation(UUID playerId, @Nullable Nation nation) {
		if (nation == null) {
			playerNations.remove(playerId);
		} else {
			playerNations.put(playerId, nation.id);
			nation.playerKing = playerId;
		}
		markDirty();
	}

	/** エンティティが所属する国家を解決する。 */
	@Nullable
	public Nation nationOfEntity(Entity entity) {
		if (entity instanceof net.minecraft.world.entity.player.Player player) {
			return nationOfPlayer(player.getUUID());
		}
		if (entity instanceof INationMember member) {
			return nation(member.getNationId());
		}
		return null;
	}

	// ------------------------------------------------------------------
	// 建国・滅亡
	// ------------------------------------------------------------------

	/** プレイヤーによる建国（建国憲章の使用）。 */
	public Nation foundPlayerNation(ServerPlayer player, String name, Ideology ideology, WorldPos capital, int color) {
		Nation nation = NationFactory.createNation(UUID.randomUUID(), name, color, ideology, capital);
		nation.playerKing = player.getUUID();
		nation.foundedTick = tickCounter;
		// プレイヤー国家の国王NPCは「宰相」として残し、人間プレイヤーが王となる
		NpcRecord king = nation.king();
		if (king != null) {
			king.role = NationRole.PRINCE;
			king.name = "宰相 " + king.name;
		}
		NationPopulation.populateInitial(nation, random, 10);
		register(nation);
		setPlayerNation(player.getUUID(), nation);
		nation.chronicle("建国: 人間の王 " + player.getGameProfile().getName() + " が " + name + " を建国した。");
		territory.claimRadius(nation, new net.minecraft.world.level.ChunkPos(capital.pos()), WKConfig.get().initialClaimRadius);
		broadcast(Component.literal("§6[建国] §f" + player.getGameProfile().getName()
				+ " が §e" + name + "§f を建国した。"));
		return nation;
	}

	/** 国家の滅亡処理。 */
	public void dissolveNation(Nation nation, String reason) {
		if (!nation.alive) {
			return;
		}
		nation.alive = false;
		nation.chronicle("滅亡: " + reason);
		for (Nation other : nations.values()) {
			other.relations.remove(nation.id);
		}
		territory.releaseAll(nation);
		markDirty();
		broadcast(Component.literal("§4[滅亡] §f" + nation.name + " が滅亡した。(" + reason + ")"));
	}

	// ------------------------------------------------------------------
	// 通知
	// ------------------------------------------------------------------

	/** 全プレイヤーへ国家イベントを通知する。 */
	public void broadcast(Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.displayClientMessage(message, false);
		}
	}

	/** 特定国家の構成員へ通知する。 */
	public void notifyNation(Nation nation, Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID nationId = playerNations.get(player.getUUID());
			if (nationId != null && nationId.equals(nation.id)) {
				player.displayClientMessage(message, false);
			}
		}
	}

	// ------------------------------------------------------------------
	// tick
	// ------------------------------------------------------------------

	public void tick() {
		tickCounter++;
		scheduler.tick(WKConfig.get().schedulerBudget);

		// 戦争は毎tick（戦闘判定が絡むため）
		war.tick();

		// 経済・外交・民心は 100tick（5秒）ごと
		if (tickCounter % 100L == 0L) {
			economy.tick();
			diplomacy.tick();
		}
		// 王位・反乱・人口動態は 400tick（20秒）ごと
		if (tickCounter % 400L == 0L) {
			succession.periodicTick();
			populationTick();
		}
		// 定期的な自動セーブ（5分ごと）
		if (tickCounter % 6000L == 0L) {
			save();
			exportJson();
		}
	}

	private void populationTick() {
		for (Nation nation : aliveNations()) {
			boolean foodSurplus = nation.stock(ResourceType.FOOD) > nation.population() * ResourceType.FOOD.perCapitaDemand() * 1.2;
			if (NationPopulation.grow(nation, random, foodSurplus)) {
				markDirty();
			}
			// 古い記録の整理（死亡した NPC は台帳から 1日後に消す）
			List<UUID> stale = new ArrayList<>();
			for (NpcRecord rec : nation.subjects()) {
				if (!rec.alive && tickCounter - rec.lastSeenTick > 24000L) {
					stale.add(rec.id);
				}
			}
			for (UUID id : stale) {
				nation.removeSubject(id);
			}
		}
	}

	// ------------------------------------------------------------------
	// 永続化
	// ------------------------------------------------------------------

	public void markDirty() {
		dirty = true;
	}

	private NationSavedData data() {
		return server.overworld().getDataStorage().computeIfAbsent(NationSavedData.factory(), NationSavedData.NAME);
	}

	public void save() {
		CompoundTag root = new CompoundTag();
		root.putLong("Tick", tickCounter);
		ListTag nationList = new ListTag();
		for (Nation nation : nations.values()) {
			nationList.add(nation.save());
		}
		root.put("Nations", nationList);
		CompoundTag players = new CompoundTag();
		for (Map.Entry<UUID, UUID> entry : playerNations.entrySet()) {
			players.putString(entry.getKey().toString(), entry.getValue().toString());
		}
		root.put("Players", players);
		ListTag warList = new ListTag();
		for (War w : war.activeWars()) {
			warList.add(w.save());
		}
		root.put("Wars", warList);

		NationSavedData data = data();
		data.setPayload(root);
		dirty = false;
		WKLog.info("nations saved: " + nations.size() + " states, " + war.activeWars().size() + " active wars");
	}

	public void load() {
		NationSavedData data = data();
		CompoundTag root = data.payload();
		tickCounter = root.getLong("Tick");
		nations.clear();
		playerNations.clear();
		war.clear();
		ListTag nationList = root.getList("Nations", 10);
		for (int i = 0; i < nationList.size(); i++) {
			Nation nation = Nation.load(nationList.getCompound(i));
			nations.put(nation.id, nation);
		}
		CompoundTag players = root.getCompound("Players");
		for (String key : players.getAllKeys()) {
			try {
				playerNations.put(UUID.fromString(key), UUID.fromString(players.getString(key)));
			} catch (Throwable ignored) {
				// 壊れたエントリは捨てる
			}
		}
		ListTag warList = root.getList("Wars", 10);
		for (int i = 0; i < warList.size(); i++) {
			War w = War.load(warList.getCompound(i));
			if (w != null) {
				war.restore(w);
			}
		}

		if (nations.isEmpty()) {
			bootstrapWorld();
		} else {
			for (Nation nation : nations.values()) {
				for (Nation other : nations.values()) {
					if (!other.id.equals(nation.id)) {
						nation.relation(other.id);
					}
				}
			}
		}
		territory.rebuild();
	}

	/** 新規ワールドの国家初期化（5か国以上を自動生成）。 */
	private void bootstrapWorld() {
		long seed = server.overworld().getSeed() ^ 0x9E3779B97F4A7C15L;
		List<Nation> initial = NationFactory.createInitialNations(server, WKConfig.get().initialNations, seed);
		Random bootRandom = new Random(seed);
		for (Nation nation : initial) {
			NationPopulation.populateInitial(nation, bootRandom, WKConfig.get().initialPopulation);
			register(nation);
			if (nation.capital != null) {
				territory.claimRadius(nation, new net.minecraft.world.level.ChunkPos(nation.capital.pos()),
						WKConfig.get().initialClaimRadius);
			}
		}
		// 初期外交関係（地理的近接・理念の相性で決まる）
		diplomacy.seedInitialRelations();
		save();
		WKLog.info("world bootstrap: " + initial.size() + " autonomous NPC nations created");
	}

	/** 状態を JSON としてエクスポートする（外部ツール・デバッグ用）。 */
	public JsonObject exportJsonInternal() {
		JsonObject root = new JsonObject();
		root.addProperty("tick", tickCounter);
		JsonArray array = new JsonArray();
		for (Nation nation : aliveNations()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("id", nation.id.toString());
			obj.addProperty("name", nation.name);
			obj.addProperty("ideology", nation.ideology.name());
			obj.addProperty("king", nation.kingName);
			obj.addProperty("kingPersonality", nation.kingPersonality.name());
			obj.addProperty("generation", nation.dynastyGeneration);
			obj.addProperty("population", nation.population());
			obj.addProperty("army", nation.armySize());
			obj.addProperty("militaryPower", nation.militaryPower());
			obj.addProperty("treasury", nation.treasury);
			obj.addProperty("unrest", nation.unrest);
			obj.addProperty("legitimacy", nation.legitimacy);
			obj.addProperty("technology", nation.technology);
			obj.addProperty("chunks", nation.claimedChunks.size());
			obj.addProperty("wars", nation.atWar());
			if (nation.capital != null) {
				JsonObject capital = new JsonObject();
				capital.addProperty("dim", nation.capital.dimension().toString());
				capital.addProperty("x", nation.capital.pos().getX());
				capital.addProperty("y", nation.capital.pos().getY());
				capital.addProperty("z", nation.capital.pos().getZ());
				obj.add("capital", capital);
			}
			JsonObject stockObj = new JsonObject();
			for (ResourceType type : ResourceType.values()) {
				stockObj.addProperty(type.name().toLowerCase(java.util.Locale.ROOT), nation.stock(type));
			}
			obj.add("stock", stockObj);
			array.add(obj);
		}
		root.add("nations", array);
		return root;
	}

	public Path exportPath() {
		return server.getServerDirectory().toPath().resolve("wisdomking").resolve("nations.json");
	}

	public void exportJson() {
		try {
			Path path = exportPath();
			Files.createDirectories(path.getParent());
			Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(exportJsonInternal()),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			WKLog.warn("failed to export nation json: " + e.getMessage());
		}
	}

	public static void debugExportAll() {
		if (instance != null) {
			instance.exportJson();
		}
	}

	/** テスト・デバッグ用の識別子。 */
	public static String debugTag() {
		return WK.MOD_ID + ":debug";
	}
}
