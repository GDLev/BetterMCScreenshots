package dev.gdlev.better_screenshots.mixin.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ModListScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ModListScreen.class)
public class ModListScreenMixin {
    @Shadow
    private List<ModContainer> mods;

    @Inject(method = "reloadMods", at = @At("RETURN"))
    private void makeFilteredModsMutable(CallbackInfo ci) {
        mods = new ArrayList<>(mods);
    }
}
