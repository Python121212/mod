package com.wisdomking.entity;

import com.wisdomking.WK;
import com.wisdomking.core.WKLog;
import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import com.wisdomking.nation.NationRole;
import com.wisdomking.nation.NpcRecord;
import com.wisdomking.nation.Personality;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * NPC エンティティ型の登録とスポーン補助。
 *
 * <p>柔軟な識別子（{@code wisdomking:npc}）で登録し、属性は
 * {@link WKNpcEntity#createAttributes()} を Fabric 標準レジストリへ渡す。
 * 個体ごとの能力差は {@code NationRole} に応じて {@code bind()} 時に上書きする。</p>
 */
public final class WKNpcEntities {
	public static final String NPC_PATH = "npc";

	@Nullable
	public static EntityType<WKNpcEntity> WK_NPC;

	private WKNpcEntities() {
	}

	public static void init() {
		try {
			WK_NPC = net.minecraft.core.Registry.register(BuiltInRegistries.ENTITY_TYPE, WK.id(NPC_PATH),
					EntityType.Builder.of(WKNpcEntity::new, MobCategory.MISC)
							.sized(0.6F, 1.8F)
							.clientTrackingRange(10)
							.updateInterval(3)
							.build("wisdomking:npc"));
			FabricDefaultAttributeRegistry.register(WK_NPC, WKNpcEntity.createAttributes());
			WKLog.info("《解析完了》NPC エンティティを登録しました。");
		} catch (Throwable t) {
			WKLog.error("failed to register npc entity", t);
		}
	}

	/** 国家の構成員を世界へ実体化する。上限に達した場合は何もしない。 */
	@Nullable
	public static WKNpcEntity spawn(ServerLevel level, Nation nation, NpcRecord record, BlockPos pos) {
		if (WK_NPC == null) {
			return null;
		}
		if (countNpcs(level, nation.id) >= com.wisdomking.core.WKConfig.get().npcEntityCap) {
			return null;
		}
		try {
			WKNpcEntity npc = WK_NPC.spawn(level, pos, MobSpawnType.EVENT);
			if (npc == null) {
				return null;
			}
			npc.bind(nation, record, pos);
			record.lastPos = com.wisdomking.nation.WorldPos.of(level, pos);
			record.lastSeenTick = level.getGameTime();
			level.addFreshEntity(npc);
			return npc;
		} catch (Throwable t) {
			WKLog.warn("npc spawn failed: " + t);
			return null;
		}
	}

	/** 指定国家の実体化済み NPC 数。 */
	public static int countNpcs(ServerLevel level, java.util.UUID nationId) {
		int count = 0;
		try {
			for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
				if (entity instanceof WKNpcEntity npc && nationId.equals(npc.getNationId())) {
					count++;
				}
			}
		} catch (Throwable t) {
			WKLog.debug("npc count failed: " + t);
		}
		return count;
	}

	/** サーバー内すべての実体化済み NPC 数。 */
	public static int countAll(ServerLevel level) {
		int count = 0;
		try {
			for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
				if (entity instanceof WKNpcEntity) {
					count++;
				}
			}
		} catch (Throwable ignored) {
			// 集計失敗は無視
		}
		return count;
	}

	/** 記録（NpcRecord）に基づいて適切な位置を決めて実体化する。 */
	@Nullable
	public static WKNpcEntity materialize(ServerLevel level, Nation nation, NpcRecord record) {
		BlockPos target = record.lastPos != null && record.lastPos.dimension().equals(level.dimension().location())
				? record.lastPos.pos()
				: NationManager.get() == null ? level.getSharedSpawnPos()
						: NationManager.get().territory().capitalOf(nation);
		if (target == null) {
			target = level.getSharedSpawnPos();
		}
		// 候補位置を少し散らして密集を避ける
		for (int attempt = 0; attempt < 6; attempt++) {
			BlockPos candidate = target.offset(level.random.nextInt(9) - 4, 0, level.random.nextInt(9) - 4);
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
					candidate.getX(), candidate.getZ());
			BlockPos spawnPos = new BlockPos(candidate.getX(), y, candidate.getZ());
			if (!level.getBlockState(spawnPos).isSuffocating(level, spawnPos)
					&& level.getBlockState(spawnPos.below()).isSolid()) {
				WKNpcEntity npc = spawn(level, nation, record, spawnPos);
				if (npc != null) {
					return npc;
				}
			}
		}
		return null;
	}

	/** 役割に応じた名前付き構成員を新規作成する（人口増加・補充用）。 */
	public static NpcRecord createRecord(ServerLevel level, Nation nation, NationRole role, Personality personality) {
		return new NpcRecord(java.util.UUID.randomUUID(), NationFactoryNames.random(level),
				role, personality);
	}

	/** 名前生成のための薄い境界（NationFactory への依存を1箇所に閉じ込める）。 */
	private static final class NationFactoryNames {
		private static String random(Level level) {
			try {
				return com.wisdomking.nation.NationFactory.randomPersonName(level.random);
			} catch (Throwable t) {
				return "名もなき者";
			}
		}
	}

	/** 型キー（デバッグ・タグ用）。 */
	public static ResourceKey<EntityType<?>> key() {
		return ResourceKey.create(Registries.ENTITY_TYPE, WK.id(NPC_PATH));
	}
}
