package buildcraft.core;

import buildcraft.api.tiles.IDebuggable;
import buildcraft.core.lib.items.ItemBuildCraft;
import buildcraft.core.lib.utils.BCStringUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class ItemDebugger extends ItemBuildCraft {
    public ItemDebugger() {
        super();

        setFull3D();
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof IDebuggable) {
            ArrayList<String> info = new ArrayList<>();
            String effSide = FMLCommonHandler.instance().getEffectiveSide().name().substring(0, 1) + ":";
            ((IDebuggable) tile).getDebugInfo(info, info, side);
            for (String s : info) {
                player.addChatComponentMessage(new ChatComponentText(effSide + s));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean doesSneakBypassUse(World world, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean debug) {
        list.add(BCStringUtils.localize("item.debugger.warning"));
    }
}
