package com.angryalchemist.ecstatic.sample;

import com.angryalchemist.ecstatic.Constants;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Uses Minecraft's density function used in chunk generation in a vertical march + a binary refine to find the surface height
 * highly version specific, density function literally doesn't exist pre 1.18, keep any divergence contained here
 * returns a SurfaceSample object, contains a height, BiomeRawID, a color, and a contains-trees bool
 */
public final class SurfaceSampler { 
    private static final int MARCH_STEP = 8; 
    public static final int NO_HEIGHT_HINT = Integer.MIN_VALUE; // no neighboring point to check against for floating points
    private static final int FLOATING_OUTLIER_THRESHOLD_BLOCKS = 24; // maximum height diff between neighboring points before being treated as a floating block/blob
    private static final int GRASS_COLOR_BLEND_RADIUS = 2; // radius to blend terrain color
    private static final Map<Biome, Boolean> hasTreesCache = new ConcurrentHashMap<>(); //caches biome tree data. computed once per biome instance. 
    private static final Method SURFACE_SYSTEM_GET_BAND = resolveGetBandMethod(); // vanilla's terracotta band gen
    private static final Set<Identifier> BADLANDS_BIOMES = Set.of(
            Biomes.BADLANDS.identifier(), Biomes.ERODED_BADLANDS.identifier(), Biomes.WOODED_BADLANDS.identifier());

    private static final java.util.concurrent.atomic.AtomicInteger TIMING_LOGS = new java.util.concurrent.atomic.AtomicInteger();

