package com.wisdomking.block;

import com.wisdomking.nation.Nation;
import com.wisdomking.nation.NationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * 国家所有のブロックエンティティ共通基底。
 *
 * <p>所有国家IDを保持し、ロード時に国家の領土宣言を再確認する。
 * 国家側のデータ（{@link NationManager}）とブロックを疎結合に保つための土台である。</p>
 */
public abstract class NationBlockEntity extends BlockEntity {
	protected UUID nationId;
	protected long lastSyncTick;
	/** クライアント側で受信した HUD 表示用データ。 */
	@Nullable
	private CompoundTag clientHud;

	protected NationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Nullable
	public UUID getNationId() {
		return nationId;
	}

	public void setNation(Nation nation) {
		this.nationId = nation == null ? null : nation.id;
		setChanged();
	}

	@Nullable
	public Nation nation() {
		NationManager manager = NationManager.get();
		return manager == null ? null : manager.nation(nationId);
	}

	/** 所有国家が未設定なら、設置者などから推定して登録する。 */
	public void assignIfAbsent(Nation nation) {
		if (nationId == null && nation != null) {
			setNation(nation);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (nationId != null) {
			tag.putString("Nation", nationId.toString());
		}
		tag.putLong("LastSync", lastSyncTick);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("Nation")) {
			try {
				nationId = UUID.fromString(tag.getString("Nation"));
			} catch (Throwable t) {
				nationId = null;
			}
		}
		lastSyncTick = tag.getLong("LastSync");
		if (level != null && level.isClientSide && !tag.isEmpty()) {
			clientHud = tag.copy();
		}
	}

	/** クライアントへの同期用タグ（Jade/WTHIT 相当の HUD 情報を含む）。 */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		tag.merge(describeForHud());
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** データ変更時に間隔を空けてクライアントへ再送する（毎tickの帯域消費を防ぐ）。 */
	@Override
	public void setChanged() {
		super.setChanged();
		if (level instanceof ServerLevel server && server.getGameTime() - lastSyncTick > 100L) {
			lastSyncTick = server.getGameTime();
			BlockState state = getBlockState();
			server.sendBlockUpdated(worldPosition, state, state, 2);
		}
	}

	/** 所有国家IDを付与した状態でクライアントへ同期する。 */
	public CompoundTag describeForHud() {
		if (level != null && level.isClientSide && clientHud != null) {
			return clientHud.copy();
		}
		CompoundTag tag = new CompoundTag();
		Nation nation = nation();
		tag.putString("NationName", nation == null ? "無所属" : nation.name);
		tag.putInt("NationColor", nation == null ? 0xFFFFFF : nation.color);
		tag.putString("Owner", nation == null ? "-" : nation.kingName);
		return tag;
	}
}
