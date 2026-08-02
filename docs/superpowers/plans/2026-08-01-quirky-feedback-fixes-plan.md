# Quirky 反馈修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复云瓶紫黑模型与贴图、开放创造模式背包换装，并按用户授权移除指南针提示。

**Architecture:** 继续使用单一 Fabric 模组与现有包结构。云瓶问题根因是 26.2 需要 `assets/quirky/items/bottled_cloud.json` 新式物品模型注册，旧 `models/item` 文件不会被自动引用。创造模式换装由客户端在 `CreativeModeInventoryScreen` 中发送右键事件，并把创造界面槽位映射回服务端 `InventoryMenu` 槽位。指南针提示从 tooltip Mixin 与语言文件删除，时钟提示保留。

**Tech Stack:** Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.155.2+26.2、Java 25、Gradle 9.6.1、官方映射、JUnit 5 + Mockito。

## Global Constraints

- 模组 ID 必须是 `quirky`，Java 包根必须是 `dev.quirky`。
- 只依赖 Fabric API，不引入其他模组 API。
- Minecraft 必须是 `26.2`，Java `>=25`，编译目标 `release 25`。
- API 名称以 `$HOME/.cache/mcsrc` 的 26.2 反编译源码为准。
- 编辑用 `apply_patch`；JSON 使用 2 空格缩进；Java 使用 Tab 缩进。
- 每次任务结束必须通过构建并提交；不提交用户已有的无关改动（`AGENTS.md`、`.agents/`、`.codex/`、`.codegraph/`、`logs/`、`skills-lock.json`）。
- 构建命令：

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle clean build --no-daemon --console=plain
```

---

## Task 1: 修复云瓶模型注册并重画云瓶/云方块贴图

**Files:**
- Create: `src/main/resources/assets/quirky/items/bottled_cloud.json`
- Create: `tools/generate_cloud_textures.py`
- Modify: `src/main/resources/assets/quirky/textures/item/bottled_cloud.png`
- Modify: `src/main/resources/assets/quirky/textures/block/cloud.png`
- Verify: `build/libs/quirky-0.1.0.jar`

**Interfaces:**
- Consumes: 现有 `assets/quirky/models/item/bottled_cloud.json` 与 `assets/quirky/textures/item/bottled_cloud.png`。
- Produces: `quirky:item/bottled_cloud` 的新式 ClientItem 模型注册；`assets/quirky/items/bottled_cloud.json` 的 `model` 指向 `quirky:item/bottled_cloud`。

根因：`ClientItemInfoLoader` 只扫描 `assets/<namespace>/items/<id>.json`（`FileToIdConverter.json("items")`），`ItemModelResolver` 只读取 `DataComponents.ITEM_MODEL`。旧 jar 里只有 `models/item/bottled_cloud.json`，因此物品模型 ID 无对应 ClientItem，渲染为缺失模型紫黑方块。

- [x] **Step 1: 添加新式物品模型注册文件**

创建 `src/main/resources/assets/quirky/items/bottled_cloud.json`：

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "quirky:item/bottled_cloud"
  }
}
```

- [x] **Step 2: 创建贴图生成脚本**

创建 `tools/generate_cloud_textures.py`，内容如下：

