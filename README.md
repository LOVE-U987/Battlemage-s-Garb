# Battlemage's Garb (法术战袍)

为 [神化 (Apotheosis)](https://modrinth.com/mod/apotheosis) 模组添加一个新的装备类型：**法术战袍**。
法术战袍对应 [Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks) 中专门为法师设计的盔甲，
其神化词条为对应装备主加成流派提供额外加成，以弥补神化装备加成中缺少法师可用加成词条的问题。

Adds a new equipment type — the **Spell Garb** — to [Apotheosis](https://modrinth.com/mod/apotheosis).
It targets Iron's Spells 'n Spellbooks mage armor, and its affixes grant school-specific bonuses for spellcasters.

## 功能 / Features

### 法术战袍识别

- 通过物品标签 `battlemages_garb:spell_garb` 识别（默认引用 Iron's Spells 的全部法师盔甲标签），
  并以「含流派法术强度属性」兜底。其他模组的法师盔甲可向该标签追加以纳入。

### 词条系统

**专属词条**（流派动态绑定，共 8 个）：

| 词条 | 效果 |
|---|---|
| 学派精通 `school_power` | +主流派法术强度 |
| 学派精通·进阶 `school_power_greater` | +主流派法术强度（需流派加成 ≥ 0.10） |
| 学派精通·终极 `school_power_exalted` | +主流派法术强度（需流派加成 ≥ 0.15） |
| 学派抗性 `school_resist` | +主流派法术抗性 |
| 学派抗性·进阶 `school_resist_greater` | +主流派法术抗性（需流派加成 ≥ 0.10） |
| 学派专精 `school_specialization` | 主流派法术强度大幅提升，其余流派法术强度降低（偏科设计） |
| 法术等级·流派 `school_spell_level` | 主流派法术等级 +N |
| 法术等级·全局 `spell_level` | 所有流派法术等级 +N |

**通用词条**（限定法术战袍，共 8 个）：法力泉涌 `max_mana`、法力涌动 `mana_regen`、法术强化 `spell_power`、
冷却缩减 `cooldown_reduction`、施法加速 `cast_time_reduction`、召唤强化 `summon_damage`、施法疾行 `casting_movespeed`、
法术抗性 `spell_resist`。

### 概率浮动机制

- 装备的**流派法术强度加成之和越高，可 roll 出的专属词条越多**（通过 `min_school_power` 阈值实现）。
  例如 pyromancer 胸甲（+0.10）可 roll 出「学派精通 + 进阶」，而 wizard 套（无流派加成）专属词条全部不适用。

### 法术等级追加显示

- 法术等级加成以「**基础等级（+加成）**」展示，例如卷轴 5 级 + 词条加成 3 级 → 「**5级（+3级）**」，
  而非把加成合并进主数字（「8级（+3）」）。通过客户端 mixin（`TooltipsUtilsMixin`）实现，仅在法术 tooltip 生效。

### 强化台概率显示

- 在神化强化台（Augmenting Table）的「潜在刷新结果」列表中，每个候选词条后附加其出现概率「(xx%)」，
  按词条权重占比计算。通过客户端 mixin（`AugmentingScreenMixin`）实现。

### 可扩展流派绑定

- 流派集合由属性标签 `battlemages_garb:school_power_attributes` 定义（默认收录 Iron's Spells 的 9 个 `*_spell_power` 属性）。
  其他模组只需用 datapack 向该标签追加自己的流派属性即可兼容，无需修改代码。

### 规范化的词条注册 API

- 词条类型（codec）与实例（JSON）分离，新增词条多数只需写一个 JSON。详见 [docs/AFFIX_API.md](docs/AFFIX_API.md)。

## 依赖 / Dependencies

- [Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks) (`irons_spellbooks`)
- [Apotheosis](https://modrinth.com/mod/apotheosis) (`apotheosis`)

## 环境 / Environment

- Minecraft `1.21.1`
- NeoForge `21.1.235`
- Java `21`

## 构建 / Building

```bash
./gradlew build
```

产物输出到 `build/libs/`。

> 注意：`maven.neoforged.net` 在中国大陆可能无法直连，本项目已内置镜像重写（`neoforged.forgecdn.net`），无需额外配置。
> 构建使用 `-proc:none` 禁用 mixin 注解处理器（NeoForge 运行时为 mojmap 命名，不需要 AP 生成的 refmap/混淆映射）。

## 自动测试 / Self-Test

`runServer` 配置了自检开关，启动完整服务器、运行断言并自动退出：

```bash
./gradlew runServer -PbattleSelfTest
```

自检覆盖（56 项断言）：词条加载、Iron's 战袍识别、流派加成求和、所有词条 `canApplyTo`、
「流派加成越高 → 专属词条越多」的概率浮动机制、以及「法术等级」词条的事件逻辑
（`ModifySpellLevelEvent`：流派匹配、全局加成、不匹配不生效）。全部通过时退出码 0。
