# 客户端 QoL 批次审查问题清单

> 审查时间：2026-08-02；三组 reviewer 并行（tooltip+HUD / 渲染+交互 / 内容），范围 48a5d04^..HEAD。
> 状态图例：✅ 已修复（commit 59b06d8 / 2101c5a / b08aedf）｜🔶 待决策（需用户拍板）｜🔷 待处理（低优先）｜❌ 误报（已验证不成立）

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
| W4 | PickBlockMixin FIELD redirect 匹配 3 处 `hitResult` 读取（无 ordinal 全重定向）——功能正确但依赖未文档化语义，原版增删读取点会静默改变范围 | ✅ 2101c5a：javap 实测 3 处 GETFIELD（offset 1/8/27），改双 `@Redirect`（ordinal 1=getType 检查 + ordinal 2=switch 分发），null 检查保持原版字段 |
| W5 | `goldButton`/`ironButton`/`obsidianPlate` 三个 config 开关**全仓库无读取方**（死配置，关了毫无效果；对照 torchArrow/woodenHopper 已接线） | ✅ b08aedf（用户选 A）：按钮 `useWithoutItem` 拦截、压力板 `getSignalStrength` 返回 0，热切换生效；新增 3 测试 |
| W6 | GrassColorMatrix 强度直接乘对角项：0.5 时 R×0.445 **变暗**，与 spec"拉低趋近原版"语义冲突（插值趋近恒等矩阵 vs 缩放对角，spec 措辞歧义） | ✅ b08aedf（用户选 A）：改为插值 `scale = 1 + m×(factor−1)`——0.5 趋近原版、1.0 Quark 默认、1.5 外推更强；测试更新 |

## 三、已决策（✅ 2026-08-02 用户选 A）

### D1：grassMultiplier 语义（spec 5.7 措辞歧义）→ 选项 A 插值
- 实现：`scale = 1 + multiplier×(factor−1)`；0.5 时矩阵 (0.945, 1.055, 0.945) 趋近原版，1.0 = Quark 默认 (0.89, 1.11, 0.89)，1.5 = (0.835, 1.165, 0.835) 更强。commit b08aedf。

### D2：按钮/压力板三个死开关 → 选项 A 运行时拦截
- 实现：`MetalButtonBlock.useWithoutItem`（构造新增 `gold` 布尔区分开关）+ `ObsidianPressurePlateBlock.getSignalStrength` 返回 0；热切换生效。新增 3 测试（金按钮关→PASS 不按下/开→SUCCESS 进 POWERED、压力板关→恒 0）。commit b08aedf。

## 四、待处理低优先（🔷 建议后续批次）

| # | 问题 | 状态 |
|---|---|---|
| S1 | GreenerGrass `grassAffectLeaves` 只在注册时读一次，游戏内切换需重进世界 | ✅ 2101c5a：树叶无条件注册包装，wrapper 内每次取色判断开关 |
| S2 | `soul_candle[_lit].json` 为孤儿资源（无 blockstate/items 引用），靠 26.2 directory 图集机制侥幸可用 | ✅ 2101c5a：已记入 `docs/26.2-mechanics-notes.md`（升级时检查图集定义） |
| S3 | EquipSwapClient 直接引用服务端类 `EquipSwapServer.isOffhandSwapItem`（main 侧类） | ✅ 2101c5a：新建共享 `OffhandSwapItems`，两侧改引用 |
| S4 | 火把箭 `setBaseDamageFromMob` 空覆写钉死生物射击伤害（26.2 唯一调用方 `ProjectileUtil.getMobArrow`） | ✅ 2101c5a：spec 5.14 补边界说明 |
| S5 | wooden_hopper blockstate 只有 5 个 facing 变体缺 `enabled`（实测谓词部分匹配，功能正确） | ✅ 2101c5a：补全 10 变体（facing × enabled） |
| S6 | MetalButtonBlock `holdTicks` 与父类私有 `ticksToStayPressed` 冗余（仅测试可读性） | ✅ 2101c5a：加注释说明镜像原因 |
| S7 | spec §7"lang 含 tooltip.quirky.* 键"——新物品只有物品名无 tooltip 键 | ✅ 关闭：`tooltip.quirky.clock` 已满足，新物品无专属键与既有模式一致 |

## 五、误报记录（❌ 已验证不成立）

- B 组 reviewer 建议 `@ConfigEntry.BoundedDouble`——26.2 cloth-config javap 实测**不存在**（仅 BoundedDiscrete），改为运行时 clamp（W2）。
- B 组 reviewer 称副手分支"零测试覆盖"——实际 `EquipSwapServerTest` 已有 5 个副手测试（shield/torch/互换/非副手物品/开关关闭），本次仅因 W1 语义变化更新了 1 个。

## 六、总结

- 审查共发现 2 Critical + 6 Warning + 7 Suggestion，误报 2 条。
- **全部已解决**：Critical/Warnings/Suggestions 分 3 批提交（59b06d8 修复、2101c5a 加固清理、b08aedf 决策落地），150 测试全绿。
- 剩余：桌面端按 `docs/client-qol-manual-verification.md` 手动验收（发布前必要）。
