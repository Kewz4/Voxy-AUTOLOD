package me.cortex.voxy.addon.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.List;

final class BlockStateClassifiers {

    private BlockStateClassifiers() {}

    static boolean isDynamicBlockEntity(BlockState state) {
        return state != null && state.hasBlockEntity();
    }

    static boolean isProtectedStaticModel(BlockState state) {
        if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) return false;

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null || "minecraft".equals(id.getNamespace())) return false;

        if (ConnectedTextureSupport.isConnectedTextureBlock(state.getBlock())) return false;

        if (state.hasBlockEntity()) return true;

        try {
            var model = Minecraft.getInstance().getBlockRenderer()
                .getBlockModelShaper().getBlockModel(state);

            // Check for Fabric Renderer API model (available via Embeddium on Forge)
            Class<?> fbmClass = FabricBakedModelHelper.getFabricBakedModelClass();
            if (fbmClass != null && fbmClass.isInstance(model)) {
                Method isVanilla = fbmClass.getMethod("isVanillaAdapter");
                if (!(boolean) isVanilla.invoke(model)) return true;
            }

            return !isSimpleFullCubeModel(model, state);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSimpleFullCubeModel(
            net.minecraft.client.resources.model.BakedModel model, BlockState state) {

        RandomSource rng = net.minecraft.util.RandomSource.create(42L);
        int total = 0;
        for (Direction dir : Direction.values()) {
            List<net.minecraft.client.renderer.block.model.BakedQuad> quads =
                model.getQuads(state, dir, rng);
            if (quads.size() != 1 || !isFullFaceQuad(quads.get(0), dir)) return false;
            total += quads.size();
        }
        if (!model.getQuads(state, null, rng).isEmpty()) return false;
        return total == 6;
    }

    private static boolean isFullFaceQuad(
            net.minecraft.client.renderer.block.model.BakedQuad quad, Direction direction) {
        int[] verts = quad.getVertices();
        if (verts.length < 32) return false;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (int v = 0; v < 4; v++) {
            int off = v * 8;
            float x = Float.intBitsToFloat(verts[off]);
            float y = Float.intBitsToFloat(verts[off + 1]);
            float z = Float.intBitsToFloat(verts[off + 2]);
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
        }

        return switch (direction) {
            case DOWN  -> close(minY, 0f) && spansFull(minX, maxX) && spansFull(minZ, maxZ);
            case UP    -> close(maxY, 1f) && spansFull(minX, maxX) && spansFull(minZ, maxZ);
            case NORTH -> close(minZ, 0f) && spansFull(minX, maxX) && spansFull(minY, maxY);
            case SOUTH -> close(maxZ, 1f) && spansFull(minX, maxX) && spansFull(minY, maxY);
            case WEST  -> close(minX, 0f) && spansFull(minY, maxY) && spansFull(minZ, maxZ);
            case EAST  -> close(maxX, 1f) && spansFull(minY, maxY) && spansFull(minZ, maxZ);
        };
    }

    private static boolean spansFull(float min, float max) { return close(min, 0f) && close(max, 1f); }
    private static boolean close(float a, float b) { return Math.abs(a - b) < 0.001f; }
}
