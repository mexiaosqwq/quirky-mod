# 合并交接：usage ticker 工作区 → master

> 本文件供合并 agent 使用：把工作区改动合并回 master，并做合并后验证。
> 遵循根目录 `AGENTS.md`（执行纪律、提交规范、验证要求）。

## 一、合并目标

把 `.worktrees/quirky`（分支 `fix/usage-ticker-placement`）的 **8 个 commit** 合并回 master。
master 自分支创建后未动过（`543b763`），预期 **fast-forward**。

## 二、分支内容（使用量挂件打磨，全部已过 reviewer 两阶段审查）

| commit | 内容 |
|---|---|
| `a203aee` | 修复连续放置方块时挂件不弹出（重触发语义：已显示时新事件只刷新内容不重启动画；滑出按位置反演 `p'=1-√(2q-q²)`；partial tick 插值平滑动画） |
| `ce7ce66` | 护甲耐久升高/穿脱/换装触发（原只响应耐久降低） |
| `b6a9dd7` | 统一检测：一次遍历 43 槽快照 `InventorySnapshot(totals, durability, armor, equipment)`，按变化类型分发（数量→左、耐久/盔甲槽→右）；`ArmorTicker` 改为通用 `DurabilityTicker` |
| `f1539ee` | 多堆叠取最损堆叠渲染；不可损坏穿戴物（南瓜头）显示图标无条；javadoc/常量/测试修正 |
| `a4cd431` | 修复副手/BODY/SADDLE 槽摆放变化回归（`diffEquipment` 兜底触发左侧） |
| `abc289b` | Quark 式护甲：4 个固定位独立元素（哪件变化哪件弹），工具/副手耐久走浮动列表 |
| `9c9c790` | 审查修复：穿+备同物品排除边界、窄屏工具列表 X 钳制、javadoc、补 4 测试 |

改动文件（7 个，全部在 `src/`）：
- `src/client/java/dev/quirky/client/usage_ticker/`：`TickerSnapshot.java`（统一快照 + diffTotals/diffEquipment/diffArmorSlots/diffToolDurability）、`TickerElement.java`（重触发状态机 + progress(float)）、`DurabilityTicker.java`（新，通用渲染）、`UsageTickerHud.java`
- `src/test/java/dev/quirky/client/usage_ticker/`：`TickerElementTest.java`、`TickerSnapshotTest.java`；`ArmorTickerTest.java` 已删（检测并入 TickerSnapshotTest）

## 三、合并步骤（在主工作区执行）

```sh
# 1. 备份分支（执行纪律：git 操作前 bundle 备份）
git bundle create /tmp/usage-ticker-$(date +%Y%m%d).bundle fix/usage-ticker-placement

# 2. 合并（预期 fast-forward；若意外出现冲突，用 --no-ff 另开 merge commit 并报告）
git merge fix/usage-ticker-placement

# 3. 验证（合并后必须在主工作区完整跑一遍）
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
# 预期：BUILD SUCCESSFUL，usage_ticker 测试 51 个全绿（TickerElement 12 + TickerSnapshot 38 + 其他）

# 4. 清理 worktree 与分支
git worktree remove .worktrees/quirky   # 先确认 worktree 内无未提交改动（git -C .worktrees/quirky status）
git branch -d fix/usage-ticker-placement
```

## 四、注意事项

- **docs/ 不入库**（`.gitignore` 排除，commit 09613e4 起）：规格 `docs/superpowers/specs/2026-08-02-quirky-client-qol-design.md` §5.4 与手工验证清单 `docs/client-qol-manual-verification.md` 已在主工作区同步完毕，**无需也不应**从 worktree 合并文档。
- 不 push 远程、不创建远程仓库（执行纪律）。
- 项目记忆已沉淀（`~/.pi/agent/projects-memory/minecraft/MEMORY.md`：26.2 43 槽、Items clinit 测试坑、Mockito stub 残留、memory 工具 EACCES 兜底）。

## 五、合并后剩余事项（桌面端手工验证，非本次合并阻塞项）

清单：`docs/client-qol-manual-verification.md` 使用量挂件（Task 5）：
- 拾取/吃面包/连续放置 → 左侧弹出不闪回
- 快捷栏切换 → 左侧显示新主手物品
- 镐子挖矿掉耐久 → 右侧浮动列表弹镐子 + 耐久条
- 铁砧修复/经验修补耐久升高 → 同样触发
- 被攻击掉耐久 → 仅对应护甲**固定位**弹出（其他 3 件不动）
- 穿脱/换装 → 对应固定位；脱装备不弹
- 拾取新工具 → 仅左侧弹数量
- 整理背包 → 不闪挂件；动画平滑无 tick 步进

## 六、其他 worktree（非本次会话产物，合并前需确认各自验证状态）

| worktree | 分支 | commits (vs master) | 说明 |
|---|---|---|---|
| `.worktrees/equip-tooltip-options` | `feat/equip-tooltip-options` | 3（offhand 物品集扩展 + docs） | 非本次会话产物，工作区干净；是否合并需用户确认 |
| `.worktrees/shulker-tooltip-ui` | `feat/shulker-tooltip-ui` | 4（潜影盒 tooltip UI 改造） | 非本次会话产物，工作区干净；项目记忆标记"进行中"，是否合并需用户确认 |
