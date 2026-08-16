package dev.battlemages_garb;

import java.util.List;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apothic_attributes.modifiers.StackAttributeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 识别「法术战袍」与「流派法术强度属性」的工具类。
 * <p>
 * 「法术战袍」通过物品标签 {@code battlemages_garb:spell_garb} 定义，默认引用 Iron's Spells 的
 * 全部法师盔甲标签；「流派」通过属性标签 {@code battlemages_garb:school_power_attributes} 定义。
 * 其他模组均可用 datapack 向这两个标签追加内容来扩展。
 * <p>
 * 注意：{@link ItemStack#getAttributeModifiers()} 会触发 {@code StackAttributeModifiersEvent}。
 * 因此<b>绝不能在事件监听器内</b>（如 {@code Affix#addModifiers}）调用基于 {@link ItemStack}
 * 的读取方法，否则会无限递归导致 {@link StackOverflowError}。事件处理器内应使用基于
 * {@code List<StackAttributeModifiers.Entry>} 的重载（读取 {@code event.getModifiers()}）。
 */
public final class SpellSchoolHelper {

    /** 流派法术强度属性的统一后缀：{@code <school>_spell_power}。 */
    private static final String SCHOOL_POWER_SUFFIX = "_spell_power";

    /** 「法术战袍」物品标签（引用 Iron's Spells 的全部法师盔甲标签，可扩展）。 */
    public static final TagKey<Item> SPELL_GARB_ITEMS = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(BattlemagesGarb.MOD_ID, "spell_garb"));

    /** 表示「一个法术流派的强度」的属性标签（默认 Iron's Spells 的 9 个 *_spell_power 属性）。 */
    public static final TagKey<Attribute> SCHOOL_POWER_ATTRIBUTES = TagKey.create(
        Registries.ATTRIBUTE,
        ResourceLocation.fromNamespaceAndPath(BattlemagesGarb.MOD_ID, "school_power_attributes"));

    private SpellSchoolHelper() {}

    /** 是否为神化的盔甲类型（头盔/胸甲/护腿/靴子）。 */
    public static boolean isArmorCategory(LootCategory cat) {
        return cat == Apoth.LootCategories.HELMET || cat == Apoth.LootCategories.CHESTPLATE
            || cat == Apoth.LootCategories.LEGGINGS || cat == Apoth.LootCategories.BOOTS;
    }

    /** 是否为「法术战袍」（法师盔甲）。物品标签优先，另以「含流派法术强度属性」兜底。 */
    public static boolean isSpellGarb(ItemStack stack) {
        return stack.is(SPELL_GARB_ITEMS) || getSchoolAttribute(stack) != null;
    }

    // ============================== 基于 ItemStack ==============================
    // 注意：这里绝不调用 stack.getAttributeModifiers()（它会触发 StackAttributeModifiersEvent，
    // 而在事件监听器链中（如 AffixHelper.streamAffixes → canApplyTo / addModifiers）调用会造成无限递归）。
    // 改为手动合并「默认属性 + 附加属性」，得到与事件读取一致但不触发事件的视图。

    /** 手动合并物品的默认属性修饰符与附加修饰符（不触发 StackAttributeModifiersEvent）。 */
    public static ItemAttributeModifiers getModifiersSafe(ItemStack stack) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry e : stack.getItem().getDefaultAttributeModifiers().modifiers()) {
            builder.add(e.attribute(), e.modifier(), e.slot());
        }
        for (ItemAttributeModifiers.Entry e : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            builder.add(e.attribute(), e.modifier(), e.slot());
        }
        return builder.build();
    }

    /** 返回该物品主加成流派的法术强度属性；无则返回 null。 */
    public static Holder<Attribute> getSchoolAttribute(ItemStack stack) {
        ItemAttributeModifiers modifiers = getModifiersSafe(stack);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().is(SCHOOL_POWER_ATTRIBUTES)) {
                return entry.attribute();
            }
        }
        return null;
    }

    /** 返回该物品所有流派法术强度加成之和（属性修饰符数值累加）；无则为 0。 */
    public static double getSchoolPower(ItemStack stack) {
        ItemAttributeModifiers modifiers = getModifiersSafe(stack);
        double total = 0;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().is(SCHOOL_POWER_ATTRIBUTES)) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }

    /** 由该物品主加成流派推导对应的流派抗性属性；无则返回 null。 */
    public static Holder<Attribute> getSchoolResistAttribute(ItemStack stack) {
        return toResist(getSchoolAttribute(stack));
    }

    /** 判断某个流派法术强度属性是否与给定的法术流派 id（如 {@code irons_spellbooks:fire}）同流派。 */
    public static boolean isSameSchool(Holder<Attribute> schoolPowerAttr, ResourceLocation spellSchoolId) {
        if (schoolPowerAttr == null || spellSchoolId == null) {
            return false;
        }
        ResourceLocation attrId = schoolPowerAttr.unwrapKey().map(ResourceKey::location).orElse(null);
        if (attrId == null) {
            return false;
        }
        String path = attrId.getPath();
        if (!path.endsWith(SCHOOL_POWER_SUFFIX)) {
            return false;
        }
        String school = path.substring(0, path.length() - SCHOOL_POWER_SUFFIX.length());
        return attrId.getNamespace().equals(spellSchoolId.getNamespace()) && school.equals(spellSchoolId.getPath());
    }

    // ============================== 基于事件条目（安全用于 StackAttributeModifiersEvent 监听器，如 Affix#addModifiers） ==============================

    /** 返回事件条目中第一个命中学派标签的属性；无则返回 null。 */
    public static Holder<Attribute> getSchoolAttribute(List<StackAttributeModifiers.Entry> entries) {
        for (StackAttributeModifiers.Entry entry : entries) {
            if (entry.attribute().is(SCHOOL_POWER_ATTRIBUTES)) {
                return entry.attribute();
            }
        }
        return null;
    }

    /** 返回事件条目中所有流派法术强度加成之和；无则为 0。 */
    public static double getSchoolPower(List<StackAttributeModifiers.Entry> entries) {
        double total = 0;
        for (StackAttributeModifiers.Entry entry : entries) {
            if (entry.attribute().is(SCHOOL_POWER_ATTRIBUTES)) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }

    /** 由事件条目中的主加成流派推导对应的流派抗性属性；无则返回 null。 */
    public static Holder<Attribute> getSchoolResistAttribute(List<StackAttributeModifiers.Entry> entries) {
        return toResist(getSchoolAttribute(entries));
    }

    // ============================== 内部工具 ==============================

    /** 由流派法术强度属性推导对应的流派抗性属性（{@code <school>_spell_power} → {@code <school>_magic_resist}）。 */
    private static Holder<Attribute> toResist(Holder<Attribute> power) {
        if (power == null) {
            return null;
        }
        ResourceLocation id = power.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        if (!path.endsWith(SCHOOL_POWER_SUFFIX)) {
            return null;
        }
        String school = path.substring(0, path.length() - SCHOOL_POWER_SUFFIX.length());
        ResourceLocation resistId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), school + "_magic_resist");
        return BuiltInRegistries.ATTRIBUTE.getHolder(resistId).orElse(null);
    }
}
