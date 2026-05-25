package me.cortex.voxy.addon.compat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reflection-based support for Fusion connected models (com.supermartijn642.fusion).
 * Works on both Fabric and Forge as Fusion exists on both platforms.
 */
public final class FusionModelSupport {

    private static final String CONNECTING_MODEL = "com.supermartijn642.fusion.model.types.connecting.ConnectingBakedModel";
    private static final int MAX_SCAN_DEPTH = 8;

    private static Class<?> modelDataClass;
    private static Object emptyModelData;
    private static boolean modelDataUnavailable;
    private static boolean loggedReflectionFailure;

    private FusionModelSupport() {}

    public static boolean containsFusionConnectedModel(BakedModel model) {
        return containsFusionConnectedModel(model, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    @Nullable
    public static Object getModelData(BakedModel model, BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Object empty = getEmptyModelData();
        if (empty == null) return null;
        try {
            Method m = model.getClass().getMethod("getModelData", BlockAndTintGetter.class, BlockPos.class, BlockState.class, modelDataClass);
            return m.invoke(model, level, pos, state, empty);
        } catch (Throwable t) {
            logReflectionFailure("Failed to query Fusion model data for Voxy LOD.", t);
            return null;
        }
    }

    @Nullable
    public static List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
            BakedModel model, BlockState state, @Nullable Direction direction,
            RandomSource random, Object modelData, @Nullable RenderType renderType) {
        if (modelDataClass == null) return null;
        try {
            Method m = model.getClass().getMethod("getQuads", BlockState.class, Direction.class,
                RandomSource.class, modelDataClass, RenderType.class);
            //noinspection unchecked
            return (List<net.minecraft.client.renderer.block.model.BakedQuad>)
                m.invoke(model, state, direction, random, modelData, renderType);
        } catch (Throwable t) {
            logReflectionFailure("Failed to query Fusion model quads for Voxy LOD.", t);
            return null;
        }
    }

    public static List<RenderType> getRenderTypes(BakedModel model, BlockState state,
            RandomSource random, Object modelData, @Nullable RenderType fallbackLayer) {
        if (modelDataClass == null) return fallbackRenderTypes(fallbackLayer);
        try {
            Method m = model.getClass().getMethod("getRenderTypes", BlockState.class, RandomSource.class, modelDataClass);
            Object value = m.invoke(model, state, random, modelData);
            if (value instanceof Iterable<?> iterable) {
                LinkedHashSet<RenderType> types = new LinkedHashSet<>();
                for (Object rt : iterable) {
                    if (rt instanceof RenderType r) types.add(r);
                }
                if (!types.isEmpty()) return new ArrayList<>(types);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            logReflectionFailure("Failed to query Fusion model render types for Voxy LOD.", t);
        }
        return fallbackRenderTypes(fallbackLayer);
    }

    private static boolean containsFusionConnectedModel(@Nullable Object value, Set<Object> seen, int depth) {
        if (value == null || depth > MAX_SCAN_DEPTH || !seen.add(value)) return false;
        Class<?> clazz = value.getClass();
        if (CONNECTING_MODEL.equals(clazz.getName())) return true;

        if (value instanceof Iterable<?> it) {
            for (Object child : it) {
                if (containsFusionConnectedModel(child, seen, depth + 1)) return true;
            }
            return false;
        }
        if (clazz.isArray()) {
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (containsFusionConnectedModel(Array.get(value, i), seen, depth + 1)) return true;
            }
            return false;
        }
        if (!(value instanceof BakedModel)) return false;

        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (!BakedModel.class.isAssignableFrom(ft)
                        && !Iterable.class.isAssignableFrom(ft)
                        && !ft.isArray()) continue;
                try {
                    f.setAccessible(true);
                    if (containsFusionConnectedModel(f.get(value), seen, depth + 1)) return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    @Nullable
    private static Object getEmptyModelData() {
        if (modelDataUnavailable) return null;
        if (emptyModelData != null) return emptyModelData;
        try {
            // Forge 1.20.1 uses net.minecraftforge.client.model.data.ModelData
            modelDataClass = Class.forName("net.minecraftforge.client.model.data.ModelData");
            emptyModelData = modelDataClass.getField("EMPTY").get(null);
            return emptyModelData;
        } catch (Throwable t) {
            modelDataUnavailable = true;
            logReflectionFailure("Forge ModelData is unavailable; Fusion connected textures will use vanilla Voxy LOD quads.", t);
            return null;
        }
    }

    private static List<RenderType> fallbackRenderTypes(@Nullable RenderType fallback) {
        return fallback == null ? Collections.emptyList() : List.of(fallback);
    }

    private static void logReflectionFailure(String msg, Throwable t) {
        if (!loggedReflectionFailure) {
            loggedReflectionFailure = true;
            VoxyCompatAddonForgeMod.LOGGER.warn(msg, t);
        }
    }
}
