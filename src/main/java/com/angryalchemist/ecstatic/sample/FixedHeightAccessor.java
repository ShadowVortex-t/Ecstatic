package com.angryalchemist.ecstatic.sample;

import net.minecraft.world.level.LevelHeightAccessor;

public record FixedHeightAccessor(int minBuildHeight, int maxBuildHeight) implements LevelHeightAccessor {
    @Override
    public int getHeight() {
        return this.maxBuildHeight - this.minBuildHeight;
    }

    @Override
    public int getMinY() {
        return this.minBuildHeight;
    }
}
