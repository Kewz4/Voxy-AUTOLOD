package me.cortex.voxy.addon.compat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Thin reflection wrapper around the voxy_compat_core ModelBakeContext object.
 * All methods cache their Method lookup on first call.
 */
final class ModelBakeContextReflect {

    private final Object ctx;
    private final Class<?> ctxClass;

    // Cached method handles
    private Method mBakeContext, mModel, mLevel, mPos, mState, mLayer,
                   mOpaqueConsumer, mTranslucentConsumer;
    // ReuseVertexConsumer method handles (from consumer object)
    private Method mQuad, mAddVertex, mSetUv, mMeta;
    private Class<?> consumerClass;

    ModelBakeContextReflect(Object ctx) {
        this.ctx = ctx;
        this.ctxClass = ctx.getClass();
    }

    boolean hasBakeContext() {
        try { return get("bakeContext", mBakeContext) != null; }
        catch (Throwable t) { return false; }
    }

    @Nullable BakedModel model() {
        try { return (BakedModel) get("model", mModel); }
        catch (Throwable t) { return null; }
    }

    @Nullable BlockAndTintGetter level() {
        try { return (BlockAndTintGetter) get("level", mLevel); }
        catch (Throwable t) { return null; }
    }

    @Nullable BlockPos pos() {
        try { return (BlockPos) get("pos", mPos); }
        catch (Throwable t) { return null; }
    }

    @Nullable BlockState state() {
        try { return (BlockState) get("state", mState); }
        catch (Throwable t) { return null; }
    }

    @Nullable RenderType layer() {
        try { return (RenderType) get("layer", mLayer); }
        catch (Throwable t) { return null; }
    }

    Object opaqueConsumer() {
        try { return get("opaqueConsumer", mOpaqueConsumer); }
        catch (Throwable t) { return null; }
    }

    Object translucentConsumer() {
        try { return get("translucentConsumer", mTranslucentConsumer); }
        catch (Throwable t) { return null; }
    }

    void consumerQuad(Object consumer,
                      net.minecraft.client.renderer.block.model.BakedQuad quad,
                      boolean forceSolid, RenderType layer) {
        try {
            if (mQuad == null || consumerClass != consumer.getClass()) {
                consumerClass = consumer.getClass();
                mQuad = findMethod(consumerClass, "quad",
                    net.minecraft.client.renderer.block.model.BakedQuad.class, boolean.class, RenderType.class);
                mQuad.setAccessible(true);
            }
            mQuad.invoke(consumer, quad, forceSolid, layer);
        } catch (Throwable t) {
            VoxyCompatAddonForgeMod.LOGGER.warn("Failed to emit quad to ReuseVertexConsumer", t);
        }
    }

    // -------------------------------------------------------------------------

    private Object get(String name, Method cached) throws Exception {
        Method m = cached;
        if (m == null) {
            m = findAccessorMethod(name);
        }
        return m.invoke(ctx);
    }

    private Method findAccessorMethod(String name) throws NoSuchMethodException {
        for (Class<?> c = ctxClass; c != null && c != Object.class; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod(name); m.setAccessible(true); return m; }
            catch (NoSuchMethodException ignored) {}
            for (Class<?> iface : c.getInterfaces()) {
                try { return iface.getMethod(name); } catch (NoSuchMethodException ignored) {}
            }
        }
        throw new NoSuchMethodException(ctxClass.getName() + "." + name + "()");
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes)
            throws NoSuchMethodException {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredMethod(name, paramTypes); }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(clazz.getName() + "." + name);
    }
}
