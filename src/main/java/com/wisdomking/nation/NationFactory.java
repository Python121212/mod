package com.wisdomking.nation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 国家・人物のジェネレータ。
 *
 * <p>世界開始時に 5 か国以上の自律 NPC 国家を生成する。各国は世界スポーンから
 * ランダムな方角・距離に首都を置き、資源格差（バイオーム分布）によって
 * 交易・侵略の動機が自然発生するように配置する。</p>
 */
public final class NationFactory {
	private static final String[] PREFIX = {
			"紅蓮", "蒼天", "鉄槌", "白金", "黒曜", "翡翠", "黄金", "銀霜", "紫電", "碧海",
			"暁", "常夜", "雷鳴", "砂塵", "氷結", "火輪", "星霜", "月影"
	};
	private static final String[] SUFFIX = {
			"王国", "帝国", "連邦", "公国", "共和国", "首長国", "評議会", "神権国"
	};
	private static final String[] PERSONAL = {
			"アルド", "セラフィナ", "グレン", "リュカ", "ミレイユ", "ヴァルド", "イザベラ", "ノックス",
			"ティア", "カイン", "エレナ", "ダリウス", "ソフィア", "レオン", "マリエル", "オルグ",
			"シエル", "ハロルド", "ヴィオラ", "ザカリー", "ユリア", "バルド", "ネメア", "ロウエン"
	};
	private static final int[] COLOR_PALETTE = {
			0xC0392B, 0x2980B9, 0x27AE60, 0x8E44AD, 0xF39C12, 0x16A085,
			0xD35400, 0x2C3E50, 0xE74C3C, 0x1ABC9C, 0x9B59B6, 0xE67E22
	};

	private NationFactory() {
	}

	public static String randomNationName(Random random) {
		return PREFIX[random.nextInt(PREFIX.length)] + SUFFIX[random.nextInt(SUFFIX.length)];
	}

	public static String randomPersonName(Random random) {
		return PERSONAL[random.nextInt(PERSONAL.length)];
	}

	/**
	 * 初期国家群を生成する。
	 *
	 * @param count 生成する国家数（仕様上 5 以上）
	 */
	public static List<Nation> createInitialNations(MinecraftServer server, int count, long worldSeed) {
		Random random = new Random(worldSeed ^ 0x5A17C0DEL);
		List<Nation> nations = new ArrayList<>();
		ServerLevel overworld = server.overworld();
		BlockPos spawn = overworld.getSharedSpawnPos();
		int actual = Math.max(5, count);
		for (int i = 0; i < actual; i++) {
			double angle = (Math.PI * 2.0 / actual) * i + random.nextDouble() * 0.35;
			int distance = 320 + random.nextInt(900);
			int x = spawn.getX() + (int) (Math.cos(angle) * distance);
			int z = spawn.getZ() + (int) (Math.sin(angle) * distance);
			int y = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
			Nation nation = createNation(new UUID(worldSeed + i * 7919L, random.nextLong()),
					randomNationName(random),
					COLOR_PALETTE[i % COLOR_PALETTE.length],
					Ideology.values()[random.nextInt(Ideology.values().length)],
					new WorldPos(Level.OVERWORLD.location(), new BlockPos(x, y, z)));
			nations.add(nation);
		}
		return nations;
	}

	/** 国家オブジェクトを生成し、初期の統治者（国王NPC）台帳を用意する。 */
	public static Nation createNation(UUID id, String name, int color, Ideology ideology, WorldPos capital) {
		Nation nation = new Nation(id, name, color, ideology);
		nation.capital = capital;
		nation.foundedTick = 0L;
		// 国王・王子・将軍・官僚の初期台帳。実体は首都チャンクがロードされた際に生成される。
		Random random = new Random(id.getLeastSignificantBits() ^ id.getMostSignificantBits());
		Personality kingPersonality = Personality.random(random);
		NpcRecord king = new NpcRecord(new UUID(random.nextLong(), random.nextLong()),
				randomPersonName(random), NationRole.KING, kingPersonality);
		king.popularity = 0.6f + random.nextFloat() * 0.3f;
		king.power = 2.4f;
		nation.addSubject(king);
		nation.kingNpc = king.id;
		nation.kingName = king.name;
		nation.kingPersonality = kingPersonality;
		for (int i = 0; i < 2; i++) {
			NpcRecord prince = new NpcRecord(new UUID(random.nextLong(), random.nextLong()),
					randomPersonName(random), NationRole.PRINCE, Personality.random(random));
			nation.addSubject(prince);
		}
		NpcRecord general = new NpcRecord(new UUID(random.nextLong(), random.nextLong()),
				randomPersonName(random), NationRole.GENERAL, Personality.random(random));
		general.power = 2.1f;
		nation.addSubject(general);
		nation.chronicle("建国: " + name + " が " + ideology.name() + " を掲げて成立した。");
		return nation;
	}

	/** 反乱・独立で新国家を建国する（動的国家数変動）。 */
	public static Nation secede(Nation parent, NpcRecord leader, WorldPos capital, Random random) {
		Nation nation = createNation(UUID.randomUUID(), randomNationName(random),
				COLOR_PALETTE[random.nextInt(COLOR_PALETTE.length)],
				Ideology.values()[random.nextInt(Ideology.values().length)], capital);
		nation.chronicle("独立: " + parent.name + " から " + leader.name + " が独立を宣言した。");
		nation.overlord = null;
		return nation;
	}
}