```python
#!/usr/bin/env python3
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def write_png(path, pixels):
    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    raw = b"".join(
        b"\x00" + b"".join(struct.pack("4B", *pixel) for pixel in row)
        for row in pixels
    )
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw))
        + chunk(b"IEND", b"")
    )
    path.write_bytes(png)


def canvas():
    return [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]


def px(c, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        c[y][x] = color


def rect(c, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(c, x, y, color)


def disc(c, cx, cy, r, color):
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy <= r * r:
                px(c, cx + dx, cy + dy, color)


def bottled_cloud():
    c = canvas()
    glass_dark = (42, 78, 96, 255)
    glass_mid = (128, 176, 192, 235)
    glass_light = (205, 232, 240, 215)
    cloud_white = (255, 255, 255, 255)
    cloud_soft = (220, 240, 248, 255)
    cloud_shadow = (172, 206, 220, 255)
    cork = (166, 120, 74, 255)
    cork_dark = (124, 84, 52, 255)

    rect(c, 6, 1, 9, 2, cork)
    px(c, 6, 1, cork_dark)
    px(c, 9, 1, cork_dark)
    px(c, 6, 2, cork_dark)
    px(c, 9, 2, cork_dark)

    rect(c, 3, 6, 12, 11, glass_mid)
    rect(c, 4, 5, 11, 5, glass_mid)
    rect(c, 5, 4, 10, 4, glass_mid)
    rect(c, 6, 3, 9, 3, glass_mid)
    rect(c, 4, 12, 11, 12, glass_mid)
    rect(c, 5, 13, 10, 13, glass_mid)
    rect(c, 6, 14, 9, 14, glass_mid)

    rect(c, 5, 3, 5, 3, glass_dark)
    rect(c, 10, 3, 10, 3, glass_dark)
    rect(c, 4, 4, 4, 4, glass_dark)
    rect(c, 11, 4, 11, 4, glass_dark)
    rect(c, 3, 5, 3, 5, glass_dark)
    rect(c, 12, 5, 12, 5, glass_dark)
    for y in range(6, 12):
        px(c, 2, y, glass_dark)
        px(c, 13, y, glass_dark)
    px(c, 3, 12, glass_dark)
    px(c, 12, 12, glass_dark)
    px(c, 4, 13, glass_dark)
    px(c, 11, 13, glass_dark)
    px(c, 5, 14, glass_dark)
    px(c, 10, 14, glass_dark)
    px(c, 6, 15, glass_dark)
    px(c, 9, 15, glass_dark)

    disc(c, 6, 7, 2, cloud_white)
    disc(c, 9, 7, 2, cloud_white)
    disc(c, 7, 6, 2, cloud_soft)
    disc(c, 8, 8, 2, cloud_white)
    disc(c, 5, 9, 2, cloud_soft)
    disc(c, 9, 9, 2, cloud_soft)
    disc(c, 7, 10, 2, cloud_white)
    px(c, 5, 11, cloud_shadow)
    px(c, 8, 11, cloud_shadow)
    px(c, 9, 11, cloud_shadow)
    px(c, 4, 10, cloud_shadow)
    px(c, 10, 10, cloud_shadow)

    for y in range(6, 11):
        px(c, 4, y, glass_light)
    px(c, 5, 6, glass_light)
    px(c, 10, 7, glass_light)
    return c


def cloud_block():
    c = canvas()
    base = (232, 243, 250, 255)
    white = (255, 255, 255, 255)
    soft = (245, 251, 253, 255)
    shadow = (178, 208, 222, 255)
    deep = (150, 184, 200, 255)

    rect(c, 0, 0, 15, 15, base)
    disc(c, 3, 3, 4, white)
    disc(c, 8, 2, 5, white)
    disc(c, 13, 4, 4, white)
    disc(c, 5, 8, 4, soft)
    disc(c, 10, 7, 4, white)
    disc(c, 2, 9, 3, soft)
    disc(c, 14, 10, 3, soft)
    disc(c, 8, 11, 3, white)
    for x in range(16):
        px(c, x, 15, deep)
    rect(c, 0, 14, 15, 14, shadow)
    for x, y in ((1, 13), (4, 13), (7, 13), (10, 13), (13, 13), (5, 12), (11, 12)):
        px(c, x, y, deep)
    px(c, 2, 2, white)
    px(c, 7, 1, white)
    px(c, 12, 3, white)
    return c


write_png(
    ROOT / "src/main/resources/assets/quirky/textures/item/bottled_cloud.png",
    bottled_cloud(),
)
write_png(
    ROOT / "src/main/resources/assets/quirky/textures/block/cloud.png",
    cloud_block(),
)
```

- [x] **Step 3: 运行生成脚本并验证 PNG**

