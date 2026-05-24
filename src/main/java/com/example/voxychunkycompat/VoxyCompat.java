package com.example.voxychunkycompat;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.lang.reflect.Method;

/**
 * All Voxy API calls go through here via reflection so the mod compiles and
 * distributes without needing the Voxy jar on the classpath.  At runtime the
 * guard in each method ensures we never reach the reflective call when Voxy
 * is not installed.
 */
public final class VoxyCompat {

    // me.cortex.voxy.commonImpl.VoxyCommon
    private static Method m_isAvailable;
    // me.cortex.voxy.commonImpl.WorldIdentifier
    private static Method m_worldIdOf;
    private static Class<?> c_worldId;
    // me.cortex.voxy.common.world.service.VoxelIngestService
    private static Method m_tryAutoIngest;
    private static Method m_tryIngestChunk;
    private static Method m_rawIngest;

    private static boolean resolved = false;

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> common = Class.forName("me.cortex.voxy.commonImpl.VoxyCommon");
            m_isAvailable = common.getMethod("isAvailable");

            c_worldId = Class.forName("me.cortex.voxy.commonImpl.WorldIdentifier");
            m_worldIdOf = c_worldId.getMethod("of", Level.class);

            Class<?> svc = Class.forName("me.cortex.voxy.common.world.service.VoxelIngestService");
            m_tryAutoIngest = svc.getMethod("tryAutoIngestChunk", LevelChunk.class);
            m_tryIngestChunk = svc.getMethod("tryIngestChunk", c_worldId, LevelChunk.class);
            m_rawIngest = svc.getMethod("rawIngest", c_worldId, LevelChunkSection.class,
                    int.class, int.class, int.class, DataLayer.class, DataLayer.class);
        } catch (Exception ignored) {
            // Voxy not installed or API mismatch — all guard checks will return false
        }
    }

    public static boolean isAvailable() {
        resolve();
        if (m_isAvailable == null) return false;
        try {
            return Boolean.TRUE.equals(m_isAvailable.invoke(null));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean tryAutoIngestChunk(LevelChunk chunk) {
        resolve();
        if (m_tryAutoIngest == null) return false;
        try {
            Object result = m_tryAutoIngest.invoke(null, chunk);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            VoxyServerLODMod.LOGGER.error("[VoxyAutoLOD] tryAutoIngestChunk failed at ({}, {}): {}",
                    chunk.getPos().x, chunk.getPos().z, e.getMessage());
            return false;
        }
    }

    public static boolean tryIngestChunk(Level level, LevelChunk chunk) {
        resolve();
        if (m_tryIngestChunk == null || m_worldIdOf == null) return false;
        try {
            Object worldId = m_worldIdOf.invoke(null, level);
            Object result = m_tryIngestChunk.invoke(null, worldId, chunk);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            VoxyServerLODMod.LOGGER.warn("[VoxyAutoLOD] tryIngestChunk failed at ({}, {}): {}",
                    chunk.getPos().x, chunk.getPos().z, e.getMessage());
            return false;
        }
    }

    public static void rawIngest(Level level, LevelChunkSection section,
            int chunkX, int sectionY, int chunkZ,
            DataLayer skyLight, DataLayer blockLight) {
        resolve();
        if (m_rawIngest == null || m_worldIdOf == null) return;
        try {
            Object worldId = m_worldIdOf.invoke(null, level);
            m_rawIngest.invoke(null, worldId, section, chunkX, sectionY, chunkZ, skyLight, blockLight);
        } catch (Exception e) {
            VoxyServerLODMod.LOGGER.error("[VoxyAutoLOD] rawIngest failed at ({}, {}, {}): {}",
                    chunkX, sectionY, chunkZ, e.getMessage());
        }
    }

    private VoxyCompat() {}
}
