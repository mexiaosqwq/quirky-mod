package dev.quirky.deathcam;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * 死亡镜头的服务端注册：clientbound payload 类型注册（双端进程都会执行本注册——
 * 服务端进程用于编码发送，客户端进程用于解码接收）。无服务端 receiver：payload 客户端自用。
 */
public final class DeathCamServer {
	private DeathCamServer() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(DeathCamPayload.TYPE, DeathCamPayload.STREAM_CODEC);
	}
}
