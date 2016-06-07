/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt */
package buildcraft.core;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import buildcraft.BuildCraftCore;
import buildcraft.api.core.ISerializable;
import buildcraft.api.tiles.ITileAreaProvider;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.utils.NBTUtils;
import buildcraft.core.lib.utils.Utils;

import io.netty.buffer.ByteBuf;

public class TileMarker extends TileBuildCraft implements ITileAreaProvider {
    public static class TileWrapper implements ISerializable {

        public int x, y, z;
        private TileMarker marker;

        public TileWrapper() {
            x = Integer.MAX_VALUE;
            y = Integer.MAX_VALUE;
            z = Integer.MAX_VALUE;
        }

        public TileWrapper(BlockPos pos) {
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
        }

        public boolean isSet() {
            return x != Integer.MAX_VALUE;
        }

        public TileMarker getMarker(World world) {
            if (!isSet()) {
                return null;
            }

            if (marker == null) {
                TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
                if (tile instanceof TileMarker) {
                    marker = (TileMarker) tile;
                }
            }

            return marker;
        }

        public void reset() {
            x = Integer.MAX_VALUE;
            y = Integer.MAX_VALUE;
            z = Integer.MAX_VALUE;
        }

        @Override
        public void readData(ByteBuf stream) {
            x = stream.readInt();
            if (isSet()) {
                y = stream.readShort();
                z = stream.readInt();
            }
        }

        @Override
        public void writeData(ByteBuf stream) {
            stream.writeInt(x);
            if (isSet()) {
                // Only X is used for checking if a vector is set, so we can save space on the Y coordinate.
                stream.writeShort(y);
                stream.writeInt(z);
            }
        }

        public BlockPos getPos() {
            return new BlockPos(x, y, z);
        }
    }

    public static class Origin implements ISerializable {
        public TileWrapper vectO = new TileWrapper();
        public TileWrapper[] vect = { new TileWrapper(), new TileWrapper(), new TileWrapper() };
        public int xMin, yMin, zMin, xMax, yMax, zMax;

        public boolean isSet() {
            return vectO.isSet();
        }

        @Override
        public void writeData(ByteBuf stream) {
            vectO.writeData(stream);
            for (TileWrapper tw : vect) {
                tw.writeData(stream);
            }
            stream.writeInt(xMin);
            stream.writeShort(yMin);
            stream.writeInt(zMin);
            stream.writeInt(xMax);
            stream.writeShort(yMax);
            stream.writeInt(zMax);
        }

        @Override
        public void readData(ByteBuf stream) {
            vectO.readData(stream);
            for (TileWrapper tw : vect) {
                tw.readData(stream);
            }
            xMin = stream.readInt();
            yMin = stream.readShort();
            zMin = stream.readInt();
            xMax = stream.readInt();
            yMax = stream.readShort();
            zMax = stream.readInt();
        }

        public BlockPos min() {
            return new BlockPos(xMin, yMin, zMin);
        }

        public BlockPos max() {
            return new BlockPos(xMax, yMax, zMax);
        }
    }

    public Origin origin = new Origin();
    public boolean showSignals = false;

    private Vec3d initVectO;
    private Vec3d[] initVect;
    public LaserData[] lasers;
    public LaserData[] signals;

    public void updateSignals() {
        if (!worldObj.isRemote) {
            showSignals = worldObj.isBlockIndirectlyGettingPowered(pos) > 0;
            sendNetworkUpdate();
        }
    }

    private void switchSignals() {
        signals = null;
        if (showSignals) {
            signals = new LaserData[6];
            Vec3d cPos = Utils.convert(pos);
            int rangePlus = DefaultProps.MARKER_RANGE + 1;
            int rangeMinus = DefaultProps.MARKER_RANGE - 1;
            Vec3d offset = Utils.VEC_HALF;
            if (!origin.isSet() || !origin.vect[0].isSet()) {
                signals[0] = new LaserData(cPos.add(offset), cPos.add(offset).addVector(rangeMinus, 0, 0));
                signals[1] = new LaserData(cPos.add(offset).addVector(-rangePlus, 0, 0), cPos.add(offset));
            }

            if (!origin.isSet() || !origin.vect[1].isSet()) {
                signals[2] = new LaserData(cPos.add(offset), cPos.add(offset).addVector(0, rangeMinus, 0));
                signals[3] = new LaserData(cPos.add(offset).addVector(0, -rangePlus, 0), cPos.add(offset));
            }

            if (!origin.isSet() || !origin.vect[2].isSet()) {
                signals[4] = new LaserData(cPos.add(offset), cPos.add(offset).addVector(0, 0, rangeMinus));
                signals[5] = new LaserData(cPos.add(offset).addVector(0, 0, -rangePlus), cPos.add(offset));
            }
        }
    }

