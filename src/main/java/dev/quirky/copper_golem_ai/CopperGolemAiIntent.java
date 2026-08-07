package dev.quirky.copper_golem_ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 搬运意图模型与白名单校验（纯逻辑，可单测）。
 * V2：source/destination 为字符串——"copper"（最近铜箱）/ "give"（只作目标，递给玩家）/ 坐标 "x,y,z"（来自 look_containers 结果）。
 * item 必须引用感知工具返回的真实 ID（knownItems 校验），杜绝 AI 瞎编。
 */
public final class CopperGolemAiIntent {

	/** 物品 ID（含通配 "any"）。 */
	public record TransportRequest(String item, String source, String destination) {
	}

	private static final Pattern ITEM_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+(×\\d+)?$");
	private static final Pattern COORDS = Pattern.compile("^-?\\d+,-?\\d+,-?\\d+$");

	private CopperGolemAiIntent() {
	}

	/** 解析 tool arguments JSON → TransportRequest；非法 → null。 */
	public static @Nullable TransportRequest parse(String argumentsJson) {
		try {
			JsonObject o = JsonParser.parseString(argumentsJson).getAsJsonObject();
			String item = o.has("item") ? o.get("item").getAsString() : null;
			String source = o.has("source") ? o.get("source").getAsString() : null;
			String destination = o.has("destination") ? o.get("destination").getAsString() : null;
			if (item == null || source == null || destination == null) {
				return null;
			}
			if (!isPlausibleTarget(source) || !isPlausibleTarget(destination) || "give".equals(source) || "hand".equals(destination)) {
				return null;
			}
			if (source.equals(destination)) {
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

	/** 搬运目标合法：copper / give / hand（仅 source：把手上的物品放下）/ 坐标 x,y,z。 */
	public static boolean isPlausibleTarget(String t) {
		return t != null && (t.equals("copper") || t.equals("give") || t.equals("hand") || COORDS.matcher(t).matches());
	}

	/** 标准化物品名：剥离 ×N 数量后缀（AI 可能从感知结果带上数量）。 */
	public static String normalizeItem(String item) {
		if (item == null) {
			return "";
		}
		int idx = item.indexOf('×');
		return idx > 0 ? item.substring(0, idx) : item;
	}

	/** item 必须来自感知结果（knownItems）；"any" 放行。 */
	public static boolean isKnownItem(String item, Set<String> knownItems) {
		return item != null && (item.equals("any") || knownItems.contains(item));
	}

	/** 动作意图词（对话硬校验 + PENDING_GOAL 写入判定共用）。 */
	public static final List<String> ACTION_WORDS = List.of("搬", "拿", "捡", "跟", "找", "去", "给", "放", "收集", "打扫", "带", "取", "清理");

	/** 完成语（心跳/对话回复判定：AI 宣布任务达成 → 清 PENDING_GOAL）。 */
	public static final List<String> DONE_WORDS = List.of("办好了", "搬完了", "捡完了", "搞定了", "做好了", "完成了", "弄完了", "搬好", "捡好", "搞定", "收拾完了");

	public static boolean hasActionIntent(String text) {
		return text != null && ACTION_WORDS.stream().anyMatch(text::contains);
	}

	public static boolean isDoneStatement(String text) {
		return text != null && DONE_WORDS.stream().anyMatch(text::contains);
	}
}
