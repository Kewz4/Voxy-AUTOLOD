package me.cortex.voxy.addon.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

/**
 * Registers addon providers with voxy_compat_core via reflection so this mod
 * compiles without a hard dependency on the core API jar.
 */
public final class AddonRegistrar {

    private static final AddonModelBakeProvider MODEL_BAKE_PROVIDER = new AddonModelBakeProvider();

    public static void register() {
        Object api;
        try {
            Class<?> coreClass = Class.forName("me.cortex.voxy.compat.core.VoxyCompatCore");
            api = coreClass.getMethod("api").invoke(null);
        } catch (ClassNotFoundException ignored) {
            return; // voxy_compat_core not installed
        } catch (Throwable t) {
            VoxyCompatAddonForgeMod.LOGGER.warn("Failed to obtain voxy_compat_core API", t);
            return;
        }
        if (api == null) return;

        try { registerConnectedTextures(api); } catch (Throwable t) {
            VoxyCompatAddonForgeMod.LOGGER.warn("Failed to register connected texture provider", t);
        }
        try { registerModelBakeProvider(api); } catch (Throwable t) {
            VoxyCompatAddonForgeMod.LOGGER.warn("Failed to register model bake provider", t);
        }
        try { registerProtectedModels(api); } catch (Throwable t) {
            VoxyCompatAddonForgeMod.LOGGER.warn("Failed to register protected model classifiers", t);
        }
        VoxyCompatAddonForgeMod.LOGGER.info("Registered Voxy compatibility addon providers with compat core.");
    }

    // -------------------------------------------------------------------------
    // Connected textures
    // -------------------------------------------------------------------------

    private static void registerConnectedTextures(Object api) throws Exception {
        Object ctApi = invoke(api, "connectedTextures");
        Class<?> providerIface = Class.forName("me.cortex.voxy.compat.core.api.ConnectedTextureProvider");
        Class<?> bakeRequestClass = Class.forName("me.cortex.voxy.compat.core.api.ConnectedTextureBakeRequest");
        Class<?> bakeContextClass = Class.forName("me.cortex.voxy.compat.core.api.BakeContext");

        Constructor<?> bakeContextCtor = bakeContextClass.getConstructor(BlockAndTintGetter.class, BlockPos.class);
        Constructor<?> bakeRequestCtor = bakeRequestClass.getConstructor(long.class, bakeContextClass);

        Object provider = Proxy.newProxyInstance(
            AddonRegistrar.class.getClassLoader(),
            new Class<?>[]{ providerIface },
            (proxy, method, args) -> switch (method.getName()) {
                case "applies" -> {
                    BlockState s = (BlockState) args[0];
                    yield s != null && ConnectedTextureSupport.isConnectedTextureBlock(s.getBlock());
                }
                case "matches" -> {
                    BlockState src = (BlockState) args[0], nbr = (BlockState) args[1];
                    yield src != null && nbr != null
                        && ConnectedTextureSupport.sharedInstance().matches(src.getBlock(), nbr.getBlock());
                }
                case "createRequest" -> {
                    ConnectedTextureSupport.BakeRequestData data =
                        ConnectedTextureSupport.sharedInstance().computeBakeRequest(
                            (BlockState) args[0],
                            (int) args[1], (int) args[2], (int) args[3], (int) args[4],
                            (BlockState[]) args[5], (int[]) args[6]);
                    if (data == null) yield null;
                    Object ctx = bakeContextCtor.newInstance(data.level(), data.origin());
                    yield bakeRequestCtor.newInstance(data.key(), ctx);
                }
                case "equals"   -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "VoxyAddonCTProvider";
                default         -> null;
            }
        );
        invoke(ctApi, "registerProvider", new Class<?>[]{ providerIface }, provider);
    }

    // -------------------------------------------------------------------------
    // Model bake provider
    // -------------------------------------------------------------------------

    private static void registerModelBakeProvider(Object api) throws Exception {
        Object mbApi = invoke(api, "modelBakeProviders");
        Class<?> mbpIface = Class.forName("me.cortex.voxy.compat.core.api.ModelBakeProvider");

        Object provider = Proxy.newProxyInstance(
            AddonRegistrar.class.getClassLoader(),
            new Class<?>[]{ mbpIface },
            (proxy, method, args) -> switch (method.getName()) {
                case "bake"     -> MODEL_BAKE_PROVIDER.bake(args[0]);
                case "equals"   -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default         -> null;
            }
        );
        invoke(mbApi, "registerProvider", new Class<?>[]{ mbpIface }, provider);
    }

    // -------------------------------------------------------------------------
    // Protected model classifiers
    // -------------------------------------------------------------------------

    private static void registerProtectedModels(Object api) throws Exception {
        Object pmApi = invoke(api, "protectedModels");
        Object drApi = invoke(api, "dynamicRenderers");

        Class<?> modeClass  = Class.forName("me.cortex.voxy.compat.core.api.ProtectionMode");
        Object dynamicBE    = modeClass.getField("DYNAMIC_BLOCK_ENTITY").get(null);
        Object staticMesh   = modeClass.getField("STATIC_CAPTURED_MESH").get(null);

        Predicate<BlockState> isDynamic = BlockStateClassifiers::isDynamicBlockEntity;
        Predicate<BlockState> isStatic  = BlockStateClassifiers::isProtectedStaticModel;

        Method protectWithMode = findMethod(pmApi.getClass(), "protect", Predicate.class, modeClass);
        protectWithMode.invoke(pmApi, isDynamic, dynamicBE);
        protectWithMode.invoke(pmApi, isStatic,  staticMesh);

        Method protectDynamic = findMethod(drApi.getClass(), "protect", Predicate.class);
        protectDynamic.invoke(drApi, isDynamic);
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    static Object invoke(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return target.getClass().getMethod(name, types).invoke(target, args);
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes)
            throws NoSuchMethodException {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredMethod(name, paramTypes); } catch (NoSuchMethodException ignored) {}
            for (Class<?> iface : c.getInterfaces()) {
                try { return iface.getMethod(name, paramTypes); } catch (NoSuchMethodException ignored) {}
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "." + name);
    }

    private AddonRegistrar() {}
}
