package me.cortex.voxy.addon.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Parses OptiFine CTM (connected texture) .properties files from resource packs
 * and Fusion connected models to build a map of which blocks need per-face texture
 * blending in Voxy's LOD pass.
 */
public final class ConnectedTextureSupport {

    private static final boolean ENABLE_RANDOM_VARIANTS = Boolean.getBoolean("voxy.compat.ctm.enableRandom");
    private final IdentityHashMap<net.minecraft.world.level.block.Block, Rule> rules = new IdentityHashMap<>();
    private static volatile ConnectedTextureSupport instance;

    private ConnectedTextureSupport() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static ConnectedTextureSupport sharedInstance() {
        ConnectedTextureSupport cur = instance;
        if (cur != null) return cur;
        synchronized (ConnectedTextureSupport.class) {
            if (instance == null) instance = load();
            return instance;
        }
    }

    public static boolean isConnectedTextureBlock(net.minecraft.world.level.block.Block block) {
        return block != null && sharedInstance().get(block) != null;
    }

    public static ConnectedTextureSupport load() {
        ConnectedTextureSupport support = new ConnectedTextureSupport();

        Map<ResourceLocation, Resource> resources = new HashMap<>();
        collectCtmResources(resources, "optifine/ctm");
        collectCtmResources(resources, "mcpatcher/ctm");

        List<ParsedRule> parsedRules = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (InputStream stream = entry.getValue().open()) {
                Properties props = new Properties();
                props.load(stream);
                ParsedRule rule = parseRule(entry.getKey(), props);
                if (rule != null) parsedRules.add(rule);
            } catch (IOException e) {
                VoxyCompatAddonForgeMod.LOGGER.warn("Failed to read CTM properties from addon: {}", entry.getKey());
            } catch (Throwable t) {
                VoxyCompatAddonForgeMod.LOGGER.warn("Failed to parse CTM properties from addon: {}", entry.getKey());
            }
        }

        resolveMatchTileRules(parsedRules);
        for (ParsedRule pr : parsedRules) {
            if (pr.rule().hasBlocks() && pr.rule().hasContext()) support.addRule(pr.rule());
        }

