package dev.quirky.copper_golem_ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/** 搬运意图模型与白名单校验（纯逻辑，可单测）。 */
public final class CopperGolemAiIntent {
	public enum Target {
		TARGETED, // 玩家准心指着的容器
		COPPER;   // 最近的铜箱子

		@Nullable
		public static Target from(String s) {
			if (s == null) {
				return null;
			}
			return switch (s) {
				case "targeted" -> TARGETED;
				case "copper" -> COPPER;
				default -> null;
			};
		}
	}

	public record TransportRequest(String item, Target source, Target destination) {
	}

	private static final Pattern ITEM_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

	private CopperGolemAiIntent() {
	}

	/** 解析 tool arguments JSON → TransportRequest；非法枚举/缺字段/source==destination → null。 */
	public static @Nullable TransportRequest parse(String argumentsJson) {
		try {
			JsonObject o = JsonParser.parseString(argumentsJson).getAsJsonObject();
			String item = o.has("item") ? o.get("item").getAsString() : null;
			Target source = Target.from(o.has("source") ? o.get("source").getAsString() : null);
			Target destination = Target.from(o.has("destination") ? o.get("destination").getAsString() : null);
			if (item == null || source == null || destination == null || source == destination) {
				return null;
			}
			return new TransportRequest(item, source, destination);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** item 是合法物品 ID 或通配 "any"。 */
	public static boolean isPlausibleItem(String item) {
		return item != null && (item.equals("any") || ITEM_ID.matcher(item).matches());
	}
}
