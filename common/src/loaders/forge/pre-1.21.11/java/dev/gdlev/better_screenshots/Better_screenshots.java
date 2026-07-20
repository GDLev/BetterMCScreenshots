package dev.gdlev.better_screenshots;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Better_screenshots.MODID)
public class Better_screenshots {
    public static final String MODID = "better_screenshots";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final SoundEvent SHUTTER_SOFT =
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "shutter_soft"));
    public static final SoundEvent SHUTTER_CLASSIC =
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "shutter_classic"));

    public Better_screenshots(FMLJavaModLoadingContext context) {
        dev.gdlev.better_screenshots.client.ScreenshotConfig.load();
    }
}
