/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt */
package buildcraft.robotics;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.ChunkPos;

import net.minecraftforge.common.util.Constants;

import buildcraft.api.core.ISerializable;
import buildcraft.api.core.IZone;

import io.netty.buffer.ByteBuf;

public class ZonePlan implements IZone, ISerializable {
    private final HashMap<ChunkPos, ZoneChunk> chunkMapping = new HashMap<>();

    public boolean get(int x, int z) {
        int xChunk = x >> 4;
        int zChunk = z >> 4;
        ChunkPos chunkId = new ChunkPos(xChunk, zChunk);
        ZoneChunk property;

        if (!chunkMapping.containsKey(chunkId)) {
            return false;
        } else {
            property = chunkMapping.get(chunkId);
            return property.get(x & 0xF, z & 0xF);
        }
    }

    public void set(int x, int z, boolean val) {
        int xChunk = x >> 4;
        int zChunk = z >> 4;
        ChunkPos chunkId = new ChunkPos(xChunk, zChunk);
        ZoneChunk property;

        if (!chunkMapping.containsKey(chunkId)) {
            if (val) {
                property = new ZoneChunk();
                chunkMapping.put(chunkId, property);
            } else {
                return;
            }
        } else {
            property = chunkMapping.get(chunkId);
        }

        property.set(x & 0xF, z & 0xF, val);

        if (property.isEmpty()) {
            chunkMapping.remove(chunkId);
        }
    }

    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();

        for (Map.Entry<ChunkPos, ZoneChunk> e : chunkMapping.entrySet()) {
            NBTTagCompound subNBT = new NBTTagCompound();
            subNBT.setInteger("chunkX", e.getKey().chunkXPos);
            subNBT.setInteger("chunkZ", e.getKey().chunkZPos);
            e.getValue().writeToNBT(subNBT);
            list.appendTag(subNBT);
        }

        nbt.setTag("chunkMapping", list);
    }

    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("chunkMapping", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < list.tagCount(); ++i) {
            NBTTagCompound subNBT = list.getCompoundTagAt(i);

            ChunkPos id = new ChunkPos(subNBT.getInteger("chunkX"), subNBT.getInteger("chunkZ"));

            ZoneChunk chunk = new ZoneChunk();
            chunk.readFromNBT(subNBT);

            chunkMapping.put(id, chunk);
        }
    }

    @Override
    public double distanceTo(BlockPos index) {
        return Math.sqrt(distanceToSquared(index));
    }

    @Override
    public double distanceToSquared(BlockPos index) {
        double maxSqrDistance = Double.MAX_VALUE;

        for (Map.Entry<ChunkPos, ZoneChunk> e : chunkMapping.entrySet()) {
            double dx = (e.getKey().chunkXPos << 4 + 8) - index.getX();
            double dz = (e.getKey().chunkZPos << 4 + 8) - index.getZ();

            double sqrDistance = dx * dx + dz * dz;

            if (sqrDistance < maxSqrDistance) {
                maxSqrDistance = sqrDistance;
            }
        }

        return maxSqrDistance;
    }

    @Override
    public boolean contains(Vec3d point) {
        int xBlock = (int) Math.floor(point.xCoord);
        int zBlock = (int) Math.floor(point.zCoord);

        return get(xBlock, zBlock);
    }

    @Override
    public BlockPos getRandomBlockPos(Random rand) {
        if (chunkMapping.size() == 0) {
            return null;
        }

        int chunkId = rand.nextInt(chunkMapping.size());

        for (Map.Entry<ChunkPos, ZoneChunk> e : chunkMapping.entrySet()) {
            if (chunkId == 0) {
                BlockPos i = e.getValue().getRandomBlockPos(rand);
                int x = (e.getKey().chunkXPos << 4) + i.getX();
                int z = (e.getKey().chunkZPos << 4) + i.getZ();

                return new BlockPos(x, i.getY(), z);
            }

            chunkId--;
        }

        return null;
    }

    @Override
    public void readData(ByteBuf stream) {
        chunkMapping.clear();
        int size = stream.readInt();
        for (int i = 0; i < size; i++) {
            ChunkPos key = new ChunkPos(stream.readInt(), stream.readInt());
            ZoneChunk value = new ZoneChunk();
            value.readData(stream);
            chunkMapping.put(key, value);
        }
    }

    @Override
    public void writeData(ByteBuf stream) {
        stream.writeInt(chunkMapping.size());
        for (Map.Entry<ChunkPos, ZoneChunk> e : chunkMapping.entrySet()) {
            stream.writeInt(e.getKey().chunkXPos);
            stream.writeInt(e.getKey().chunkZPos);
            e.getValue().writeData(stream);
        }
    }
}
