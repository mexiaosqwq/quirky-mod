package dev.quirky.copper_golem_ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.quirky.config.QuirkyConfigHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 铜傀儡 agent 工具：10 个工具 schema 定义 + 执行器分发（服务端）。
 * LLM 永不直接操作游戏——工具 = 意图声明，这里执行真实查询/行动。
 */
public final class CopperGolemAgentTools {
	private static final Gson GSON = new Gson();
	private static final Logger LOGGER = LoggerFactory.getLogger("quirky-copper-golem-ai");

	/** 10 工具声明（OpenAI 兼容 tools 数组）。感知 4 + 行动 5 + transport。 */
	public static final String TOOLS_JSON =
		"["
			+ "{\"type\":\"function\",\"function\":{\"name\":\"look_containers\",\"description\":\"查看附近容器里的物品（箱子/木桶/潜影盒），返回位置+物品清单；物品ID必须从这里获取才能搬运\",\"parameters\":{\"type\":\"object\",\"properties\":{\"range\":{\"type\":\"integer\",\"description\":\"搜索半径格，默认32\"},\"copper_only\":{\"type\":\"boolean\",\"description\":\"只看铜箱\"}},\"required\":[]}}},"
			+ "{\"type\":\"function\",\"function\":{\"name\":\"get_player_status\",\"description\":\"查看附近的玩家：名字/位置/手持物品/血量\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}},"
			+ "{\"type\":\"function\",\"function\":{\"name\":\"get_world_info\",\"description\":\"查看世界状态：时间/天气/生物群系\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}},"
			+ "{\"type\":\"function\",\"function\":{\"name\":\"get_self_status\",\"description\":\"查看自己的状态：位置/手持物品/头顶天线/当前任务\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}},"
			+ "{\"type\":\"function\",\"function\":{\"name\":\"move_to\",\"description\":\"走到指定坐标\",\"parameters\":{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"integer\"},\"z\":{\"type\":\"integer\"}},\"required\":[\"x\",\"y\",\"z\"]}}},"
			+ "{\"type\":\"function\",\"function\":{\"name\":\"follow_player\",\"description\":\"跟随玩家（保持2-3格距离），说停下/stop 取消\",\"parameters\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"玩家名字\"}},\"required\":[\"name\"]}}},"
			+ "{\"type\":\"function\",\"function\":{\"name\":\"approach_entity\",\"description\":\"走到附近指定类型的生物旁（含其他铜傀儡 copper_golem）\",\"parameters\":{\"type\":\"object\",\"properties\":{\"type\":{\"type\":\"string\",\"description\":\"生物类型，如 sheep/zombie/player/copper_golem\"}},\"required\":[\"type\"]}}},"
			+ "{\"type\":\"function\",\"function\":{\"name\":\"stop\",\"description\":\"停止所有行动（移动/跟随/搬运），恢复待机\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}},"
			+ "{\"type\":\"function\",\"function\":{\"name\":\"collect_dropped_items\",\"description\":\"捡起附近地上所有掉落物并自动放进64格内最近的铜箱，一次调用捡完为止（最多64个）；随时可被stop打断\",\"parameters\":{\"type\":\"object\",\"properties\":{\"range\":{\"type\":\"integer\",\"description\":\"搜索半径格，默认16\"}},\"required\":[]}}},"
			+ "{\"type\":\"function\",\"function\":{\"name\":\"transport\",\"description\":\"把物品在容器之间搬运；item 必须引用 look_containers 返回的真实物品ID；source/destination 用 look_containers 返回的坐标(如 12,64,-8)或 copper；destination 可为 give=直接给玩家\",\"parameters\":{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\",\"description\":\"物品ID，必须来自 look_containers 结果\"},\"source\":{\"type\":\"string\",\"description\":\"取货来源：坐标或 copper\"},\"destination\":{\"type\":\"string\",\"description\":\"放货目标：坐标/copper/give\"}},\"required\":[\"item\",\"source\",\"destination\"]}}}]";

	/** 工具执行上下文。knownItems=本回合 look_containers 已感知的物品 ID（transport 引用校验用）。 */
	public record ToolContext(CopperGolem golem, ServerLevel level, @Nullable ServerPlayer player, java.util.Set<String> knownItems) {
	}

