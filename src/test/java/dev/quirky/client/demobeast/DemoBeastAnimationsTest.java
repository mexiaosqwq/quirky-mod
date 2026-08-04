package dev.quirky.client.demobeast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.quirky.TestBootstrap;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.AnimationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * demo_beast 动画运行时冒烟测试：26.2 AnimationDefinition → bake → apply 全链路。
 * ageInTicks→毫秒换算：getTimeInMillis = (ageInTicks - startTick) × 50（mcsrc AnimationState.java:43）。
 * walk 2s：leg_front_l @0.5s = -30°；idle 1.5s：body @0.75s y=0.5（MC y 向下取负）；
 * tail_wag 1s：tail @0.5s = -25°。
 */
class DemoBeastAnimationsTest {
	private ModelPart modelRoot;

	@BeforeAll
	static void boot() {
		TestBootstrap.boot();
	}

	// KeyframeAnimation.apply 不自动 reset 姿态，每个测试用独立烘焙的模型避免残留叠加
	@BeforeEach
	void freshModel() {
		modelRoot = DemoBeastModel.createBodyLayer().bakeRoot().getChild("root");
	}

	private static AnimationState started() {
		AnimationState state = new AnimationState();
		state.start(0);
		return state;
	}

	@Test
	void bakeAndApplyAllAnimationsRunsWithoutException() {
		KeyframeAnimation walk = DemoBeastAnimations.WALK.bake(modelRoot);
		KeyframeAnimation idle = DemoBeastAnimations.IDLE.bake(modelRoot);
		KeyframeAnimation wag = DemoBeastAnimations.TAIL_WAG.bake(modelRoot);

		AnimationState state = started();
		for (int tick = 0; tick < 200; tick++) { // 10 秒循环播放
			walk.apply(state, tick);
			idle.apply(state, tick);
			wag.apply(state, tick);
		}
		walk.applyWalk(1.0F, 1.0F, 2.0F, 2.5F);
		idle.applyStatic();
		wag.applyStatic();
	}

	@Test
	void walkTurnsDiagonalLegsOppositeAtHalfStep() {
		// t=0.5s = ageInTicks 10：bbmodel 对角步态 front_l/back_r +30°、front_r/back_l -30°（convert 原样输出）
		DemoBeastAnimations.WALK.bake(modelRoot).apply(started(), 10);
		ModelPart body = modelRoot.getChild("body");
		assertEquals(0.5236F, body.getChild("leg_front_l").xRot, 0.001F);
		assertEquals(-0.5236F, body.getChild("leg_front_r").xRot, 0.001F);
		assertEquals(-0.5236F, body.getChild("leg_back_l").xRot, 0.001F);
		assertEquals(0.5236F, body.getChild("leg_back_r").xRot, 0.001F);
	}

	@Test
	void idleBobsBodyDownAndTiltsHead() {
		// t=0.75s = ageInTicks 15：BB body y +0.5 → MC -0.5，叠加基础 offset -4 → -4.5
		DemoBeastAnimations.IDLE.bake(modelRoot).apply(started(), 15);
		ModelPart body = modelRoot.getChild("body");
		assertEquals(-4.5F, body.y, 0.001F);
		assertNotEquals(0.0F, body.getChild("head").xRot, 0.001F);
	}

	@Test
	void tailWagSweepsYaw() {
		// bbmodel tail zRot：+25@0 → -25@0.5 → +25@1（convert 原样输出）
		// t=0.5s = ageInTicks 10 → -25° = -0.4363；t=0.25s=5 线性中段 → 0°
		KeyframeAnimation wag = DemoBeastAnimations.TAIL_WAG.bake(modelRoot);
		ModelPart tail = modelRoot.getChild("body").getChild("tail");
		wag.apply(started(), 10);
		assertEquals(-0.4363F, tail.zRot, 0.001F);
		// 渲染链路里每帧 setupAnim 先 resetPose（Model.setupAnim 基类默认），测试同样处理
		modelRoot.getAllParts().forEach(ModelPart::resetPose);
		wag.apply(started(), 5);
		assertEquals(0.0F, tail.zRot, 0.001F);
	}
}
