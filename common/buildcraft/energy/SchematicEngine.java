/**
 * Copyright (c) 2011-2014, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.energy;

import java.util.LinkedList;

import net.minecraft.item.ItemStack;

import net.minecraftforge.common.util.ForgeDirection;

import buildcraft.api.blueprints.IBuilderContext;
import buildcraft.api.blueprints.SchematicTile;

public class SchematicEngine extends SchematicTile {

	@Override
	public void rotateLeft(IBuilderContext context) {
		int o = cpt.getInteger("orientation");

		o = ForgeDirection.values()[o].getRotation(ForgeDirection.UP).ordinal();

		cpt.setInteger("orientation", o);
	}

	@Override
	public void writeToSchematic(IBuilderContext context, int x, int y, int z) {
		super.writeToSchematic(context, x, y, z);

		TileEngine engine = (TileEngine) context.world().getTileEntity(x, y, z);

		cpt.setInteger("orientation", engine.orientation.ordinal());
		cpt.removeTag("progress");
		cpt.removeTag("energy");
		cpt.removeTag("heat");
	}

	@Override
	public void writeToWorld(IBuilderContext context, int x, int y, int z, LinkedList<ItemStack> stacks) {
		super.writeToWorld(context, x, y, z, stacks);

		TileEngine engine = (TileEngine) context.world().getTileEntity(x, y, z);

		engine.orientation = ForgeDirection.getOrientation(cpt.getInteger("orientation"));
		engine.sendNetworkUpdate();
	}

	@Override
	public void postProcessing (IBuilderContext context, int x, int y, int z) {
		TileEngine engine = (TileEngine) context.world().getTileEntity(x, y, z);

		if (engine != null) {
			engine.orientation = ForgeDirection.getOrientation(cpt.getInteger("orientation"));
			engine.sendNetworkUpdate();
			context.world().markBlockForUpdate(x, y, z);
			context.world().notifyBlocksOfNeighborChange(x, y, z, block);
		}
	}

	@Override
	public BuildingStage getBuildStage() {
		return BuildingStage.STANDALONE;
	}

}
