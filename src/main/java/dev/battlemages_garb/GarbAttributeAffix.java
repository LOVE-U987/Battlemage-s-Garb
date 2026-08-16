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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;

/**
 * 「法术战袍」通用词条类型：给法师盔甲添加固定属性加成（最大法力、回蓝、法术强度、冷却、施法速度等）。
 * <p>
 * 与内置 {@code apotheosis:attribute} 的唯一区别是：额外要求物品为「法术战袍」，
 * 从而与通用护甲词条共存（战袍保留在盔甲类型中）。
 * <p>
 * 一个<b>类型</b>对应多个<b>词条实例</b>（数据包 JSON）。JSON 结构：
 * <pre>{@code
 * {
 *   "type": "battlemages_garb:garb_attribute",
 *   "definition": { "affix_type": "stat", "exclusive_set": [], "weights": {...} },
 *   "attribute": "irons_spellbooks:max_mana",
 *   "operation": "add_value",
 *   "values": { "apotheosis:common": {"min":..,"max":..}, ... }
 * }
 * }</pre>
 * <p>
 * <b>防错约束</b>：{@code canApplyTo} 内判断是否为法术战袍时用
 * {@link SpellSchoolHelper#getModifiersSafe(ItemStack)}（不触发事件），禁止直接调用
 * {@code stack.getAttributeModifiers()}。
 */
public class GarbAttributeAffix extends Affix {

    public static final Codec<GarbAttributeAffix> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            affixDef(),
            BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(a -> a.attribute),
            PlaceboCodecs.enumCodec(Operation.class).fieldOf("operation").forGetter(a -> a.operation),
            LootRarity.mapCodec(StepFunction.CODEC).fieldOf("values").forGetter(a -> a.values))
        .apply(inst, GarbAttributeAffix::new));

    protected final Holder<Attribute> attribute;
    protected final Operation operation;
    protected final Map<LootRarity, StepFunction> values;

    public GarbAttributeAffix(AffixDefinition definition, Holder<Attribute> attribute, Operation operation, Map<LootRarity, StepFunction> values) {
        super(definition);
        this.attribute = attribute;
        this.operation = operation;
        this.values = values;
    }

    @Override
    public boolean canApplyTo(ItemStack stack, LootCategory cat, LootRarity rarity) {
        if (cat.isNone() || !SpellSchoolHelper.isArmorCategory(cat)) {
            return false;
        }
        return SpellSchoolHelper.isSpellGarb(stack) && this.values.containsKey(rarity);
    }

    @Override
    public void addModifiers(AffixInstance inst, StackAttributeModifiersEvent event) {
        LootCategory cat = inst.category();
        if (cat.isNone()) {
            return;
        }
        StepFunction stepFunction = this.values.get(inst.getRarity());
        if (stepFunction == null) {
            return;
        }
        event.addModifier(this.attribute, new AttributeModifier(inst.makeUniqueId(), stepFunction.get(inst.level()), this.operation), cat.getSlots());
    }

    @Override
    public MutableComponent getDescription(AffixInstance inst, AttributeTooltipContext ctx) {
        StepFunction stepFunction = this.values.get(inst.getRarity());
        if (stepFunction == null) {
            return Component.empty();
        }
        double value = stepFunction.get(inst.level());
        MutableComponent valueComponent = this.attribute.value().toValueComponent(this.operation, value, ctx.flag());
        return Component.translatable(
            "affix." + this.id() + ".desc",
            valueComponent,
            Component.translatable(this.attribute.value().getDescriptionId()));
    }

    @Override
    public Component getAugmentingText(AffixInstance inst, AttributeTooltipContext ctx) {
        StepFunction stepFunction = this.values.get(inst.getRarity());
        if (stepFunction == null) {
            return Component.empty();
        }
        Attribute attr = this.attribute.value();
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

    @Override
    public Codec<? extends Affix> getCodec() {
        return CODEC;
    }
}
