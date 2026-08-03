# Quirky 工作区合并交接说明

> 给后续合并 agent 使用。本文档按当前实际 Git 状态编写。
>
> 本文件是一次性合并交接文档，位于项目根目录，合并完成后可由维护者删除或归档；不要把它当作功能代码的一部分。

## 目标

将以下三个独立 worktree 分支合并到主分支 `master`。三个分支都从同一个基线 `543b763` 分叉，彼此不是祖先关系；应分别合并，不要只合并其中一个。

## 当前工作区状态

主仓库：

```text
/data/data/com.termux/files/home/minecraft
```

主分支基线：

```text
master / origin/master: 543b763 chore: drop internal review tag from MetalButtonBlock comment
```

工作区均已核对为 clean：

| Worktree | 分支 | HEAD | 内容 |
|---|---|---|---|
| `.worktrees/quirky` | `fix/usage-ticker-placement` | `9c9c790` | 使用量挂件、护甲耐久挂件、装备槽变化触发与快照逻辑 |
| `.worktrees/equip-tooltip-options` | `feat/equip-tooltip-options` | `6fadbdb` | 副手装备物品集合与配置/测试 |
| `.worktrees/shulker-tooltip-ui` | `feat/shulker-tooltip-ui` | `d61e0d1` | 潜影盒 tooltip 原版槽位 UI 与测试 |

另有一个不挂载 worktree 的 ref：

```text
pi-agent-71941c44-5b05-467 -> ad30d14
```

它与 `feat/equip-tooltip-options` 属于同一副手功能线的替代提交，**不要额外合并**，否则会重复引入同一功能。除非用户另行指定，不要处理这个 ref。

首次操作前核对：

```sh
ROOT=/data/data/com.termux/files/home/minecraft

git -C "$ROOT" worktree list
git -C "$ROOT" status --short --branch
for wt in "$ROOT/.worktrees/quirky" \
          "$ROOT/.worktrees/equip-tooltip-options" \
          "$ROOT/.worktrees/shulker-tooltip-ui"; do
  git -C "$wt" status --short --branch
done
```

主根目录中的 `MERGE_WORKTREES.md` 是本次交接文件，可能显示为未跟踪文件；不要删除。它不与任何功能分支路径冲突，通常不会阻止合并。

## 推荐合并顺序

必须在主仓库根目录执行，不要在 linked worktree 内执行：

```sh
ROOT=/data/data/com.termux/files/home/minecraft
cd "$ROOT"

git checkout master

git merge --no-ff fix/usage-ticker-placement \
  -m "merge: integrate usage ticker improvements"
```

先合并使用量挂件分支，因为它改动最多，先单独验证更容易定位问题。

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle test --no-daemon --console=plain
```

测试通过后继续：

```sh
git merge --no-ff feat/equip-tooltip-options \
  -m "merge: integrate equip tooltip options"
```

再次运行同一条 `gradle test`，通过后再合并潜影盒 UI：

```sh
git merge --no-ff feat/shulker-tooltip-ui \
  -m "merge: integrate vanilla shulker tooltip UI"
```

最终验证：

```sh
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain

git status --short --branch
git log --oneline --decorate --max-count=12
git worktree list
```

## 各分支改动范围

### `fix/usage-ticker-placement`

只涉及使用量挂件相关文件：

- 删除：`src/client/java/dev/quirky/client/usage_ticker/ArmorTicker.java`
- 新增：`src/client/java/dev/quirky/client/usage_ticker/DurabilityTicker.java`
- 修改：`TickerElement.java`
- 修改：`TickerSnapshot.java`
- 修改：`UsageTickerHud.java`
- 修改：`TickerElementTest.java`
- 修改：`TickerSnapshotTest.java`

该分支当前 HEAD 为 `9c9c790`，包含从快速放置方块触发修复到 per-slot armor ticker 的连续改动。合并后重点检查使用量挂件、耐久挂件和装备槽触发是否仍然共存。

### `feat/equip-tooltip-options`

只涉及副手装备集合与配置：

- `src/main/java/dev/quirky/config/QuirkyConfig.java`
- `src/main/java/dev/quirky/equip_swap/OffhandSwapItems.java`
- `src/test/java/dev/quirky/config/QuirkyConfigDefaultsTest.java`
- `src/test/java/dev/quirky/equip_swap/OffhandSwapItemsTest.java`
- 以及该分支新增的 `docs/superpowers/` 设计/计划文档

### `feat/shulker-tooltip-ui`

潜影盒 tooltip 改为原版 slot sprite：

- `src/client/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponent.java`
- `src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java`
- `src/main/java/dev/quirky/tooltips/ShulkerTooltipComponent.java`
- `src/test/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponentTest.java`
- `docs/superpowers/specs/2026-08-03-shulker-tooltip-ui-design.md`
- `docs/superpowers/plans/2026-08-03-shulker-tooltip-ui-plan.md`

该分支的实现提交为 `d61e0d1`；其前置设计/计划提交为 `3a20c8d`、`3c4b28f`、`c5ca3e4`。功能行为：9×3、每格 18px、使用 `minecraft:container/slot`，删除紫色自绘背景和盒色传递。自动测试与构建已在该 worktree 通过；桌面客户端视觉截图尚未完成，合并后仍需手动验收。

## 冲突处理规则

- 三个功能分支的源码路径基本不重叠，正常情况下不应有 Java 冲突。
- `docs/superpowers/` 下的文件名也不同；不要因为根目录 `.gitignore` 忽略 `docs` 就擅自删除分支中的已跟踪文档。
- 若出现冲突，先执行：

```sh
git status
git diff --cc
```

- 不要直接使用 `git checkout --theirs` 或 `git checkout --ours` 覆盖整个文件；逐段确认后再解决。
- 解决冲突后必须重新运行 `gradle test`；不能只看 `git diff` 判断成功。
- 任意一次合并后的测试失败都先停止，保留现场并报告失败任务、日志和冲突文件，不要继续合并下一个分支。

## 约束

- 不执行 `git push`，不创建远程仓库，不修改远程配置。
- 不删除任何 worktree 或分支；合并完成后保留它们，方便回溯和人工核对。
- 不把 `pi-agent-71941c44-5b05-467` 作为第四个功能分支合并。
- 合并完成前不要删除本交接文档。
- 最终报告必须包含：合并顺序、每次测试/构建命令及结果、最终 HEAD、worktree 状态，以及潜影盒 UI 尚未进行桌面视觉验收这一事实。
