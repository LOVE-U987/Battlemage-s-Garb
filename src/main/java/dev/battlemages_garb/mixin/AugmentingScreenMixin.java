package dev.battlemages_garb.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.affix.augmenting.AugmentingScreen;
import dev.shadowsoffire.apotheosis.client.DropDownList;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;

/**
 * 强化台（Augmenting Table）「潜在刷新结果」显示词条出现概率。
 * <p>
 * Apotheosis 默认在潜在重 roll 列表中只显示每个候选词条的描述，不显示概率。
 * 本 mixin 重写 {@link AugmentingScreen#computeAlternatives(int)}，按各词条的
 * {@code weights().weightForTier(tier, luck)} 权重占比，在每个词条描述后附加
 * 「 (xx%)」出现概率。
 * <p>
 * 仅客户端生效（配置于 mixins json 的 {@code client} 列表），不影响服务端。
 */
@Mixin(AugmentingScreen.class)
public class AugmentingScreenMixin {

    @Shadow
    private List<AffixInstance> currentItemAffixes;

    @Shadow
    private ItemStack lastMainItem;

    @Shadow
    protected final AttributeTooltipContext tooltipCtx = null;

    @Shadow
    protected List<List<FormattedText>> alternativePages;

    @Shadow
    protected int alternativePage;

    @Shadow
    protected int alternativeXPos;

    @Shadow
    protected int alternativeWidth;

    // 注意：font 是继承自 Screen 的 protected 字段，Mixin 的 @Shadow 只查找目标类自身声明的字段（不查继承链），
    // 因此这里不能 @Shadow font，改用 Minecraft.getInstance().font（Screen.font 与 minecraft.font 是同一引用）。

    @Inject(method = "computeAlternatives", at = @At("HEAD"), cancellable = true)
    private void bg_computeAlternativesWithChances(int selected, CallbackInfo ci) {
        if (selected == DropDownList.NO_SELECTION) {
            this.alternativePages = java.util.Collections.emptyList();
            this.alternativePage = DropDownList.NO_SELECTION;
            ci.cancel();
            return;
        }

        AffixInstance current = this.currentItemAffixes.get(selected);
        Player player = Minecraft.getInstance().player;
        List<DynamicHolder<Affix>> alternatives = LootController.getAlternativeAffixes(player, this.lastMainItem, current.getRarity(), current.affix()).toList();

        if (alternatives.isEmpty()) {
            this.alternativePages = java.util.Collections.emptyList();
            this.alternativePage = DropDownList.NO_SELECTION;
            ci.cancel();
            return;
        }

        // 按权重计算每个候选词条的出现概率（weight / 总权重）。
        GenContext ctx = GenContext.forPlayer(player);
        List<WeightedEntry.Wrapper<Affix>> weighted = alternatives.stream()
            .map(a -> a.get().<Affix>wrap(ctx.tier(), ctx.luck()))
            .toList();
        double totalWeight = weighted.stream().mapToDouble(w -> w.getWeight().asInt()).sum();

        Font font = Minecraft.getInstance().font;
        StringSplitter splitter = font.getSplitter();
        int maxWidth = 0;
        Component heading = Component.translatable("text.apotheosis.potential_rerolls").withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE);
        List<List<FormattedText>> pages = new ArrayList<>();
        List<FormattedText> page = new ArrayList<>();
        page.add(heading);
        boolean first = true;

        for (int i = 0; i < alternatives.size(); i++) {
            AffixInstance inst = new AffixInstance(alternatives.get(i), current.level(), current.rarity(), current.stack());
            double pct = totalWeight > 0 ? 100.0 * weighted.get(i).getWeight().asInt() / totalWeight : 0.0;
            MutableComponent augTxt = inst.getAugmentingText(this.tooltipCtx).copy();
            augTxt.append(Component.translatable("text.battlemages_garb.affix_chance", Affix.fmt((float) pct)));
            List<FormattedText> split = splitter.splitLines(Component.translatable("%s", augTxt).withStyle(ChatFormatting.YELLOW), AugmentingScreen.ALTERNATIVE_TEXT_WIDTH, augTxt.getStyle());
            maxWidth = Math.max(maxWidth, split.stream().map(font::width).max(Integer::compare).get());

            if (page.size() + split.size() + 1 > AugmentingScreen.ALTERNATIVE_MAX_LINES) {
                pages.add(page);
                page = new ArrayList<>();
                page.add(heading);
                page.addAll(split);
            }
            else {
                if (!first) {
                    page.add(CommonComponents.SPACE);
                }
                page.addAll(split);
                first = false;
            }

            if (i == alternatives.size() - 1) {
                pages.add(page);
            }
        }

        this.alternativePage = 0;
        this.alternativePages = pages;
        this.alternativeXPos = ((AbstractContainerScreen<?>) (Object) this).getGuiLeft() - 16 - maxWidth;
        this.alternativeWidth = maxWidth;

        int pageCount = this.alternativePages.size();
        if (pageCount > 1) {
            for (int i = 0; i < pageCount; i++) {
                List<FormattedText> p = this.alternativePages.get(i);
                p.add(CommonComponents.SPACE);
                p.add(Component.translatable("text.apotheosis.alternative_page", i + 1, pageCount).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        ci.cancel();
    }
}
