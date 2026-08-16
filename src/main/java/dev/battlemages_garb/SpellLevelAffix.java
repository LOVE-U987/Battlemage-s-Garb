package dev.battlemages_garb;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixDefinition;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.placebo.codec.PlaceboCodecs;
import dev.shadowsoffire.placebo.util.StepFunction;
import io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;

/**
 * 「法术等级」词条类型：提升施法者施放法术的等级。
 * <p>
 * Iron's Spells 本身没有「全局法术等级」属性，因此本类型通过监听其 API 事件
 * {@link ModifySpellLevelEvent}（在 {@link AbstractSpell#getLevelFor(int, LivingEntity)}
 * 中触发，服务端与客户端都会触发，同时影响法术 tooltip、法力消耗与实际效果）实现。
 * <p>
 * 一个<b>类型</b>对应多个<b>词条实例</b>（数据包 JSON）。JSON 结构：
 * <pre>{@code
 * {
 *   "type": "battlemages_garb:spell_level",
 *   "definition": { "affix_type": "stat", "exclusive_set": [], "weights": {...} },
 *   "mode": "global" | "school",   // global = 所有流派；school = 仅战袍主流派
 *   "values": { "apotheosis:common": {"min":1,"max":1}, ... }
 * }
 * }</pre>
 * <p>
 * <b>防错约束</b>：
 * <ol>
 *   <li>事件监听器内读取物品词条用 {@link AffixHelper#streamAffixes(ItemStack)}（读数据组件，
 *       不触发 {@code StackAttributeModifiersEvent}）；读取流派属性用
 *       {@link SpellSchoolHelper#getModifiersSafe(ItemStack)}，禁止调用
 *       {@code stack.getAttributeModifiers()}。</li>
 *   <li>等级取整用 {@code Math.round}。</li>
 *   <li>数值字段用 {@code double}（勿用 {@code float}）。</li>
 * </ol>
 * <p>
 * <b>等级加成语义</b>：本词条通过 {@code event.addLevels(bonus)} 把加成叠加到
 * {@code ModifySpellLevelEvent} 的 {@code totalLevel} 上，<b>不</b>clamp 到 {@code maxLevel}。
 * Iron's 的 {@code getLevelFor} 本身不限制 totalLevel（其 affinity 加成同样不 clamp），
 * 法术等级 tooltip 会以「基础等级（+加成）」形式展示（配合本模组的 {@code TooltipsUtilsMixin}）。
 * 若 clamp 到 maxLevel，加成会被完全吞掉（显示为单一等级，即「合并计算」）。
 */
public class SpellLevelAffix extends Affix {

    public enum Mode {
        /** 所有流派法术的等级提升。 */
        GLOBAL,
        /** 仅战袍主加成流派的法术等级提升。 */
        SCHOOL
    }

    public static final Codec<SpellLevelAffix> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            affixDef(),
            PlaceboCodecs.enumCodec(Mode.class).fieldOf("mode").forGetter(a -> a.mode),
            LootRarity.mapCodec(StepFunction.CODEC).fieldOf("values").forGetter(a -> a.values))
        .apply(inst, SpellLevelAffix::new));

    protected final Mode mode;
    protected final Map<LootRarity, StepFunction> values;

    public SpellLevelAffix(AffixDefinition definition, Mode mode, Map<LootRarity, StepFunction> values) {
        super(definition);
        this.mode = mode;
        this.values = values;
    }

    /** 注册 {@link ModifySpellLevelEvent} 监听。必须在 {@code @Mod} 构造函数中调用一次。 */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(SpellLevelAffix::onModifySpellLevel);
    }

    /**
     * 事件处理：遍历施法者盔甲槽上的本类型词条实例，累加法术等级加成。
     * 任何施法者（含 Iron's 的敌方法师）穿上带词条的战袍都会生效。
     */
    private static void onModifySpellLevel(ModifySpellLevelEvent event) {
        LivingEntity caster = event.getEntity();
        if (caster == null) {
            return;
        }
        int bonus = computeBonus(caster, event.getSpell());
        if (bonus > 0) {
            // 不 clamp 到 maxLevel：Iron's 的 getLevelFor 本身不限制 totalLevel（affinity 加成也不 clamp）。
            // clamp 会让 tooltip 显示 diff=0，把加成完全吞掉（「合并计算」）。改为直接叠加加成。
            event.addLevels(bonus);
        }
    }

    /**
     * 计算施法者盔甲槽上本类型词条对指定法术提供的等级加成之和。
     * <p>
     * 独立成方法便于自检与后续扩展。
     */
    public static int computeBonus(LivingEntity caster, AbstractSpell spell) {
        int bonus = 0;
        for (ItemStack stack : caster.getArmorSlots()) {
            for (AffixInstance inst : AffixHelper.streamAffixes(stack).toList()) {
                if (inst.getAffix() instanceof SpellLevelAffix affix && affix.matchesSchool(stack, spell)) {
                    StepFunction step = affix.values.get(inst.getRarity());
                    if (step != null) {
                        bonus += (int) Math.round(step.get(inst.level()));
                    }
                }
            }
        }
        return bonus;
    }

    /** 判断该词条是否作用于当前法术：GLOBAL 恒真；SCHOOL 需与战袍主加成流派匹配。 */
    private boolean matchesSchool(ItemStack stack, AbstractSpell spell) {
        if (this.mode == Mode.GLOBAL) {
            return true;
        }
        SchoolType schoolType = spell.getSchoolType();
        if (schoolType == null) {
            return false;
        }
        Holder<Attribute> schoolAttr = SpellSchoolHelper.getSchoolAttribute(stack);
        return SpellSchoolHelper.isSameSchool(schoolAttr, schoolType.getId());
    }

    @Override
    public boolean canApplyTo(ItemStack stack, LootCategory cat, LootRarity rarity) {
        if (cat.isNone() || !SpellSchoolHelper.isArmorCategory(cat)) {
            return false;
        }
        if (!SpellSchoolHelper.isSpellGarb(stack)) {
            return false;
        }
        if (this.mode == Mode.SCHOOL && SpellSchoolHelper.getSchoolAttribute(stack) == null) {
            return false; // 无主加成流派的战袍无法 roll 出「流派法术等级」词条
        }
        return this.values.containsKey(rarity);
    }

    @Override
    public MutableComponent getDescription(AffixInstance inst, AttributeTooltipContext ctx) {
        StepFunction step = this.values.get(inst.getRarity());
        if (step == null) {
            return Component.empty();
        }
        int level = (int) Math.round(step.get(inst.level()));
        return Component.translatable("affix." + this.id() + ".desc", level);
    }

    @Override
    public Component getAugmentingText(AffixInstance inst, AttributeTooltipContext ctx) {
        StepFunction step = this.values.get(inst.getRarity());
        if (step == null) {
            return Component.empty();
        }
        int level = (int) Math.round(step.get(inst.level()));
        MutableComponent comp = Component.translatable("affix." + this.id() + ".desc", level);
        if (step.get(0) != step.get(1)) {
            MutableComponent minComp = Component.translatable("affix." + this.id() + ".desc", (int) Math.round(step.get(0)));
            MutableComponent maxComp = Component.translatable("affix." + this.id() + ".desc", (int) Math.round(step.get(1)));
            comp.append(Affix.valueBounds(minComp, maxComp));
        }
        return comp;
    }

    @Override
    public Codec<? extends Affix> getCodec() {
        return CODEC;
    }
}