    @Override
    public void initialize() {
        super.initialize();

        updateSignals();

        if (initVectO != null) {
            origin = new Origin();

            origin.vectO = new TileWrapper(Utils.convertFloor(initVectO));

            for (int i = 0; i < 3; ++i) {
                if (initVect[i] != null) {
                    linkTo((TileMarker) worldObj.getTileEntity(Utils.convertFloor(initVect[i])), i);
                }
            }
        }
    }

    public void tryConnection() {
        if (worldObj.isRemote) {
            return;
        }

        for (int j = 0; j < 3; ++j) {
            if (!origin.isSet() || !origin.vect[j].isSet()) {
                setVect(j);
            }
        }

        sendNetworkUpdate();
    }

    void setVect(int n) {
        int[] coords = new int[3];

        coords[0] = pos.getX();
        coords[1] = pos.getY();
        coords[2] = pos.getZ();
        BlockPos coord = pos;

        if (!origin.isSet() || !origin.vect[n].isSet()) {
            for (int j = 1; j < DefaultProps.MARKER_RANGE; ++j) {
                coords[n] += j;
                coord = new BlockPos(coords[0], coords[1], coords[2]);

                TileEntity tile = worldObj.getTileEntity(coord);

                if (tile instanceof TileMarker) {
                    if (linkTo((TileMarker) tile, n)) {
                        break;
                    }
                }

                coords[n] -= j;
                coords[n] -= j;
                coord = new BlockPos(coords[0], coords[1], coords[2]);

                tile = worldObj.getTileEntity(coord);

                if (tile instanceof TileMarker) {
                    if (linkTo((TileMarker) tile, n)) {
                        break;
                    }
                }

                coords[n] += j;
            }
        }
    }

    private boolean linkTo(TileMarker marker, int n) {
        if (marker == null) {
            return false;
        }

        if (origin.isSet() && marker.origin.isSet()) {
            return false;
        }

        if (!origin.isSet() && !marker.origin.isSet()) {
            origin = new Origin();
            marker.origin = origin;
            origin.vectO = new TileWrapper(pos);
            origin.vect[n] = new TileWrapper(marker.pos);
        } else if (!origin.isSet()) {
            origin = marker.origin;
            origin.vect[n] = new TileWrapper(pos);
        } else {
            marker.origin = origin;
            origin.vect[n] = new TileWrapper(marker.pos);
        }

        origin.vectO.getMarker(worldObj).createLasers();
        updateSignals();
        marker.updateSignals();

        return true;
    }

    private void createLasers() {
        lasers = null;

        lasers = new LaserData[12];
        Origin o = origin;

        if (!origin.vect[0].isSet()) {
            o.xMin = origin.vectO.x;
            o.xMax = origin.vectO.x;
        } else if (origin.vect[0].x < pos.getX()) {
            o.xMin = origin.vect[0].x;
            o.xMax = pos.getX();
        } else {
            o.xMin = pos.getX();
            o.xMax = origin.vect[0].x;
        }

        if (!origin.vect[1].isSet()) {
            o.yMin = origin.vectO.y;
            o.yMax = origin.vectO.y;
        } else if (origin.vect[1].y < pos.getY()) {
            o.yMin = origin.vect[1].y;
            o.yMax = pos.getY();
        } else {
            o.yMin = pos.getY();
            o.yMax = origin.vect[1].y;
        }

        if (!origin.vect[2].isSet()) {
            o.zMin = origin.vectO.z;
            o.zMax = origin.vectO.z;
        } else if (origin.vect[2].z < pos.getZ()) {
            o.zMin = origin.vect[2].z;
            o.zMax = pos.getZ();
        } else {
            o.zMin = pos.getZ();
            o.zMax = origin.vect[2].z;
        }

        lasers = Utils.createLaserDataBox(o.xMin + 0.5, o.yMin + 0.5, o.zMin + 0.5, o.xMax + 0.5, o.yMax + 0.5, o.zMax + 0.5);
    }

