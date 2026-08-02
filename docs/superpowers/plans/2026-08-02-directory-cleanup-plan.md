# Quirky 项目目录整理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理 Quirky 仓库根级散落的一次性文件与冗余文档，归档或删除，使根目录只保留项目级基础设施文件，目录职责清晰。

**Architecture:** 按"根级只留基础设施"原则：一次性派发脚本/日志（review-dispatch.*、chat-export）删除（信息已沉淀至 AGENTS.md/记忆）；features.md 与 README.md 合并去重后删除冗余副本；docs/superpowers 下临时性文档（review-issues、session-handoff）移入归档目录；日志保留但已忽略。全程只动文档与脚本，不碰 src/ 代码。

**Tech Stack:** 无代码改动——纯文件整理（git mv / rm / 文档合并）。验证 = `git status` 干净 + `git grep` 无断链引用。

## Global Constraints

- 根级只保留：`.gitattributes` `.gitignore` `AGENTS.md` `LICENSE` `README.md` `build.gradle` `gradle.properties` `gradlew` `gradlew.bat` `settings.gradle` + 目录（`.github/` `.pi/` `docs/` `gradle/` `src/` `openspec/`）
- 不碰 `src/`、`build.gradle`、`settings.gradle`、`.github/`、`.pi/`、`openspec/` 内任何内容
- 删除前确认信息已沉淀（AGENTS.md / hermes-memory / docs/26.2-mechanics-notes.md），不丢失可追溯知识
- 文档内引用（git grep 命中）随文件移动/删除同步更新
- 提交信息用仓库历史前缀：`chore:`（整理类）
- 每任务独立提交，验证 `git status` 干净

---

### Task 1: 删除一次性审查派发脚本与输出

**Files:**
- Delete: `review-dispatch.sh`（git 已跟踪）
- Delete: `review-dispatch.out`（git 已跟踪）

**Interfaces:**
- Consumes: 无（脚本是 8/2 一次性三路 reviewer 派发器，日志 review-{A,B,C}.log 已删）
- Produces: 无

**背景**：该脚本把三条审查任务并发派给 pi reviewer（`launch A/B/C` → review-*.log），已执行完毕；审查结论已落地（docs/superpowers/2026-08-02-client-qol-review-issues.md、AGENTS.md Pitfalls、commit 59b06d8）。脚本引用的日志文件已删除，脚本本身无再执行价值。

- [ ] **Step 1: 确认脚本信息已沉淀**

```bash
cd /data/data/com.termux/files/home/minecraft
git log --oneline -3 | head -5        # 审查 commit 已提交
git grep -l "review-dispatch" -- . ':!review-dispatch.sh' ':!review-dispatch.out' || echo "无外部引用"
```

- [ ] **Step 2: 删除文件**

```bash
git rm review-dispatch.sh review-dispatch.out
```

- [ ] **Step 3: 验证**

```bash
git status --short        # 预期：只显示这两个 D 删除
```

- [ ] **Step 4: 提交**

```bash
git commit -m "chore: remove one-off review dispatch script and output"
```

---

### Task 2: 合并 features.md 入 README 后删除

**Files:**
- Modify: `README.md`（补充功能描述段落，若缺失）
- Delete: `features.md`（git 已跟踪）

**Interfaces:**
- Consumes: `features.md` 全文（8 个机制的玩法描述，用户向文案）
- Produces: README.md 成为唯一用户向功能文档

**背景**：`README.md` 与 `features.md` 功能清单完全重合（grep 特征词集合一致）。README 是标准项目入口（含功能列表），features.md 是口语化长文副本。保留 README，把 features.md 中 README 未覆盖的"玩法感受"段（若存在）并入 README 后删除副本。

- [ ] **Step 1: 对比两文件内容**

```bash
cd /data/data/com.termux/files/home/minecraft
diff <(grep -oE '地图预览|右键收割|双开门|时钟|云瓶|图腾|火把箭|灵魂' README.md | sort -u) \
     <(grep -oE '地图预览|右键收割|双开门|时钟|云瓶|图腾|火把箭|灵魂' features.md | sort -u)
# 预期：无差异（功能集合一致）
wc -l README.md features.md
```

- [ ] **Step 2: 检查 features.md 是否有 README 缺失的段落**

```bash
# 对比章节标题
grep -nE '^#{1,3} ' README.md features.md
```

若 features.md 含 README 没有的玩法细节段落（如"功能说明"的展开描述），手工复制到 README 对应功能条目下（保留原措辞）；若无，跳过本步。

- [ ] **Step 3: 删除 features.md**

```bash
git rm features.md
```

- [ ] **Step 4: 验证无残留引用**

```bash
git grep -l "features.md" -- . || echo "无引用"
git status --short
```

- [ ] **Step 5: 提交**

```bash
git commit -m "chore: merge features doc into README and remove duplicate"
```

---

### Task 3: 归档临时审查/交接文档

