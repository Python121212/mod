package com.wisdomking.compat;

import com.wisdomking.WK;
import com.wisdomking.core.WKLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部Modとのソフト依存（Soft Dependency）管理。
 *
 * <p>Create / Iron's Spells 'n Spellbooks / Simple Voice Chat / JEI / REI / EMI /
 * Jade / WTHIT / Advancement Plaques などは<b>存在しなくても動作する</b>。
 * 本クラスは各Modの在否を起動時に判定して記録し、
 * 連携コードは「在否を確認してから」呼び出す。</p>
 *
 * <p>クラス参照は一切行わず、Mod ローダーの照会（{@link WK#isModLoaded(String)}）と
 * クラスパス照会（{@link WK#hasClass(String)}）のみを使うため、
 * これらのModが未導入の環境でもクラッシュしない。</p>
 */
public final class CompatRegistry {
	private static final Map<String, Boolean> MODS = new LinkedHashMap<>();
	private static final Map<String, Boolean> CLASSES = new LinkedHashMap<>();

	private CompatRegistry() {
	}

	/** 起動時に一度だけ呼ぶ。 */
	public static void init() {
		probe("create", "com.simibubi.create.Create");
		probe("irons_spellbooks", "io.redspace.ironsspellbooks.IronsSpellbooks");
		probe("voicechat", "de.maxhenkel.voicechat.Voicechat");
		probe("jei", "mezz.jei.api.constants.VanillaTypes");
		probe("rei", "me.shedaniel.rei.api.common.entry.EntryStack");
		probe("emi", "dev.emi.emi.api.EmiPlugin");
		probe("jade", "snownee.jade.api.IWailaPlugin");
		probe("wthit", "mcp.mobius.waila.api.IWthitPlugin");
		probe("advancementplaques", "dev.ftb.mods.ftblibrary.FTBLibrary");
		probe("patchouli", "vazkii.patchouli.api.PatchouliAPI");
		probe("computercraft", "dan200.computercraft.api.ComputerCraftAPI");
		probe("kubejs", "dev.latvian.mods.kubejs.KubeJSPlugin");
		probe("terraform", "com.terraformersmc.modmenu.ModMenu");
		WKLog.info(describe());
		if (present("create")) {
			WKLog.info("Create 連携: 回転数・ストレス解析を有効化しました。");
		}
		if (present("irons_spellbooks")) {
			WKLog.info("Iron's Spells 連携: 魔法系NPCの術式分類を有効化しました。");
		}
		if (present("voicechat")) {
			WKLog.info("Simple Voice Chat 併用時も独自音声経路で競合しません。");
		}
		if (anyRecipeViewer()) {
			WKLog.info("レシピビューア連携: 本Modのレシピは {@code wisdomking:} 名前空間で自動表示されます。");
		}
	}

	private static void probe(String modId, String classPath) {
		boolean loaded = false;
		try {
			loaded = WK.isModLoaded(modId);
		} catch (Throwable ignored) {
			loaded = false;
		}
		MODS.put(modId, loaded);
		CLASSES.put(modId, WK.hasClass(classPath));
	}

	/** 指定Modが導入されているか。 */
	public static boolean present(String modId) {
		return Boolean.TRUE.equals(MODS.get(modId));
	}

	/** 指定Modのクラスがクラスパス上に存在するか。 */
	public static boolean classPresent(String modId) {
		return Boolean.TRUE.equals(CLASSES.get(modId));
	}

	/** レシピビューア（JEI/REI/EMI）のいずれかがあるか。 */
	public static boolean anyRecipeViewer() {
		return present("jei") || present("rei") || present("emi");
	}

	/** HUD 情報Mod（Jade/WTHIT）のいずれかがあるか。 */
	public static boolean anyHudMod() {
		return present("jade") || present("wthit");
	}

	/** 導入済みModの一覧（Raphael の報告用）。 */
	public static List<String> detectedMods() {
		List<String> list = new ArrayList<>();
		MODS.forEach((id, loaded) -> {
			if (Boolean.TRUE.equals(loaded)) {
				list.add(id);
			}
		});
		return list;
	}

	/** 検出結果の要約文字列。 */
	public static String describe() {
		StringBuilder builder = new StringBuilder("ソフト依存検出: ");
		MODS.forEach((id, loaded) -> builder.append(id).append(loaded ? "=有 " : "=無 "));
		return builder.toString().trim();
	}
}
