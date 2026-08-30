package com.rvvisor.mixin;

import com.rvvisor.RVVisorMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Ensures Sodium updates chunk visibility and translucent water pipelines
 * for each VR eye pass to prevent missing water or culled chunks at wide VR FOVs.
 */
@Pseudo
@Mixin(targets = {
        "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer",
        "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer"
}, remap = false)
public class SodiumWorldRendererMixin {

    @Group(name = "forceChunkUpdate", min = 0, max = 1)
    @ModifyVariable(method = "updateChunks", at = @At("STORE"), ordinal = 1, expect = 0, require = 0)
    private boolean rvvisor$forceRenderUpdate(boolean dirty) {
        RVVisorMod mod = RVVisorMod.getInstance();
        return (mod != null && mod.isVrActive()) || dirty;
    }

    @Group(name = "forceChunkUpdate", min = 0, max = 1)
    @ModifyVariable(method = "setupTerrain", at = @At("STORE"), ordinal = 2, expect = 0, require = 0)
    private boolean rvvisor$forceRenderUpdateSodium5(boolean dirty) {
        RVVisorMod mod = RVVisorMod.getInstance();
        return (mod != null && mod.isVrActive()) || dirty;
    }
}
