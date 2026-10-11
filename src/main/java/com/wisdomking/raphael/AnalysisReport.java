package com.wisdomking.raphael;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 智慧之王の単一の解析レポート。
 *
 * <p>チャット表示・GUI 表示・音声（TTS）読み上げ・ネットワーク送信に共通で用いる
 * 不変データである。{@link #spokenText()} は読み上げ用に標識と本文を連結した文字列を返す。</p>
 */
public record AnalysisReport(Tone tone, String headline, List<String> details, Urgency urgency) {

	public AnalysisReport {
		details = List.copyOf(details);
	}

	public static AnalysisReport of(Tone tone, String headline, Urgency urgency, String... details) {
		return new AnalysisReport(tone, headline, List.of(details), urgency);
	}

	/** チャット用の整形（標識 + 見出し、続けて詳細行）。 */
	public List<Component> toChatLines() {
		List<Component> lines = new ArrayList<>();
		MutableComponent head = Component.literal(tone.token() + " ")
				.withStyle(tone.color())
				.append(Component.literal(headline).withStyle(ChatFormatting.WHITE));
		lines.add(head);
		for (String detail : details) {
			lines.add(Component.literal("§7  └ §f" + detail));
		}
		return lines;
	}

	/** 読み上げ用テキスト。 */
	public String spokenText() {
		StringBuilder builder = new StringBuilder(tone.token());
		builder.append(' ').append(headline);
		for (String detail : details) {
			builder.append('、').append(detail);
		}
		return builder.toString();
	}

	/** 一行要約（HUD・ログ用）。 */
	public String oneLine() {
		return tone.token() + " " + headline;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Tone", tone.name());
		tag.putString("Headline", headline);
		tag.putString("Urgency", urgency.name());
		ListTag list = new ListTag();
		for (String detail : details) {
			list.add(net.minecraft.nbt.StringTag.valueOf(detail));
		}
		tag.put("Details", list);
		return tag;
	}

	public static AnalysisReport load(CompoundTag tag) {
		List<String> details = new ArrayList<>();
		ListTag list = tag.getList("Details", 8);
		for (int i = 0; i < list.size(); i++) {
			details.add(list.getString(i));
		}
		return new AnalysisReport(Tone.byName(tag.getString("Tone"), Tone.REPORT),
				tag.getString("Headline"), details,
				Urgency.byName(tag.getString("Urgency"), Urgency.INFO));
	}
}
