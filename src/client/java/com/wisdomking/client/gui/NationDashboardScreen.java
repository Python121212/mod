package com.wisdomking.client.gui;

import com.wisdomking.WK;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家運営＆智慧之王ダッシュボード。
 *
 * <p>タブ構成:</p>
 * <ul>
 *   <li>概要 — 国家統計・資源備蓄・国庫</li>
 *   <li>臣下 — NPC一覧（役職・忠誠・継承スコア）と装備配分</li>
 *   <li>外交 — 関係値・戦況、宣戦／講和／同盟の操作</li>
 *   <li>智慧之王 — 解析レポートの履歴と実行ボタン</li>
 *   <li>世界 — 全国家の地政学ビュー</li>
 * </ul>
 */
public class NationDashboardScreen extends Screen {
	private static final String[] TABS = {"概要", "臣下", "外交", "智慧之王", "世界"};
	private static final int PANEL_COLOR = 0xC0101010;
	private static final int HEADER_COLOR = 0xFF2A2A3A;

	private final CompoundTag data;
	private int tab;
	private int scroll;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	private final List<String> lines = new ArrayList<>();

	public NationDashboardScreen(CompoundTag data) {
		super(Component.literal("Wisdom King - Nation Dashboard"));
		this.data = data;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(this.width - 40, 420);
		panelHeight = Math.min(this.height - 60, 240);
		panelX = (this.width - panelWidth) / 2;
		panelY = (this.height - panelHeight) / 2;

		int tabWidth = panelWidth / TABS.length;
		for (int i = 0; i < TABS.length; i++) {
			final int index = i;
			addRenderableWidget(Button.builder(Component.literal(TABS[i]), button -> {
				tab = index;
				scroll = 0;
			}).bounds(panelX + i * tabWidth, panelY - 22, tabWidth - 2, 20).build());
		}

		// 操作ボタン（タブごと）
		int buttonY = panelY + panelHeight + 4;
		if (tab == 2) {
			addRenderableWidget(Button.builder(Component.literal("戦況解析"), button ->
					com.wisdomking.client.WKClientNetwork.sendAction("war_report", ""))
					.bounds(panelX, buttonY, 80, 20).build());
			addRenderableWidget(Button.builder(Component.literal("国力比較"), button ->
					com.wisdomking.client.WKClientNetwork.sendAction("threat_report", ""))
					.bounds(panelX + 84, buttonY, 80, 20).build());
		} else if (tab == 3) {
			addRenderableWidget(Button.builder(Component.literal("全Mod走査"), button ->
					com.wisdomking.client.WKClientNetwork.sendAction("scan_mods", ""))
					.bounds(panelX, buttonY, 90, 20).build());
			addRenderableWidget(Button.builder(Component.literal("機械解析"), button ->
					com.wisdomking.client.WKClientNetwork.sendAction("automation_advice", ""))
					.bounds(panelX + 94, buttonY, 90, 20).build());
			addRenderableWidget(Button.builder(Component.literal("統合報告"), button ->
					com.wisdomking.client.WKClientNetwork.sendAction("full_briefing", ""))
					.bounds(panelX + 188, buttonY, 90, 20).build());
		} else if (tab == 1) {
			addRenderableWidget(Button.builder(Component.literal("装備を自動配分"), button ->
					com.wisdomking.client.WKClientNetwork.sendAction("assign_equipment", ""))
					.bounds(panelX, buttonY, 120, 20).build());
			addRenderableWidget(Button.builder(Component.literal("土木: 道路"), button ->
					com.wisdomking.client.WKClientNetwork.sendAction("civil_roads", ""))
					.bounds(panelX + 124, buttonY, 100, 20).build());
		} else if (tab == 4) {
			addRenderableWidget(Button.builder(Component.literal("世界情勢"), button ->
					com.wisdomking.client.WKClientNetwork.sendAction("request_dashboard", ""))
					.bounds(panelX, buttonY, 100, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("閉じる"), button -> onClose())
				.bounds(panelX + panelWidth - 60, buttonY, 60, 20).build());

		buildLines();
	}

	private void buildLines() {
		lines.clear();
		if (!data.getBoolean("valid")) {
			lines.add("§c" + data.getString("message"));
			lines.add("");
			lines.add("§7世界の国家数: " + data.getInt("nationCount"));
			appendWorld();
			return;
		}
		switch (tab) {
			case 0 -> appendOverview();
			case 1 -> appendSubjects();
			case 2 -> appendDiplomacy();
			case 3 -> appendRaphael();
			case 4 -> appendWorld();
			default -> appendOverview();
		}
	}

	private void appendOverview() {
		lines.add("§6" + data.getString("name") + " §7[" + data.getString("ideology") + "]");
		lines.add("§f王: §e" + data.getString("king") + " §7(第" + data.getInt("generation")
				+ "代 / 性格 " + data.getString("kingPersonality") + ")");
		lines.add(String.format("§f人口 §e%d §7/ 兵力 §e%d §7/ 総合戦力 §e%.1f",
				data.getInt("population"), data.getInt("army"), data.getFloat("militaryPower")));
		lines.add(String.format("§f不満度 §e%.0f%% §7/ 正当性 §e%.0f%% §7/ 士気 §e%.0f%% / 技術 §e%.0f%%",
				data.getFloat("unrest") * 100, data.getFloat("legitimacy") * 100,
				data.getFloat("morale") * 100, data.getFloat("technology") * 100));
		lines.add(String.format("§f国庫 §e%.1f §7/ 領土 §e%d §7チャンク", data.getDouble("treasury"),
				data.getInt("chunks")));
		lines.add("§7--- 資源備蓄 ---");
		CompoundTag stock = data.getCompound("stock");
		for (String key : stock.getAllKeys()) {
			if (key.endsWith("_deficit")) {
				continue;
			}
			double value = stock.getDouble(key);
			double deficit = stock.getDouble(key + "_deficit");
			String warning = deficit > 0.55 ? " §c▲不足" : "";
			lines.add(String.format("§f%s: §e%.1f%s", key, value, warning));
		}
	}

	private void appendSubjects() {
		ListTag subjects = data.getList("subjects", 10);
		lines.add("§6臣下一覧（" + subjects.size() + " 名 / 継承スコア順）");
		for (int i = 0; i < subjects.size(); i++) {
			CompoundTag subject = subjects.getCompound(i);
			String marker = subject.getBoolean("isKing") ? "§6[王] " : "§7";
			lines.add(String.format("%s%s §7[%s/%s] 忠誠%.0f%% 人望%.0f%% 戦力%.2f",
					marker, subject.getString("name"), subject.getString("role"),
					subject.getString("personality"), subject.getFloat("loyalty") * 100,
					subject.getFloat("popularity") * 100, subject.getFloat("power")));
		}
	}

	private void appendDiplomacy() {
		ListTag list = data.getList("diplomacy", 10);
		lines.add("§6外交一覧");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag other = list.getCompound(i);
			String color = other.getBoolean("atWar") ? "§c" : "§f";
			lines.add(String.format("%s%s §7[%s] 関係値 %.0f / 戦力比 %.2f / 王 %s",
					color, other.getString("name"), other.getString("state"),
					other.getFloat("value"), other.getFloat("ratio"), other.getString("king")));
		}
		lines.add("§7--- 戦況 ---");
		ListTag wars = data.getList("wars", 10);
		if (wars.isEmpty()) {
			lines.add("§7交戦中の国家はありません。");
		}
		for (int i = 0; i < wars.size(); i++) {
			CompoundTag war = wars.getCompound(i);
			lines.add(String.format("§c%s との戦争 §7(優勢度 %d / %s / 事由 %s)",
					war.getString("enemy"), war.getInt("score"), war.getString("phase"),
					war.getString("casus")));
		}
	}

