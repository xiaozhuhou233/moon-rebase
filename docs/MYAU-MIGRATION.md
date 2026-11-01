# InjectMyau KillAura / Scaffold / Velocity port

来源：用户提供的 `InjectMyau-main.zip`，GPL-3.0。许可和说明位于
`resources/licenses/InjectMyau-*`。

GUI 中的新模块为 Combat → **KillAura**、**Velocity**，以及 Player →
**Scaffold**；配置键分别为 `myau-killaura`、`myau-velocity`、`myau-scaffold`。
所有上游公开设置均接入 Moon
设置系统，按 Main、Rotations / MoveFix、Auto Block、Targets、Movement、
Tower / Keep Y、Display 分组；模式选项和可见条件沿用上游。
Velocity 的设置保留七个上游模式及其可见逻辑，分组为 Knockback、Explosions、
Delay、Jump Reset、Watchdog。

核心文件位于 `src/moon/port/myau/module/modules/`。共享转头、MoveFix、
Blink 和槽位状态位于 `management/`；Moon 接口集中在
`src/moon/client/McpMyauModules.java`。算法保留上游实现，改变包名和平台接口。
NORMAL / GRIM 放置发包、静默物理朝向、跳跃朝向、客户端模型朝向、
LOCK 镜头、格挡姿态、持物伪装和鼠标/物品切换取消均已接线。

原有 `src/moon/module/combat/KillAuraModule.java` 和
`src/moon/module/player/ScaffoldWalkModule.java`、
`src/moon/module/combat/VelocityModule.java` 及其设置/实现文件保留。
它们不再进入模块列表、GUI、按键绑定列表或配置绑定，默认保持关闭。
旧配置条目不删除，也不自动套用到设置不同的新模块。

没有额外移植上游 KeepSprint、HitSelect、NoSlow、AutoHeal、BedNuker、
LongJump、TargetStrafe、AntiBot、BlockIn 等模块。
这些跨模块协作入口按未安装处理；KillAura 自身的 HIT_SELECT 模式仍保留。
这不等于整个 Myau 客户端或所有服务器兼容性已验证。

验证工具位于相邻 moon-deobf 工作区的 `tools/validate_myau_modules.py` 和
`tools/MyauModulesGameCheck.java`，读取本项目 `target/classes`，使用独立
生存存档和配置，检查服务器伤害、NORMAL/GRIM 放置与物品消耗、
静默朝向、八方向 MoveFix、设置校验/保存重载、Blink 和取消旧模块注册。

最终验证（2026-10-31）：

- JDK 24，`mvn -DskipTests compile`：BUILD SUCCESS，2519 个 Java 源文件。
- `MyauModulesGameCheck`：321 项断言通过，含真实服务器伤害、自动格挡、
  NORMAL/GRIM 放置和库存扣减、静默视角、模型朝向、八方向 MoveFix、
  点击入口取消、持物伪装、设置重载、Blink 和关闭 LOCK 后的状态清理。
  新增 Velocity 全部 23 个设置、七模式与可见项、实际 NetHandler 入站击退缩放、
  fake-check、爆炸门控、DELAY/WATCHDOG 队列按时释放及包顺序、MATRIX、
  Jump Reset PRE/POST 按键恢复、Aura attack-reduce 共享实例和停用清理。
- `SprintStateGameCheck`：47 项断言通过，20 次 W 按下/松开、持续斜向移动、
  Aura 目标停止疾跑及目标消失恢复，疾跑键状态不受合成写入影响。
- `audit_myau_source.py`：三个核心及五个关键转头/MoveFix 文件通过源码比对。
  排除命名空间与依赖导入适配后，算法源码与所提供的压缩包一致。

游戏验收使用本机集成服务器；未据此验证各在线服务器的兼容性。
