package com.wisdomking.nation;

import java.util.UUID;
import java.util.Random;

/**
 * 国家の人口（NPC台帳）生成ロジック。
 *
 * <p>世界人口の約98%がNPCであるという設定を、国家ごとの台帳として表現する。
 * 実際のエンティティは首都・施設付近のチャンクがロードされた際に
 * {@code NpcSpawner} が台帳から実体化する。</p>
 */
public final class NationPopulation {
	private NationPopulation() {
	}

	/** 初期人口を生成する。 */
	public static void populateInitial(Nation nation, Random random, int citizens) {
		for (int i = 0; i < citizens; i++) {
			NationRole role;
			float roll = random.nextFloat();
			if (roll < 0.44f) {
				role = NationRole.SOLDIER;
			} else if (roll < 0.52f) {
				role = NationRole.ENGINEER;
			} else if (roll < 0.60f) {
				role = NationRole.MERCHANT;
			} else if (roll < 0.68f) {
				role = NationRole.CRAFTSMAN;
			} else if (roll < 0.73f) {
				role = NationRole.SCHOLAR;
			} else if (roll < 0.78f) {
				role = NationRole.MAGE;
			} else {
				role = NationRole.CITIZEN;
			}
			NpcRecord record = new NpcRecord(new UUID(random.nextLong(), random.nextLong()),
					NationFactory.randomPersonName(random), role, Personality.random(random));
			record.popularity = random.nextFloat();
			record.power = role.authority() * (0.6f + random.nextFloat() * 0.9f);
			record.birthTick = -random.nextInt(200000);
			nation.addSubject(record);
		}
	}

	/** 人口上限（領土・技術・施設で決まる）。 */
	public static int populationCap(Nation nation) {
		return 24 + nation.claimedChunks.size() * 2 + (int) (nation.technology * 40.0f);
	}

	/** 毎tickの人口動態。糧食が余っていれば増え、不足なら餓死・亡命が発生する。 */
	public static boolean grow(Nation nation, Random random, boolean foodSurplus) {
		int cap = populationCap(nation);
		if (nation.population() < cap && foodSurplus && random.nextFloat() < 0.35f) {
			NationRole role = random.nextFloat() < 0.5f ? NationRole.CITIZEN
					: (random.nextFloat() < 0.4f ? NationRole.SOLDIER : NationRole.ENGINEER);
			NpcRecord record = new NpcRecord(new UUID(random.nextLong(), random.nextLong()),
					NationFactory.randomPersonName(random), role, Personality.random(random));
			record.birthTick = 0L;
			nation.addSubject(record);
			return true;
		}
		if (!foodSurplus && random.nextFloat() < 0.3f && nation.population() > 8) {
			// 食料不足: 最も忠誠の低い市民が亡命する
			NpcRecord worst = null;
			for (NpcRecord rec : nation.subjects()) {
				if (rec.role == NationRole.CITIZEN && (worst == null || rec.loyalty < worst.loyalty)) {
					worst = rec;
				}
			}
			if (worst != null) {
				nation.removeSubject(worst.id);
				return true;
			}
		}
		return false;
	}
}
