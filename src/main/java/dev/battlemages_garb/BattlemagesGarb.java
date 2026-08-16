package dev.battlemages_garb;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Battlemage's Garb (法术战袍)。
 * <p>
 * 为神化(Apotheosis)新增「法术战袍」装备类型（对应 Iron's Spells 的法师盔甲）。
 * 其词条与通用护甲词条共存，并为对应装备主加成流派提供额外加成。
 */
@Mod(BattlemagesGarb.MOD_ID)
public class BattlemagesGarb {
    public static final String MOD_ID = "battlemages_garb";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BattlemagesGarb(IEventBus modEventBus, ModContainer modContainer) {
        // 词条 codec 与所需事件监听统一经注册中心注册（必须在首次 datapack 重载之前）。
        // SpellLevelAffix 的 ModifySpellLevelEvent 监听已包含在 registerCodecs() 内，勿在此重复注册。
        BattlemageAffixes.registerCodecs();
        NeoForge.EVENT_BUS.addListener(BattlemagesGarb::onServerStarted);
    }

    /** 自检钩子：./gradlew runServer -PbattleSelfTest 时在服务器启动后执行断言并退出。 */
    private static void onServerStarted(ServerStartedEvent event) {
        if (Boolean.getBoolean("battlemages_garb.selftest")) {
            BattlemagesGarbSelfTest.run(event.getServer());
        }
    }
}
