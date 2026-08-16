package dev.battlemages_garb;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixDefinition;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
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
 * 「学派」专属词条类型：根据法术战袍的主加成流派，动态给予对应流派属性加成。
 * <p>
 * 一个<b>类型</b>对应多个<b>词条实例</b>（数据包 JSON）。JSON 结构：
 * <pre>{@code
 * {
 *   "type": "battlemages_garb:school_attribute",
 *   "definition": { "affix_type": "stat", "exclusive_set": [], "weights": {...} },
 *   "target": "power" | "resist",
 *   "operation": "add_multiplied_base",
 *   "min_school_power": 0.0,          // 可选；流派加成低于此阈值的战袍无法 roll 出该词条
 *   "values": { "apotheosis:common": {"min":..,"max":..}, ... }
 * }
 * }</pre>
 * <p>
 * {@code min_school_power} 用于实现「流派加成越高 → 可 roll 的专属词条越多」的概率浮动机制，
 * 读自装备<b>所有</b>流派法术强度属性之和。该字段必须用 {@code double}（避免 float 精度偏差）。
 * <p>
 * <b>防错约束</b>：
 * <ol>
 *   <li>{@code addModifiers}（事件监听器）内<b>必须</b>读 {@code event.getModifiers()}，
 *       禁止调用 {@code stack.getAttributeModifiers()}（会再次触发本事件 → StackOverflowError）。</li>
 *   <li>{@code canApplyTo} / {@code getDescription} / {@code getAugmentingText} 内读属性<b>必须</b>用
 *       {@link SpellSchoolHelper#getModifiersSafe(ItemStack)}（不触发事件）。</li>
 * </ol>
 */
public class SchoolAttributeAffix extends Affix {

    public enum Target {
        POWER, RESIST
    }

    public static final Codec<SchoolAttributeAffix> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            affixDef(),
            PlaceboCodecs.enumCodec(Target.class).fieldOf("target").forGetter(a -> a.target),
            PlaceboCodecs.enumCodec(Operation.class).fieldOf("operation").forGetter(a -> a.operation),
            LootRarity.mapCodec(StepFunction.CODEC).fieldOf("values").forGetter(a -> a.values),
            Codec.DOUBLE.optionalFieldOf("min_school_power", 0D).forGetter(a -> a.minSchoolPower))
        .apply(inst, SchoolAttributeAffix::new));

    protected final Target target;
    protected final Operation operation;
    protected final Map<LootRarity, StepFunction> values;
    protected final double minSchoolPower;

    public SchoolAttributeAffix(AffixDefinition definition, Target target, Operation operation, Map<LootRarity, StepFunction> values, double minSchoolPower) {
        super(definition);
        this.target = target;
        this.operation = operation;
        this.values = values;
        this.minSchoolPower = minSchoolPower;
    }

    @Override
    public boolean canApplyTo(ItemStack stack, LootCategory cat, LootRarity rarity) {
        if (cat.isNone() || !SpellSchoolHelper.isArmorCategory(cat)) {
            return false;
        }
        if (!SpellSchoolHelper.isSpellGarb(stack)) {
            return false;
        }
        if (SpellSchoolHelper.getSchoolPower(stack) < this.minSchoolPower) {
            return false;
        }
        return this.values.containsKey(rarity) && resolveAttribute(stack) != null;
    }

    @Override
    public void addModifiers(AffixInstance inst, StackAttributeModifiersEvent event) {
        LootCategory cat = inst.category();
        if (cat.isNone()) {
            return;
        }
        // 注意：必须在事件处理器内读取 event.getModifiers()，不能调用 inst.stack().getAttributeModifiers()，
        // 否则会再次触发本事件导致无限递归（StackOverflowError）。
        Holder<Attribute> attribute = this.target == Target.POWER
            ? SpellSchoolHelper.getSchoolAttribute(event.getModifiers())
            : SpellSchoolHelper.getSchoolResistAttribute(event.getModifiers());
        if (attribute == null) {
            return;
        }
        StepFunction stepFunction = this.values.get(inst.getRarity());
        if (stepFunction == null) {
            return;
        }
        double value = stepFunction.get(inst.level());
        event.addModifier(attribute, new AttributeModifier(inst.makeUniqueId(), value, this.operation), cat.getSlots());
    }

    @Override
    public MutableComponent getDescription(AffixInstance inst, AttributeTooltipContext ctx) {
        Holder<Attribute> attribute = resolveAttribute(inst.stack());
        if (attribute == null) {
            return Component.empty();
        }
        StepFunction stepFunction = this.values.get(inst.getRarity());
        if (stepFunction == null) {
            return Component.empty();
        }
        double value = stepFunction.get(inst.level());
        MutableComponent valueComponent = attribute.value().toValueComponent(this.operation, value, ctx.flag());
        return Component.translatable(
            "affix." + this.id() + ".desc",
            valueComponent,
            Component.translatable(attribute.value().getDescriptionId()));
    }

    @Override
    public Component getAugmentingText(AffixInstance inst, AttributeTooltipContext ctx) {
        Holder<Attribute> attribute = resolveAttribute(inst.stack());
        if (attribute == null) {
            return Component.empty();
        }
        StepFunction stepFunction = this.values.get(inst.getRarity());
        if (stepFunction == null) {
            return Component.empty();
        }
        Attribute attr = attribute.value();
        double value = stepFunction.get(inst.level());
        MutableComponent valueComponent = attr.toValueComponent(this.operation, value, ctx.flag());
        MutableComponent comp = Component.translatable("affix." + this.id() + ".desc", valueComponent, Component.translatable(attr.getDescriptionId()));
        if (stepFunction.get(0) != stepFunction.get(1)) {
            MutableComponent minComp = attr.toValueComponent(this.operation, stepFunction.get(0), ctx.flag());
            MutableComponent maxComp = attr.toValueComponent(this.operation, stepFunction.get(1), ctx.flag());
            comp.append(Affix.valueBounds(minComp, maxComp));
        }
        return comp;
    }

    private Holder<Attribute> resolveAttribute(ItemStack stack) {
        return this.target == Target.POWER
            ? SpellSchoolHelper.getSchoolAttribute(stack)
            : SpellSchoolHelper.getSchoolResistAttribute(stack);
    }

    @Override
    public Codec<? extends Affix> getCodec() {
        return CODEC;
    }
}
