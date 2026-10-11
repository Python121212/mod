package com.wisdomking.raphael;

import com.wisdomking.core.WKLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 機械・自動化設備の解析器（Create ほか機械Mod対応）。
 *
 * <p>他Modのブロックエンティティは API が存在しない場合でも解析できるよう、
 * 以下の 3 段構えで情報を収集する:</p>
 * <ol>
 *   <li>NBT スナップショット（{@code saveWithFullMetadata}）から回転数・ストレス・
 *       エネルギー・進捗などの数値キーを抽出する</li>
 *   <li>既知のメソッド名（{@code getSpeed}, {@code getStressImpact} 等）を
 *       リフレクションで安全に呼び出す（存在しなければスキップ）</li>
 *   <li>インベントリ・周辺の動力源・同系統機械の分布からボトルネックを推定する</li>
 * </ol>
 *
 * <p>API 非依存のため、いかなる機械Modが追加・更新されてもクラッシュしない。</p>
 */
public final class MachineAnalyzer {
	/** 動力を供給する設備を示す名前ヒューリスティクス。 */
	private static final String[] POWER_SOURCE_HINTS = {
			"water_wheel", "waterwheel", "windmill", "sail", "steam_engine", "engine", "generator",
			"dynamo", "turbine", "solar", "reactor", "boiler", "flywheel", "motor", "hand_crank",
			"creative_motor", "alternator", "wind_turbine"
	};
	/** 負荷（ストレス）を消費する設備。 */
	private static final String[] CONSUMER_HINTS = {
			"millstone", "crushing", "press", "mixer", "saw", "drill", "fan", "pump", "deployer",
			"mechanical_arm", "mechanical_piston", "bearing", "roller", "spout", "funnel", "chute",
			"machine", "furnace", "crusher", "assembler", "smelter", "centrifuge"
	};

	private MachineAnalyzer() {
	}