```bash
python3 tools/generate_cloud_textures.py
identify src/main/resources/assets/quirky/textures/item/bottled_cloud.png \
  src/main/resources/assets/quirky/textures/block/cloud.png
```

预期：两张图均为 `16 x 16`、`8-bit/color RGBA`。

- [x] **Step 4: 构建并检查 jar 内容**

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
unzip -l build/libs/quirky-0.1.0.jar | grep 'assets/quirky/items/bottled_cloud.json'
```

预期：构建成功，jar 内包含 `assets/quirky/items/bottled_cloud.json`。

- [x] **Step 5: 提交**

```bash
git add src/main/resources/assets/quirky/items/bottled_cloud.json \
  tools/generate_cloud_textures.py \
  src/main/resources/assets/quirky/textures/item/bottled_cloud.png \
  src/main/resources/assets/quirky/textures/block/cloud.png
git commit -m "fix: load bottled cloud item model with 26.2 item registration"
```

---

## Task 2: 开放创造模式背包换装

**Files:**
- Modify: `src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java`
- Create: `src/test/java/dev/quirky/client/equip_swap/EquipSwapClientTest.java`

**Interfaces:**
- Consumes: `EquipSwapPayload(int containerId, int slotIndex)` 现有网络包；服务端 `EquipSwapServer.handle` 现有校验逻辑。
- Produces: 包内静态方法 `static int serverSlotIndex(Slot slot, Screen screen, @Nullable Player player)`，供测试直接调用。

根因：客户端在 `ScreenEvents.BEFORE_INIT` 中明确排除了 `CreativeModeInventoryScreen`，创造模式右键不会发送换装包。另外创造界面的非背包页签使用 `ItemPickerMenu` 热键栏槽位编号 45-53，服务端 `InventoryMenu` 热键栏编号是 36-44，必须映射。

- [x] **Step 1: 先写失败测试**

创建 `src/test/java/dev/quirky/client/equip_swap/EquipSwapClientTest.java`：

```java
package dev.quirky.client.equip_swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.quirky.TestBootstrap;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EquipSwapClientTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void creativeInventoryTabUsesSlotIndexDirectly() {
		CreativeModeInventoryScreen screen = mock(CreativeModeInventoryScreen.class);
		when(screen.isInventoryOpen()).thenReturn(true);
		Slot slot = new Slot(mock(Container.class), 7, 0, 0);
		slot.index = 7;

		assertEquals(7, EquipSwapClient.serverSlotIndex(slot, screen, null));
	}

	@Test
	void creativeOtherTabMapsHotbarSlotToInventoryMenuIndex() {
		CreativeModeInventoryScreen screen = mock(CreativeModeInventoryScreen.class);
		when(screen.isInventoryOpen()).thenReturn(false);
		Inventory inventory = mock(Inventory.class);
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		Slot slot = new Slot(inventory, 3, 0, 0);
		slot.index = 48;

		assertEquals(39, EquipSwapClient.serverSlotIndex(slot, screen, player));
	}

	@Test
	void creativeOtherTabRejectsItemListSlot() {
		CreativeModeInventoryScreen screen = mock(CreativeModeInventoryScreen.class);
		when(screen.isInventoryOpen()).thenReturn(false);
		Slot slot = new Slot(mock(Container.class), 3, 0, 0);
		slot.index = 3;

		assertEquals(-1, EquipSwapClient.serverSlotIndex(slot, screen, mock(Player.class)));
	}

	@Test
	void normalContainerScreenUsesSlotIndex() {
		Slot slot = new Slot(mock(Container.class), 4, 0, 0);
		slot.index = 4;

		assertEquals(4, EquipSwapClient.serverSlotIndex(slot, mock(Screen.class), null));
	}
}
```

- [x] **Step 2: 运行测试确认失败**

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --tests 'dev.quirky.client.equip_swap.EquipSwapClientTest' --no-daemon --console=plain
```

预期：编译失败，因为 `serverSlotIndex` 不存在。

- [x] **Step 3: 实现客户端改造**

