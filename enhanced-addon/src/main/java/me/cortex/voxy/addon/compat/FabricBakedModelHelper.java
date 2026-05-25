package me.cortex.voxy.addon.compat;

/**
 * Lazily resolves net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel,
 * which is available on Forge when Embeddium (with FRAPI compat) is installed.
 */
final class FabricBakedModelHelper {

    private static volatile boolean checked = false;
    private static volatile Class<?> fabricBakedModelClass = null;

    static Class<?> getFabricBakedModelClass() {
        if (!checked) {
            checked = true;
            try {
                fabricBakedModelClass = Class.forName(
                    "net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel");
            } catch (ClassNotFoundException ignored) {}
        }
        return fabricBakedModelClass;
    }

    private FabricBakedModelHelper() {}
}
