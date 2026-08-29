package com.rvvisor.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.jetbrains.annotations.Nullable;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("transparencyChain")
    @Nullable
    PostChain getTransparencyChain();
}
