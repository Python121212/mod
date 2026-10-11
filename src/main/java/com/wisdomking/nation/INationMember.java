package com.wisdomking.nation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 国家に所属する実体（NPCエンティティ・プレイヤー側の代理オブジェクト）が実装する契約。
 *
 * <p>国家システム側がエンティティの具象クラスに依存しないための境界インタフェースである。</p>
 */
public interface INationMember {
	@Nullable
	UUID getNationId();

	NationRole getNationRole();

	default boolean belongsTo(UUID nation) {
		UUID own = getNationId();
		return own != null && own.equals(nation);
	}
}
