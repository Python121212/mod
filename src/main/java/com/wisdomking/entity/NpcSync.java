package com.wisdomking.entity;

import com.wisdomking.WK;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NpcRecord;
import com.wisdomking.network.WKPayloads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 視界内 NPC の外見・装備情報をクライアントへ配信する。
 *
 * <p>NPC は独自エンティティのため、国家色・役割・性格は通常のチャンク同期には
 * 含まれない。ここで 1 秒ごとに周辺 NPC の情報をまとめて送ることで、
 * クライアント側の動的スキン生成（{@code NpcSkinManager}）と
 * HUD 表示（{@code TargetOverlay}）が正確な色・役割を使用できる。</p>
 *
 * <p>ペイロードは 1 プレイヤーあたり 1 通/秒に集約しているため、帯域消費は小さい。</p>
 */
public final class NpcSync {
	private static final double SYNC_RADIUS = 64.0D;

	private NpcSync() {
	}

	/** サーバー tick（1秒間隔で実行される）。 */
	public static void tick(ServerLevel level) {
		if (level.getGameTime() % 20L != 0L) {
			return;
		}
		try {
			List<ServerPlayer> players = level.players();
			if (players.isEmpty()) {
				return;
			}
			for (ServerPlayer player : players) {
				CompoundTag tag = new CompoundTag();
				ListTag list = new ListTag();
				for (Entity entity : level.getAllEntities()) {
					if (!(entity instanceof WKNpcEntity npc)) {
						continue;
					}
					if (npc.distanceToSqr(player) > SYNC_RADIUS * SYNC_RADIUS) {
						continue;
					}
					list.add(describe(npc));
				}
				tag.put("Npcs", list);
				tag.putInt("Count", list.size());
				ServerPlayNetworking.send(player, new WKPayloads.NpcSyncPayload(tag));
			}
		} catch (Throwable t) {
			WK.LOG.warn("[wisdomking] npc sync failed: {}", t.toString());
		}
	}

	private static CompoundTag describe(WKNpcEntity npc) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Id", npc.getId());
		Nation nation = npc.nation();
		NpcRecord record = npc.record();
		tag.putString("Name", record != null ? record.name : npc.getName().getString());
		tag.putString("Nation", nation == null ? "" : nation.name);
		tag.putInt("Color", nation == null ? 0x8899AA : nation.color);
		tag.putString("Role", npc.role().name());
		tag.putString("Personality", npc.personality().name());
		tag.putString("Style", npc.attackStyle().name());
		tag.putFloat("Hp", npc.getHealth());
		tag.putFloat("MaxHp", npc.getMaxHealth());
		ItemStack main = npc.getMainHandItem();
		tag.putString("Weapon", main.isEmpty() ? "" : main.getHoverName().getString());
		ItemStack chest = npc.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		tag.putString("Armor", chest.isEmpty() ? "" : chest.getHoverName().getString());
		tag.putBoolean("King", npc.role() == com.wisdomking.nation.NationRole.KING);
		return tag;
	}
}
