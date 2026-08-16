package dev.battlemages_garb;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixDefinition;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apothic_attributes.modifiers.StackAttributeModifiers;
import dev.shadowsoffire.apothic_attributes.modifiers.StackAttributeModifiersEvent;
import dev.shadowsoffire.placebo.codec.PlaceboCodecs;
import dev.shadowsoffire.placebo.util.StepFunction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;

/**
 * 「学派专精」词条类型：战袍主加成流派的法术强度提升，其余流派（战袍上出现的）法术强度降低。
 * <p>
 * 收益与代价并存的「偏科」设计：专精让主流派更强，但会削弱其它流派的输出。
 * <p>
 * 一个<b>类型</b>对应多个<b>词条实例</b>（数据包 JSON）。JSON 结构：
 * <pre>{@code
 * {
 *   "type": "battlemages_garb:school_specialization",
 *   "definition": { "affix_type": "stat", "exclusive_set": [], "weights": {...} },
 *   "operation": "add_multiplied_base",
 *   "bonus_values":   { "apotheosis:common": {"min":..,"max":..}, ... },   // 主流派加成
 *   "penalty_values": { "apotheosis:common": {"min":..,"max":..}, ... }    // 其它流派削减
 * }
 * }</pre>
 * <p>
 * <b>防错约束</b>：{@code addModifiers}（事件监听器）内<b>必须</b>读 {@code event.getModifiers()}，
 * 禁止调用 {@code stack.getAttributeModifiers()}（会再次触发本事件 → StackOverflowError）；
 * 同一词条对多个属性添加修饰符时用 {@link AffixInstance#makeUniqueId(String)} 保证 id 唯一。
 */
public class SchoolSpecializationAffix extends Affix {

    public static final Codec<SchoolSpecializationAffix> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            affixDef(),
            PlaceboCodecs.enumCodec(Operation.class).fieldOf("operation").forGetter(a -> a.operation),
            LootRarity.mapCodec(StepFunction.CODEC).fieldOf("bonus_values").forGetter(a -> a.bonusValues),
            LootRarity.mapCodec(StepFunction.CODEC).fieldOf("penalty_values").forGetter(a -> a.penaltyValues))
        .apply(inst, SchoolSpecializationAffix::new));

    protected final Operation operation;
    protected final Map<LootRarity, StepFunction> bonusValues;
    protected final Map<LootRarity, StepFunction> penaltyValues;

    public SchoolSpecializationAffix(AffixDefinition definition, Operation operation,
                                     Map<LootRarity, StepFunction> bonusValues, Map<LootRarity, StepFunction> penaltyValues) {
        super(definition);
        this.operation = operation;
        this.bonusValues = bonusValues;
        this.penaltyValues = penaltyValues;
    }

    @Override
    public boolean canApplyTo(ItemStack stack, LootCategory cat, LootRarity rarity) {
        if (cat.isNone() || !SpellSchoolHelper.isArmorCategory(cat)) {
            return false;
        }
        if (!SpellSchoolHelper.isSpellGarb(stack)) {
            return false;
        }
        if (SpellSchoolHelper.getSchoolAttribute(stack) == null) {
            return false; // 需有主加成流派才能「专精」
        }
        return this.bonusValues.containsKey(rarity) && this.penaltyValues.containsKey(rarity);
    }

    @Override
    public void addModifiers(AffixInstance inst, StackAttributeModifiersEvent event) {
        LootCategory cat = inst.category();
        if (cat.isNone()) {
            return;
        }
        // 注意：必须在事件处理器内读取 event.getModifiers()，不能调用 inst.stack().getAttributeModifiers()。
        Holder<Attribute> primary = SpellSchoolHelper.getSchoolAttribute(event.getModifiers());
        if (primary == null) {
            return;
        }
        StepFunction bonusFn = this.bonusValues.get(inst.getRarity());
        StepFunction penaltyFn = this.penaltyValues.get(inst.getRarity());
        if (bonusFn == null || penaltyFn == null) {
            return;
        }
        double bonus = bonusFn.get(inst.level());
        double penalty = penaltyFn.get(inst.level());

        // 主流派法术强度 +bonus
        event.addModifier(primary, new AttributeModifier(inst.makeUniqueId(), bonus, this.operation), cat.getSlots());

        // 其余流派（仅事件中出现过的流派属性）-penalty；同一词条对多属性用带 salt 的 id 区分
        for (StackAttributeModifiers.Entry entry : event.getModifiers()) {
            Holder<Attribute> attr = entry.attribute();
            if (attr.is(SpellSchoolHelper.SCHOOL_POWER_ATTRIBUTES) && !attr.equals(primary)) {
                event.addModifier(attr, new AttributeModifier(
                    inst.makeUniqueId(entry.attribute().getRegisteredName()), -penalty, this.operation), cat.getSlots());
            }
        }
    }

    @Override
    public MutableComponent getDescription(AffixInstance inst, AttributeTooltipContext ctx) {
        Holder<Attribute> attribute = SpellSchoolHelper.getSchoolAttribute(inst.stack());
        if (attribute == null) {
            return Component.empty();
        }
        StepFunction bonusFn = this.bonusValues.get(inst.getRarity());
        StepFunction penaltyFn = this.penaltyValues.get(inst.getRarity());
        if (bonusFn == null || penaltyFn == null) {
            return Component.empty();
        }
        Attribute attr = attribute.value();
        MutableComponent bonusComp = attr.toValueComponent(this.operation, bonusFn.get(inst.level()), ctx.flag());
        MutableComponent penaltyComp = attr.toValueComponent(this.operation, -penaltyFn.get(inst.level()), ctx.flag());
        return Component.translatable("affix." + this.id() + ".desc", bonusComp, penaltyComp);
    }

    @Override
    public Component getAugmentingText(AffixInstance inst, AttributeTooltipContext ctx) {
        Holder<Attribute> attribute = SpellSchoolHelper.getSchoolAttribute(inst.stack());
        if (attribute == null) {
            return Component.empty();
        }
        StepFunction bonusFn = this.bonusValues.get(inst.getRarity());
        StepFunction penaltyFn = this.penaltyValues.get(inst.getRarity());
        if (bonusFn == null || penaltyFn == null) {
            return Component.empty();
        }
        Attribute attr = attribute.value();
        MutableComponent bonusComp = attr.toValueComponent(this.operation, bonusFn.get(inst.level()), ctx.flag());
        MutableComponent penaltyComp = attr.toValueComponent(this.operation, -penaltyFn.get(inst.level()), ctx.flag());
        MutableComponent comp = Component.translatable("affix." + this.id() + ".desc", bonusComp, penaltyComp);
        if (bonusFn.get(0) != bonusFn.get(1) || penaltyFn.get(0) != penaltyFn.get(1)) {
            MutableComponent minComp = Component.translatable("affix." + this.id() + ".desc",
                attr.toValueComponent(this.operation, bonusFn.get(0), ctx.flag()),
                attr.toValueComponent(this.operation, -penaltyFn.get(0), ctx.flag()));
            MutableComponent maxComp = Component.translatable("affix." + this.id() + ".desc",
                attr.toValueComponent(this.operation, bonusFn.get(1), ctx.flag()),
                attr.toValueComponent(this.operation, -penaltyFn.get(1), ctx.flag()));
            comp.append(Affix.valueBounds(minComp, maxComp));
        }
        return comp;
    }

    @Override
    public Codec<? extends Affix> getCodec() {
        return CODEC;
    }
}
