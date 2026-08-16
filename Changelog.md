# Battlemage's Garb Changelog

## v0.2.1 — 2026-08-16

### 修复:强化台概率显示 mixin 崩溃（`AugmentingScreenMixin`）
- 客户端启动崩溃 `@Shadow field font was not located in the target class`:Mixin 的 `@Shadow` 只查找目标类自身声明的字段,不查继承链,而 `font` 继承自 `Screen` 无法 attach
- 移除 `@Shadow protected Font font`,改用 `Minecraft.getInstance().font`(`Screen.font` 与 `minecraft.font` 为同一引用),修复启动崩溃

## v0.2.0 — 2026-08-16

### 新增:法术等级词条类型 `battlemages_garb:spell_level`（`SpellLevelAffix`）
- 通过 Iron's Spells 的 `ModifySpellLevelEvent` 提升施法者法术等级,同时影响法术 tooltip、法力消耗与实际效果
- `mode: "global"` = 所有流派法术 +N 级;`mode: "school"` = 仅战袍主加成流派 +N 级
- 等级加成用 `event.addLevels(bonus)` 直接叠加,**不** clamp 到 `maxLevel`(Iron's 的 `getLevelFor` 本身不限制,affinity 加成同理;clamp 会把加成吞掉变成"合并计算")
- 流派匹配用 `SpellSchoolHelper.isSameSchool`(流派属性 ↔ 法术流派 id)
- 词条实例:`spell_level`(全局,权重 15)、`school_spell_level`(流派,权重 20)

### 新增:学派专精词条类型 `battlemages_garb:school_specialization`（`SchoolSpecializationAffix`）
- 战袍主加成流派法术强度提升 `bonus_values`,其余流派(战袍上出现的)降低 `penalty_values`
- 同一词条对多个流派属性用 `makeUniqueId(salt)` 保证 modifier id 唯一
- 词条实例:`school_specialization`(权重 12)

### 新增:纯数据进阶词条
- `school_power_exalted`:流派法术强度·终极(门槛 `min_school_power` 0.15)
- `school_resist_greater`:流派抗性·进阶(门槛 `min_school_power` 0.10)

### 新增:强化台显示词条概率（`AugmentingScreenMixin`,客户端）
- mixin 重写 `AugmentingScreen#computeAlternatives`,在「潜在刷新结果」里按各词条 `weights` 权重占比附加出现概率,如「全部法术等级 +1(12%)」
- 概率文本 key:`text.battlemages_garb.affix_chance`

### 新增:法术等级 tooltip 显示基础等级（`TooltipsUtilsMixin`,客户端）
- hook Iron's `TooltipsUtils#getLevelComponenet`,把法术等级 tooltip 的主数字改为「基础等级」,词条加成以「( +N)」展示(如 5 级 + 3 = 显示「5(+3)」而非把加成合并进主数字)

### 调整
- `SchoolAttributeAffix` 的 `min_school_power` 门槛现在对 `resist` 目标同样生效(原仅对 `power`);现有实例默认 0,行为不变
- 自检扩展至 56 项断言,覆盖新词条加载/`canApplyTo`/门槛/`ModifySpellLevelEvent` 事件逻辑
  - 自检无玩家环境需手动构建 Iron's `SpellConfigManager` 配置(反射调用 `buildConfigManager`),否则 `getSchoolType()`/`getMaxLevel()` 返回静态默认值导致断言失真
- `SpellLevelAffix.register()` 事件监听并入 `BattlemageAffixes.registerCodecs()` 统一注册
- 构建:`JavaCompile` 加 `-proc:none` 禁用 mixin 注解处理器(NeoForge 运行时为 mojmap,不需 AP 生成的 refmap/混淆映射;否则 AP 会对依赖 jar 的 @Inject 目标报 obfuscation 错误)

## v0.1.0 — 2026-08-15

### 修复: generateModMetadata 模板解析失败导致构建中断
- `src/main/templates/META-INF/neoforge.mods.toml` 第 2 行注释中的字面 `${...}` 文本会被 Gradle `expand`(SimpleTemplateEngine) 当作 Groovy 表达式解析,`...` 非法导致 `Unexpected input` 编译错误
  - 处理:注释改为纯文本描述,不再包含字面 `$` + `{` 组合
- 模板中 description 原先使用 TOML 三引号 `'''...'''` 多行字符串,与 SimpleTemplateEngine 的字符串识别冲突
  - 处理:改为单行双引号字符串 + `\n` 转义表示换行(TOML 合法转义)
- 构建任务 `generateModMetadata` 现可正常展开占位符(`mod_id` / `mod_version` / `neo_version` 等),`./gradlew build` 通过
