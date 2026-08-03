package dev.quirky.mixin;

import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 供 CampfireBlockMixin 读写 CampfireBlockEntityMixin 追加的烟色/夜光字段。
 * 接口 mixin 会被应用到目标类，运行时可直接 (CampfireBlockEntityAccessor) cast。
 * 必须排在 CampfireBlockEntityMixin 之后应用（同一 mixin 配置内按列表顺序，字段先存在）。
 */
@Mixin(CampfireBlockEntity.class)
public interface CampfireBlockEntityAccessor {

	@Accessor("quirky$smokeColor")
	int quirky$getSmokeColor();

	@Accessor("quirky$smokeColor")
	void quirky$setSmokeColor(int color);

	@Accessor("quirky$glow")
	boolean quirky$getGlow();

	@Accessor("quirky$glow")
	void quirky$setGlow(boolean glow);
}