    @Override
    public BlockPos min() {
        if (origin.isSet()) return origin.min();
        return pos;
    }

    @Override
    public BlockPos max() {
        if (origin.isSet()) return origin.max();
        return pos;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        destroy();
    }

    @Override
    public void destroy() {
        TileMarker markerOrigin = null;

        if (origin.isSet()) {
            markerOrigin = origin.vectO.getMarker(worldObj);

            Origin o = origin;

            if (markerOrigin != null && markerOrigin.lasers != null) {
                markerOrigin.lasers = null;
            }

            for (TileWrapper m : o.vect) {
                TileMarker mark = m.getMarker(worldObj);

                if (mark != null) {
                    mark.lasers = null;

                    if (mark != this) {
                        mark.origin = new Origin();
                    }
                }
            }

            if (markerOrigin != this && markerOrigin != null) {
                markerOrigin.origin = new Origin();
            }

            for (TileWrapper wrapper : o.vect) {
                TileMarker mark = wrapper.getMarker(worldObj);

                if (mark != null) {
                    mark.updateSignals();
                }
            }
            if (markerOrigin != null) {
                markerOrigin.updateSignals();
            }
        }

        signals = null;

        if (!worldObj.isRemote && markerOrigin != null && markerOrigin != this) {
            markerOrigin.sendNetworkUpdate();
        }
    }

    @Override
    public void removeFromWorld() {
        if (!origin.isSet()) {
            return;
        }

        Origin o = origin;

        for (TileWrapper m : o.vect.clone()) {
            if (m.isSet()) {
                IBlockState state = worldObj.getBlockState(m.getPos());
                worldObj.setBlockToAir(m.getPos());
                BuildCraftCore.markerBlock.dropBlockAsItem(worldObj, m.getPos(), state, 0);
            }
        }

        IBlockState state = worldObj.getBlockState(o.vectO.getPos());
        worldObj.setBlockToAir(o.vectO.getPos());

        BuildCraftCore.markerBlock.dropBlockAsItem(worldObj, o.vectO.getPos(), state, 0);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);

        if (nbttagcompound.hasKey("vectO")) {
            initVectO = NBTUtils.readVec3d(nbttagcompound, "vectO");
            initVect = new Vec3d[3];

            for (int i = 0; i < 3; ++i) {
                if (nbttagcompound.hasKey("vect" + i)) {
                    initVect[i] = NBTUtils.readVec3d(nbttagcompound, "vect" + i);
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        if (origin.isSet() && origin.vectO.getMarker(worldObj) == this) {
            nbt.setTag("vectO", NBTUtils.writeVec3d(Utils.convert(origin.vectO.getMarker(worldObj).pos)));

            for (int i = 0; i < 3; ++i) {
                if (origin.vect[i].isSet()) {
                    nbt.setTag("vect" + i, NBTUtils.writeVec3d(Utils.convert(origin.vect[i].getPos())));
                }
            }
        }
    }

    @Override
    public void writeData(ByteBuf stream) {
        origin.writeData(stream);
        stream.writeBoolean(showSignals);
    }

    @Override
    public void readData(ByteBuf stream) {
        origin.readData(stream);
        showSignals = stream.readBoolean();

        switchSignals();

        if (origin.vectO.isSet() && origin.vectO.getMarker(worldObj) != null) {
            origin.vectO.getMarker(worldObj).updateSignals();

            for (TileWrapper w : origin.vect) {
                TileMarker m = w.getMarker(worldObj);

                if (m != null) {
                    m.updateSignals();
                }
            }
        }

        createLasers();

    }

    @Override
    public boolean isValidFromLocation(BlockPos pos) {
        // Rules:
        // - one or two, but not three, of the coordinates must be equal to the marker's location
        // - one of the coordinates must be either -1 or 1 away
        // - it must be physically touching the box
        // - however, it cannot be INSIDE the box
        // int equal = (pos.getX() == this.pos.getX() ? 1 : 0) + (pos.getY() == this.pos.getY() ? 1 : 0) + (pos.getZ()
        // == this.pos.getZ() ? 1 : 0);
        // int touching = 0;
        //
        // if (equal == 0 || equal == 3) {
        // return false;
        // }

        Box box = new Box(min(), max());

        if (box.contains(pos)) return false;
        return box.distanceToSquared(pos) == 1;
    }
}
