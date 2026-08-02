# 会话交接快照（2026-08-02 21:10）

> 用途：会话清空后，新会话据此恢复上下文继续工作。记忆体系已按分层约定恢复（项目 MEMORY.md 7 条 + 全局 MEMORY.md TOOLING 单条 + USER.md），技能 quirky-mixin-runtime-audit 已建。

## 1. 项目状态

- Quirky mod（MC 26.2 Fabric，dev.quirky，mojmap）15 项客户端 QoL 功能**全部实现并合入 master**
- 设计 spec / 计划 / 审查问题清单 / 手动验证清单均已在 `docs/superpowers/`：
  - `specs/2026-08-02-quirky-client-qol-design.md`、`plans/2026-08-02-quirky-client-qol-plan.md`
  - `2026-08-02-client-qol-review-issues.md`（全部闭环）
  - `client-qol-manual-verification.md`（15 项桌面验证清单）
- **当前 HEAD**：`2634fa4`（docs: add 26.2 mixin runtime pitfalls to AGENTS.md）
- **当前 jar**：`build/libs/quirky-0.1.0.jar`，SHA256 `924b6976dfe45349df32956fc5365b614e0934a0bfdcfdfb697169dd346d1791`
- 构建命令：`JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk PATH=.../bin:$PATH gradle build --no-daemon --console=plain`（150 tests 全绿）

## 2. 用户正在做什么

桌面端（vivo Android + Zalith Launcher，Fabric 0.19.3，26.2）已成功启动进入游戏，**正在手动验收 15 项**。发现的 bug 会陆续报来（或在本会话清空后新会话继续）。

## 3. 已修的 4 个运行时崩溃（编译全绿 ≠ 能跑的血泪史）

| 崩溃 | 根因 | 修复 commit |
|---|---|---|
| 1. `Layer with identifier minecraft:hotbar already exists`（启动即崩） | fabric-rendering-v1 25.3.1 静态块预注册 23 个原版 RootLayer，`addLast(HOTBAR,...)` validateUnique 首次即抛 | 38c0f6c：`attachElementAfter(VanillaHudElements.HOTBAR, QuirkyMod.id("usage_ticker"), ...)` |
| 2. `@Shadow setSprite was not located in the target class FlameParticle`（粒子注册崩） | @Shadow 只匹配目标类**本类**成员，setSprite 在父类 SingleQuadParticle | 7fc6429：目标类改 SingleQuadParticle + `(Object) this instanceof FlameParticle` 过滤 |
| 3. `tesselateBlock 描述符不匹配`（区块渲染崩，主动排查发现） | target 描述符写 `FFFF`（4 float），真实 `FFF`——mixin 字符串编译期不校验 | 1bdee66：改 `FFF` |
| 4. `InvalidInjectionException <init> 描述符不匹配`（粒子注册崩，修 #2 引入） | `@Inject(method="<init>")` 应用到**所有**构造器，4 参构造器与 handler 不匹配 | b941dab：完整描述符限定 `"<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"` |

## 4. 已验证通过的注入点（mcsrc 逐字核对，勿重复排查）

Camera.alignWithEntity(float) / Gui.setScreen(Screen)（handlePlayerCombatKill + Gui.tick 两处）/ Minecraft.pauseGame(boolean) / ItemStack.getTooltipLines(TooltipContext, Player, TooltipFlag) / LocalPlayer.aiStep() / ServerPlayer.die(DamageSource) + dropAllDeathLoot(ServerLevel, DamageSource) / DoorBlock.useWithoutItem + setOpen / completeUsingItem 内 ItemStack.finishUsingItem(Level, LivingEntity) / Item.getTooltipImage / Minecraft.hitResult 字段 / @Shadow level（ClientPacketListener 本类）/ CameraAccessor setRotation+setPosition（本类）/ getHoveredSlot（本类）/ Entity.igniteForSeconds(float) / DeathScreen(Component, boolean, LocalPlayer) / Level.getDefaultClockTime()（mcsrc 显示 ln()，运行时名以编译产物为准）。

## 5. 待桌面验收清单（按 client-qol-manual-verification.md）

1. 潜影盒 tooltip（3x9 网格）2. 食物 tooltip（鸡腿+数值）3. 属性 tooltip（附魔实际伤害）4. 使用量挂件（拾取/消耗/掉耐久弹出动画）5. 死亡镜头（死→环绕 2.5s→Esc 跳过→死亡界面）6. 灵魂光源（灵魂沙上放火把/灯笼/蜡烛→火焰青色；破坏恢复）7. 草地增绿（grassMultiplier 0.5/1.0/1.5 对比；grassAffectLeaves 热切换）8. 远距中键拾取（创造 100 格）9. 爬梯吸附 10. 副手换装（火把/盾牌右键）11. 金按钮 2 刻 12. 铁按钮 5 秒 13. 黑曜石压力板（仅玩家）14. 火把箭（弓装填/射中放火把/点燃/双回收已防）15. 木漏斗（1/4 速、红石锁不住、燃料 300 tick）
- 每个新物品/方块检查紫黑棋盘格（items/ + models/item/ 双文件）
- config 开关 15 个逐一验证（goldButton/ironButton/obsidianPlate 已接线热切换）

## 6. 关键 26.2 坑（详细在 docs/26.2-mechanics-notes.md + AGENTS.md Known Pitfalls + skill quirky-mixin-runtime-audit）

- mixin 四条：target 描述符逐字核对 / @Shadow 只匹配本类 / <init> 多构造器用描述符限定 / fabric API 用 javap 验证语义
- 26.2 运行时 = mojmap（jar 无 refmap 也跑）；mcsrc 反编译名不稳定（ln() = getDefaultClockTime），以编译产物为准
- 物品双文件（items/ + models/item/）；config 读取走 QuirkyConfigHolder（测试注入默认实例）；测试引导 TestBootstrap.boot() + bindItem()（bindMinimalComponents 无 EQUIPPABLE）
- cloth-config 26.2：无 BoundedDouble/Slider，float 运行时 clamp；AutoConfigClient.getConfigScreen 返回 Supplier
- HudElementRegistry 用 attachElementAfter/Before
- 记忆写入：memory 工具 EACCES（原子写失败会覆盖/清空文件！），可靠做法 = 直接 edit/write 文件；恢复源 = `.MEMORY.md.recovery-*` 备份 + 会话启动时自动 reconcile 进 db

## 7. 待办

- 桌面验收反馈的 bug 修复（按 quirky-mixin-runtime-audit 审计后交付）
- PENDING：截图模式 Camera Mode（spec 第 11 节，条件 = 本批验收通过后单独 brainstorm/spec）
- chat-export-2026-08-02_20-21-18.md / _20-37-58.md：桌面端 AI 对崩溃日志的诊断记录（20-37 那份已提交，20-21 那份未提交——如需保留请提交）
