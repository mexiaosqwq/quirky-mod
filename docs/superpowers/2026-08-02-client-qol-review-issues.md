# 客户端 QoL 批次审查问题清单

> 审查时间：2026-08-02；三组 reviewer 并行（tooltip+HUD / 渲染+交互 / 内容），范围 48a5d04^..HEAD。
> 状态图例：✅ 已修复（commit 59b06d8）｜🔶 待决策（需用户拍板）｜🔷 待处理（低优先）｜❌ 误报（已验证不成立）

## 一、Critical（已全部修复 ✅）

| # | 问题 | 修复 |
|---|---|---|
| C1 | 火把箭缺 `data/minecraft/tags/item/arrows.json`——26.2 弓/弩弹药判定走 `ProjectileWeaponItem.ARROW_ONLY = stack.is(ItemTags.ARROWS)`，不加入 tag 弓无法装填 | 新增 arrows.json（`quirky:torch_arrow`） |
| C2 | 火把箭命中不可放置方块时**物品双回收**：`super.onHitBlock` 已把箭卡入方块（可拾取），分支又 `spawnAtLocation` 掉一份 → 1 支变 2 支 | 放置失败分支先 `discard()` 再掉落 |

## 二、Warnings（已修复 ✅ / 待处理 🔷）

| # | 问题 | 状态 |
|---|---|---|
| W1 | `offhandSwap=false` 时右键盾牌被静默吞掉（盾牌带 EQUIPPABLE，客户端照常拦截、服务端直接拒绝） | ✅ 服务端 `offhandItem` 判定加开关；关闭时盾牌回退原版 EQUIPPABLE 路径，火把（无组件）被拒；测试同步更新（`offhandSwapDisabledFallsBackToEquippablePath` + `torchRejectedWhenOffhandSwapDisabled`） |
| W2 | `grassMultiplier`/`ladderSnapStrength` 无边界注解——26.2 cloth-config **无 `@ConfigEntry.BoundedDouble`**（javap 实测只有 BoundedDiscrete） | ✅ 运行时 clamp（0.5~1.5 / 0.1~1.0），测试更新 |
| W3 | GreenerGrass 包装注册只做一次——F3+T 资源重载/重进世界后 BlockColors 实例重建，增绿效果静默丢失 | ✅ 每 tick 检测 `Minecraft.getBlockColors()` 实例变化，变化则重注册 |
| W4 | PickBlockMixin FIELD redirect 匹配 3 处 `hitResult` 读取（无 ordinal 全重定向）——功能正确但依赖未文档化语义，原版增删读取点会静默改变范围 | 🔷 待处理：加 ordinal 只定向 switch 读取 |
| W5 | `goldButton`/`ironButton`/`obsidianPlate` 三个 config 开关**全仓库无读取方**（死配置，关了毫无效果；对照 torchArrow/woodenHopper 已接线） | 🔶 待决策：接线方式见下 |
| W6 | GrassColorMatrix 强度直接乘对角项：0.5 时 R×0.445 **变暗**，与 spec"拉低趋近原版"语义冲突（插值趋近恒等矩阵 vs 缩放对角，spec 措辞歧义） | 🔶 待决策：见下 |

## 三、待决策（🔶 需用户拍板）

### D1：grassMultiplier 语义（spec 5.7 措辞歧义）
- **现状**：强度直接缩放 Quark 默认矩阵（R×0.89、G×1.11、B×0.89）的对角项——`0.5` 时草地 R×0.445 明显**变暗**，不是"趋近原版"。
- **选项 A（插值，推荐）**：`factor 0.5 → 0.5×默认矩阵 + 0.5×恒等矩阵`——0.5 时接近原版、1.0 为 Quark 默认增绿、1.5 更强。符合 spec"拉低趋近原版"。
- **选项 B（保持缩放）**：维持现状，spec 措辞改为"缩放矩阵对角强度"。
- 影响：GrassColorMatrix 构造逻辑 + 2 个测试断言。

### D2：按钮/压力板三个死开关（spec §6 列了开关但未接线）
- 现状：方块注册无运行时开关检查（对照服务端机制约定"入口先检查开关"）。
- **选项 A（运行时拦截，推荐）**：`MetalButtonBlock.useWithoutItem` / 压力板实体检测处检查开关——热切换生效，与原约定一致。
- **选项 B（注册级开关）**：接受"关闭需重启生效"，spec 注明语义。
- 影响：MetalButtonBlock / ObsidianPressurePlateBlock 各加一处判断 + 测试。

## 四、待处理低优先（🔷 建议后续批次）

| # | 问题 | 建议 |
|---|---|---|
| S1 | GreenerGrass `grassAffectLeaves` 只在注册时读一次，游戏内切换需重进世界 | 包装求值时读取开关（wrapper 已读 `greenerGrass`，补读 leaves） |
| S2 | `soul_candle[_lit].json` 为孤儿资源（无 blockstate/items 引用），靠 26.2 directory 图集机制侥幸可用 | 在 `docs/26.2-mechanics-notes.md` 记录该依赖 |
| S3 | EquipSwapClient 直接引用服务端类 `EquipSwapServer.isOffhandSwapItem`（main 侧类） | 纯物品判定挪到共享 helper（如 `dev.quirky.equip_swap.OffhandSwapItems`） |
| S4 | 火把箭 `setBaseDamageFromMob` 空覆写钉死生物射击伤害（26.2 唯一调用方 `ProjectileUtil.getMobArrow`） | 在类注释/提交信息注明"生物射出不随力量缩放"边界 |
| S5 | wooden_hopper blockstate 只有 5 个 facing 变体缺 `enabled`（实测谓词部分匹配，功能正确） | 补全 10 变体或改 spec 措辞 |
| S6 | MetalButtonBlock `holdTicks` 与父类私有 `ticksToStayPressed` 冗余（仅测试可读性） | 加注释说明镜像原因 |
| S7 | spec §7"lang 含 tooltip.quirky.* 键"——新物品只有物品名无 tooltip 键 | 与既有物品一致则无问题，否则补键 |

## 五、误报记录（❌ 已验证不成立）

- B 组 reviewer 建议 `@ConfigEntry.BoundedDouble`——26.2 cloth-config javap 实测**不存在**（仅 BoundedDiscrete），改为运行时 clamp（W2）。
- B 组 reviewer 称副手分支"零测试覆盖"——实际 `EquipSwapServerTest` 已有 5 个副手测试（shield/torch/互换/非副手物品/开关关闭），本次仅因 W1 语义变化更新了 1 个。

## 六、总结

- 审查共发现 2 Critical + 6 Warning + 7 Suggestion，误报 2 条。
- Critical 与 3 条 Warning 已修复并全量回归（gradle build + 150 测试全绿）。
- 剩余 1 条 Warning（W4 PickBlockMixin ordinal）低风险，2 条待用户决策（D1/D2），5 条低优先建议。
