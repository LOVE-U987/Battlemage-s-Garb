# 法术战袍 · 词条注册 API 开发规范

本模组的词条系统建立在 Apotheosis（8.7.0）的词条机制之上。本文档规定**如何为本模组添加新词条**，以及必须遵守的防错约束，避免再次出现栈溢出、浮点精度等错误。

---

## 1. 核心概念：词条类型 vs 词条实例

| 概念 | 说明 | 存放位置 |
|---|---|---|
| **词条类型（codec）** | 一种行为模式，对应一个 `Affix` 子类 + 静态 `CODEC`。类型决定词条「怎么起作用」。 | `src/main/java/dev/battlemages_garb/` |
| **词条实例** | 一条数据包 JSON，对应一个具体词条。实例决定词条「是什么」（属性、数值、权重、稀有度）。 | `src/main/resources/data/battlemages_garb/affixes/<name>.json` |

- 一个类型可对应**任意多个**实例（例如 `garb_attribute` 类型下有 `max_mana`、`mana_regen`、`spell_power`…… 11 个实例）。
- **绝大多数新词条只需要写一个 JSON**，不需要写 Java。

### 已有词条类型

| 类型 id | 类 | 用途 |
|---|---|---|
| `battlemages_garb:school_attribute` | `SchoolAttributeAffix` | 根据战袍主加成流派，动态给对应流派属性加成（支持 `min_school_power` 概率浮动） |
| `battlemages_garb:garb_attribute` | `GarbAttributeAffix` | 给法师盔甲添加固定属性加成（限定法术战袍） |
| `battlemages_garb:spell_level` | `SpellLevelAffix` | 法术等级加成（`mode`：`global` 全部流派 / `school` 仅战袍主流派），通过 `ModifySpellLevelEvent` 叠加，tooltip 显示「基础等级（+加成）」 |
| `battlemages_garb:school_specialization` | `SchoolSpecializationAffix` | 学派专精：主流派法术强度提升、其余流派降低（`bonus_values` / `penalty_values`） |

---

## 2. 添加一个「固定属性」战袍词条（纯数据驱动，推荐）

例如新增「召唤强化」（`irons_spellbooks:summon_damage`）：

**第 1 步**：在 `data/battlemages_garb/affixes/` 下新建 `summon_damage.json`：

```json
{
  "type": "battlemages_garb:garb_attribute",
  "definition": {
    "affix_type": "stat",
    "exclusive_set": [],
    "weights": { "quality": 0.1, "weight": 20 }
  },
  "attribute": "irons_spellbooks:summon_damage",
  "operation": "add_multiplied_base",
  "values": {
    "apotheosis:common":   { "min": 0.05, "max": 0.08 },
    "apotheosis:uncommon": { "min": 0.07, "max": 0.12 },
    "apotheosis:rare":     { "min": 0.10, "max": 0.16 },
    "apotheosis:epic":     { "min": 0.14, "max": 0.20 },
    "apotheosis:mythic":   { "min": 0.18, "max": 0.25 }
  }
}
```

字段说明：
- `type`：词条类型 id。
- `definition.weights`：词条权重（`weight` = 出现权重，`quality` = 幸运加成系数）。权重越高，重铸时越容易 roll 出。
- `attribute`：目标属性（必须已注册，如 Iron's 的 `irons_spellbooks:xxx` 或原版的 `minecraft:xxx`）。
- `operation`：`add_value` / `add_multiplied_base` / `add_multiplied_total`。
- `values`：每个稀有度的加成范围（`StepFunction`，`min`~`max` 按词条等级插值）。

**第 2 步**：在中英文语言文件 `assets/battlemages_garb/lang/en_us.json` / `zh_cn.json` 中添加：

```json
"affix.battlemages_garb:summon_damage": "Summoning",
"affix.battlemages_garb:summon_damage.suffix": "of Summoning",
"affix.battlemages_garb:summon_damage.desc": "%s %s"
```

> ⚠️ lang key 的 `<词条id>` 前面是**冒号**（`affix.battlemages_garb:summon_damage`），因为 `Affix#id()` 返回 `ResourceLocation`（含冒号）。不要用点。

**第 3 步（可选但推荐）**：在 `BattlemagesGarbSelfTest` 中加一行 `check("affix <id> is loaded", () -> isBound("<id>"))` 验证。

**完成。无需改任何 Java 代码。**

---

## 3. 添加一个「流派动态」词条

复用 `school_attribute` 类型，JSON 示例（`school_power.json`）：

