package dev.battlemages_garb;

import dev.shadowsoffire.apotheosis.affix.AffixRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * 词条注册中心：本模组所有自定义词条类型（codec）的<b>唯一注册入口</b>。
 * <p>
 * 添加新词条时遵循「类型 / 实例」分离：
 * <ul>
 *   <li><b>词条类型</b>（codec）：一种行为模式，对应一个 {@code Affix} 子类。已有类型见本类常量，通常无需新增。</li>
 *   <li><b>词条实例</b>（数据驱动 JSON）：放在 {@code data/battlemages_garb/affixes/<name>.json}，一条 JSON = 一个词条。</li>
 * </ul>
 * 绝大多数新词条只需<b>写一个 JSON</b> 即可（复用已有类型），无需改 Java 代码。
 *
 * <h2>硬性约束（避免崩溃/错误）</h2>
 * <ol>
 *   <li>词条类型 codec 必须经本类注册，且必须在首次 datapack 重载（词条解析）之前完成（即 {@code @Mod} 构造函数中）。</li>
 *   <li>在 {@code StackAttributeModifiersEvent} 监听器（{@code Affix#addModifiers}）内<b>禁止</b>调用
 *       {@code ItemStack#getAttributeModifiers()}，它会在事件处理中再次触发同一事件，造成无限递归（StackOverflowError）。
 *       应改用 {@link SpellSchoolHelper} 基于 {@code event.getModifiers()} 的重载。</li>
 *   <li>在 {@code canApplyTo} / 词条描述内读取物品属性时，<b>必须</b>使用 {@link SpellSchoolHelper#getModifiersSafe(ItemStack)}
 *       （不触发事件），而不是 {@code ItemStack#getAttributeModifiers()}。</li>
 *   <li>所有数值字段使用 {@code double}（勿用 {@code float}，避免 {@code 0.10F} 转 double 的精度偏差）。</li>
 *   <li>词条本地化 key 格式为 {@code affix.battlemages_garb:<词条id>}（冒号，因 {@code Affix#id()} 返回 ResourceLocation）。</li>
 * </ol>
 */
public final class BattlemageAffixes {

    // ============ 词条类型 id（对应数据包 JSON 中的 "type" 字段）============

    /** 流派动态词条：根据战袍主加成流派，动态给对应流派属性加成。 */
    public static final ResourceLocation SCHOOL_ATTRIBUTE = loc("school_attribute");

    /** 战袍通用词条：给法师盔甲添加固定属性加成（限定法术战袍）。 */
    public static final ResourceLocation GARB_ATTRIBUTE = loc("garb_attribute");

    /** 法术等级词条：提升施法者的法术等级（全局或仅主流派）。 */
    public static final ResourceLocation SPELL_LEVEL = loc("spell_level");

    /** 学派专精词条：主流派法术强度提升、其余流派降低。 */
    public static final ResourceLocation SCHOOL_SPECIALIZATION = loc("school_specialization");

    private BattlemageAffixes() {}

    /**
     * 注册所有词条类型 codec 到 {@link AffixRegistry}，并注册需要全局事件的词条类型。
     * <p>
     * 必须由 {@code @Mod} 构造函数调用（在首次 datapack 重载之前）。新增词条类型时在此追加一行注册；
     * 若词条类型需要监听 NeoForge 事件（如 {@link SpellLevelAffix} 监听 {@code ModifySpellLevelEvent}），
     * 同时调用其 {@code register()}。
     */
    public static void registerCodecs() {
        AffixRegistry.INSTANCE.registerCodec(SCHOOL_ATTRIBUTE, SchoolAttributeAffix.CODEC);
        AffixRegistry.INSTANCE.registerCodec(GARB_ATTRIBUTE, GarbAttributeAffix.CODEC);
        AffixRegistry.INSTANCE.registerCodec(SPELL_LEVEL, SpellLevelAffix.CODEC);
        AffixRegistry.INSTANCE.registerCodec(SCHOOL_SPECIALIZATION, SchoolSpecializationAffix.CODEC);
        SpellLevelAffix.register(); // 监听 ModifySpellLevelEvent，使法术等级词条生效
        BattlemagesGarb.LOGGER.info("Battlemage's Garb affix codecs registered: {}, {}, {}, {}",
            SCHOOL_ATTRIBUTE, GARB_ATTRIBUTE, SPELL_LEVEL, SCHOOL_SPECIALIZATION);
    }

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(BattlemagesGarb.MOD_ID, path);
    }
}
