package com.wisdomking.client.compat;

import com.wisdomking.block.NationBlockEntity;
import com.wisdomking.client.render.NpcInfoCache;
import com.wisdomking.equipment.CombatRole;
import com.wisdomking.equipment.ItemClassifier;
import com.wisdomking.nation.NationRole;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 照準先の情報を画面に常時表示する HUD。
 *
 * <p>Jade / WTHIT が無い環境でも「所有者国家・領土保護・NPCの役割・装備・HP」を
 * 確認できるよう、本Mod独自の軽量オーバーレイとして実装する
 * （Jade / WTHIT がある場合は併用しても競合しない）。</p>
 *
 * <p>表示内容:</p>
 * <ul>
 *   <li>国家ブロック: 所有国家名・首都/施設種別・保護状態・国庫残高</li>
 *   <li>NPC: 名前・国家・役割・性格・攻撃スタイル・HP・装備</li>
 *   <li>手に持つアイテム: 汎用分類（近接/遠隔/盾/鎧）とMod名</li>
 * </ul>
 */
public final class TargetOverlay {
	private TargetOverlay() {
	}

	public static void init() {
		HudRenderCallback.EVENT.register(TargetOverlay::onRender);
	}

	private static void onRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.player == null || client.level == null || client.options.hideGui) {
				return;
			}
			HitResult hit = client.hitResult;
			List<String> lines = new ArrayList<>();
			int accent = 0xFFB6D7F5;
			if (hit instanceof EntityHitResult entityHit) {
				Entity entity = entityHit.getEntity();
				NpcInfoCache.Entry info = NpcInfoCache.get(entity.getId());
				if (info != null) {
					accent = 0xFF000000 | (info.color() & 0xFFFFFF);
					lines.add("§l" + info.name() + (info.king() ? " §6[国王]" : ""));
					lines.add("§7国家: §f" + (info.nation().isEmpty() ? "無所属" : info.nation())
							+ " §7役割: §f" + roleName(info.role())
							+ " §7性格: §f" + personalityName(info.personality()));
					lines.add("§7HP: §f" + Math.round(info.hp()) + "/" + Math.round(info.maxHp())
							+ " §7戦法: §f" + info.style());
					if (!info.weapon().isEmpty()) {
						lines.add("§7武器: §f" + info.weapon());
					}
					if (!info.armor().isEmpty()) {
						lines.add("§7防具: §f" + info.armor());
					}
				} else if (entity instanceof LivingEntity living
						&& com.wisdomking.compat.CompatRegistry.present("create")) {
					// 他Modの強力なMob: HP のみ簡易表示（Create 導入環境）
					lines.add("§7" + living.getName().getString() + " §c" + Math.round(living.getHealth())
							+ "/" + Math.round(living.getMaxHealth()));
				}
			} else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = blockHit.getBlockPos();
				BlockEntity be = client.level.getBlockEntity(pos);
				if (be instanceof NationBlockEntity nationBe) {
					CompoundTag hud = nationBe.describeForHud();
					if (hud.getBoolean("NationName") || hud.contains("NationName")) {
						String owner = hud.getString("NationName");
						accent = 0xFF000000 | (hud.getInt("NationColor") & 0xFFFFFF);
						lines.add("§l" + client.level.getBlockState(pos).getBlock().getName().getString());
						lines.add("§7所有国家: §f" + (owner.isEmpty() ? "無所属" : owner)
								+ " §7王: §f" + hud.getString("Owner"));
						if (hud.getBoolean("Facility")) {
							lines.add("§7施設: §f" + hud.getString("FacilityType")
									+ " §7生産: §f" + hud.getInt("Produced"));
						}
						if (hud.contains("Treasury")) {
							lines.add("§7国庫: §f" + hud.getLong("Treasury"));
						}
						if (hud.getBoolean("Protected")) {
							lines.add("§a領土保護: 有効（他国・非構成員は破壊不可）");
						} else {
							lines.add("§c領土保護: 無効");
						}
					}
				}
			}

			// 手に持つアイテムの汎用分類（他Modの武器・防具も判定）
			ItemStack held = client.player.getMainHandItem();
			if (!held.isEmpty()) {
				CombatRole role = ItemClassifier.classify(held);
				if (role != null && role != CombatRole.UNKNOWN) {
					lines.add("§b《解》分類: §f" + net.minecraft.network.chat.Component.translatable(role.key())
							.getString() + " §7[" + ItemClassifier.idNamespace(held) + "]");
				}
			}

			if (lines.isEmpty()) {
				return;
			}
			int y = client.getWindow().getGuiScaledHeight() / 2 + 12;
			int x = client.getWindow().getGuiScaledWidth() / 2 + 14;
			for (String line : lines) {
				int width = client.font.width(line) + 8;
				graphics.fill(x - 4, y - 2, x - 4 + width, y + 10, 0x88000000);
				graphics.drawString(client.font, line, x, y, accent, true);
				y += 12;
			}
		} catch (Throwable t) {
			// HUD 描画の失敗でクライアントを落とさない
			com.wisdomking.WK.LOG.debug("target overlay failed: {}", t.toString());
		}
	}

	private static String roleName(NationRole role) {
		return switch (role) {
			case KING -> "国王";
			case PRINCE -> "王族";
			case GENERAL -> "将軍";
			case SOLDIER -> "兵士";
			case MAGE -> "魔導士";
			case ENGINEER -> "技師";
			case MERCHANT -> "商人";
			case CRAFTSMAN -> "職人";
			case SCHOLAR -> "学者";
			case CITIZEN -> "市民";
		};
	}

	private static String personalityName(com.wisdomking.nation.Personality personality) {
		return switch (personality) {
			case BOLD -> "豪胆";
			case CAUTIOUS -> "慎重";
			case CRUEL -> "苛烈";
			case MERCIFUL -> "仁慈";
			case GREEDY -> "強欲";
			case ZEALOUS -> "狂信";
			case LOYAL -> "忠義";
			case AMBITIOUS -> "野望";
		};
	}
}
