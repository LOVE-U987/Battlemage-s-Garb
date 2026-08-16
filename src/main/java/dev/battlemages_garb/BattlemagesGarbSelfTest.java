package dev.battlemages_garb;

import java.util.function.BooleanSupplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.affix.AffixType;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 服务器启动自检：验证词条是否被加载、Iron's 法师盔甲识别、canApplyTo 是否通过。
 * 通过 {@code ./gradlew runServer -PbattleSelfTest} 触发，运行完自动关闭服务器。
 */
public final class BattlemagesGarbSelfTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int passed = 0;
    private static int failed = 0;

    private BattlemagesGarbSelfTest() {}

    public static void run(MinecraftServer server) {
        LOGGER.info("========== Battlemage's Garb SelfTest begin ==========");

        check("affix school_power is loaded", () -> isBound("school_power"));
        check("affix school_power_greater is loaded", () -> isBound("school_power_greater"));
        check("affix school_resist is loaded", () -> isBound("school_resist"));
        check("affix max_mana is loaded", () -> isBound("max_mana"));
        check("affix mana_regen is loaded", () -> isBound("mana_regen"));
        check("affix spell_power is loaded", () -> isBound("spell_power"));
        check("affix cooldown_reduction is loaded", () -> isBound("cooldown_reduction"));
        check("affix cast_time_reduction is loaded", () -> isBound("cast_time_reduction"));
        check("affix summon_damage is loaded", () -> isBound("summon_damage"));
        check("affix casting_movespeed is loaded", () -> isBound("casting_movespeed"));
        check("affix spell_resist is loaded", () -> isBound("spell_resist"));
        check("affix spell_level is loaded", () -> isBound("spell_level"));
        check("affix school_spell_level is loaded", () -> isBound("school_spell_level"));
        check("affix school_specialization is loaded", () -> isBound("school_specialization"));
        check("affix school_power_exalted is loaded", () -> isBound("school_power_exalted"));
        check("affix school_resist_greater is loaded", () -> isBound("school_resist_greater"));

        check("Apotheosis built-in STAT affixes exist", () -> AffixRegistry.INSTANCE.getTypeMap().get(AffixType.STAT).size() > 0);

        Registry<Item> items = server.registryAccess().registryOrThrow(Registries.ITEM);
        ResourceKey<Item> pyromancerKey = ResourceKey.create(Registries.ITEM, rl("irons_spellbooks", "pyromancer_chestplate"));
        check("pyromancer_chestplate is registered (Iron's loaded?)", () -> items.containsKey(pyromancerKey));

        if (items.containsKey(pyromancerKey)) {
            ItemStack garb = new ItemStack(items.get(pyromancerKey));

            check("isSpellGarb(pyromancer_chestplate)", () -> SpellSchoolHelper.isSpellGarb(garb));
            check("getSchoolPower == 0.10 (sum of school attributes)", () -> Math.abs(SpellSchoolHelper.getSchoolPower(garb) - 0.10D) < 0.001D);
            check("getModifiersSafe (non-event) finds school attribute", () -> SpellSchoolHelper.getModifiersSafe(garb).modifiers().stream()
                .anyMatch(e -> e.attribute().is(SpellSchoolHelper.SCHOOL_POWER_ATTRIBUTES)));

            LootCategory cat = LootCategory.forItem(garb);
            check("category is armor (got: " + cat + ")", () -> SpellSchoolHelper.isArmorCategory(cat));

            DynamicHolder<LootRarity> common = RarityRegistry.INSTANCE.holder(rl("apotheosis", "common"));
            check("apotheosis:common rarity is loaded", common::isBound);
            if (common.isBound()) {
                LootRarity rarity = common.get();
                check("canApplyTo school_power", () -> affix("school_power").canApplyTo(garb, cat, rarity));
                check("canApplyTo school_power_greater (min 0.10)", () -> affix("school_power_greater").canApplyTo(garb, cat, rarity));
                check("canApplyTo school_resist", () -> affix("school_resist").canApplyTo(garb, cat, rarity));
                check("canApplyTo max_mana", () -> affix("max_mana").canApplyTo(garb, cat, rarity));
                check("canApplyTo spell_power", () -> affix("spell_power").canApplyTo(garb, cat, rarity));
                check("canApplyTo spell_level", () -> affix("spell_level").canApplyTo(garb, cat, rarity));
                check("canApplyTo school_spell_level", () -> affix("school_spell_level").canApplyTo(garb, cat, rarity));
                check("canApplyTo school_specialization", () -> affix("school_specialization").canApplyTo(garb, cat, rarity));
                check("canApplyTo school_power_exalted blocked (<0.15)", () -> !affix("school_power_exalted").canApplyTo(garb, cat, rarity));
                check("canApplyTo school_resist_greater (>=0.10)", () -> affix("school_resist_greater").canApplyTo(garb, cat, rarity));
            }

            check("school resist attribute resolvable", () -> SpellSchoolHelper.getSchoolResistAttribute(garb) != null);
            check("byType(STAT) contains school_power", () -> AffixRegistry.INSTANCE.getTypeMap().get(AffixType.STAT).stream()
                .anyMatch(h -> h.isBound() && h.getId().getPath().equals("school_power")));

            // ===== 概率浮动机制验证：加成越高 → 可 roll 的专属词条越多 =====
            // pyromancer (+0.10 流派加成)：school_power 与 school_power_greater 都可用 → 2 个专属词条
            check("prob: pyromancer school_power applies", () -> affix("school_power").canApplyTo(garb, cat, common.get()));
            check("prob: pyromancer school_power_greater applies (>=0.10)", () -> affix("school_power_greater").canApplyTo(garb, cat, common.get()));

            // ===== 回归验证：应用词条后触发属性事件不得再 StackOverflow =====
            // 旧实现 addModifiers 内部调用 stack.getAttributeModifiers() 会递归触发同一事件。
            try {
                ItemStack affixed = garb.copy();
                AffixInstance affixInst = new AffixInstance(
                    AffixRegistry.INSTANCE.holder(rl(BattlemagesGarb.MOD_ID, "school_power")), 1.0F, common, affixed);
                AffixHelper.applyAffix(affixed, affixInst);
                ItemAttributeModifiers affixedMods = affixed.getAttributeModifiers();
                boolean hasMod = affixedMods.modifiers().stream()
                    .anyMatch(e -> e.attribute().is(SpellSchoolHelper.SCHOOL_POWER_ATTRIBUTES));
                check("applyAffix + compute attributes: no stack-overflow, school_power modifier present", () -> hasMod);
            }
            catch (Throwable t) {
                check("applyAffix + compute attributes: no stack-overflow", () -> false);
                LOGGER.error("  [EXCEPTION] applyAffix/compute attributes -> {}", t);
            }

            // 模拟客户端重复渲染/属性计算（缓存失效场景）：连续多次应用词条并计算属性、canApplyTo
            try {
                for (int i = 0; i < 5; i++) {
                    ItemStack tmp = garb.copy();
                    AffixInstance tmpInst = new AffixInstance(
                        AffixRegistry.INSTANCE.holder(rl(BattlemagesGarb.MOD_ID, "school_power")), 1.0F, common, tmp);
                    AffixHelper.applyAffix(tmp, tmpInst);
                    tmp.getAttributeModifiers();
                    affix("school_power").canApplyTo(tmp, cat, common.get());
                    affix("school_power_greater").canApplyTo(tmp, cat, common.get());
                }
                check("repeated affix apply + compute + canApplyTo: no stack-overflow", () -> true);
            }
            catch (Throwable t) {
                check("repeated affix apply + compute + canApplyTo: no stack-overflow", () -> false);
                LOGGER.error("  [EXCEPTION] repeated affix apply/compute -> {}", t);
            }

            // ===== 「法术等级」词条事件验证（ModifySpellLevelEvent）=====
            try {
                // Iron's 的 SpellConfigManager 配置仅在 OnDatapackSyncEvent（玩家加入）时构建；
                // 无玩家的自检服务器需手动构建，否则 getSchoolType()/getMaxLevel() 返回静态默认值
                // （SCHOOL=EVOCATION、MAX_LEVEL=1），会导致流派匹配与封顶断言失真。
                try {
                    java.lang.reflect.Method buildMethod = SpellConfigManager.class.getDeclaredMethod(
                        "buildConfigManager", java.util.Map.class, boolean.class);
                    buildMethod.setAccessible(true);
                    buildMethod.invoke(SpellConfigManager.getInstance(), java.util.Collections.emptyMap(), true);
                }
                catch (ReflectiveOperationException e) {
                    LOGGER.warn("  [WARN] Could not pre-build Iron's spell config for selftest: {}", e);
                }

                ServerLevel level = server.overworld();
                Zombie dummy = new Zombie(level);

                // 流派法术等级（school_spell_level，common +1 级）：仅提升主流派（火焰）法术
                ItemStack schoolGarb = garb.copy();
                AffixInstance schoolInst = new AffixInstance(
                    AffixRegistry.INSTANCE.holder(rl(BattlemagesGarb.MOD_ID, "school_spell_level")), 1.0F, common, schoolGarb);
                AffixHelper.applyAffix(schoolGarb, schoolInst);
                AffixHelper.setRarity(schoolGarb, common.get()); // 神化物品需带稀有度，AffixHelper.getAffixes 才会读到
                dummy.setItemSlot(EquipmentSlot.CHEST, schoolGarb);

                // 诊断：词条是否可从物品读取、盔甲槽是否持有战袍
                check("diag: dummy armor slot holds schoolGarb", () -> {
                    for (ItemStack s : dummy.getArmorSlots()) {
                        if (s == schoolGarb) { return true; }
                    }
                    return false;
                });
                check("diag: rarity on schoolGarb bound", () -> AffixHelper.getRarity(schoolGarb).isBound());
                check("diag: streamAffixes(schoolGarb) count == 1", () -> AffixHelper.streamAffixes(schoolGarb).count() == 1);

                AbstractSpell fireball = SpellRegistry.FIREBALL_SPELL.get();
                // 诊断：computeBonus 直接计算结果（不经过事件）
                check("diag: computeBonus(fireball) == 1", () -> SpellLevelAffix.computeBonus(dummy, fireball) == 1);
                check("diag: computeBonus(icicle) == 0", () -> SpellLevelAffix.computeBonus(dummy, SpellRegistry.ICICLE_SPELL.get()) == 0);
                ModifySpellLevelEvent fireEvent = new ModifySpellLevelEvent(fireball, dummy, 1, 1);
                NeoForge.EVENT_BUS.post(fireEvent);
                check("spell_level(school): fire spell level 1 -> 2", () -> fireEvent.getLevel() == 2);

                AbstractSpell icicle = SpellRegistry.ICICLE_SPELL.get();
                ModifySpellLevelEvent iceEvent = new ModifySpellLevelEvent(icicle, dummy, 1, 1);
                NeoForge.EVENT_BUS.post(iceEvent);
                check("spell_level(school): ice spell level stays 1", () -> iceEvent.getLevel() == 1);

                // 全局法术等级（spell_level，common +1 级）：所有流派都提升
                ItemStack globalGarb = garb.copy();
                AffixInstance globalInst = new AffixInstance(
                    AffixRegistry.INSTANCE.holder(rl(BattlemagesGarb.MOD_ID, "spell_level")), 1.0F, common, globalGarb);
                AffixHelper.applyAffix(globalGarb, globalInst);
                AffixHelper.setRarity(globalGarb, common.get());
                dummy.setItemSlot(EquipmentSlot.CHEST, globalGarb);

                ModifySpellLevelEvent gFire = new ModifySpellLevelEvent(fireball, dummy, 1, 1);
                NeoForge.EVENT_BUS.post(gFire);
                check("spell_level(global): fire spell level 1 -> 2", () -> gFire.getLevel() == 2);
                ModifySpellLevelEvent gIce = new ModifySpellLevelEvent(icicle, dummy, 1, 1);
                NeoForge.EVENT_BUS.post(gIce);
                check("spell_level(global): ice spell level 1 -> 2", () -> gIce.getLevel() == 2);
            }
            catch (Throwable t) {
                check("spell_level event verification", () -> false);
                LOGGER.error("  [EXCEPTION] spell_level event verification -> {}", t);
            }
        }

        // wizard 胸甲（无流派加成，通用套）：school_power 可用，但 school_power_greater 被门槛挡住 → 1 个专属词条
        ResourceKey<Item> wizardKey = ResourceKey.create(Registries.ITEM, rl("irons_spellbooks", "wizard_chestplate"));
        check("wizard_chestplate is registered", () -> items.containsKey(wizardKey));
        if (items.containsKey(wizardKey)) {
            ItemStack wizard = new ItemStack(items.get(wizardKey));
            check("prob: wizard isSpellGarb", () -> SpellSchoolHelper.isSpellGarb(wizard));
            check("prob: wizard school power == 0", () -> Math.abs(SpellSchoolHelper.getSchoolPower(wizard)) < 0.0001D);
            LootCategory wizardCat = LootCategory.forItem(wizard);
            DynamicHolder<LootRarity> wizardCommon = RarityRegistry.INSTANCE.holder(rl("apotheosis", "common"));
            if (wizardCommon.isBound()) {
                LootRarity wRarity = wizardCommon.get();
                // wizard 无流派加成 → 专属流派词条全部不适用（0 个）；pyromancer +0.10 → 2 个专属词条。
                // 这就是「流派加成越高 → 专属词条越多/概率越高」的浮动机制。
                check("prob: wizard school_power does NOT apply (no school attribute)", () -> !affix("school_power").canApplyTo(wizard, wizardCat, wRarity));
                check("prob: wizard school_power_greater does NOT apply (< 0.10)", () -> !affix("school_power_greater").canApplyTo(wizard, wizardCat, wRarity));
                check("prob: wizard school_spell_level does NOT apply (no school)", () -> !affix("school_spell_level").canApplyTo(wizard, wizardCat, wRarity));
                check("prob: wizard school_specialization does NOT apply (no school)", () -> !affix("school_specialization").canApplyTo(wizard, wizardCat, wRarity));
                check("prob: wizard school_power_exalted does NOT apply (<0.15)", () -> !affix("school_power_exalted").canApplyTo(wizard, wizardCat, wRarity));
                check("prob: wizard school_resist_greater does NOT apply (<0.10)", () -> !affix("school_resist_greater").canApplyTo(wizard, wizardCat, wRarity));
                check("prob: wizard spell_level applies (global)", () -> affix("spell_level").canApplyTo(wizard, wizardCat, wRarity));
            }
        }

        LOGGER.info("========== Battlemage's Garb SelfTest done: {} passed, {} failed ==========", passed, failed);
        server.halt(failed > 0);
    }

    private static boolean isBound(String path) {
        return AffixRegistry.INSTANCE.holder(rl(BattlemagesGarb.MOD_ID, path)).isBound();
    }

    private static Affix affix(String path) {
        return AffixRegistry.INSTANCE.holder(rl(BattlemagesGarb.MOD_ID, path)).get();
    }

    private static ResourceLocation rl(String ns, String path) {
        return ResourceLocation.fromNamespaceAndPath(ns, path);
    }

    private static void check(String name, BooleanSupplier test) {
        boolean ok;
        try {
            ok = test.getAsBoolean();
        }
        catch (Throwable t) {
            ok = false;
            LOGGER.error("  [EXCEPTION] {} -> {}", name, t);
        }
        if (ok) {
            passed++;
            LOGGER.info("  [PASS] {}", name);
        }
        else {
            failed++;
            LOGGER.error("  [FAIL] {}", name);
        }
    }
}
