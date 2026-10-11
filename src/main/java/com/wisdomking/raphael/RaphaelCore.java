package com.wisdomking.raphael;

import com.wisdomking.core.WKConfig;
import com.wisdomking.core.WKLog;
import com.wisdomking.equipment.CombatRole;
import com.wisdomking.equipment.EquipmentScorer;
import com.wisdomking.equipment.ItemClassifier;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.NpcRecord;
import com.wisdomking.nation.Personality;
import com.wisdomking.nation.RelationState;
import com.wisdomking.nation.ResourceType;
import com.wisdomking.world.ResourceMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 智慧之王（ラファエル）中核。
 *
 * <p>プレイヤーの視認対象（アイテム・ブロック・機械・NPC）と、国家情勢・戦況・脅威を
 * 統合解析し、すべての結果を「《告》《解》《解析完了》《報告》」の標識付きで
 * 厳格かつ冷徹なトーンで提示する。</p>
 *
 * <p>配送は {@link RaphaelRelay} を経由するため、チャット・GUI・音声（TTS）の
 * いずれの環境でも同一の解析結果が利用できる。</p>
 */
public final class RaphaelCore {
	/** プレイヤーごとの最終音声報告tick（連投防止）。 */
	private static final Map<UUID, Long> LAST_SPEAK = new HashMap<>();
	private static final Map<UUID, Long> LAST_PASSIVE = new HashMap<>();

	private RaphaelCore() {
	}

	// ------------------------------------------------------------------
	// 対話的解析（プレイヤー操作による）
	// ------------------------------------------------------------------

	/** 手に持つアイテムの解析。 */
	public static void analyzeHeld(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		ItemStack target = main.isEmpty() ? off : main;
		if (target.isEmpty()) {
			reply(player, AnalysisReport.of(Tone.ANALYSIS, "解析対象がありません。アイテムを手に持ってください。",
					Urgency.INFO));
			return;
		}
		reply(player, ItemAnalyzer.analyze(target));
		if (player.isShiftKeyDown()) {
			List<String> scanDetails = new ArrayList<>();
			scanDetails.add("Mod数: " + ModScanner.modCount());
			ModScanner.categorySummary().forEach((category, count) ->
					scanDetails.add("  - " + category + ": " + count + " Mod"));
			RaphaelRelay.scanComplete(player, "導入Mod環境の走査", scanDetails);
		}
	}

	/** 視線先のブロック・エンティティの解析。 */
	public static void analyzeTarget(ServerPlayer player, double reach) {
		Entity lookedEntity = findLookedEntity(player, 8.0);
		if (lookedEntity != null) {
			analyzeEntity(player, lookedEntity);
			return;
		}
		HitResult hit = player.pick(reach, 0.0f, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			if (!player.level().isLoaded(pos)) {
				reply(player, AnalysisReport.of(Tone.ANALYSIS, "対象チャンクが未ロードのため解析できません。",
						Urgency.INFO));
				return;
			}
			reply(player, MachineAnalyzer.analyze((ServerLevel) player.level(), pos, player.getName().getString()));
			return;
		}
		reply(player, AnalysisReport.of(Tone.ANALYSIS, "視線先に解析対象がありません。",
				Urgency.INFO, "ブロックまたはエンティティに照準を合わせてください。"));
	}

