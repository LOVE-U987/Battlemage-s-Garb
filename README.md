# Battlemage's Garb (法术战袍)

为 [神化 (Apotheosis)](https://modrinth.com/mod/apotheosis) 模组添加一个新的装备类型：**法术战袍**。
法术战袍对应 Iron's Spells 'n Spellbooks 中专门为法师设计的盔甲，其神化词条为对应装备主加成流派的额外加成，
以弥补神化装备加成中缺少法师可用加成词条的问题。

Adds a new equipment type — the **Spell Garb** — to [Apotheosis](https://modrinth.com/mod/apotheosis).
It targets Iron's Spells 'n Spellbooks mage armor, and its affixes grant school-specific bonuses for spellcasters.

## 功能 / Features

- **新装备类型「法术战袍」**（`battlemages_garb:spell_garb`）：匹配提供流派法术强度加成的盔甲。
- **「学派精通」词条**（`battlemages_garb:school_power`）：根据战袍自身的主加成流派，动态给予对应流派法术强度加成。
- **可扩展流派绑定**：流派集合由属性标签 `battlemages_garb:school_power_attributes` 定义（默认收录 Iron's Spells 的 9 个 `*_spell_power` 属性）。其他模组只需用 datapack 向该标签追加自己的流派属性即可兼容，无需修改代码。
- **规范化的词条注册 API**：词条类型与实例分离，新增词条多数只需写一个 JSON。详见 [docs/AFFIX_API.md](docs/AFFIX_API.md)。

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

## 自动测试 / Self-Test

`runServer` 配置了自检开关，启动完整服务器、运行断言并自动退出：

```bash
./gradlew runServer -PbattleSelfTest
```

自检覆盖：词条加载、Iron's 战袍识别、流派加成求和、所有词条 `canApplyTo`，以及「流派加成越高 → 专属词条越多」的概率浮动机制（pyromancer +0.10 有 2 个专属词条，wizard 0 加成有 0 个）。全部通过时退出码 0。
