package cn.nukkit.level;

import cn.nukkit.Server;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.nio.ByteOrder;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public class GlobalBlockPalette {
    private static final Int2IntMap legacyToRuntimeId = new Int2IntOpenHashMap();
    private static final Int2IntMap runtimeIdToLegacy = new Int2IntOpenHashMap();
    private static final Int2ObjectMap<String> legacyIdToString = new Int2ObjectOpenHashMap<>();
    private static final Map<String, Integer> stringToLegacyId = new HashMap<>();
    private static final AtomicInteger runtimeIdAllocator = new AtomicInteger(0);
    public static final byte[] BLOCK_PALETTE;

    static {
        legacyToRuntimeId.defaultReturnValue(-1);
        runtimeIdToLegacy.defaultReturnValue(-1);

        InputStream stream = Server.class.getClassLoader().getResourceAsStream("runtime_block_states.dat");
        if (stream == null) {
            throw new AssertionError("Unable to locate block state nbt");
        }
        ListTag<CompoundTag> tag;
        try {
            //noinspection unchecked
            tag = (ListTag<CompoundTag>) NBTIO.readTag(stream, ByteOrder.LITTLE_ENDIAN, false);
        } catch (IOException e) {
            throw new AssertionError("Unable to load block palette", e);
        }

        for (CompoundTag state : tag.getAll()) {
            int runtimeId = runtimeIdAllocator.getAndIncrement();
            if (!state.contains("meta")) continue;

            int id = state.getShort("id");
            int[] meta = state.getIntArray("meta");
            String name = state.getCompound("block").getString("name");

            // Resolve to first legacy id
            runtimeIdToLegacy.put(runtimeId, id << 6 | meta[0]);
            stringToLegacyId.put(name, id);
            legacyIdToString.put(id, name);

            for (int val : meta) {
                int legacyId = id << 6 | val;
                legacyToRuntimeId.put(legacyId, runtimeId);
            }
            state.remove("meta"); // No point in sending this since the client doesn't use it.
        }

        try {
            BLOCK_PALETTE = NBTIO.write(tag, ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            throw new AssertionError("Unable to write block palette", e);
        }
    }

    public static int getOrCreateRuntimeId(int id, int meta) {
        int legacyId = id << 6 | meta;
        int runtimeId = legacyToRuntimeId.get(legacyId);
        if (runtimeId == -1) {
            runtimeId = legacyToRuntimeId.get(id << 6);
            if (runtimeId == -1) {
                throw new NoSuchElementException("Unmapped block registered id:" + id + " meta:" + meta);
            }
        }
        return runtimeId;
    }

    public static int getOrCreateRuntimeId(int legacyId) throws NoSuchElementException {
        return getOrCreateRuntimeId(legacyId >> 4, legacyId & 0xf);
    }

    public static int getLegacyId(int runtimeId) {
        return runtimeIdToLegacy.get(runtimeId);
    }

    public static String getName(int id) {
        return legacyIdToString.get(id);
    }
}