**Files:**
- Move: `docs/superpowers/2026-08-02-client-qol-review-issues.md` → `docs/superpowers/archive/2026-08-02-client-qol-review-issues.md`
- Move: `docs/superpowers/2026-08-02-session-handoff.md` → `docs/superpowers/archive/2026-08-02-session-handoff.md`

**Interfaces:**
- Consumes: 两文件现状（review-issues = 8/2 三组 reviewer 发现清单；session-handoff = 会话交接快照）
- Produces: `docs/superpowers/archive/` 目录（临时性/一次性文档归档处）

**背景**：两者都是**时点快照**（某次审查/某次会话的瞬时状态），不是持续有效的规格或计划。归档而非删除以保留可追溯性；不留在 docs/superpowers/ 根级以免与 specs/ plans/ 混淆。

- [ ] **Step 1: 创建归档目录并移动**

```bash
cd /data/data/com.termux/files/home/minecraft
mkdir -p docs/superpowers/archive
git mv docs/superpowers/2026-08-02-client-qol-review-issues.md docs/superpowers/archive/
git mv docs/superpowers/2026-08-02-session-handoff.md docs/superpowers/archive/
```

- [ ] **Step 2: 检查外部引用并更新**

```bash
git grep -l "2026-08-02-client-qol-review-issues\|2026-08-02-session-handoff" -- . || echo "无引用"
# 若有命中（如 AGENTS.md/README/其他 docs），将路径改为 docs/superpowers/archive/ 前缀
```

- [ ] **Step 3: 验证**

```bash
ls docs/superpowers/archive/        # 两个文件
git status --short                   # 两个 rename
```

- [ ] **Step 4: 提交**

```bash
git commit -m "chore: archive one-off review issues and session handoff docs"
```

---

### Task 4: 清理会话导出文件（chat-export）

**Files:**
- Delete: `chat-export-2026-08-02_20-37-58.md`（git 已跟踪）

**Interfaces:**
- Consumes: 无
- Produces: 无

**背景**：这是 8/2 从 pi 会话导出的"模组崩溃诊断"聊天记录（FlameParticleMixin 崩溃分析）。根因结论已沉淀：commit b941dab（修复 7 参构造器注入）+ AGENTS.md Pitfalls「Mixin @Shadow/@Invoker 只匹配目标类本类成员」+ hermes-memory SHADOW 条目。导出文件本身是一次性对话快照，无持续引用价值。

- [ ] **Step 1: 确认信息已沉淀**

```bash
git log --oneline --all --grep="FlameParticle" | head -3   # b941dab 已提交
git grep -l "chat-export" -- . ':!chat-export-*.md' || echo "无引用"
```

- [ ] **Step 2: 删除**

```bash
git rm chat-export-2026-08-02_20-37-58.md
```

- [ ] **Step 3: 验证 + 提交**

```bash
git status --short
git commit -m "chore: remove exported crash-diagnosis chat log"
```

---

### Task 5: 最终一致性验证

**Files:**
- Modify: 无（只验证）

**Interfaces:**
- Consumes: 前四任务结果
- Produces: 验收结论

- [ ] **Step 1: 根目录清单核对**

```bash
cd /data/data/com.termux/files/home/minecraft
ls -la | grep -vE "^total|^d"
# 预期仅：.gitattributes .gitignore AGENTS.md LICENSE README.md build.gradle
#          gradle.properties gradlew gradlew.bat settings.gradle
```

- [ ] **Step 2: 无散落临时文件**

```bash
ls *.log *.out *.sh *.txt chat-export* 2>/dev/null || echo "根级无临时文件"
```

- [ ] **Step 3: docs 结构核对**

```bash
find docs -maxdepth 2 -type f | sort
# 预期：docs/26.2-mechanics-notes.md, docs/client-qol-manual-verification.md,
#       docs/superpowers/{archive/,plans/,specs/}
```

- [ ] **Step 4: git 状态干净**

```bash
git status --short          # 无未提交改动（除 AGENTS.md 等既有改动，见备注）
git log --oneline -5        # 4 个 chore 提交
```

- [ ] **Step 5: 构建不回归（可选，整理不碰代码）**

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
# 预期：BUILD SUCCESSFUL（代码未动，应秒级增量构建）
```

---

## Self-Review 记录

**1. Spec 覆盖**：需求=整理项目目录。覆盖：根级散落文件清理（T1/T4）、文档去重（T2）、临时文档归档（T3）、验证（T5）。src/ 与构建配置严格不动（Global Constraints）。✓

**2. Placeholder 扫描**：无 TBD/TODO；每步含实际命令。Task 2 Step 2 的"若 features.md 含未覆盖段落则复制"为条件分支，附具体判定方法（diff 章节标题），非占位。✓

**3. 类型一致性**：文件路径在任务间一致（docs/superpowers/archive/ 目录在 T3 创建、T5 验证引用同名）；无函数/符号。✓

**备注**：仓库当前有未提交改动（AGENTS.md、.pi/agents/ 等——本会话文档更新），Task 5 验证时这些会显示为 M，属预期；整理任务的 4 个提交只包含本计划文件，不与既有改动混提。
