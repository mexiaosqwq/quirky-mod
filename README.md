# Quirky

一个面向 Minecraft 26.2 的小型 Fabric 模组，提供 7 个原版风格的小机制，灵感来自 Quark。模组只依赖 Fabric API，额外添加一个临时方块。

## 功能

- **地图预览提示**：悬停已填充的地图时，显示 71x71 预览，并保留原版羊皮纸边框。
- **右键收割与补种**：右键成熟的小麦、胡萝卜、马铃薯、甜菜根、下界疣或可可豆即可收割，并尽可能补种；也可以右键瓜藤采摘南瓜和西瓜。
- **双开门联动**：打开一扇木门时，相邻的配套木门会一起开关，包括实体触发的开门；铁门和纯红石控制保持原版行为。
- **时钟提示**：时钟显示游戏内天数和时间。
- **云瓶**：用玻璃瓶和幻翼膜合成 `quirky:bottled_cloud`。右键放置一朵临时云，进入后会缓慢下落，持续 10 秒，并且可以被放置方块替换；着火的实体进入云块会被灭火，云块随之消失。消耗云瓶时返还玻璃瓶；创造模式不消耗也不返还。
- **背包快捷换装**：在容器界面右键可装备物品，直接穿戴，或与已穿戴装备互换；创造模式背包同样可用。
- **西瓜籽吐出**：生存模式吃完最后一片西瓜时，会吐出一个西瓜籽掉落物，带 40 tick 拾取延迟和音效。

## 运行要求

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API 0.155.2+26.2
- Java 25+

## 安装

1. 为 Minecraft 26.2 安装 Fabric Loader。
2. 下载或自行构建 `quirky-0.1.0.jar`。
3. 把 jar 放入 `.minecraft/mods/`。
4. 使用 Fabric 配置启动游戏。

## 构建

需要 JDK 25，在仓库根目录运行：

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
```

单元测试使用 `gradle test`；客户端视觉效果仍需要在桌面客户端手动验证。构建产物位于 `build/libs/quirky-0.1.0.jar`。

## 范围

- 暂时没有配置界面。
- 红石直控门联动有意不实现。
- 客户端行为需要桌面客户端手动验证；服务端机制通过构建和专用服务器冒烟测试验证。

## 贡献

贡献者应先阅读 `AGENTS.md` 了解仓库约定。设计规格位于 `docs/superpowers/specs/`，实现计划位于 `docs/superpowers/plans/`。

## 许可证

CC0 1.0 Universal，详见 [LICENSE](LICENSE)。
