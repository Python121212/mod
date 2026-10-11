package com.wisdomking.nation;

import net.minecraft.nbt.CompoundTag;

/**
 * 二国間関係。関係値 (-100..+100)・状態・条約・戦争スコアを保持する。
 */
public final class Relation {
	public static final float MIN = -100.0f;
	public static final float MAX = 100.0f;

	/** 相手国。 */
	public final java.util.UUID other;
	/** 関係値。 */
	public float value;
	/** 状態。 */
	public RelationState state = RelationState.NEUTRAL;
	/** 戦争スコア (-100=相手の圧勝 .. +100=我方の圧勝)。 */
	public int warScore;
	/** 条約の残りtick（停戦・同盟）。 */
	public long treatyTicks;
	/** 最後に戦争を開始したtick。 */
	public long warStartTick;

	public Relation(java.util.UUID other) {
		this.other = other;
	}

	public Relation(java.util.UUID other, float value) {
		this.other = other;
		this.value = value;
	}

	public void add(float delta) {
		this.value = Math.max(MIN, Math.min(MAX, this.value + delta));
		if (this.state != RelationState.WAR && this.state != RelationState.VASSAL
				&& this.state != RelationState.OVERLORD && this.state != RelationState.ALLIED
				&& this.state != RelationState.TRUCE) {
			if (this.value >= 60.0f) {
				this.state = RelationState.FRIENDLY;
			} else if (this.value >= 25.0f) {
				this.state = RelationState.TRADE_PACT;
			} else if (this.value <= -50.0f) {
				this.state = RelationState.TENSE;
			} else {
				this.state = RelationState.NEUTRAL;
			}
		}
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Other", other.toString());
		tag.putFloat("Value", value);
		tag.putString("State", state.name());
		tag.putInt("WarScore", warScore);
		tag.putLong("Treaty", treatyTicks);
		tag.putLong("WarStart", warStartTick);
		return tag;
	}

	public static Relation load(CompoundTag tag) {
		java.util.UUID id;
		try {
			id = java.util.UUID.fromString(tag.getString("Other"));
		} catch (Throwable t) {
			id = java.util.UUID.randomUUID();
		}
		Relation rel = new Relation(id);
		rel.value = tag.getFloat("Value");
		rel.state = RelationState.byName(tag.getString("State"), RelationState.NEUTRAL);
		rel.warScore = tag.getInt("WarScore");
		rel.treatyTicks = tag.getLong("Treaty");
		rel.warStartTick = tag.getLong("WarStart");
		return rel;
	}
}