	/** 单个容器概要（formatContainers 的输入）。 */
	public record ContainerInfo(String type, String pos, List<String> items, int totalItems) {
	}

	private CopperGolemAgentTools() {
	}

	/** 按工具名分发执行；未知工具 → error JSON。每次调用打 debug 日志（排查 AI 行为用）。 */
	public static String execute(String name, String argsJson, @Nullable ToolContext ctx) {
		JsonObject args;
		try {
			args = argsJson == null || argsJson.isBlank() ? new JsonObject() : JsonParser.parseString(argsJson).getAsJsonObject();
		} catch (RuntimeException e) {
			args = new JsonObject();
		}
		String result = switch (name) {
			case "look_containers" -> lookContainers(ctx, args);
			case "get_player_status" -> getPlayerStatus(ctx);
			case "get_world_info" -> getWorldInfo(ctx);
			case "get_self_status" -> getSelfStatus(ctx);
			case "move_to" -> moveTo(ctx, args);
			case "follow_player" -> followPlayer(ctx, args);
			case "approach_entity" -> approachEntity(ctx, args);
			case "stop" -> stop(ctx);
			case "collect_dropped_items" -> collectDropped(ctx, args);
			case "transport" -> transport(ctx, args);
			default -> "{\"error\":\"unknown tool: " + name + "\"}";
		};
		LOGGER.info("golem tool {} args={} -> {}", name, argsJson, result.length() > 200 ? result.substring(0, 200) + "…" : result);
		return result;
	}

	// ===== 感知工具 =====

	/** 玩家视线是否朝向傀儡（点积判定）。 */
	private static boolean lookingAt(net.minecraft.world.entity.Entity golem, ServerPlayer player) {
		net.minecraft.world.phys.Vec3 toGolem = golem.position().subtract(player.position()).normalize();
		return toGolem.dot(player.getLookAngle()) > 0.6;
	}

