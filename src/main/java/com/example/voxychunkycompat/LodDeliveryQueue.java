package com.example.voxychunkycompat;

import com.example.voxychunkycompat.network.LodProgressMessage;
import com.example.voxychunkycompat.network.VoxyNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LodDeliveryQueue {

    private static final Map<UUID, PlayerTask> TASKS = new ConcurrentHashMap<>();
    private static final int UPDATE_INTERVAL_TICKS = 20; // 1 s

    public static void enqueue(ServerLevel level, ServerPlayer player, int centerX, int centerZ, int radius) {
        ArrayList<long[]> positions = new ArrayList<>();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    positions.add(new long[]{centerX + dx, centerZ + dz});
                }
            }
        }
        PlayerTask task = new PlayerTask(level, player, new ArrayDeque<>(positions));
        TASKS.put(player.getUUID(), task);
        // Send the initial HUD packet so the client knows the total right away.
        sendProgress(task);
    }

    public static void removePlayer(UUID playerId) {
        TASKS.remove(playerId);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (TASKS.isEmpty()) return;

        int chunksPerTick = VoxyConfig.CHUNKS_PER_TICK.get();

        for (Map.Entry<UUID, PlayerTask> entry : TASKS.entrySet()) {
            PlayerTask task = entry.getValue();

            if (!task.player.isAlive() || task.player.hasDisconnected()) {
                TASKS.remove(entry.getKey());
                continue;
            }

            boolean dedicated = task.level.getServer().isDedicatedServer();
            Deque<long[]> activeQueue;

            if (!task.queue.isEmpty()) {
                activeQueue = task.queue;
            } else if ("generate".equals(VoxyConfig.generationMode) && !task.generationQueue.isEmpty()) {
                activeQueue = task.generationQueue;
            } else {
                TASKS.remove(entry.getKey());
                VoxyServerLODMod.LOGGER.info("[VoxyAutoLOD] Finished extended LOD delivery for {}",
                        task.player.getName().getString());
                // Final progress: tell the client we're done.
                sendProgressDone(task);
                continue;
            }

            int processed = 0;
            while (processed < chunksPerTick && !activeQueue.isEmpty()) {
                long[] pos = activeQueue.poll();
                int cx = (int) pos[0];
                int cz = (int) pos[1];

                ChunkAccess ca = task.level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (ca instanceof LevelChunk lc) {
                    deliver(task, dedicated, lc);
                    processed++;
                    continue;
                }

                if (activeQueue == task.generationQueue) {
                    ca = task.level.getChunk(cx, cz, ChunkStatus.FULL, true);
                    if (ca instanceof LevelChunk lc) deliver(task, dedicated, lc);
                    processed++;
                    continue;
                }

                // Disk-only mode: check whether the chunk exists on disk before loading.
                long key = ChunkPos.asLong(cx, cz);
                CompletableFuture<Boolean> diskCheck = task.pendingDiskChecks.get(key);

                if (diskCheck == null) {
                    diskCheck = task.level.getChunkSource().chunkMap
                            .read(new ChunkPos(cx, cz))
                            .thenApply(Optional::isPresent);
                    task.pendingDiskChecks.put(key, diskCheck);
                    task.queue.addFirst(pos); // re-queue to check next tick
                    processed++;
                    continue;
                }

                if (!diskCheck.isDone()) {
                    task.queue.addFirst(pos);
                    processed++;
                    continue;
                }

                task.pendingDiskChecks.remove(key);
                boolean onDisk = diskCheck.join();

                if (onDisk) {
                    ca = task.level.getChunk(cx, cz, ChunkStatus.FULL, true);
                    if (ca instanceof LevelChunk lc) deliver(task, dedicated, lc);
                } else if ("generate".equals(VoxyConfig.generationMode)) {
                    task.generationQueue.addLast(pos);
                }
                processed++;
            }

            // Send periodic HUD update.
            task.ticksSinceUpdate++;
            if (task.ticksSinceUpdate >= UPDATE_INTERVAL_TICKS) {
                task.ticksSinceUpdate = 0;
                sendProgress(task);
            }
        }
    }

    /** Sends current progress to the player's client. */
    private static void sendProgress(PlayerTask task) {
        int remaining = task.queue.size() + task.pendingDiskChecks.size() + task.generationQueue.size();
        int done = task.totalPositions - remaining;
        VoxyNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> task.player),
                new LodProgressMessage(done, task.totalPositions));
    }

    /** Sends the "all done" signal (HUD clears itself when processed >= total). */
    private static void sendProgressDone(PlayerTask task) {
        VoxyNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> task.player),
                new LodProgressMessage(task.totalPositions, task.totalPositions));
    }

    private static void deliver(PlayerTask task, boolean dedicated, LevelChunk chunk) {
        if (dedicated) {
            VoxyIntegration.sendToPlayer(task.level, chunk, task.player);
        } else {
            VoxyIntegration.tryIngest(task.level, chunk);
        }
    }

    private static class PlayerTask {
        final ServerLevel level;
        final ServerPlayer player;
        final Deque<long[]> queue;
        final int totalPositions;
        final Map<Long, CompletableFuture<Boolean>> pendingDiskChecks = new ConcurrentHashMap<>();
        final Deque<long[]> generationQueue = new ArrayDeque<>();
        int ticksSinceUpdate = 0;

        PlayerTask(ServerLevel level, ServerPlayer player, Deque<long[]> queue) {
            this.level = level;
            this.player = player;
            this.queue = queue;
            this.totalPositions = queue.size();
        }
    }
}
