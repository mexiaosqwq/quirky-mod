#!/data/data/com.termux/files/usr/bin/bash
cd /data/data/com.termux/files/home/minecraft

COMMON='你是 Quirky 模组的资深代码审查员（MC 26.2 官方映射）。审查范围为最近一批提交（BASE 48a5d04^ → HEAD），只做**只读**审查（git diff/git show/read/grep/find/ls，禁止修改文件、禁止跑 build）。双透镜：**规格审查**（对照 docs/superpowers/specs/2026-08-02-quirky-client-qol-design.md 的行为与验收标准，API 以 $HOME/.cache/mcsrc 为准）+ **质量审查**（干净、可维护、遵循 minecraft/AGENTS.md：注册模式 ResourceKey+setId、items/ 双文件、config 走 QuirkyConfigHolder、mixin 命名 XxxMixin）。输出格式：## Files Reviewed / ## Critical (must fix) / ## Warnings (should fix) / ## Suggestions / ## Summary（2-3 句总评 + 是否符合 spec）。文件路径带行号，具体到问题。'

TASKA="$COMMON

审查范围 A（tooltip 三件 + 使用量挂件 + 死亡电影镜头）：
src/client/java/dev/quirky/client/tooltips/ClientAttributeTooltipComponent.java
src/client/java/dev/quirky/client/tooltips/ClientFoodTooltipComponent.java
src/client/java/dev/quirky/client/tooltips/ClientShulkerTooltipComponent.java
src/client/java/dev/quirky/client/usage_ticker/ArmorTicker.java
src/client/java/dev/quirky/client/usage_ticker/TickerElement.java
src/client/java/dev/quirky/client/usage_ticker/TickerSnapshot.java
src/client/java/dev/quirky/client/usage_ticker/UsageTickerHud.java
src/client/java/dev/quirky/client/deathcam/DeathCamClient.java
src/client/java/dev/quirky/client/deathcam/DeathCamTimeline.java
src/main/java/dev/quirky/deathcam/DeathCamPayload.java
src/main/java/dev/quirky/deathcam/DeathCamServer.java
src/main/java/dev/quirky/mixin/TooltipDetailsMixin.java
src/main/java/dev/quirky/tooltips/AttributeLineCollector.java
src/main/java/dev/quirky/tooltips/AttributeTooltipComponent.java
src/main/java/dev/quirky/tooltips/EnchantedDamageCalculator.java
src/main/java/dev/quirky/tooltips/FoodTooltipComponent.java
src/main/java/dev/quirky/tooltips/ShulkerTooltipComponent.java
src/client/java/dev/quirky/client/mixin/CameraSetupMixin.java
src/client/java/dev/quirky/client/mixin/DeathScreenDelayMixin.java
src/client/java/dev/quirky/client/mixin/GuiDeathScreenDelayMixin.java
src/client/java/dev/quirky/client/mixin/DeathCamSkipMixin.java
src/client/java/dev/quirky/client/mixin/CameraAccessor.java

spec 要点核对：食物行鸡腿+数值/饱和度图标；属性行含附魔实际伤害、Shift 隐藏（hasShiftDown 时 getWidth/Height 返回 0）；挂件事件驱动（拾取/消耗/掉耐久触发、同帧多槽变化抑制、自下而上 ease-out 动画）；死亡镜头服务端 die RETURN 发 payload、客户端相机接管、Esc 跳过、结束进死亡界面、断线/换维度安全退出。"

TASKB="$COMMON

审查范围 B（灵魂光源 + 草地增绿 + 远距拾取 + 爬梯吸附 + 副手换装）：
src/client/java/dev/quirky/client/soul_lighting/SoulLightingHelper.java
src/client/java/dev/quirky/client/soul_lighting/SoulLightingModels.java
src/client/java/dev/quirky/client/soul_lighting/SoulCandleModel.java
src/client/java/dev/quirky/client/mixin/SectionCompilerMixin.java
src/client/java/dev/quirky/client/mixin/FlameParticleMixin.java
src/client/java/dev/quirky/client/greener_grass/GreenerGrassClient.java
src/main/java/dev/quirky/client_color/GrassColorMatrix.java
src/client/java/dev/quirky/client/pick_range/PickRangeHelper.java
src/client/java/dev/quirky/client/mixin/PickBlockMixin.java
src/client/java/dev/quirky/client/ladder_snap/LadderSnapHelper.java
src/client/java/dev/quirky/client/mixin/LocalPlayerAIStepMixin.java
src/client/java/dev/quirky/client/equip_swap/EquipSwapClient.java
src/main/java/dev/quirky/equip_swap/EquipSwapServer.java

spec 要点核对：灵魂光源仅光源方块（火把/灯笼/蜡烛）且 y-1 为 soul_sand/soul_soil 时换模型/粒子，破坏自动恢复、开关 soulLighting；草地增绿 3x3 矩阵（默认 R0.89/G1.11/B0.89 × grassMultiplier）、首帧后注册包装原 tint source、grassAffectLeaves；远距拾取创造 100/生存 12 格、开关 longPick；爬梯吸附 climbing 且无左右输入才修正、强度 ladderSnapStrength；副手换装仅盾牌/火把（isOffhandSwapItem）、开关 offhandSwap、防脱卸附魔保护保留。"

TASKC="$COMMON

审查范围 C（金/铁按钮 + 黑曜石压力板 + 火把箭 + 木漏斗）：
src/main/java/dev/quirky/ModBlocks.java
src/main/java/dev/quirky/ModItems.java
src/main/java/dev/quirky/ModEntities.java
src/main/java/dev/quirky/block/MetalButtonBlock.java
src/main/java/dev/quirky/block/ObsidianPressurePlateBlock.java
src/main/java/dev/quirky/block/WoodenHopperBlock.java
src/main/java/dev/quirky/block/be/WoodenHopperBlockEntity.java
src/main/java/dev/quirky/torch_arrow/TorchArrowEntity.java
src/main/java/dev/quirky/torch_arrow/TorchArrowItem.java
src/client/java/dev/quirky/client/torch_arrow/TorchArrowRenderer.java
src/client/java/dev/quirky/client/torch_arrow/TorchArrowRenderState.java
src/main/resources/data/quirky/recipe/*.json（5 个配方）
src/main/resources/assets/quirky/items/*.json 与 models/item/*.json（核对 items/ 双文件一致性）
src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json 与 needs_diamond_tool.json

spec 要点核对：金按钮 2 刻/铁按钮 100 刻、金属 BlockSetType、配方金=木按钮tag+金粒/铁=石按钮+铁粒；压力板仅玩家触发、2 黑曜石；火把箭命中方块放火把（不可放置掉落物品）/命中实体点燃、基础伤害 1.0、发射器可射、配方火把+箭；木漏斗 32 tick 冷却、红石锁不住、燃料 300 tick、配方 5 木板+箱子；所有新物品 items/ 双文件齐全（紫黑棋盘格陷阱）；方块进 REDSTONE_BLOCKS/COMBAT 页签。"

launch() {
  local tag=$1 task=$2
  ( timeout 1500 pi --mode text -p --no-session --model opencode-go/deepseek-v4-flash --append-system-prompt "$HOME/.pi/agent/agents/reviewer.md" "$task" > "/data/data/com.termux/files/home/minecraft/review-$tag.log" 2>&1 ) &
  echo "launched reviewer-$tag pid $!"
}

launch A "$TASKA"
launch B "$TASKB"
launch C "$TASKC"
wait
echo REVIEW_ALL_DONE