	/** 視線先のエンティティを探索する（アイテムピッキング相当）。 */
	@Nullable
	public static Entity findLookedEntity(ServerPlayer player, double range) {
		try {
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getViewVector(1.0f).normalize();
			AABB area = player.getBoundingBox().inflate(range);
			Entity best = null;
			double bestScore = 0.0;
			for (Entity entity : player.level().getEntities(player, area)) {
				Vec3 delta = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0).subtract(eye);
				double distance = delta.length();
				if (distance > range || distance < 0.2) {
					continue;
				}
				double dot = delta.normalize().dot(look);
				if (dot < 0.82) {
					continue;
				}
				double score = dot / Math.max(0.5, distance);
				if (score > bestScore) {
					bestScore = score;
					best = entity;
				}
			}
			return best;
		} catch (Throwable t) {
			return null;
		}
	}

	/** エンティティ（NPC・敵Mob）の解析。 */
	public static void analyzeEntity(ServerPlayer player, Entity entity) {
		List<String> details = new ArrayList<>();
		var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		String namespace = typeId == null ? "minecraft" : typeId.getNamespace();
		details.add("対象: " + (typeId == null ? "unknown" : typeId.toString())
				+ "（Mod: " + ModScanner.displayName(namespace) + "）");
		if (entity.hasCustomName()) {
			details.add("固有名称: " + entity.getCustomName().getString());
		}
		if (entity instanceof LivingEntity living) {
			details.add(String.format("体力 %.1f/%.1f", living.getHealth(), living.getMaxHealth()));
			ItemStack weapon = living.getItemBySlot(EquipmentSlot.MAINHAND);
			if (!weapon.isEmpty()) {
				CombatRole role = ItemClassifier.classify(weapon);
				details.add("武装: " + weapon.getHoverName().getString() + "（" + role.name()
						+ " / スコア " + String.format("%.2f", EquipmentScorer.score(weapon, role)) + "）");
			} else {
				details.add("武装: なし（素手）");
			}
			int armorPieces = 0;
			double armorTotal = 0.0;
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
					EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack armor = living.getItemBySlot(slot);
				if (!armor.isEmpty()) {
					armorPieces++;
					armorTotal += EquipmentScorer.armorValue(armor);
				}
			}
			details.add("防具: " + armorPieces + " 部位 / 防御合計 " + String.format("%.1f", armorTotal));
		}
		NationManager manager = NationManager.get();
		if (manager != null) {
			Nation nation = manager.nationOfEntity(entity);
			if (nation != null) {
				details.add("所属国家: " + nation.name + "（" + nation.ideology.name() + " / 国王 "
						+ nation.kingName + "）");
				if (entity instanceof com.wisdomking.nation.INationMember member) {
					NationRole role = member.getNationRole();
					details.add("役職: " + role.name() + " / 戦闘適性 " + role.combatBias()
							+ " / 権威 " + role.authority());
					NpcRecord record = nation.subject(entity.getUUID());
					if (record != null) {
						details.add(String.format("忠誠 %.0f%% / 人望 %.0f%% / 個人戦力 %.2f / 性格 %s",
								record.loyalty * 100, record.popularity * 100, record.power,
								record.personality.name()));
						details.add("継承スコア: " + String.format("%.2f", record.successionScore()));
					}
				}
				UUID playerNation = manager.nationIdOfPlayer(player);
				boolean hostile = playerNation != null && nation.atWarWith(playerNation);
				details.add(hostile ? "《告》交戦国家の構成員です。" : "外交状態: "
						+ (playerNation == null ? "不明" : nation.relation(playerNation).state.name()));
			} else if (entity instanceof LivingEntity && namespace.equals("minecraft")) {
				details.add("国家に所属しない在野の存在です。");
			}
		}
		reply(player, new AnalysisReport(Tone.ANALYSIS, "個体解析結果", details, Urgency.INFO));
	}

	// ------------------------------------------------------------------
	// 国家・戦況解析
	// ------------------------------------------------------------------

	@Nullable
	private static Nation currentNation(ServerPlayer player) {
		NationManager manager = NationManager.get();
		return manager == null ? null : manager.nationOfPlayer(player.getUUID());
	}

	/** 国家情勢の解析（ダッシュボードの主要レポート）。 */
	public static void reportNation(ServerPlayer player) {
		Nation nation = currentNation(player);
		if (nation == null) {
			reply(player, AnalysisReport.of(Tone.ALERT, "あなたは国家に所属していません。",
					Urgency.NOTICE, "建国憲章を使用してキャピタル・コアを設置するか、既存国家の首都中核を制圧してください。"));
			return;
		}
		NationManager manager = NationManager.require();
		List<String> details = new ArrayList<>();
		details.add(String.format("%s / 理念 %s / 第%d代国王 %s（性格 %s）",
				nation.name, nation.ideology.name(), nation.dynastyGeneration, nation.kingName,
				nation.kingPersonality.name()));
		details.add(String.format("人口 %d / 兵力 %d / 総合戦力 %.1f / 士気 %.0f%%",
				nation.population(), nation.armySize(), nation.militaryPower(), nation.morale * 100));
		details.add(String.format("不満度 %.0f%% / 正当性 %.0f%% / 技術水準 %.0f%%",
				nation.unrest * 100, nation.legitimacy * 100, nation.technology * 100));
		details.add(String.format("国庫 %.1f / 領土 %d チャンク", nation.treasury, nation.claimedChunks.size()));
		StringBuilder stock = new StringBuilder("備蓄: ");
		for (ResourceType type : ResourceType.values()) {
			stock.append(type.name()).append('=').append(String.format("%.0f", nation.stock(type))).append(' ');
		}
		details.add(stock.toString().trim());
		NpcRecord king = nation.king();
		if (king != null) {
			details.add(String.format("国王の個人評価: 人望 %.0f%% / 戦力 %.2f / 継承スコア %.2f",
					king.popularity * 100, king.power, king.successionScore()));
		}
		float risk = ThreatAnalyzer.coupRisk(nation);
		if (risk > 0.2f) {
			details.add(String.format("《告》反乱リスク %.0f%%。忠誠の低い臣下: %s", risk * 100,
					describeDissidents(nation)));
		}
		List<String> wars = manager.war().warSummary(nation);
		details.addAll(wars);
		details.add("直近の国家史:");
		for (String entry : nation.recentChronicle(3)) {
			details.add("  ‣ " + entry);
		}
		reply(player, new AnalysisReport(Tone.REPORT, nation.name + " 国家情勢解析", details,
				risk > 0.45f ? Urgency.NOTICE : Urgency.INFO));
	}

	private static String describeDissidents(Nation nation) {
		List<String> names = new ArrayList<>();
		for (NpcRecord record : ThreatAnalyzer.rankedByRisk(nation)) {
			if (record.loyalty < 0.45f && names.size() < 3) {
				names.add(record.name + "(" + Math.round(record.loyalty * 100) + "%)");
			}
		}
		return names.isEmpty() ? "該当なし" : String.join(", ", names);
	}

	/** 戦況の解析。 */
	public static void reportWar(ServerPlayer player) {
		Nation nation = currentNation(player);
		if (nation == null) {
			reply(player, AnalysisReport.of(Tone.ALERT, "国家に所属していないため戦況解析を行えません。", Urgency.INFO));
			return;
		}
		NationManager manager = NationManager.require();
		List<String> details = new ArrayList<>();
		List<String> wars = manager.war().warSummary(nation);
		if (wars.isEmpty()) {
			details.add("現在、交戦中の国家はありません。");
			Nation threat = manager.diplomacy().threatsTo(nation).stream().findFirst().orElse(null);
			if (threat != null) {
				details.add(String.format("潜在的脅威: %s（戦力比 %.2f）", threat.name,
						threat.militaryPower() / Math.max(1.0f, nation.militaryPower())));
			}
			Nation ally = manager.diplomacy().bestAllyCandidate(nation);
			if (ally != null) {
				details.add("同盟候補: " + ally.name + "（関係値 "
						+ String.format("%.0f", nation.relation(ally.id).value) + "）");
			}
			reply(player, new AnalysisReport(Tone.REPORT, "戦況解析: 平和", details, Urgency.INFO));
			return;
		}
		details.addAll(wars);
		for (com.wisdomking.nation.War war : manager.war().warsOf(nation)) {
			Nation enemy = manager.nation(war.enemyOf(nation.id));
			if (enemy == null) {
				continue;
			}
			details.add(String.format("%s: 相手戦力 %.1f / こちら %.1f / 首都陥落 %s",
					enemy.name, enemy.militaryPower(), nation.militaryPower(),
					war.capitalCaptured ? "あり" : "なし"));
			details.add("  戦争目的: " + war.casusBelli + " / 経過 " + war.phase.name());
			for (String event : war.events.subList(Math.max(0, war.events.size() - 2), war.events.size())) {
				details.add("  ‣ " + event);
			}
		}
		reply(player, new AnalysisReport(Tone.REPORT, "戦況解析", details, Urgency.NOTICE));
	}

	/** 経済解析。 */
	public static void reportEconomy(ServerPlayer player) {
		Nation nation = currentNation(player);
		if (nation == null) {
			reply(player, AnalysisReport.of(Tone.ALERT, "国家に所属していないため経済解析を行えません。", Urgency.INFO));
			return;
		}
		NationManager manager = NationManager.require();
		List<String> details = new ArrayList<>();
		details.addAll(manager.economy().productionReport(nation));
		details.add("最も豊富な資源: " + manager.economy().mostAbundant(nation).name());
		for (ResourceType type : ResourceType.values()) {
			double deficit = manager.economy().deficit(nation, type);
			if (deficit > 0.55) {
				details.add(String.format("《告》%s が %.0f%% 不足。交易または生産拡大が必要です。",
						type.name(), deficit * 100));
			}
		}
		details.add("交易価値評価: " + String.format("%.1f", manager.economy().tradeAttractiveness(nation)));
		reply(player, new AnalysisReport(Tone.ANALYSIS, "経済解析", details, Urgency.INFO));
	}

	// ------------------------------------------------------------------
	// 全Mod走査・自動化解析
	// ------------------------------------------------------------------

	/** 導入Mod全体の走査報告。 */
	public static void scanMods(ServerPlayer player) {
		List<String> details = new ArrayList<>();
		details.add("検出Mod数: " + ModScanner.modCount());
		ModScanner.categorySummary().forEach((category, count) ->
				details.add(String.format("%s: %d Mod", category, count)));
		details.add("主要Mod（登録要素数順）:");
		details.addAll(ModScanner.describe(8));
		details.add("機械系Mod: " + (ModScanner.hasMechanicalMods() ? "検出（Create等の動力網解析が有効）" : "なし"));
		details.add("魔法系Mod: " + (ModScanner.hasMagicMods() ? "検出（魔法研究所の自動生産が有効）" : "なし"));
		RaphaelRelay.scanComplete(player, "全Mod走査が完了しました。", details);
	}

	/** 周辺自動化ラインの解析。 */
	public static void adviseAutomation(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		AnalysisReport report = AutomationAdvisor.advise(level, player.blockPosition(),
				WKConfig.get().analysisRadius);
		reply(player, report);
	}

	/** 脅威解析。 */
	public static void reportThreats(ServerPlayer player) {
		reply(player, ThreatAnalyzer.analyze(player));
	}

	/** 統合ブリーフィング（国家 + 脅威 + 自動化）。 */
	public static void fullBriefing(ServerPlayer player) {
		Nation nation = currentNation(player);
		if (nation == null) {
			reply(player, AnalysisReport.of(Tone.ALERT, "国家未所属のため簡易報告のみ実施します。", Urgency.NOTICE,
					"建国憲章で国家を設立してください。"));
			reportThreats(player);
			return;
		}
		reply(player, new AnalysisReport(Tone.REPORT, "統合ブリーフィングを開始します。", List.of(
				"対象国家: " + nation.name,
				"解析項目: 国家情勢 / 戦況 / 経済 / 脅威 / 自動化ライン"), Urgency.INFO));
		reportNation(player);
		reportWar(player);
		reportThreats(player);
		if (ModScanner.hasMechanicalMods()) {
			adviseAutomation(player);
		}
	}

	// ------------------------------------------------------------------
	// 受動解析（tick）
	// ------------------------------------------------------------------

	/** サーバーtickから呼ばれる受動解析。重大事象のみ能動的に通知する。 */
	public static void passiveTick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (tick % 400L != 0L) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!WKConfig.get().passiveAnalysis) {
				continue;
			}
			long last = LAST_PASSIVE.getOrDefault(player.getUUID(), 0L);
			if (tick - last < WKConfig.get().passiveAnalysisIntervalTicks) {
				continue;
			}
			LAST_PASSIVE.put(player.getUUID(), tick);
			WKLog.safely("passive analysis", () -> {
				AnalysisReport threat = ThreatAnalyzer.analyze(player);
				if (threat.urgency() == Urgency.CRITICAL) {
					RaphaelRelay.announceTo(player, threat, true);
				}
			});
		}
	}

	// ------------------------------------------------------------------
	// 配送ヘルパ
	// ------------------------------------------------------------------

	/** 解析結果を配送する（重要度に応じて音声読み上げ）。 */
	public static void reply(ServerPlayer player, AnalysisReport report) {
		boolean speak = report.urgency() == Urgency.CRITICAL || (report.urgency() == Urgency.NOTICE
				&& WKConfig.get().speakNotices);
		if (speak) {
			long now = player.serverLevel().getGameTime();
			long last = LAST_SPEAK.getOrDefault(player.getUUID(), -1000L);
			if (now - last < 40L) {
				speak = false;
			} else {
				LAST_SPEAK.put(player.getUUID(), now);
			}
		}
		RaphaelRelay.remember(report);
		RaphaelRelay.announceTo(player, report, speak);
	}

	/** システム起動時の宣言（《解析完了》）。 */
	public static void greet(ServerPlayer player) {
		List<String> details = new ArrayList<>();
		details.add("検出Mod数: " + ModScanner.modCount());
		details.add("機械系Mod: " + (ModScanner.hasMechanicalMods() ? "あり" : "なし")
				+ " / 魔法系Mod: " + (ModScanner.hasMagicMods() ? "あり" : "なし"));
		details.add("国家数: " + (NationManager.get() == null ? 0 : NationManager.get().nationCount()));
		details.add("解析機能: 個体 / 機械 / 自動化 / 国家 / 戦況 / 脅威 / 全Mod走査");
		RaphaelRelay.scanComplete(player, "智慧之王システム、起動完了。", details);
	}

	/** 全プレイヤーへ国家統計のサマリを返す（GUI 用）。 */
	public static List<String> nationSummaryLines() {
		NationManager manager = NationManager.get();
		if (manager == null) {
			return List.of("国家データ未初期化");
		}
		List<String> lines = new ArrayList<>();
		for (Nation nation : manager.aliveNations()) {
			lines.add(String.format("%s（%s） 人口%d 兵力%d 戦力%.1f 国庫%.0f 不満%.0f%%%s",
					nation.name, nation.kingName, nation.population(), nation.armySize(),
					nation.militaryPower(), nation.treasury, nation.unrest * 100,
					nation.atWar() ? " [交戦中]" : ""));
		}
		return lines;
	}

	/** 外交一覧（GUI 用）。 */
	public static List<String> diplomacyLines(Nation nation) {
		List<String> lines = new ArrayList<>();
		for (Nation other : NationManager.require().aliveNations()) {
			if (other.id.equals(nation.id)) {
				continue;
			}
			var rel = nation.relation(other.id);
			lines.add(String.format("%s: %s (関係値 %.0f) 戦力比 %.2f", other.name,
					rel.state.name(), rel.value,
					other.militaryPower() / Math.max(1.0f, nation.militaryPower())));
		}
		return lines;
	}

	/** 臣下一覧（GUI 用）。 */
	public static List<String> subjectLines(Nation nation) {
		List<String> lines = new ArrayList<>();
		List<NpcRecord> records = new ArrayList<>(nation.subjects());
		records.sort((a, b) -> Float.compare(b.successionScore(), a.successionScore()));
		for (NpcRecord record : records) {
			if (!record.alive) {
				continue;
			}
			lines.add(String.format("%s [%s] 忠誠%.0f%% 人望%.0f%% 戦力%.2f 性格%s",
					record.name, record.role.name(), record.loyalty * 100, record.popularity * 100,
					record.power, record.personality.name()));
			if (lines.size() >= 40) {
				break;
			}
		}
		return lines;
	}

	/** 未使用の警告を避けるためのユーティリティ（性格名の翻訳キー）。 */
	public static String personalityKey(Personality personality) {
		return personality.key();
	}

	/** 関係状態の翻訳キー。 */
	public static String relationKey(RelationState state) {
		return state.key();
	}

	/** 資源名の翻訳キー。 */
	public static String resourceKey(ResourceType type) {
		return type.key();
	}

	/** 国家の概要一行（他システムから参照）。 */
	public static Component nationHeadline(Nation nation) {
		return Component.literal(String.format("%s / %s / 人口%d / 戦力%.1f",
				nation.name, nation.kingName, nation.population(), nation.militaryPower()));
	}

	/** アイテムの資源価値（経済連携）。 */
	public static double itemValue(ItemStack stack) {
		ResourceType type = ResourceMapper.resourceFor(stack);
		return type == null ? 0.0 : ResourceMapper.valueOf(stack, type);
	}

	/** デバッグ出力。 */
	public static void debugDump(ServerPlayer player) {
		player.displayClientMessage(Component.literal("§7[wisdomking] mods=" + ModScanner.modCount()
				+ " nations=" + (NationManager.get() == null ? 0 : NationManager.get().nationCount())
				+ " locale=" + Locale.getDefault()), false);
	}
}
