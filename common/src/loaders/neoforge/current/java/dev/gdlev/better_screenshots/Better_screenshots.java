package dev.gdlev.better_screenshots;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Better_screenshots.MODID)
public class Better_screenshots {
    public static final String MODID = "better_screenshots";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SHUTTER_SOFT = SOUND_EVENTS.register("shutter_soft",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MODID, "shutter_soft")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SHUTTER_CLASSIC = SOUND_EVENTS.register("shutter_classic",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MODID, "shutter_classic")));

    public Better_screenshots(IEventBus modEventBus, ModContainer modContainer) {
        SOUND_EVENTS.register(modEventBus);
        dev.gdlev.better_screenshots.client.ScreenshotConfig.load();
    }
}