```json
{
  "type": "battlemages_garb:school_attribute",
  "definition": {
    "affix_type": "stat",
    "exclusive_set": [],
    "weights": { "quality": 0.1, "weight": 30 }
  },
  "target": "power",
  "operation": "add_multiplied_base",
  "min_school_power": 0.0,
  "values": {
    "apotheosis:common":   { "min": 0.03, "max": 0.06 },
    "apotheosis:rare":     { "min": 0.07, "max": 0.12 },
    "apotheosis:mythic":   { "min": 0.12, "max": 0.18 }
  }
}
```

字段说明：
- `target`：`power`（流派法术强度）或 `resist`（流派抗性）。
- `min_school_power`：**概率浮动机制**。装备所有流派法术强度加成之和低于该阈值时，该词条不会出现。加成越高 → 可 roll 的专属词条越多。
  - ⚠️ 该字段在 JSON 里写数字（如 `0.10`），Java 端必须是 `double`（不要改成 `float`，避免 `0.10F` 的精度偏差）。
- 属性本身（如 `irons_spellbooks:fire_spell_power`）是**运行时动态识别**的：从装备自身属性中找出命中标签 `battlemages_garb:school_power_attributes` 的流派属性。

---

## 4. 添加一个「全新行为类型」（Java）

只有当现有两个类型无法表达所需行为时才需要新增类型。步骤如下：

1. 创建 `XxxAffix extends Affix`，实现 `canApplyTo` / `addModifiers` / `getDescription` / `getAugmentingText` / `getCodec`。
2. 提供静态 `CODEC`（用 `RecordCodecBuilder` + `affixDef()`）。
3. 在 `BattlemageAffixes` 中注册类型 id 并 `AffixRegistry.INSTANCE.registerCodec(...)`。
4. 写 JSON 实例 + lang key。
5. 在 `BattlemagesGarbSelfTest` 加验证。

---

## 5. 防错硬性约束（必须遵守）

以下约束是之前实际踩过的坑，任何新词条类型都不得违反：

### 5.1 属性事件内禁止递归读取（StackOverflowError 根源）

`ItemStack#getAttributeModifiers()` 会触发 `StackAttributeModifiersEvent`。在**该事件的监听器**（即 `Affix#addModifiers`）内再次调用它，会无限递归直至栈溢出。

| 位置 | 允许的属性读取方式 | 禁止 |
|---|---|---|
| `addModifiers`（事件监听器） | `SpellSchoolHelper.getSchoolAttribute(event.getModifiers())` 等**基于 `event.getModifiers()` 的重载** | `stack.getAttributeModifiers()`、`stack.getItem().getDefaultAttributeModifiers()` 的组合触发的读取 |
| `canApplyTo` / `getDescription` / `getAugmentingText` | `SpellSchoolHelper.getModifiersSafe(stack)` | `stack.getAttributeModifiers()` |

`SpellSchoolHelper` 中的「基于 `List<StackAttributeModifiers.Entry>`」的重载是唯一允许在事件内使用的方式。

### 5.2 数值字段用 double

所有数值字段（`min_school_power`、加成值、`StepFunction` 阈值）使用 `double`。`float` 转 `double` 会产生精度偏差（如 `0.10F` → `0.10000000149...`），导致比较判断错误。

### 5.3 本地化 key 用冒号

`affix.battlemages_garb:<词条id>`（冒号分隔），因为 `"affix." + this.id()` 拼接的是 `ResourceLocation.toString()`。

### 5.4 codec 注册时机

词条类型 codec 必须在**首次 datapack 重载之前**注册，即 `@Mod` 构造函数中，统一经 `BattlemageAffixes.registerCodecs()`。

### 5.5 识别战袍用物品标签 + 兜底

`isSpellGarb` = 物品命中 `battlemages_garb:spell_garb` 标签 **或** 含流派属性（兜底）。新词条的 `canApplyTo` 应调用 `SpellSchoolHelper.isSpellGarb(stack)` 判断，而非自己实现。

---

## 6. 扩展流派（其他模组）

流派集合由**属性标签** `battlemages_garb:school_power_attributes` 定义（默认收录 Iron's Spells 的 9 个 `*_spell_power` 属性）。其他模组只需用 datapack 向该标签追加自己的流派属性：

```json
// 任意 datapack 中：data/<yourmod>/tags/attribute/battlemages_garb_school_power_attributes.json
{
  "replace": false,
  "values": [ "yourmod:your_school_spell_power" ]
}
```

即自动被本模组的流派动态词条识别，无需改本模组代码。

---

## 7. 自检 / 验证

```bash
./gradlew runServer -PbattleSelfTest
```

自检覆盖：词条加载、战袍识别、流派加成求和、所有词条 `canApplyTo`、概率浮动（pyromancer +0.10 → 2 个专属词条 / wizard 0 → 0 个）、以及「应用词条后计算属性不栈溢出」的回归测试。全部通过退出码 0。