	/** 附近储物容器（箱子/木桶/潜影盒）内容。copper_only 只看铜箱。 */
	private static String lookContainers(@Nullable ToolContext ctx, JsonObject args) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		int range = rangeOf(args, 32);
		boolean copperOnly = args.has("copper_only") && args.get("copper_only").getAsBoolean();
		BlockPos golemPos = ctx.golem().blockPosition();
		List<ContainerInfo> found = new ArrayList<>();
		for (int dx = -range; dx <= range; dx += 16) {
			for (int dz = -range; dz <= range; dz += 16) {
				ChunkPos cp = ChunkPos.containing(golemPos.offset(dx, 0, dz));
				LevelChunk chunk = ctx.level().getChunkSource().getChunkNow(cp.x(), cp.z());
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity)) {
						continue;
					}
					BlockPos pos = be.getBlockPos();
					if (Math.abs(pos.getX() - golemPos.getX()) > range || Math.abs(pos.getZ() - golemPos.getZ()) > range) {
						continue;
					}
					if (copperOnly && !ctx.level().getBlockState(pos).is(net.minecraft.tags.BlockTags.COPPER_CHESTS)) {
						continue;
					}
					if (!(be instanceof Container c)) {
						continue;
					}
					List<String> items = new ArrayList<>();
					int total = 0;
					for (int slot = 0; slot < c.getContainerSize(); slot++) {
						ItemStack stack = c.getItem(slot);
						if (stack.isEmpty()) {
							continue;
						}
						total++;
						String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
						items.add(id + "(" + stack.getHoverName().getString() + ")×" + stack.getCount());
						ctx.knownItems().add(id);
					}
					found.add(new ContainerInfo(typeOf(be), pos.getX() + "," + pos.getY() + "," + pos.getZ(), items, total));
				}
			}
		}
		return "{\"containers\":" + GSON.toJson(formatContainers(found, 20, 10)) + "}";
	}

	private static String typeOf(BlockEntity be) {
		if (be instanceof ChestBlockEntity) {
			return "chest";
		}
		if (be instanceof BarrelBlockEntity) {
			return "barrel";
		}
		return "shulker";
	}

	/** 附近玩家（≤32 格）：名字/坐标/手持/血量。 */
	private static String getPlayerStatus(@Nullable ToolContext ctx) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		AABB box = new AABB(ctx.golem().blockPosition()).inflate(32);
		List<ServerPlayer> players = ctx.level().players().stream()
			.filter(p -> !p.isRemoved() && box.contains(p.getX(), p.getY(), p.getZ())).toList();
		List<String> out = new ArrayList<>();
		for (ServerPlayer p : players) {
			ItemStack held = p.getMainHandItem();
			String heldName = held.isEmpty() ? "空手" : held.getHoverName().getString();
			int dist = (int) Math.round(Math.sqrt(p.distanceToSqr(ctx.golem())));
			String looking = lookingAt(ctx.golem(), p) ? "看着你" : "没看你";
			out.add(p.getName().getString() + "@(" + (int) p.getX() + "," + (int) p.getY() + "," + (int) p.getZ()
				+ ") 距你" + dist + "格 手持" + heldName + " 血量" + (int) Math.ceil(p.getHealth()) + " " + looking);
		}
		return "{\"players\":" + GSON.toJson(out) + "}";
	}

	/** 世界状态：时间/天气/生物群系。 */
	private static String getWorldInfo(@Nullable ToolContext ctx) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		ServerLevel level = ctx.level();
		long tick = level.getOverworldClockTime();
		String weather = level.isThundering() ? "雷暴" : level.isRaining() ? "下雨" : "晴天";
		String biome = level.getBiome(ctx.golem().blockPosition()).unwrapKey()
			.map(key -> key.identifier().toString()).orElse("unknown");
		JsonObject out = new JsonObject();
		out.addProperty("time", tick + "tick(" + periodOfDay(tick) + ")");
		out.addProperty("weather", weather);
		out.addProperty("biome", biome);
		return GSON.toJson(out);
	}

	/** 傀儡自身状态：位置/手持/天线/任务/闪电。 */
	private static String getSelfStatus(@Nullable ToolContext ctx) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		CopperGolem golem = ctx.golem();
		ItemStack held = golem.getMainHandItem();
		ItemStack antenna = golem.getItemBySlot(EquipmentSlot.SADDLE);
		JsonObject out = new JsonObject();
		out.addProperty("name", golem.getName().getString());
		out.addProperty("pos", (int) golem.getX() + "," + (int) golem.getY() + "," + (int) golem.getZ());
		out.addProperty("held", held.isEmpty() ? "空手" : held.getHoverName().getString() + "×" + held.getCount());
		out.addProperty("antenna", antenna.isEmpty() ? "无" : antenna.getHoverName().getString());
		out.addProperty("task", CopperGolemAiService.currentTaskDescription(golem));
		out.addProperty("last_lightning", CopperGolemAiService.lightningInfo(golem));
		return GSON.toJson(out);
	}

	// ===== 行动工具（服务端执行，复用原版移动机制）=====

	private static String transport(@Nullable ToolContext ctx, JsonObject args) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		CopperGolemAiIntent.TransportRequest req = CopperGolemAiIntent.parse(args.toString());
		if (req == null) {
			return "{\"error\":\"搬运参数不合法：item 用物品ID（或 any），source/destination 用 look_containers 的坐标或 copper，destination 可为 give\"}";
		}
		return CopperGolemAiService.handleTransportRequest(ctx.golem(), ctx.level(), ctx.player(), ctx.knownItems(), req);
	}

	private static String collectDropped(@Nullable ToolContext ctx, JsonObject args) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		int range = rangeOf(args, QuirkyConfigHolder.get().droppedPickupRange);
		return CopperGolemAiService.startCollect(ctx.golem(), ctx.level(), range);
	}

	private static String moveTo(@Nullable ToolContext ctx, JsonObject args) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		BlockPos target = parseCoords(args.has("x") && args.has("y") && args.has("z")
			? args.get("x").getAsInt() + "," + args.get("y").getAsInt() + "," + args.get("z").getAsInt() : "");
		if (target == null) {
			return "{\"error\":\"invalid coords\"}";
		}
		CopperGolemAiService.clearOtherTasks(ctx.golem(), "move"); // 新目标顶掉旧任务（与其余行动工具一致）
		return CopperGolemAiService.startMove(ctx.golem(), target);
	}

	private static String followPlayer(@Nullable ToolContext ctx, JsonObject args) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		String name = args.has("name") ? args.get("name").getAsString() : "";
		// 名字不可靠（AI 可能传错）：优先按名匹配，找不到就用对话发起者兜底
		if (ctx.player() != null) {
			final String candidate = name;
			boolean nameMatches = !candidate.isBlank()
				&& ctx.level().players().stream().anyMatch(p -> p.getName().getString().equals(candidate));
			if (!nameMatches) {
				name = ctx.player().getName().getString();
			}
		}
		if (name.isBlank()) {
			return "{\"error\":\"missing name\"}";
		}
		return CopperGolemAiService.startFollow(ctx.golem(), ctx.level(), name);
	}

	private static String approachEntity(@Nullable ToolContext ctx, JsonObject args) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		String type = args.has("type") ? args.get("type").getAsString() : "";
		String id = parseEntityType(type);
		if (id == null) {
			return "{\"error\":\"invalid entity type: " + type + "\"}";
		}
		return CopperGolemAiService.startApproach(ctx.golem(), ctx.level(), id);
	}

	private static String stop(@Nullable ToolContext ctx) {
		if (ctx == null) {
			return "{\"error\":\"no context\"}";
		}
		return CopperGolemAiService.stopAll(ctx.golem());
	}

	// ===== 纯逻辑（可单测）=====

	/** 时段：0-12000 白天 / 12000-13000 黄昏 / 13000-23000 夜晚 / 23000-24000 黎明。 */
	public static String periodOfDay(long tick) {
		long t = Math.floorMod(tick, 24000);
		if (t < 12000) {
			return "白天";
		}
		if (t < 13000) {
			return "黄昏";
		}
		if (t < 23000) {
			return "夜晚";
		}
		return "黎明";
	}

	/** 容器清单截断：最多 maxContainers 个容器 × 每容器前 maxItems 种物品，超出补"还有 N 未列出"。 */
	public static String formatContainers(List<ContainerInfo> containers, int maxContainers, int maxItems) {
		List<String> out = new ArrayList<>();
		int truncated = 0;
		for (int i = 0; i < containers.size() && i < maxContainers; i++) {
			ContainerInfo c = containers.get(i);
			StringBuilder sb = new StringBuilder(c.type() + "@(" + c.pos() + ")");
			if (c.items().isEmpty()) {
				sb.append("：空");
			} else {
				sb.append("：");
				int shown = Math.min(maxItems, c.items().size());
				for (int j = 0; j < shown; j++) {
					if (j > 0) {
						sb.append(" ");
					}
					sb.append(c.items().get(j));
				}
				if (c.items().size() > maxItems) {
					sb.append(" 还有 " + (c.items().size() - maxItems) + " 种物品未列出");
				}
			}
			out.add(sb.toString());
		}
		if (containers.size() > maxContainers) {
			out.add("还有 " + (containers.size() - maxContainers) + " 个容器未列出");
		}
		return String.join("\n", out);
	}

	/** range 参数解析：缺省/非法 → 默认值。 */
	public static int rangeOf(JsonObject args, int def) {
		try {
			return args.has("range") ? args.get("range").getAsInt() : def;
		} catch (RuntimeException e) {
			return def;
		}
	}

	/** 跟随是否应停止：玩家超过 maxDist 格。 */
	public static boolean shouldStopFollow(double distSqr, int maxDist) {
		return distSqr > (double) maxDist * maxDist;
	}

	/** 实体类型解析：接受 "sheep" 或 "minecraft:sheep" → 完整 ID；非法 → null。 */
	public static @Nullable String parseEntityType(String type) {
		if (type == null || type.isBlank() || type.contains("/")) {
			return null;
		}
		String id = type.contains(":") ? type : "minecraft:" + type;
		return id.matches("^[a-z0-9_.-]+:[a-z0-9_./-]+$") ? id : null;
	}

	/** 坐标字符串解析 "x,y,z" → BlockPos；非法 → null。 */
	public static @Nullable BlockPos parseCoords(String coords) {
		if (coords == null || coords.isBlank()) {
			return null;
		}
		try {
			String[] parts = coords.split(",");
			if (parts.length != 3) {
				return null;
			}
			return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