    private SurfaceSampler() {
    }
    /**
     * Minecraft's {@code SurfaceRules} is not public, so reflection is the only (Sorta) future proof way of 
     * calling the real thing opposed to rederiving the code myself, which is vulnerable to any future tweak
     * by the big Moj 
     * 
     * Resolved one time, then cached. IF it fails for some reason, it reverts back to the default grass color
     * (not pretty, but it's something) rather than just throwing.
     * 
     * I can't seem to get it to work for some reason
     */
    private static Method resolveGetBandMethod() {
        try {
            for (Method method : MaterialSystem.class.getDeclaredMethods()) {
                if (method.getReturnType() == BlockState.class) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 3 && params[0] == int.class && params[1] == int.class && params[2] == int.class) {
                        method.trySetAccessible();
                        return method;
                    }
                }
            }
        } catch (Exception e) {
            Constants.LOG.warn("Ecstatic: Exception while scanning MaterialSystem methods", e);
        }
    
        Constants.LOG.warn("Ecstatic: Could not resolve MaterialSystem band lookup via reflection; badlands terrain will fall back to plain sampled grass color");
        return null;
    }

    public static SurfaceSample sample(ServerLevel level, int blockX, int blockZ) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        return sample(generator, randomState, biomeRegistry, level, blockX, blockZ, Integer.MIN_VALUE);
    }

    public static SurfaceSample sample(
        ChunkGenerator generator, RandomState randomState, Registry<Biome> biomeRegistry, LevelHeightAccessor heightAccessor, int blockX, int blockZ
    ) {
        return sample(generator, randomState, biomeRegistry, heightAccessor, blockX, blockZ, Integer.MIN_VALUE);
    }

    public static SurfaceSample sample(
        ChunkGenerator generator,
        RandomState randomState,
        Registry<Biome> biomeRegistry,
        LevelHeightAccessor heightAccessor,
        int blockX,
        int blockZ,
        int expectedHeightHint
    ) {
        // Ask the generator for the column height (ocean floor so seabed terrain keeps its real height).
        long t0 = System.nanoTime();
        int height = generator.getBaseHeight(blockX, blockZ, Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, randomState);
        if (TIMING_LOGS.getAndIncrement() < 8) {
            Constants.LOG.info("Ecstatic sample timing: height={} us (x={}, z={}, y={})", (System.nanoTime() - t0) / 1000L, blockX, blockZ, height);
        }

        return sampleWithHeight(generator, randomState, biomeRegistry, blockX, blockZ, height);
    }

    /** Builds a sample when the column height is already known (e.g. from {@link #sampleHeightGrid}). */
    public static SurfaceSample sampleWithHeight(
        ChunkGenerator generator, RandomState randomState, Registry<Biome> biomeRegistry, int blockX, int blockZ, int height
    ) {
        BiomeResolver resolver = biomeResolver(generator.getBiomeSource(), randomState);
        Holder<Biome> biomeHolder = resolver.getNoiseBiome(
            QuartPos.fromBlock(blockX), QuartPos.fromBlock(height), QuartPos.fromBlock(blockZ)
        );

        Biome biome = (Biome)biomeHolder.value();
        int biomeRawId = biomeRegistry.getId(biome);
        Identifier biomeKey = biomeRegistry.getKey(biome);
        int colorRgb = (biomeKey != null && BADLANDS_BIOMES.contains(biomeKey))
                ? badlandsColor(randomState, biome, blockX, height, blockZ)
                : averagedGrassColor(biome, blockX, blockZ);
        boolean hasTrees = hasTreesCache.computeIfAbsent(biome, SurfaceSampler::biomeHasTrees);
        return new SurfaceSample(height, biomeRawId, colorRgb, hasTrees);
    }
    
    private static final int BATCH_STEP_Y = 4;
    private static final int BATCH_ROWS = 8;
    private static volatile boolean batchFailed;

    /**
     * Samples the surface height of a count x count grid (index lz * count + lx) with a few large density volumes
     * instead of one volume per column. Heights are interpolated between vertical steps, so they can differ from
     * getBaseHeight by a block or two. Returns null when unsupported or on any failure; callers fall back to
     * per-column sampling.
     */
    public static int[] sampleHeightGrid(
        ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heightAccessor, int originX, int originZ, int spacing, int count
    ) {
        if (batchFailed || !(generator instanceof NoiseBasedChunkGenerator noise)) {
            return null;
        }

        try {
            var noiseSettings = noise.generatorSettings().value().noiseSettings().clampToHeightAccessor(heightAccessor);
            int minY = noiseSettings.minY();
            int ny = noiseSettings.height() / BATCH_STEP_Y;
            if (ny < 2) {
                return null;
            }

            DensitySampler sampler = finalDensitySampler(generator, randomState);
            int[] heights = new int[count * count];
            for (int rowStart = 0; rowStart < count; rowStart += BATCH_ROWS) {
                int rows = Math.min(BATCH_ROWS, count - rowStart);
                DensityVolume volume = new DensityVolume(
                    count, ny, rows, originX, minY, originZ + rowStart * spacing, spacing, BATCH_STEP_Y, spacing
                );
                SamplerContext context = SamplerContext.builder().enableCaches().build();
                try (ScopedDensityBuffer buffer = context.acquireBuffer(volume)) {
                    sampler.sampleVolume(context, buffer, volume);
                    for (int z = 0; z < rows; z++) {
                        for (int x = 0; x < count; x++) {
                            heights[(rowStart + z) * count + x] = topSolidHeight(buffer, volume, x, z, ny, minY);
                        }
                    }
                }
            }

            return heights;
        } catch (Throwable t) {
            batchFailed = true;
            Constants.LOG.warn("Ecstatic: batched terrain sampling failed, falling back to per-column sampling", t);
            return null;
        }
    }

    private static int topSolidHeight(DensityBuffer buffer, DensityVolume volume, int x, int z, int ny, int minY) {
        for (int y = ny - 1; y >= 0; y--) {
            float density = buffer.get(volume.indexUnchecked(x, y, z));
            if (density > 0.0F) {
                int blockY = volume.blockY(y);
                if (y == ny - 1) {
                    return blockY + 1;
                }

                float above = buffer.get(volume.indexUnchecked(x, y + 1, z));
                float t = density / (density - above);
                return (int)Math.floor(blockY + t * BATCH_STEP_Y) + 1;
            }
        }

        return minY;
    }

    private record SamplerKey(RandomState randomState, DensitySampler sampler) {
    }

    private record ResolverKey(BiomeSource source, RandomState randomState, BiomeResolver resolver) {
    }

    private static volatile SamplerKey cachedSampler;
    private static volatile ResolverKey cachedResolver;

    /** The compiled final-density sampler for noise-based generators, or null for anything else (flat, custom, etc). */
    private static DensitySampler finalDensitySampler(ChunkGenerator generator, RandomState randomState) {
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) {
            return null;
        }

        SamplerKey cached = cachedSampler;
        if (cached != null && cached.randomState() == randomState) {
            return cached.sampler();
        }

        DensitySampler sampler = randomState.getSampler(noise.generatorSettings().value().noiseRouter().finalDensity());
        cachedSampler = new SamplerKey(randomState, sampler);
        return sampler;
    }

    private static BiomeResolver biomeResolver(BiomeSource source, RandomState randomState) {
        ResolverKey cached = cachedResolver;
        if (cached != null && cached.source() == source && cached.randomState() == randomState) {
            return cached.resolver();
        }

        BiomeResolver resolver = source.createUncachedResolver(randomState);
        cachedResolver = new ResolverKey(source, randomState, resolver);
        return resolver;
    }

    /**
     * Checks if a biome places a {@link TreeFeature} in the vegetation step.
     * Works for modded biomes too since it doesn't pull from a hardcoded list.
     */
    private static boolean biomeHasTrees(Biome biome) {
        List<HolderSet<PlacedFeature>> steps = biome.getGenerationSettings().features();
        int stepIndex = Decoration.VEGETAL_DECORATION.ordinal();
        if (stepIndex >= steps.size()) {
            return false;
        }

        for (Holder<PlacedFeature> placedHolder : steps.get(stepIndex)) {
            boolean isTree = ((PlacedFeature)placedHolder.value()).getFeatures().anyMatch(configured -> configured.value() instanceof TreeFeature);
            if (isTree) {
                return true;
            }
        }

        return false;
    }

    private static int badlandsColor(RandomState randomState, Biome biome, int blockX, int height, int blockZ) {
        BlockState band = badlandsBand(randomState, blockX, height, blockZ);
        return band != null ? terracottaColor(band) : averagedGrassColor(biome, blockX, blockZ);
    }

    private static BlockState badlandsBand(RandomState randomState, int blockX, int blockY, int blockZ) {
        if (SURFACE_SYSTEM_GET_BAND == null) {
            return null;
        }

        try {
            return (BlockState)SURFACE_SYSTEM_GET_BAND.invoke(randomState.surfaceSystem(), blockX, blockY, blockZ);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static int terracottaColor(BlockState band) {
        if (band.is(Blocks.DYED_TERRACOTTA.orange())) {
            return 10703913;
        } else if (band.is(Blocks.DYED_TERRACOTTA.yellow())) {
            return 12099634;
        } else if (band.is(Blocks.DYED_TERRACOTTA.brown())) {
            return 5059364;
        } else if (band.is(Blocks.DYED_TERRACOTTA.red())) {
            return 9387310;
        } else if (band.is(Blocks.DYED_TERRACOTTA.white())) {
            return 13743267;
        } else {
            return band.is(Blocks.DYED_TERRACOTTA.lightGray()) ? 10257528 : 10117462;
        }
    }

    private static int averagedGrassColor(Biome biome, int blockX, int blockZ) {
        int rSum = 0;
        int gSum = 0;
        int bSum = 0;
        int count = 0;

        for (int dz = -GRASS_COLOR_BLEND_RADIUS; dz <= GRASS_COLOR_BLEND_RADIUS; dz++) {
            for (int dx = -GRASS_COLOR_BLEND_RADIUS; dx <= GRASS_COLOR_BLEND_RADIUS; dx++) {
                int sampleColor = biome.getGrassColor(blockX + dx, blockZ + dz);
                rSum += (sampleColor >> 16) & 0xFF;
                gSum += (sampleColor >> 8) & 0xFF;
                bSum += sampleColor & 0xFF;
                count++;
            }
        }
        
        int rAvg = rSum / count;
        int gAvg = gSum / count;
        int bAvg = bSum / count;

        return (rAvg << 16) | (gAvg << 8) | bAvg;
    }

    private static int findSurfaceHeight(DensitySampler finalDensity, LevelHeightAccessor heightAccessor, int blockX, int blockZ, int expectedHeightHint) {

        int bottom = heightAccessor.getMinY();
        int prevY;
        int y;
        if (expectedHeightHint != NO_HEIGHT_HINT) {
            prevY = expectedHeightHint + FLOATING_OUTLIER_THRESHOLD_BLOCKS + MARCH_STEP + 1; // Base march height off of height hint, assuming it's not nothing
        } else {
            prevY = heightAccessor.getMaxY();
        }
        y = prevY - MARCH_STEP;
        int fallback = bottom;

        while (y >= bottom) {
            if (isSolid(finalDensity, blockX, y, blockZ)) {
                int candidate = binaryRefine(finalDensity, blockX, blockZ, prevY, y) + 1;
                if (expectedHeightHint == NO_HEIGHT_HINT || Math.abs(candidate - expectedHeightHint) <= FLOATING_OUTLIER_THRESHOLD_BLOCKS) {
                    return candidate;
                }

                fallback = candidate;
                prevY = candidate - 1;
                y = prevY - MARCH_STEP;
            } else {
                prevY = y;
                y -= MARCH_STEP;
            }
        }

        return fallback;
    }

    private static int binaryRefine(DensitySampler finalDensity, int blockX, int blockZ, int highNonSolidY, int lowSolidY) {
        while (highNonSolidY - lowSolidY > 1) {
            int mid = (highNonSolidY + lowSolidY) >>> 1;
            if (isSolid(finalDensity, blockX, mid, blockZ)) {
                lowSolidY = mid;
            } else {
                highNonSolidY = mid;
            }
        }

        return lowSolidY;
    }

    private static boolean isSolid(DensitySampler finalDensity, int blockX, int blockY, int blockZ) {
        return finalDensity.sampleValue(SamplerContext.EMPTY_UNCACHED, blockX, blockY, blockZ) > 0.0F;
    }
}
