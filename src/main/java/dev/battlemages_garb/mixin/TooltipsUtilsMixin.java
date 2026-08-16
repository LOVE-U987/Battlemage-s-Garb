package dev.battlemages_garb.mixin;

import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hook Iron's {@code TooltipsUtils#getLevelComponenet}，将法术等级 tooltip 的主数字改为「基础等级」。
 * <p>
 * Iron's 默认把 {@code totalLevel}（基础 + 加成合并后的值）作为主数字，导致卷轴 5 级 + 词条加成 3 级
 * 显示为「8（+3）」，即把加成「合并」进主数字。本注入改为以<b>基础等级</b>为主数字，显示「5（+3）」。
 * <p>
 * 注意：{@code TooltipsUtils} 只在 Iron's 主 jar（不在 api jar），因此用 {@code targets} 字符串引用
 * 目标类，避免编译依赖主 jar。
 */
@Mixin(targets = "io.redspace.ironsspellbooks.util.TooltipsUtils")
public abstract class TooltipsUtilsMixin {

    @Inject(method = "getLevelComponenet", at = @At("RETURN"), cancellable = true)
    private static void battlemagesgarb$useBaseLevel(SpellData spellData, LivingEntity caster, CallbackInfoReturnable<MutableComponent> cir) {
        int base = spellData.getLevel();
        int total = spellData.getSpell().getLevelFor(base, caster);
        int diff = total - base;
        if (diff > 0) {
            cir.setReturnValue(Component.translatable("tooltip.irons_spellbooks.level_plus", base, diff));
        }
        else if (diff < 0) {
            cir.setReturnValue(Component.translatable("tooltip.irons_spellbooks.level_minus", base, diff));
        }
    }
}
