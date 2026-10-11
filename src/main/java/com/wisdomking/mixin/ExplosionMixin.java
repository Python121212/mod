package com.wisdomking.mixin;

import com.wisdomking.core.WKLog;
import com.wisdomking.world.TerritoryProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 爆発による領土内ブロックの破壊を抑止する Mixin。
 *
 * <p><b>安全設計（仕様 ⑪ 準拠）:</b></p>
 * <ul>
 *   <li>{@code require = 0} — 対象呼び出しが将来変更されても適用失敗でクラッシュせず、
 *       静かに無効化される</li>
 *   <li>{@code priority = 1100} — 他ModのMixinより後に適用され、競合時も安全</li>
 *   <li>判定ロジックは {@link TerritoryProtection} に委譲し、Mixin自体は薄く保つ</li>
 *   <li>爆発以外の保護（プレイヤー操作）は Fabric 標準イベントで処理する</li>
 * </ul>
 */
@Mixin(value = Explosion.class, priority = 1100)
public abstract class ExplosionMixin {

	/**
	 * 爆発による {@code Level#setBlock} 呼び出しを横取りし、
	 * 保護対象の座標ではブロック破壊をスキップする。
	 */
	@Redirect(
			method = "finalizeExplosion",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
			require = 0)
	private boolean wisdomking$guardTerritory(Level level, BlockPos pos, BlockState state, int flags) {
		try {
			if (!TerritoryProtection.allowExplosionAt(level, pos)) {
				return false;
			}
		} catch (Throwable t) {
			WKLog.warn("explosion guard failed safely: " + t);
		}
		return level.setBlock(pos, state, flags);
	}
}