	private void appendRaphael() {
		lines.add("§b智慧之王・解析レポート履歴");
		ListTag reports = data.getList("reports", 10);
		if (reports.isEmpty()) {
			lines.add("§7解析レポートはまだありません。Kキーで対象を解析してください。");
		}
		for (int i = 0; i < reports.size(); i++) {
			CompoundTag report = reports.getCompound(i);
			lines.add("§b" + report.getString("Tone") + "§f " + report.getString("Headline"));
			ListTag details = report.getList("Details", 8);
			for (int j = 0; j < Math.min(3, details.size()); j++) {
				lines.add("§7  └ " + details.getString(j));
			}
		}
	}

	private void appendWorld() {
		ListTag world = data.getList("world", 10);
		lines.add("§6世界の国家（" + world.size() + "）");
		for (int i = 0; i < world.size(); i++) {
			CompoundTag nation = world.getCompound(i);
			String tag = nation.getBoolean("player") ? "§a[人間] " : (nation.getBoolean("vassal") ? "§e[属国] " : "§7");
			lines.add(String.format("%s%s §7(%s / 王 %s / 人口 %d / 戦力 %.1f / 領土 %d)%s",
					tag, nation.getString("name"), nation.getString("ideology"),
					nation.getString("king"), nation.getInt("population"), nation.getFloat("power"),
					nation.getInt("chunks"), nation.getBoolean("atWar") ? " §c[戦時]" : ""));
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(panelX - 4, panelY - 4, panelX + panelWidth + 4, panelY + panelHeight + 4, PANEL_COLOR);
		graphics.fill(panelX - 4, panelY - 4, panelX + panelWidth + 4, panelY, HEADER_COLOR);
		graphics.drawString(this.font, this.title, panelX, panelY - 16, 0xFFFFFF, true);

		int lineY = panelY + 4 - scroll * 10;
		int maxLines = (panelHeight - 8) / 10;
		int total = lines.size();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - maxLines)));
		for (int i = 0; i < total; i++) {
			int y = lineY + i * 10;
			if (y < panelY + 2 || y > panelY + panelHeight - 10) {
				continue;
			}
			graphics.drawString(this.font, lines.get(i), panelX + 4, y, 0xFFFFFF, false);
		}
		if (total > maxLines) {
			graphics.drawString(this.font, "§7▲▼ スクロール (" + (scroll + 1) + "/" + (total - maxLines + 1) + ")",
					panelX + 4, panelY + panelHeight - 10, 0xAAAAAA, false);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY < 0) {
			scroll++;
		} else if (scrollY > 0) {
			scroll = Math.max(0, scroll - 1);
		}
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