        int optifineCount = support.rules.size();
        int fusionCount = support.addFusionConnectedModelRules();
        if (optifineCount > 0)
            VoxyCompatAddonForgeMod.LOGGER.info("Loaded {} blocks with OptiFine CTM hints for Voxy LOD from addon.", optifineCount);
        if (fusionCount > 0)
            VoxyCompatAddonForgeMod.LOGGER.info("Loaded {} blocks with Fusion connected model hints for Voxy LOD from addon.", fusionCount);
        return support;
    }

    @Nullable
    public Rule get(net.minecraft.world.level.block.Block block) {
        return rules.get(block);
    }

    public boolean matches(net.minecraft.world.level.block.Block source, net.minecraft.world.level.block.Block neighbor) {
        Rule rule = get(source);
        return rule != null && rule.matches(neighbor);
    }

    /** Returns the data needed to build a ConnectedTextureBakeRequest, or null if not applicable. */
    @Nullable
    public BakeRequestData computeBakeRequest(
            BlockState state, int connectionMask, int globalX, int globalY, int globalZ,
            BlockState[] neighborhoodStates, int[] neighborhoodBlockIds) {
        Rule rule = get(state.getBlock());
        if (rule == null) return null;

        int mask = rule.usesConnection() ? connectionMask : 0;
        int x = rule.usesPosition() ? Math.floorMod(globalX, Math.max(1, rule.phaseWidth()))  : 0;
        int y = rule.usesPosition() ? Math.floorMod(globalY, Math.max(1, rule.phaseHeight())) : 0;
        int z = rule.usesPosition() ? Math.floorMod(globalZ, Math.max(1, rule.phaseDepth()))  : 0;
        int randomHash = rule.usesRandomPosition() ? connectedTexturePositionHash(globalX, globalY, globalZ) : 0;

        long key = packKey(
            BuiltInRegistries.BLOCK.getId(state.getBlock()),
            mask, x, y, z, randomHash,
            rule.requiresExactNeighborhoodKey(), neighborhoodBlockIds);

        BlockPos origin = new BlockPos(globalX, globalY, globalZ);
        ConnectedTextureBlockView view = new ConnectedTextureBlockView(state, neighborhoodStates, origin);
        return new BakeRequestData(key, view, origin);
    }

    /** Carries the data that AddonRegistrar will wrap into a ConnectedTextureBakeRequest via reflection. */
    record BakeRequestData(long key, BlockAndTintGetter level, BlockPos origin) {}

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void addRule(Rule rule) {
        for (var block : rule.connectedBlocks) {
            Rule existing = rules.get(block);
            if (existing == null) rules.put(block, rule);
            else existing.merge(rule);
        }
    }

    private int addFusionConnectedModelRules() {
        Set<net.minecraft.world.level.block.Block> blocks =
            Collections.newSetFromMap(new IdentityHashMap<>());
        var shaper = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();
        outer:
        for (var block : BuiltInRegistries.BLOCK) {
            for (var state : block.getStateDefinition().getPossibleStates()) {
                try {
                    if (FusionModelSupport.containsFusionConnectedModel(shaper.getBlockModel(state))) {
                        blocks.add(block);
                        continue outer;
                    }
                } catch (Throwable ignored) {}
            }
        }
        if (blocks.isEmpty()) return 0;
        addRule(new Rule(blocks, 63, true, true, true, true, 16, 16, 16));
        return blocks.size();
    }

    private static void collectCtmResources(Map<ResourceLocation, Resource> out, String root) {
        try {
            out.putAll(Minecraft.getInstance().getResourceManager()
                .listResources(root, id -> id.getPath().endsWith(".properties")));
        } catch (Throwable t) {
            VoxyCompatAddonForgeMod.LOGGER.warn("Failed to scan {} for Voxy LOD CTM resources from addon.", root);
        }
    }

    @Nullable
    private static ParsedRule parseRule(ResourceLocation id, Properties props) {
        String matchBlocks = props.getProperty("matchBlocks", "").trim();
        String matchTiles  = props.getProperty("matchTiles",  "").trim();
        String connect     = props.getProperty("connect",     "").trim().toLowerCase(Locale.ROOT);
        String method      = props.getProperty("method",      "").trim().toLowerCase(Locale.ROOT);

        boolean usesConnection    = method.contains("ctm") || method.equals("horizontal") || method.equals("vertical")
                                 || method.equals("horizontal+vertical") || method.equals("vertical+horizontal");
        boolean usesRandomPos     = method.equals("random") || method.endsWith("_random");
        boolean usesPosition      = method.equals("repeat") || usesRandomPos;
        boolean exactNeighborhood = usesConnection && (connect.equals("tile") || connect.equals("state")
                                 || matchBlocks.isEmpty() && !matchTiles.isEmpty());

        if (matchBlocks.isEmpty() && matchTiles.isEmpty()) return null;
        if (usesRandomPos && !ENABLE_RANDOM_VARIANTS) return null;

        Set<net.minecraft.world.level.block.Block> blocks =
            Collections.newSetFromMap(new IdentityHashMap<>());
        for (String token : matchBlocks.split("\\s+")) {
            var block = parseBlock(token);
            if (block != null) blocks.add(block);
        }

        int phaseWidth  = usesRandomPos ? 16 : parseInt(props.getProperty("width"),  1);
        int phaseHeight = usesRandomPos ? 16 : parseInt(props.getProperty("height"), 1);
        int phaseDepth  = usesRandomPos ? 16 : 1;

        Rule rule = new Rule(blocks,
            parseFaces(props.getProperty("faces")),
            usesConnection, usesPosition, usesRandomPos, exactNeighborhood,
            Math.min(16, Math.max(1, phaseWidth)),
            Math.min(16, Math.max(1, phaseHeight)),
            Math.min(16, Math.max(1, phaseDepth)));

        return new ParsedRule(rule,
            parseTileList(id, props.getProperty("matchTiles"), true),
            parseTileList(id, props.getProperty("tiles"), false));
    }

    private static void resolveMatchTileRules(List<ParsedRule> rules) {
        Set<ResourceLocation> unresolved = new HashSet<>();
        for (ParsedRule pr : rules) {
            if (!pr.rule().hasBlocks()) unresolved.addAll(pr.matchTiles());
        }
        Map<ResourceLocation, Set<net.minecraft.world.level.block.Block>> modelTileBlocks =
            findBlocksUsingTiles(unresolved);
        for (ParsedRule pr : rules) {
            if (pr.rule().hasBlocks()) continue;
            for (ResourceLocation tile : pr.matchTiles()) {
                Set<net.minecraft.world.level.block.Block> bs = modelTileBlocks.get(tile);
                if (bs != null) pr.rule().addBlocks(bs);
            }
        }
        boolean changed;
        do {
            changed = false;
            Map<ResourceLocation, Set<net.minecraft.world.level.block.Block>> producers = new HashMap<>();
            for (ParsedRule pr : rules) {
                if (!pr.rule().hasBlocks()) continue;
                for (ResourceLocation tile : pr.tiles()) {
                    producers.computeIfAbsent(tile, k -> Collections.newSetFromMap(new IdentityHashMap<>()))
                              .addAll(pr.rule().connectedBlocks);
                }
            }
            for (ParsedRule pr : rules) {
                for (ResourceLocation tile : pr.matchTiles()) {
                    Set<net.minecraft.world.level.block.Block> bs = producers.get(tile);
                    if (bs != null) changed |= pr.rule().addBlocks(bs);
                }
            }
        } while (changed);
    }

    private static Map<ResourceLocation, Set<net.minecraft.world.level.block.Block>>
            findBlocksUsingTiles(Set<ResourceLocation> tiles) {
        Map<ResourceLocation, Set<net.minecraft.world.level.block.Block>> out = new HashMap<>();
        if (tiles.isEmpty()) return out;
        var shaper = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();
        for (var block : BuiltInRegistries.BLOCK) {
            boolean found = false;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                BakedModel model;
                try { model = shaper.getBlockModel(state); } catch (Throwable ignored) { continue; }
                Set<ResourceLocation> used = getModelTiles(model, state, tiles);
                if (used.isEmpty()) continue;
                for (ResourceLocation tile : used) {
                    out.computeIfAbsent(tile, k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(block);
                }
                found = true;
                break;
            }
            if (!found) continue;
        }
        return out;
    }

    private static Set<ResourceLocation> getModelTiles(BakedModel model, BlockState state, Set<ResourceLocation> targets) {
        Set<ResourceLocation> used = new HashSet<>();
        RandomSource rng = RandomSource.create(42L);
        for (Direction dir : Direction.values()) collectModelTiles(model, state, dir, rng, targets, used);
        collectModelTiles(model, state, null, rng, targets, used);
        try {
            ResourceLocation particle = model.getParticleIcon().contents().name();
            if (targets.contains(particle)) used.add(particle);
        } catch (Throwable ignored) {}
        return used;
    }

    private static void collectModelTiles(BakedModel model, BlockState state, @Nullable Direction dir,
            RandomSource rng, Set<ResourceLocation> targets, Set<ResourceLocation> out) {
        try {
            for (BakedQuad quad : model.getQuads(state, dir, rng)) {
                ResourceLocation sprite = quad.getSprite().contents().name();
                if (targets.contains(sprite)) out.add(sprite);
            }
        } catch (Throwable ignored) {}
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private static Set<ResourceLocation> parseTileList(ResourceLocation id, @Nullable String value, boolean matchTiles) {
        if (value == null || value.isBlank()) return Set.of();
        Set<ResourceLocation> tiles = new HashSet<>();
        for (String token : value.trim().split("[\\s,]+")) {
            if (!token.isBlank()) addTileToken(tiles, id, token, matchTiles);
        }
        return tiles;
    }

    private static void addTileToken(Set<ResourceLocation> tiles, ResourceLocation id, String token, boolean matchTiles) {
        token = token.replace('\\', '/').trim();
        int dash = token.indexOf('-');
        if (dash > 0) {
            String start = token.substring(0, dash);
            String end   = token.substring(dash + 1);
            if (isInteger(start) && isInteger(stripPng(end))) {
                int first = Integer.parseInt(start), last = Integer.parseInt(stripPng(end));
                int step = first <= last ? 1 : -1;
                for (int i = first; i != last + step; i += step)
                    tiles.add(resolveRelativeTile(id, Integer.toString(i)));
                return;
            }
        }
        tiles.addAll(resolveTile(id, token, matchTiles));
    }

    private static Collection<ResourceLocation> resolveTile(ResourceLocation id, String token, boolean matchTiles) {
        String norm = stripPng(stripTexturePrefix(token));
        if (norm.startsWith("./") || norm.startsWith("../") || isInteger(norm))
            return List.of(resolveRelativeTile(id, norm));
        int colon = norm.indexOf(':');
        if (colon >= 0) {
            ResourceLocation parsed = ResourceLocation.tryParse(norm);
            if (parsed == null) return List.of();
            if (matchTiles && !parsed.getPath().contains("/"))
                return List.of(parsed, new ResourceLocation(parsed.getNamespace(), "block/" + parsed.getPath()));
            return List.of(parsed);
        }
        if (matchTiles && !norm.contains("/"))
            return List.of(new ResourceLocation(id.getNamespace(), "block/" + norm), resolveRelativeTile(id, norm));
        if (matchTiles)
            return List.of(new ResourceLocation(id.getNamespace(), norm));
        return List.of(resolveRelativeTile(id, norm));
    }

    private static ResourceLocation resolveRelativeTile(ResourceLocation id, String token) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String parent = slash >= 0 ? path.substring(0, slash + 1) : "";
        return new ResourceLocation(id.getNamespace(), normalizeResourcePath(parent + token));
    }

    private static String normalizeResourcePath(String path) {
        ArrayDeque<String> stack = new ArrayDeque<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!stack.isEmpty()) stack.removeLast(); continue; }
            stack.addLast(part);
        }
        return String.join("/", stack);
    }

    private static String stripTexturePrefix(String t) { return t.startsWith("textures/") ? t.substring(9) : t; }
    private static String stripPng(String t) { return t.endsWith(".png") ? t.substring(0, t.length() - 4) : t; }
    private static boolean isInteger(String v) {
        if (v.isEmpty()) return false;
        for (char c : v.toCharArray()) if (!Character.isDigit(c)) return false;
        return true;
    }

    @Nullable
    private static net.minecraft.world.level.block.Block parseBlock(String token) {
        int bracket = token.indexOf('[');
        if (bracket >= 0) token = token.substring(0, bracket);
        if (token.isEmpty()) return null;
        ResourceLocation id = token.contains(":") ? ResourceLocation.tryParse(token)
                                                   : new ResourceLocation("minecraft", token);
        if (id == null) return null;
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    private static int parseFaces(@Nullable String faces) {
        if (faces == null || faces.isBlank()) return 63;
        int mask = 0;
        for (String f : faces.toLowerCase(Locale.ROOT).split("\\s+")) {
            mask |= switch (f) {
                case "all"               -> 0x3F;
                case "sides"             -> sideMask();
                case "top", "up"         -> 1 << Direction.UP.get3DDataValue();
                case "bottom", "down"    -> 1 << Direction.DOWN.get3DDataValue();
                case "north"             -> 1 << Direction.NORTH.get3DDataValue();
                case "south"             -> 1 << Direction.SOUTH.get3DDataValue();
                case "west"              -> 1 << Direction.WEST.get3DDataValue();
                case "east"              -> 1 << Direction.EAST.get3DDataValue();
                default                  -> 0;
            };
        }
        return mask == 0 ? 63 : mask;
    }

    private static int sideMask() {
        return 1 << Direction.NORTH.get3DDataValue() | 1 << Direction.SOUTH.get3DDataValue()
             | 1 << Direction.WEST.get3DDataValue()  | 1 << Direction.EAST.get3DDataValue();
    }

    private static int parseInt(@Nullable String value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException ignored) { return fallback; }
    }

    // -------------------------------------------------------------------------
    // Key packing
    // -------------------------------------------------------------------------

    private static long packKey(int blockId, int mask, int px, int py, int pz,
            int randomHash, boolean exactNeighborhood, int[] neighborIds) {
        long key = 7640891576956012809L;
        key = mix(key ^ blockId);
        key = mix(key ^ ((long) mask << 20));
        key = mix(key ^ ((long)(px & 0xF) << 46) ^ ((long)(py & 0xF) << 50)
                       ^ ((long)(pz & 0xF) << 54) ^ ((long)(randomHash & 0xFFFF) << 28));
        if (mask != 0 || exactNeighborhood) {
            for (int id : neighborIds) key = mix(key ^ id);
        }
        key |= 0x4000000000000000L;
        if (key == Long.MIN_VALUE) key++;
        return key;
    }

    private static int connectedTexturePositionHash(int x, int y, int z) {
        long key = -4942790177534073029L;
        key = mix(key ^ x);
        key = mix(key ^ ((long) y << 21));
        key = mix(key ^ ((long) z << 42));
        return (int) key;
    }

    private static long mix(long v) {
        v ^= v >>> 33; v *= -49064778989728563L;
        v ^= v >>> 33; v *= -4265267296055464877L;
        return v ^ (v >>> 33);
    }

    // -------------------------------------------------------------------------
    // Rule model
    // -------------------------------------------------------------------------

    public static final class Rule {
        final Set<net.minecraft.world.level.block.Block> connectedBlocks =
            Collections.newSetFromMap(new IdentityHashMap<>());
        private int faceMask;
        private boolean usesConnection, usesPosition, usesRandomPosition, requiresExactNeighborhoodKey;
        private int phaseWidth = 1, phaseHeight = 1, phaseDepth = 1;

        Rule(Collection<net.minecraft.world.level.block.Block> blocks, int faceMask,
             boolean usesConnection, boolean usesPosition, boolean usesRandomPosition,
             boolean requiresExactNeighborhoodKey, int phaseWidth, int phaseHeight, int phaseDepth) {
            connectedBlocks.addAll(blocks);
            this.faceMask = faceMask;
            this.usesConnection = usesConnection;
            this.usesPosition = usesPosition;
            this.usesRandomPosition = usesRandomPosition;
            this.requiresExactNeighborhoodKey = requiresExactNeighborhoodKey;
            this.phaseWidth  = Math.max(1, phaseWidth);
            this.phaseHeight = Math.max(1, phaseHeight);
            this.phaseDepth  = Math.max(1, phaseDepth);
        }

        public boolean usesConnection()             { return usesConnection; }
        public boolean usesPosition()               { return usesPosition; }
        public boolean usesRandomPosition()         { return usesRandomPosition; }
        public boolean requiresExactNeighborhoodKey(){ return requiresExactNeighborhoodKey; }
        public int phaseWidth()                     { return phaseWidth; }
        public int phaseHeight()                    { return phaseHeight; }
        public int phaseDepth()                     { return phaseDepth; }

        boolean hasBlocks()  { return !connectedBlocks.isEmpty(); }
        boolean hasContext() { return usesConnection || usesPosition; }

        boolean addBlocks(Collection<net.minecraft.world.level.block.Block> blocks) {
            boolean changed = false;
            for (var b : blocks) changed |= connectedBlocks.add(b);
            return changed;
        }

        void merge(Rule other) {
            connectedBlocks.addAll(other.connectedBlocks);
            faceMask |= other.faceMask;
            usesConnection      |= other.usesConnection;
            usesPosition        |= other.usesPosition;
            usesRandomPosition  |= other.usesRandomPosition;
            requiresExactNeighborhoodKey |= other.requiresExactNeighborhoodKey;
            phaseWidth  = Math.max(phaseWidth,  other.phaseWidth);
            phaseHeight = Math.max(phaseHeight, other.phaseHeight);
            phaseDepth  = Math.max(phaseDepth,  other.phaseDepth);
        }

        boolean matches(net.minecraft.world.level.block.Block block) {
            return connectedBlocks.contains(block);
        }
    }

    private record ParsedRule(Rule rule, Set<ResourceLocation> matchTiles, Set<ResourceLocation> tiles) {}

    // -------------------------------------------------------------------------
    // ConnectedTextureBlockView — minimal BlockAndTintGetter for bake requests
    // -------------------------------------------------------------------------

    private static final class ConnectedTextureBlockView implements BlockAndTintGetter {
        private final BlockState state;
        private final BlockState[] neighborhood;
        private final BlockPos origin;

        ConnectedTextureBlockView(BlockState state, BlockState[] neighborhood, BlockPos origin) {
            this.state = state;
            this.neighborhood = neighborhood;
            this.origin = origin;
        }

        @Override @Nullable
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.equals(origin)) return state;
            int idx = neighborIndex(pos);
            return idx >= 0 ? neighborhood[idx] : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getMinBuildHeight() { return 0; }

        @Override
        public int getHeight() { return 256; }

        @Override
        public float getShade(Direction direction, boolean shade) {
            ClientLevel level = Minecraft.getInstance().level;
            return level != null ? level.getShade(direction, shade) : (direction == Direction.UP ? 1f : 0.8f);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            ClientLevel level = Minecraft.getInstance().level;
            return level != null ? level.getLightEngine() : null;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return -1;
            try {
                return resolver.getColor(level.getBiome(BlockPos.ZERO).value(), 0.0, 0.0);
            } catch (Throwable ignored) {
                return -1;
            }
        }

        @Override
        public int getBrightness(LightLayer type, BlockPos pos) { return 0; }

        private int neighborIndex(BlockPos pos) {
            int dx = pos.getX() - origin.getX();
            int dy = pos.getY() - origin.getY();
            int dz = pos.getZ() - origin.getZ();
            if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || dz < -1 || dz > 1) return -1;
            return (dy + 1) * 9 + (dz + 1) * 3 + (dx + 1);
        }
    }
}