	/** ブロック・機械の解析。 */
	public static AnalysisReport analyze(ServerLevel level, BlockPos pos, @Nullable String viewerName) {
		BlockState state = level.getBlockState(pos);
		ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		String namespace = blockId == null ? "minecraft" : blockId.getNamespace();
		String modName = ModScanner.displayName(namespace);
		BlockEntity be = level.getBlockEntity(pos);
		List<String> details = new ArrayList<>();
		details.add("対象: " + (blockId == null ? "unknown" : blockId.toString()) + "（Mod: " + modName + "）");
		details.add("座標: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());

		if (be == null) {
			details.add("ブロックエンティティを持たない静的ブロックです。機械的稼働は検出されません。");
			return new AnalysisReport(Tone.ANALYSIS, "ブロック解析結果", details, Urgency.INFO);
		}

		String beClass = be.getClass().getName();
		details.add("機構クラス: " + beClass);
		details.add("系統: " + classifyFamily(beClass, blockId == null ? "" : blockId.getPath()).name());

		// 1. NBT からの数値抽出
		Map<String, Number> numbers = snapshotNumbers(be, level);
		if (!numbers.isEmpty()) {
			details.add("稼働パラメータ（NBT）: " + formatNumbers(numbers));
		}

		// 2. リフレクションによる既知API読み出し
		List<String> reflected = reflectParameters(be);
		if (!reflected.isEmpty()) {
			details.add("稼働パラメータ（API）: " + String.join(" / ", reflected));
		}

		// 3. インベントリ状況
		if (be instanceof Container container) {
			int used = 0;
			int totalSlots = container.getContainerSize();
			double fill = 0.0;
			for (int i = 0; i < totalSlots; i++) {
				ItemStack stack = container.getItem(i);
				if (!stack.isEmpty()) {
					used++;
					fill += (double) stack.getCount() / Math.max(1, stack.getMaxStackSize());
				}
			}
			details.add(String.format("内部インベントリ: %d/%d スロット使用 (充填率 %.0f%%)",
					used, totalSlots, totalSlots == 0 ? 0.0 : fill / totalSlots * 100.0));
			if (used == 0) {
				details.add("《告》入力が空です。搬入経路（ホッパー／シュート／ベルト）を確認してください。");
			} else if (used < totalSlots && fill / Math.max(1, totalSlots) > 0.85) {
				details.add("《告》出力先が飽和しています。搬出経路の増設を推奨します。");
			}
		}

		// 周辺の動力源と同系統機械
		FamilyCounts counts = countNearby(level, pos, 16);
		details.add("周辺16ブロック: 動力源 " + counts.powerSources + " / 消費機械 " + counts.consumers
				+ " / 同系統 " + counts.sameFamily);
		if (counts.consumers > 0 && counts.powerSources == 0) {
			details.add("《告》動力源が検出されません。水車・風車・蒸気機関などの増設を推奨します。");
		} else if (counts.powerSources > 0 && counts.consumers > counts.powerSources * 4) {
			details.add("《告》負荷過多の兆候。動力源の増設または機械の分散配置を推奨します。");
		}
		return new AnalysisReport(Tone.ANALYSIS,
				blockId == null ? "機械解析" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + " の解析結果",
				details, Urgency.INFO);
	}

	/** 機械系統の推定。 */
	public enum Family {
		/** 動力・回転系（Create など）。 */
		KINETIC,
		/** 工業・搬送系。 */
		INDUSTRIAL,
		/** エネルギー・電力系。 */
		POWER,
		/** 農業・加工系。 */
		AGRICULTURE,
		/** 魔法・研究系。 */
		ARCANE,
		/** 保管・物流系。 */
		STORAGE,
		/** 国家管理ブロック。 */
		NATION,
		UNKNOWN;

		public String key() {
			return "wisdomking.family." + name().toLowerCase(Locale.ROOT);
		}
	}

	public static Family classifyFamily(String className, String blockPath) {
		String lower = (className + " " + blockPath).toLowerCase(Locale.ROOT);
		if (lower.contains("wisdomking")) {
			return Family.NATION;
		}
		if (lower.contains("create") || lower.contains("kinetic") || lower.contains("cogwheel")
				|| lower.contains("shaft") || lower.contains("cog")) {
			return Family.KINETIC;
		}
		if (lower.contains("energy") || lower.contains("power") || lower.contains("electric")) {
			return Family.POWER;
		}
		if (lower.contains("farm") || lower.contains("crop") || lower.contains("garden")) {
			return Family.AGRICULTURE;
		}
		if (lower.contains("arcane") || lower.contains("spell") || lower.contains("altar")
				|| lower.contains("rune")) {
			return Family.ARCANE;
		}
		if (lower.contains("chest") || lower.contains("barrel") || lower.contains("storage")
				|| lower.contains("tank")) {
			return Family.STORAGE;
		}
		if (lower.contains("machine") || lower.contains("assembler") || lower.contains("crusher")
				|| lower.contains("mixer") || lower.contains("press")) {
			return Family.INDUSTRIAL;
		}
		return Family.UNKNOWN;
	}

	public static boolean isPowerSource(String className, String blockPath) {
		String lower = (className + " " + blockPath).toLowerCase(Locale.ROOT);
		for (String hint : POWER_SOURCE_HINTS) {
			if (lower.contains(hint)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isConsumer(String className, String blockPath) {
		String lower = (className + " " + blockPath).toLowerCase(Locale.ROOT);
		for (String hint : CONSUMER_HINTS) {
			if (lower.contains(hint)) {
				return true;
			}
		}
		return false;
	}

	/** 周辺の動力源・消費機械・同系統機械の数。 */
	public record FamilyCounts(int powerSources, int consumers, int sameFamily) {
	}

	public static FamilyCounts countNearby(ServerLevel level, BlockPos center, int radius) {
		int power = 0;
		int consumers = 0;
		int same = 0;
		String referenceFamily = null;
		try {
			BlockEntity centerBe = level.getBlockEntity(center);
			if (centerBe != null) {
				referenceFamily = classifyFamily(centerBe.getClass().getName(), "").name();
			}
			for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
					center.offset(radius, radius, radius))) {
				BlockEntity be = level.getBlockEntity(pos);
				if (be == null) {
					continue;
				}
				String className = be.getClass().getName();
				String path = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()) == null
						? "" : BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();
				if (isPowerSource(className, path)) {
					power++;
				} else if (isConsumer(className, path)) {
					consumers++;
				}
				if (referenceFamily != null && classifyFamily(className, path).name().equals(referenceFamily)) {
					same++;
				}
			}
		} catch (Throwable t) {
			WKLog.warn("machine neighborhood scan failed: " + t);
		}
		return new FamilyCounts(power, consumers, same);
	}

	/** NBT から数値キーを抽出する。 */
	public static Map<String, Number> snapshotNumbers(BlockEntity be, ServerLevel level) {
		Map<String, Number> numbers = new java.util.LinkedHashMap<>();
		try {
			CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
			collectNumbers(tag, "", numbers);
		} catch (Throwable t) {
			WKLog.warn("machine NBT snapshot failed: " + t);
		}
		return numbers;
	}

	private static void collectNumbers(CompoundTag tag, String prefix, Map<String, Number> out) {
		for (String key : tag.getAllKeys()) {
			Tag value = tag.get(key);
			String lower = key.toLowerCase(Locale.ROOT);
			boolean interesting = lower.contains("speed") || lower.contains("stress")
					|| lower.contains("energy") || lower.contains("rotation") || lower.contains("progress")
					|| lower.contains("fuel") || lower.contains("temp") || lower.contains("rate")
					|| lower.contains("capacity") || lower.contains("power") || lower.contains("rpm");
			if (!interesting) {
				continue;
			}
			if (value instanceof net.minecraft.nbt.NumericTag numeric) {
				out.put(prefix + key, numeric.getAsNumber());
			} else if (value instanceof CompoundTag child) {
				collectNumbers(child, prefix + key + ".", out);
			}
		}
	}

	private static String formatNumbers(Map<String, Number> numbers) {
		StringBuilder builder = new StringBuilder();
		int count = 0;
		for (Map.Entry<String, Number> entry : numbers.entrySet()) {
			if (count++ >= 6) {
				builder.append(" ...");
				break;
			}
			if (builder.length() > 0) {
				builder.append(" / ");
			}
			builder.append(entry.getKey()).append('=').append(format(entry.getValue()));
		}
		return builder.toString();
	}

	private static String format(Number number) {
		double value = number.doubleValue();
		if (Math.abs(value - Math.rint(value)) < 1.0E-6) {
			return String.valueOf((long) value);
		}
		return String.format("%.3f", value);
	}

	/** 既知メソッド名による安全なパラメータ読み出し。 */
	private static List<String> reflectParameters(Object be) {
		List<String> out = new ArrayList<>();
		String[] methods = {"getSpeed", "getRotationSpeed", "getStressImpact", "getStressCapacity",
				"getStress", "getEnergyStored", "getMaxEnergyStored", "getProgress", "getProcessingSpeed"};
		for (String name : methods) {
			try {
				Method method = be.getClass().getMethod(name);
				method.setAccessible(true);
				Object result = method.invoke(be);
				if (result instanceof Number number) {
					out.add(name.replace("get", "") + "=" + format(number));
				} else if (result != null && result.getClass().isEnum()) {
					out.add(name.replace("get", "") + "=" + result);
				}
			} catch (Throwable ignored) {
				// メソッドが存在しないModは静かにスキップする
			}
		}
		return out;
	}

	/** 稼働状態を表すハッシュ（停止検出に使用）。 */
	public static int activityHash(BlockEntity be, ServerLevel level) {
		Map<String, Number> numbers = snapshotNumbers(be, level);
		int hash = 17;
		for (Map.Entry<String, Number> entry : numbers.entrySet()) {
			hash = hash * 31 + entry.getKey().hashCode();
			hash = hash * 31 + Double.hashCode(entry.getValue().doubleValue());
		}
		if (be instanceof Container container) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				hash = hash * 31 + stack.getItem().hashCode() * 31 + stack.getCount();
			}
		}
		return hash;
	}
}