修改 `src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java`：

```java
package dev.quirky.client.equip_swap;

import dev.quirky.client.mixin.AbstractContainerScreenAccessor;
import dev.quirky.equip_swap.EquipSwapPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

public final class EquipSwapClient {
	private EquipSwapClient() {
	}

	public static void init() {
		ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (event.button() != 1) {
					return true;
				}
				Slot slot = ((AbstractContainerScreenAccessor) containerScreen)
					.quirky$getHoveredSlot(event.x(), event.y());
				if (slot == null
					|| !slot.hasItem()
					|| !slot.getItem().has(DataComponents.EQUIPPABLE)
					|| !containerScreen.getMenu().getCarried().isEmpty()) {
					return true;
				}
				int slotIndex = serverSlotIndex(slot, screen, client.player);
				if (slotIndex < 0) {
					return true;
				}
				ClientPlayNetworking.send(
					new EquipSwapPayload(containerScreen.getMenu().containerId, slotIndex)
				);
				return false;
			});
		});
	}

	static int serverSlotIndex(Slot slot, Screen screen, @Nullable Player player) {
		if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
			if (creativeScreen.isInventoryOpen()) {
				return slot.index;
			}
			if (player == null || slot.container != player.getInventory()) {
				return -1;
			}
			int hotbarIndex = slot.getContainerSlot();
			return hotbarIndex >= 0 && hotbarIndex < 9 ? 36 + hotbarIndex : -1;
		}
		return slot.index;
	}
}
```

- [x] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --tests 'dev.quirky.client.equip_swap.EquipSwapClientTest' --no-daemon --console=plain
```

预期：4 个测试全部通过。

- [x] **Step 5: 提交**

```bash
git add src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java \
  src/test/java/dev/quirky/client/equip_swap/EquipSwapClientTest.java
git commit -m "feat: support equip swap in creative inventory"
```

---

## Task 3: 移除指南针提示

**Files:**
- Modify: `src/client/java/dev/quirky/client/mixin/ClockCompassTooltipMixin.java`
- Modify: `src/main/resources/assets/quirky/lang/zh_cn.json`
- Modify: `src/main/resources/assets/quirky/lang/en_us.json`
- Modify: `README.md`
- Modify: `features.md`
- Modify: `docs/superpowers/specs/2026-08-01-quirky-design.md`
- Modify: `docs/superpowers/specs/2026-08-01-quirky-refinement-design.md`

**Interfaces:**
- Consumes: 现有 `tooltip.quirky.clock` 语言键；`ClockCompassTooltipMixin` 仍保留。
- Produces: 不再读取 `tooltip.quirky.compass`、`tooltip.quirky.lodestone`。

用户授权“可以把这功能删掉”，本任务选择删除指南针提示，保留时钟提示。

- [x] **Step 1: 修改 Mixin，只保留时钟分支**

`src/client/java/dev/quirky/client/mixin/ClockCompassTooltipMixin.java` 最终内容：

```java
package dev.quirky.client.mixin;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ClockCompassTooltipMixin {
	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void quirky$appendTooltip(
		Item.TooltipContext context,
		@Nullable Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> cir
	) {
		ItemStack stack = (ItemStack) (Object) this;
		if (player == null) {
			return;
		}
		TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		if (!flag.isCreative() && display.hideTooltip()) {
			return;
		}
		if (stack.is(Items.CLOCK)) {
			long dayTime = player.level().getDefaultClockTime();
			cir.getReturnValue().add(
				Component.translatable("tooltip.quirky.clock", dayTime / 24000L + 1L, formatTime(dayTime))
					.withStyle(ChatFormatting.GRAY)
			);
		}
	}

	private static String formatTime(long dayTime) {
		int ticks = (int) (dayTime % 24000L);
		int hours = (ticks / 1000 + 6) % 24;
		int minutes = (ticks % 1000) * 60 / 1000;
		return String.format("%02d:%02d", hours, minutes);
	}
}
```

- [x] **Step 2: 删除语言键**

`src/main/resources/assets/quirky/lang/zh_cn.json` 删除 `tooltip.quirky.compass` 与 `tooltip.quirky.lodestone`，只保留：

```json
{
  "item.quirky.bottled_cloud": "云瓶",
  "tooltip.quirky.clock": "第 %s 天 · %s"
}
```

`src/main/resources/assets/quirky/lang/en_us.json` 只保留：

```json
{
  "item.quirky.bottled_cloud": "Bottled Cloud",
  "tooltip.quirky.clock": "Day %s · %s"
}
```

- [x] **Step 3: 更新用户文档与规格**

`README.md`：

- 功能列表 `- **时钟与指南针提示**：...` 改为 `- **时钟提示**：时钟显示游戏内天数和时间。`
- `- **背包快捷换装**：...` 末尾补一句 `创造模式背包同样可用。`
- 范围列表删除 `创造模式背包换装、`，保留 `红石直控门联动有意不实现。`

`features.md`：

- 第 4 节标题改为 `### 4. 时钟更贴心`，正文只保留时钟说明。
- 第 6 节补一句创造模式背包也可右键换装。
- “还没有做”列表删除创造模式物品栏换装那条。

