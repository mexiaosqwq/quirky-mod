package dev.quirky.wakeup;

/**
 * 起床缓降时长的纯计算（无实体依赖，可单测）。
 * 深睡（睡满整晚自然醒）= 配置秒数；被打断（中途起床/被怪物惊醒）= 1/3（向下取整）。
 */
public final class WakeUpLogic {

	private WakeUpLogic() {
	}

	public static int durationTicks(boolean deepSleep, int configSeconds) {
		int seconds = Math.max(0, configSeconds);
		if (!deepSleep) {
			seconds = seconds / 3;
		}
		return seconds * 20;
	}
}
