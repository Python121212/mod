package com.wisdomking.raphael;

import com.wisdomking.core.WKLog;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 全Mod走査器（Universal Mod Scanner）。
 *
 * <p>導入されているすべてのModについて、追加されたアイテム・ブロック・エンティティ数を
 * レジストリから直接集計する。名前空間ごとに数えるため、Mod側のAPIに一切依存せず、
 * Create・工業・魔法・農業などあらゆるModを「解析対象」として認識できる。</p>
 */
public final class ModScanner {
	/** 機械系Mod（動力・自動化）として認識する既知のID。 */
	private static final Set<String> MECHANICAL = Set.of(
			"create", "createaddition", "createnuclear", "createsifter", "create_connected",
			"mekanism", "immersiveengineering", "thermal", "thermal_expansion", "thermal_dynamics",
			"ae2", "appliedenergistics2", "powah", "modern_industrialization", "techreborn",
			"industrialforegoing", "oritech", "botania", "fluxnetworks", "biggerreactors",
			"refinedstorage", "gregtech", "electrodynamics", "nuclearcraft", "voltaic", "engineersdecor"
	);
	/** 魔法系Mod。 */
	private static final Set<String> MAGIC = Set.of(
			"irons_spellbooks", "ars_nouveau", "botania", "eidolon", "malum", "bloodmagic",
			"occultism", "spell_engine", "wizards", "paladins", "enchanted_wizardry", "roots"
	);
	/** 農業・食料系Mod。 */
	private static final Set<String> AGRICULTURE = Set.of(
			"farmersdelight", "croptopia", "pamhc2crops", "immersive_weathering", "sereneseasons",
			"netherdepthsupgrade", "farm_and_charm"
	);
	/** 音声・UI連携系Mod。 */
	private static final Set<String> VOICE = Set.of("voicechat", "simple_voice_chat", "plasmo_voice");
	private static final Set<String> UI = Set.of("jei", "roughlyenoughitems", "emi", "jade", "wthit",
			"advancementplaques", "modmenu", "cloth-config", "architectury");

	private static Map<String, Census> cache;

	private ModScanner() {
	}

	/** レジストリ集計結果。 */
	public record Census(String modId, String name, String version, int items, int blocks, int entities,
			boolean mechanical, boolean magic, boolean agriculture, boolean voice, boolean ui) {

		public String category() {
			if (mechanical) {
				return "mechanical";
			}
			if (magic) {
				return "magic";
			}
			if (agriculture) {
				return "agriculture";
			}
			if (voice) {
				return "voice";
			}
			if (ui) {
				return "ui";
			}
			return "general";
		}

		public int totalEntries() {
			return items + blocks + entities;
		}
	}

	/** 全Modの集計を取得する（初回のみ実行しキャッシュ）。 */
	public static synchronized Map<String, Census> census() {
		if (cache != null) {
			return cache;
		}
		Map<String, int[]> counts = new HashMap<>(); // namespace -> [items, blocks, entities]
		countRegistry(BuiltInRegistries.ITEM, counts, 0);
		countRegistry(BuiltInRegistries.BLOCK, counts, 1);
		countRegistry(BuiltInRegistries.ENTITY_TYPE, counts, 2);

		Map<String, String[]> metaById = new HashMap<>();
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			try {
				String id = container.getMetadata().getId();
				metaById.put(id, new String[]{
						container.getMetadata().getName(),
						container.getMetadata().getVersion().getFriendlyString()
				});
			} catch (Throwable t) {
				WKLog.warn("mod metadata unavailable: " + t);
			}
		}

		Map<String, Census> result = new TreeMap<>();
		for (Map.Entry<String, int[]> entry : counts.entrySet()) {
			String namespace = entry.getKey();
			int[] values = entry.getValue();
			String[] meta = metaById.getOrDefault(namespace, new String[]{namespace, "unknown"});
			result.put(namespace, new Census(namespace, meta[0], meta[1], values[0], values[1], values[2],
					MECHANICAL.contains(namespace), MAGIC.contains(namespace), AGRICULTURE.contains(namespace),
					VOICE.contains(namespace), UI.contains(namespace)));
		}
		// レジストリに要素を持たないMod（音声・UI等）も一覧に含める
		for (Map.Entry<String, String[]> entry : metaById.entrySet()) {
			String id = entry.getKey();
			if (!result.containsKey(id)) {
				result.put(id, new Census(id, entry.getValue()[0], entry.getValue()[1], 0, 0, 0,
						MECHANICAL.contains(id), MAGIC.contains(id), AGRICULTURE.contains(id),
						VOICE.contains(id), UI.contains(id)));
			}
		}
		cache = result;
		WKLog.info("mod census complete: " + result.size() + " mods detected");
		return cache;
	}

	private static <T> void countRegistry(Registry<T> registry, Map<String, int[]> counts, int index) {
		try {
			for (ResourceLocation id : registry.keySet()) {
				int[] values = counts.computeIfAbsent(id.getNamespace(), key -> new int[3]);
				values[index]++;
			}
		} catch (Throwable t) {
			WKLog.warn("registry census failed: " + t);
		}
	}

	/** 集計を文字列化する（Raphael の報告用）。 */
	public static List<String> describe(int limit) {
		List<Census> list = new ArrayList<>(census().values());
		list.sort(Comparator.comparingInt(Census::totalEntries).reversed());
		List<String> lines = new ArrayList<>();
		int count = 0;
		for (Census c : list) {
			if (count++ >= limit) {
				lines.add("他 " + Math.max(0, list.size() - limit) + " 件のModを検出。");
				break;
			}
			lines.add(String.format("%s (v%s, %s): アイテム %d / ブロック %d / エンティティ %d",
					c.name(), c.version(), c.category(), c.items(), c.blocks(), c.entities()));
		}
		return lines;
	}

	/** カテゴリ別のMod数。 */
	public static Map<String, Integer> categorySummary() {
		Map<String, Integer> summary = new LinkedHashMap<>();
		for (Census c : census().values()) {
			summary.merge(c.category(), 1, Integer::sum);
		}
		return summary;
	}

	/** 機械系Modが導入されているか。 */
	public static boolean hasMechanicalMods() {
		return census().values().stream().anyMatch(Census::mechanical);
	}

	/** 魔法系Modが導入されているか。 */
	public static boolean hasMagicMods() {
		return census().values().stream().anyMatch(Census::magic);
	}

	/** 導入Mod数の合計。 */
	public static int modCount() {
		return census().size();
	}

	/** 特定名前空間のMod名を解決する。 */
	public static String displayName(String namespace) {
		Census census = census().get(namespace);
		return census == null ? namespace : census.name();
	}

	public static void invalidate() {
		cache = null;
	}
}