`docs/superpowers/specs/2026-08-01-quirky-design.md`：

- 第 2.4 节与第 5.4 节改为只描述时钟 tooltip。
- 第 3 节非目标删除“不做创造模式界面的装备替换接入”。
- 第 5.6 节删除“创造模式界面 v1 不接入”，改为“创造模式背包界面同样支持”。
- 验收标准中“时钟和指南针显示信息”改为“时钟显示信息”。

`docs/superpowers/specs/2026-08-01-quirky-refinement-design.md`：

- 音效总表中 `地图/指南针/时钟` 改为 `地图/时钟`。
- 非目标中 `不给地图、指南针、时钟加音效` 改为 `不给地图、时钟加音效`。

- [x] **Step 4: 构建验证**

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
```

预期：构建成功，Mixin 仍被客户端引用。

- [x] **Step 5: 提交**

```bash
git add src/client/java/dev/quirky/client/mixin/ClockCompassTooltipMixin.java \
  src/main/resources/assets/quirky/lang/zh_cn.json \
  src/main/resources/assets/quirky/lang/en_us.json \
  README.md features.md \
  docs/superpowers/specs/2026-08-01-quirky-design.md \
  docs/superpowers/specs/2026-08-01-quirky-refinement-design.md
git commit -m "chore: remove compass tooltip and enable creative equip swap docs"
```

---

## Task 4: 全量构建、资源清单与自检

**Files:**
- Verify: `build/libs/quirky-0.1.0.jar`
- Verify: 仓库 `git status --short`

**Interfaces:**
- Consumes: Task 1-3 的所有改动。
- Produces: 可安装的 mod jar 与最终变更清单。

- [x] **Step 1: 全量 clean build**

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle clean build --no-daemon --console=plain
```

预期：全部测试与构建通过。

- [x] **Step 2: 检查 jar 内资源**

```bash
unzip -l build/libs/quirky-0.1.0.jar | grep -E 'assets/quirky/(items/bottled_cloud|models/item/bottled_cloud|textures/item/bottled_cloud|textures/block/cloud)'
```

预期：四类资源都在 jar 中。

- [x] **Step 3: 自检清单**

```text
1. `items/bottled_cloud.json` 的 model 指向 `quirky:item/bottled_cloud`。
2. 云瓶与云方块 PNG 均为 16x16 RGBA。
3. 创造模式背包右键换装测试 4 项通过。
4. 指南针语言键不再存在于语言文件。
5. 用户无关改动（AGENTS.md、.agents/ 等）未提交。
```

- [x] **Step 4: 最终提交（如有剩余文档改动）**

```bash
git status --short
git add docs/superpowers/plans/2026-08-01-quirky-feedback-fixes-plan.md
git commit -m "docs: add feedback fixes implementation plan"
```
