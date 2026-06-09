package dev.gdlev.better_screenshots;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(Better_screenshots.MODID)
public class Better_screenshots {
    public static final String MODID = "better_screenshots";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    public static final RegistryObject<SoundEvent> SHUTTER_SOFT = SOUND_EVENTS.register("shutter_soft",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "shutter_soft")));
    public static final RegistryObject<SoundEvent> SHUTTER_CLASSIC = SOUND_EVENTS.register("shutter_classic",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "shutter_classic")));

    public Better_screenshots() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUND_EVENTS.register(modEventBus);
        dev.gdlev.better_screenshots.client.ScreenshotConfig.load();
    }
}
