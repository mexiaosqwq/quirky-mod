package dev.quirky.client.deathcam;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.deathcam.DeathCamPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 死亡电影镜头客户端状态机（纯客户端，单实例静态）。
 *
 * 时序（26.2 死亡界面由服务端包驱动，见 DeathScreenDelayMixin 注释）：
 * 1. 服务端死亡 → 先到 ClientboundPlayerCombatKillPacket → {@link #onKillPacket} 记录待显示
 *    的死亡信息并以客户端本地位置启动镜头（若尚未启动）；
 * 2. 紧接着的 DeathCamPayload → {@link #start} 用服务端精确位置/朝向刷新锚点；
 * 3. 镜头播放 deathCamDuration tick（默认 50 tick = 2.5s），tick 推进、渲染侧按 partialTick 插值；
 * 4. 播完或按 Esc（{@link #skip}）→ 恢复原相机视角并打开原版死亡界面。
 *
 * 镜头期间强制第三人称（展示尸体，且隐藏第一人称手持物）；鼠标视角改的是玩家实体旋转，
 * 镜头旋转由时间轴插值覆盖，天然不受输入影响，无需额外的鼠标 mixin。
 */
public final class DeathCamClient {
	private static DeathCamTimeline timeline;
	private static int ticksElapsed;
	private static Vec3 origin = Vec3.ZERO;
	private static @Nullable Component pendingDeathMessage;
	private static boolean pendingHardcore;
	private static @Nullable CameraType previousCameraType;
	/** 镜头结束后的冻结视角：死亡界面背景保持镜头最后位置/朝向，避免闪回玩家第一人称 */
	private static boolean frozen;
	private static Vec3 frozenPosition = Vec3.ZERO;
	private static float frozenYaw;
	private static float frozenPitch;

	private DeathCamClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DeathCamPayload.TYPE,
			(payload, context) -> start(payload.pos(), payload.yaw(), payload.pitch()));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	/**
	 * 客户端收到死亡通知（kill packet 的死亡界面打开点被拦截后调用）。
	 * 记录待显示的死亡信息并启动镜头；死亡位置先用客户端本地位置，payload 到达后刷新。
	 */
	public static void onKillPacket(Component message, boolean hardcore) {
		pendingDeathMessage = message;
		pendingHardcore = hardcore;
		if (!active()) {
			LocalPlayer player = Minecraft.getInstance().player;
			Vec3 pos = player != null ? player.position() : Vec3.ZERO;
			float yaw = player != null ? player.getYRot() : 0.0F;
			float pitch = player != null ? player.getXRot() : 0.0F;
			start(pos, yaw, pitch);
		}
	}

	/**
	 * 启动/刷新镜头。kill packet（本地位置）与 DeathCamPayload（服务端精确位置）先后到达：
	 * 首次调用创建时间轴（用当时 yaw 作镜头朝向），后续调用只刷新锚点——
	 * 不重建时间轴，否则两个包的 yaw 微小差异会让镜头朝向/进度跳变（"会随"感）。
	 */
	public static void start(Vec3 pos, float yaw, float pitch) {
		if (!QuirkyConfigHolder.get().deathCamEnabled) {
			return;
		}
		origin = pos;
		if (timeline == null) {
			timeline = new DeathCamTimeline(QuirkyConfigHolder.get().deathCamDuration, yaw);
			ticksElapsed = 0;
			forceThirdPerson();
		}
	}

	public static void tick() {
		if (timeline == null) {
			return;
		}
		ticksElapsed++;
		if (ticksElapsed >= timeline.durationTicks()) {
			finish();
		}
	}

	/** Esc 提前跳过：立即结束镜头并进入死亡界面。 */
	public static void skip() {
		if (timeline != null) {
			finish();
		}
	}

	public static boolean active() {
		return timeline != null;
	}

	/** 镜头进度 t∈[0,1]，叠加渲染帧 partialTick 平滑插值。 */
	public static float progress(float partialTick) {
		if (timeline == null) {
			return 0.0F;
		}
		return Mth.clamp((ticksElapsed + partialTick) / timeline.durationTicks(), 0.0F, 1.0F);
	}

	/** 当前帧相机绝对位置（锚点 + 时间轴偏移）。 */
	public static Vec3 cameraPosition(float partialTick) {
		return origin.add(timeline.position(progress(partialTick)));
	}

	public static float cameraYaw(float partialTick) {
		return timeline.yawDegrees(progress(partialTick));
	}

	public static float cameraPitch(float partialTick) {
		return timeline.pitchDegrees(progress(partialTick));
	}

	private static void finish() {
		// 冻结镜头最后视角：死亡界面打开后相机保持该位置/朝向（不恢复第一人称），
		// 死亡界面背景 = 镜头视角 + 原版红色渐变，无"闪回玩家第一人称"跳变；
		// 玩家重生/退出死亡界面（screen 不再是 DeathScreen）时由 CameraSetupMixin 解除冻结。
		if (timeline != null) {
			float t = progress(0.0F);
			frozenPosition = origin.add(timeline.position(t));
			frozenYaw = timeline.yawDegrees(t);
			frozenPitch = timeline.pitchDegrees(t);
			frozen = true;
		}
		timeline = null;
		Component message = pendingDeathMessage;
		pendingDeathMessage = null;
		Minecraft mc = Minecraft.getInstance();
		if (message != null && mc.player != null) {
			mc.gui.setScreen(new DeathScreen(message, pendingHardcore, mc.player));
		} else {
			// 兜底：交还原版路径（玩家死亡且无屏幕时 Gui.setScreen(null) 自动打开死亡界面）
			mc.gui.setScreen(null);
		}
	}

	/** 镜头是否已结束并冻结视角（死亡界面展示中）。 */
	public static boolean frozen() {
		return frozen;
	}

	public static Vec3 frozenPosition() {
		return frozenPosition;
	}

	public static float frozenYaw() {
		return frozenYaw;
	}

	public static float frozenPitch() {
		return frozenPitch;
	}

	/** 死亡界面关闭（重生/回主菜单）或断线时解除冻结并恢复原相机类型。 */
	public static void unfreeze() {
		frozen = false;
		restoreCameraType();
	}

	/** 切换维度/断线等安全退出：恢复相机视角并清空状态。 */
	public static void reset() {
		unfreeze();
		timeline = null;
		pendingDeathMessage = null;
	}

	private static void forceThirdPerson() {
		Minecraft mc = Minecraft.getInstance();
		previousCameraType = mc.options.getCameraType();
		if (previousCameraType.isFirstPerson()) {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
	}

	private static void restoreCameraType() {
		if (previousCameraType != null) {
			Minecraft.getInstance().options.setCameraType(previousCameraType);
			previousCameraType = null;
		}
	}
}
