package me.cortex.voxy.addon.compat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides improved block quad data to Voxy's LOD baking pipeline.
 * Supports Fusion connected models; Fabric Renderer API capture is omitted on Forge
 * (it requires Embeddium FRAPI internals not available as a stable API).
 */
public final class AddonModelBakeProvider {

    private boolean loggedFusionSuccess;

    /**
     * Called by the ModelBakeProvider proxy created in {@link AddonRegistrar}.
     * @param rawContext the ModelBakeContext object from voxy_compat_core (untyped)
     */
    public boolean bake(Object rawContext) {
        ModelBakeContextReflect ctx = new ModelBakeContextReflect(rawContext);
        if (!ctx.hasBakeContext()) return false;
        return tryBakeFusion(ctx);
    }

    // -------------------------------------------------------------------------
    // Fusion connected model support
    // -------------------------------------------------------------------------

    private boolean tryBakeFusion(ModelBakeContextReflect ctx) {
        var model = ctx.model();
        if (model == null || !FusionModelSupport.containsFusionConnectedModel(model)) return false;

        BlockAndTintGetter level = ctx.level();
        BlockPos pos = ctx.pos();
        BlockState state = ctx.state();
        if (level == null || pos == null || state == null) return false;

        Object modelData = FusionModelSupport.getModelData(model, level, pos, state);
        if (modelData == null) return false;

        RenderType layer = ctx.layer();
        RandomSource rng = RandomSource.create(42L);
        boolean queriedQuads = false;

        for (RenderType quadLayer : FusionModelSupport.getRenderTypes(model, state, rng, modelData, layer)) {
            RenderType outputLayer = quadLayer == null ? layer : quadLayer;

            for (Direction dir : new Direction[]{
                    Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
                if (dir != null && !shouldEmitFace(state, ctx, dir)) continue;

                List<BakedQuad> quads = FusionModelSupport.getQuads(model, state, dir, rng, modelData, quadLayer);
                if (quads == null) return false;
                queriedQuads = true;

                boolean forceSolid = state.canOcclude();
                Object consumer = outputLayer == RenderType.translucent()
                    ? ctx.translucentConsumer() : ctx.opaqueConsumer();
                if (consumer == null) continue;

                for (BakedQuad quad : quads) {
                    ctx.consumerQuad(consumer, quad, forceSolid, outputLayer);
                }
            }
        }

        if (queriedQuads && !loggedFusionSuccess) {
            loggedFusionSuccess = true;
            VoxyCompatAddonForgeMod.LOGGER.info("Captured Fusion model-data block quads for Voxy LOD from addon.");
        }
        return queriedQuads;
    }

    private static boolean shouldEmitFace(BlockState state, ModelBakeContextReflect ctx, @Nullable Direction dir) {
        if (dir == null) return true;
        BlockAndTintGetter level = ctx.level();
        BlockPos pos = ctx.pos();
        if (level == null || pos == null) return true;
        try {
            return Block.shouldRenderFace(state, level, pos, dir, pos.relative(dir));
        } catch (Throwable ignored) {
            return true;
        }
    }
}
