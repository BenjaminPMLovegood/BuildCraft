/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt */
package buildcraft.transport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import com.google.common.base.Strings;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockQuartz;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.BuildCraftCore;
import buildcraft.BuildCraftTransport;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.JavaTools;
import buildcraft.api.facades.FacadeType;
import buildcraft.api.facades.IFacadeItem;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.transport.IPipe;
import buildcraft.api.transport.PipeWire;
import buildcraft.api.transport.pluggable.IPipePluggableItem;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.core.BCCreativeTab;
import buildcraft.core.lib.items.ItemBuildCraft;
import buildcraft.core.lib.utils.BCStringUtils;
import buildcraft.core.lib.utils.Utils;
import buildcraft.core.proxy.CoreProxy;

public class ItemFacade extends ItemBuildCraft implements IFacadeItem, IPipePluggableItem {
    public static class FacadeState {
        public final IBlockState state;
        public final boolean transparent;
        public final boolean hollow;
        public final PipeWire wire;

        public FacadeState(IBlockState state, PipeWire wire) {
            this.state = state;
            this.wire = wire;
            this.transparent = false;
            this.hollow = false;
        }

        public FacadeState(IBlockState state, PipeWire wire, boolean hollow) {
            this.state = state;
            this.wire = wire;
            this.transparent = false;
            this.hollow = hollow;
        }

        public FacadeState(NBTTagCompound nbt) {
            String key = nbt.getString("block");
            Block block = (Block) Block.blockRegistry.getObject(new ResourceLocation(key));
            if (block == null) throw new NullPointerException("Could not load a block from the key \"" + key + "\"");
            int metadata = nbt.getByte("metadata");
            state = block.getStateFromMeta(metadata);
            this.wire = nbt.hasKey("wire") ? PipeWire.fromOrdinal(nbt.getByte("wire")) : null;
            this.transparent = nbt.hasKey("transparent") && nbt.getBoolean("transparent");
            this.hollow = nbt.hasKey("hollow") && nbt.getBoolean("hollow");
        }

        private FacadeState(PipeWire wire) {
            state = Blocks.bedrock.getDefaultState();
            this.wire = wire;
            this.transparent = true;
            this.hollow = false;
        }

        public static FacadeState create(IBlockState state) {
            return create(state, null);
        }

        public static FacadeState create(IBlockState state, PipeWire wire) {
            return new FacadeState(state, wire);
        }

        public static FacadeState createTransparent(PipeWire wire) {
            return new FacadeState(wire);
        }

        public void writeToNBT(NBTTagCompound nbt) {
            if (state != null) {
                nbt.setString("block", Utils.getNameForBlock(state.getBlock()));
                nbt.setByte("metadata", (byte) state.getBlock().getMetaFromState(state));
            }
            if (wire != null) {
                nbt.setByte("wire", (byte) wire.ordinal());
            }
            nbt.setBoolean("transparent", transparent);
            nbt.setBoolean("hollow", hollow);
        }

        public static NBTTagList writeArray(FacadeState[] states) {
            if (states == null) {
                return null;
            }
            NBTTagList list = new NBTTagList();
            for (FacadeState state : states) {
                NBTTagCompound stateNBT = new NBTTagCompound();
                state.writeToNBT(stateNBT);
                list.appendTag(stateNBT);
            }
            return list;
        }

        public static FacadeState[] readArray(NBTTagList list) {
            if (list == null) {
                return new FacadeState[0];
            }
            final int length = list.tagCount();
            FacadeState[] states = new FacadeState[length];
            for (int i = 0; i < length; i++) {
                states[i] = new FacadeState(list.getCompoundTagAt(i));
            }
            return states;
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(hollow).append(transparent).append(state).append(wire).build();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            FacadeState other = (FacadeState) obj;
            if (hollow != other.hollow) return false;
            if (state == null) {
                if (other.state != null) return false;
            } else if (!state.equals(other.state)) return false;
            if (transparent != other.transparent) return false;
            if (wire != other.wire) return false;
            return true;
        }
    }

    public static final ArrayList<ItemStack> allFacades = new ArrayList<>();
    public static final ArrayList<ItemStack> allHollowFacades = new ArrayList<>();
    public static final ArrayList<String> allFacadeIDs = new ArrayList<>();
    public static final ArrayList<String> blacklistedFacades = new ArrayList<>();

    private static final Block NULL_BLOCK = null;
    private static final ItemStack NO_MATCH = new ItemStack(NULL_BLOCK, 0, 0);

    private static final Block[] PREVIEW_FACADES = new Block[] { Blocks.planks, Blocks.stonebrick, Blocks.glass };
    private static int RANDOM_FACADE_ID = -1;

    public ItemFacade() {
        super(BuildCraftTransport.showAllFacadesCreative ? BCCreativeTab.get("facades") : BCCreativeTab.get("main"));

        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public String getItemStackDisplayName(ItemStack itemstack) {
        switch (getFacadeType(itemstack)) {
            case Basic:
                FacadeState[] states = getFacadeStates(itemstack);
                String displayName = states.length > 0 ? getFacadeStateDisplayName(states[0]) : "CORRUPT";
                return super.getItemStackDisplayName(itemstack) + ": " + displayName;
            case Phased:
                return BCStringUtils.localize("item.FacadePhased.name");
            default:
                return "";
        }
    }

    @Override
    public String getUnlocalizedName(ItemStack itemstack) {
        return "item.Facade";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean debug) {
        for (FacadeState state : getFacadeStates(stack)) {
            if (state != null && !state.transparent && state.state != null && Item.getItemFromBlock(state.state.getBlock()) != null) {
                Item.getItemFromBlock(state.state.getBlock()).addInformation(new ItemStack(state.state.getBlock(), 1, state.state.getBlock()
                        .getMetaFromState(state.state)), player, list, debug);
            }
        }
        if (getFacadeType(stack) == FacadeType.Phased) {
            String stateString = BCStringUtils.localize("item.FacadePhased.state");
            FacadeState defaultState = null;
            for (FacadeState state : getFacadeStates(stack)) {
                if (state.wire == null) {
                    defaultState = state;
                    continue;
                }
                list.add(String.format(stateString, state.wire.getColor(), getFacadeStateDisplayName(state)));
            }
            if (defaultState != null) {
                list.add(1, String.format(BCStringUtils.localize("item.FacadePhased.state_default"), getFacadeStateDisplayName(defaultState)));
            }
        }
    }

    public static String getFacadeStateDisplayName(FacadeState state) {
        if (state.state == null) {
            return BCStringUtils.localize("item.FacadePhased.state_transparent");
        }
        // if (state.state.getBlock().getRenderType() == 31) {
        // TODO: Find out what render type is 31... and what this now means
        // meta &= 0x3;
        // } else if (state.block.getRenderType() == 39 && meta > 2) {
        // meta = 2;
        // }
        String s = CoreProxy.proxy.getItemDisplayName(new ItemStack(state.state.getBlock(), 1, state.state.getBlock().getMetaFromState(state.state)));
        if (state.hollow) {
            s += " (" + BCStringUtils.localize("item.Facade.state_hollow") + ")";
        }
        return s;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs par2CreativeTabs, List itemList) {
        if (BuildCraftTransport.showAllFacadesCreative) {
            for (ItemStack stack : allFacades) {
                itemList.add(stack);
            }
            for (ItemStack stack : allHollowFacades) {
                itemList.add(stack);
            }
        } else {
            List<ItemStack> hollowFacades = new ArrayList<>();
            for (Block b : PREVIEW_FACADES) {
                if (isBlockValidForFacade(b.getStateFromMeta(0)) && !isBlockBlacklisted(b)) {
                    ItemStack facade = getFacadeForBlock(b.getStateFromMeta(0));
                    itemList.add(facade);
                    FacadeState state = getFacadeStates(facade)[0];
                    hollowFacades.add(getFacade(new FacadeState(state.state, state.wire, true)));
                }
            }
            if (RANDOM_FACADE_ID < 0) {
                RANDOM_FACADE_ID = BuildCraftCore.random.nextInt(allFacades.size());
            }
            itemList.add(allFacades.get(RANDOM_FACADE_ID));
            itemList.addAll(hollowFacades);
            itemList.add(allHollowFacades.get(RANDOM_FACADE_ID));
        }
    }

    public void initialize() {
        for (Object o : Block.blockRegistry) {
            Block b = (Block) o;

            Item item = Item.getItemFromBlock(b);

            if (item == null) {
                continue;
            }

            if (isBlockBlacklisted(b)) {
                continue;
            }

            registerValidFacades(b, item);
        }
    }

    private void registerValidFacades(Block block, Item item) {
        ArrayList<ItemStack> stacks = new ArrayList<>(16);
        HashSet<IBlockState> states = new HashSet<>();
        try {
            if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
                for (CreativeTabs ct : item.getCreativeTabs()) {
                    block.getSubBlocks(item, ct, stacks);
                }
            } else {
                for (int i = 0; i < 16; i++) {
                    try {
                        IBlockState state = block.getStateFromMeta(i);
                        if (state != null && !states.contains(state)) {
                            states.add(state);
                            stacks.add(new ItemStack(item, 1, i));
                        }
                    } catch (Exception e) {

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (ItemStack stack : stacks) {
            try {
                if (block.hasTileEntity(block.getDefaultState())) continue;

                // Check if all of these functions work correctly.
                // If an exception is fired, or null is returned, this generally means that
                // this block is invalid.
                try {
                    if (stack.getDisplayName() == null || Strings.isNullOrEmpty(stack.getUnlocalizedName())) continue;
                } catch (Throwable t) {
                    continue;
                }

                addFacade(stack);
            } catch (IndexOutOfBoundsException e) {

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private static boolean isBlockBlacklisted(Block block) {
        String blockName = Utils.getNameForBlock(block);

        if (blockName == null) return true;

        // Blocks blacklisted by mods should always be treated as blacklisted
        for (String blacklistedBlock : blacklistedFacades)
            if (blockName.equals(blacklistedBlock)) return true;

        // Blocks blacklisted by config should depend on the config settings
        for (String blacklistedBlock : BuildCraftTransport.facadeBlacklist) {
            if (blockName.equals(JavaTools.stripSurroundingQuotes(blacklistedBlock))) return true
                ^ BuildCraftTransport.facadeTreatBlacklistAsWhitelist;
        }

        return false ^ BuildCraftTransport.facadeTreatBlacklistAsWhitelist;
    }

    public static boolean isTransparentFacade(IBlockState state) {
        Block block = state.getBlock();
        return !block.isVisuallyOpaque() && !block.isOpaqueCube();
    }

    private static boolean isBlockValidForFacade(IBlockState state) {
        try {
            Block block = state.getBlock();

            // HACK: Improved facade validation will only come in 8.0, so for now we simply
            // whitelist stained glass to keep compatibility.
            if (block instanceof BlockStainedGlass) {
                return true;
            }

            if (!block.isFullBlock() || !block.isFullCube() || block.hasTileEntity(state)) return false;
            if (block.getBlockBoundsMinX() != 0.0 || block.getBlockBoundsMinY() != 0.0 || block.getBlockBoundsMinZ() != 0.0) return false;
            if (block.getBlockBoundsMaxX() != 1.0 || block.getBlockBoundsMaxY() != 1.0 || block.getBlockBoundsMaxZ() != 1.0) return false;

            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static FacadeState[] getFacadeStates(ItemStack stack) {
        if (!stack.hasTagCompound()) return new FacadeState[0];
        NBTTagCompound nbt = stack.getTagCompound();
        nbt = migrate(stack, nbt);
        if (!nbt.hasKey("states")) return new FacadeState[0];
        return FacadeState.readArray(nbt.getTagList("states", Constants.NBT.TAG_COMPOUND));
    }

    private static NBTTagCompound migrate(ItemStack stack, NBTTagCompound nbt) {
        Block block = null, blockAlt = null;
        int metadata = 0, metadataAlt;
        PipeWire wire = null;
        if (nbt.hasKey("id")) block = Block.blockRegistry.getObjectById(nbt.getInteger("id"));
        else if (nbt.hasKey("name")) block = Block.blockRegistry.getObject(new ResourceLocation(nbt.getString("name")));
        if (nbt.hasKey("name_alt")) blockAlt = Block.blockRegistry.getObject(new ResourceLocation(nbt.getString("name_alt")));
        if (nbt.hasKey("meta")) metadata = nbt.getInteger("meta");
        if (nbt.hasKey("meta_alt")) metadataAlt = nbt.getInteger("meta_alt");
        else metadataAlt = stack.getItemDamage() & 0x0000F;
        if (nbt.hasKey("wire")) wire = PipeWire.fromOrdinal(nbt.getInteger("wire"));
        if (block != null) {
            FacadeState[] states;
            FacadeState mainState = FacadeState.create(block.getStateFromMeta(metadata));
            if (blockAlt != null && wire != null) {
                FacadeState altState = FacadeState.create(blockAlt.getStateFromMeta(metadataAlt), wire);
                states = new FacadeState[] { mainState, altState };
            } else {
                states = new FacadeState[] { mainState };
            }
            NBTTagCompound newNbt = getFacade(states).getTagCompound();
            stack.setTagCompound(newNbt);
            return newNbt;
        }
        return nbt;
    }

    @Override
    public IBlockState[] getBlockStatesForFacade(ItemStack stack) {
        FacadeState[] states = getFacadeStates(stack);
        IBlockState[] blocks = new IBlockState[states.length];
        for (int i = 0; i < states.length; i++) {
            blocks[i] = states[i].state;
        }
        return blocks;
    }

    // GETTERS FOR FACADE DATA
    @Override
    public FacadeType getFacadeType(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return FacadeType.Basic;
        }
        NBTTagCompound nbt = stack.getTagCompound();
        if (!nbt.hasKey("type")) {
            return FacadeType.Basic;
        }
        return FacadeType.fromOrdinal(nbt.getInteger("type"));
    }

    @Override
    public boolean doesSneakBypassUse(World world, BlockPos pos, EntityPlayer player) {
        // Simply send shift click to the pipe / mod block.
        return true;
    }

    public void addFacade(ItemStack itemStack) {
        if (itemStack.stackSize == 0) itemStack.stackSize = 1;

        Block block = Block.getBlockFromItem(itemStack.getItem());
        if (block == null) return;

        IBlockState bstate = block.getStateFromMeta(itemStack.getItemDamage());
        if (!isBlockValidForFacade(bstate)) {
            return;
        }

        String recipeId = "buildcraft:facade{" + Utils.getNameForBlock(block) + "#" + itemStack.getItemDamage() + "}";

        ItemStack facade = getFacadeForBlock(bstate);
        ItemStack realStack = itemStack.copy();
        realStack.setItemDamage(block.damageDropped(bstate));

        if (!allFacadeIDs.contains(recipeId)) {
            allFacadeIDs.add(recipeId);
            allFacades.add(facade);

            ItemStack facade6 = facade.copy();
            facade6.stackSize = 6;

            FacadeState state = getFacadeStates(facade6)[0];
            ItemStack facadeHollow = getFacade(new FacadeState(state.state, state.wire, true));

            allHollowFacades.add(facadeHollow);

            ItemStack facade6Hollow = facadeHollow.copy();
            facade6Hollow.stackSize = 6;

            // 3 Structurepipes + this block makes 6 facades
            if (Loader.isModLoaded("BuildCraft|Silicon") && !BuildCraftTransport.facadeForceNonLaserRecipe) {
                BuildcraftRecipeRegistry.assemblyTable.addRecipe(recipeId, 8000, facade6, new ItemStack(BuildCraftTransport.pipeStructureCobblestone,
                        3), itemStack);

                BuildcraftRecipeRegistry.assemblyTable.addRecipe(recipeId + ":hollow", 8000, facade6Hollow, new ItemStack(
                        BuildCraftTransport.pipeStructureCobblestone, 3), itemStack);

                BuildcraftRecipeRegistry.assemblyTable.addRecipe(recipeId + ":toHollow", 160, facadeHollow, facade);
                BuildcraftRecipeRegistry.assemblyTable.addRecipe(recipeId + ":fromHollow", 160, facade, facadeHollow);
            } else {
                GameRegistry.addShapedRecipe(facade6, "t ", "ts", "t ", 't', realStack, 's', BuildCraftTransport.pipeStructureCobblestone);
                GameRegistry.addShapedRecipe(facade6Hollow, "t ", " s", "t ", 't', realStack, 's', BuildCraftTransport.pipeStructureCobblestone);
            }
        }
    }

    public static void blacklistFacade(String blockName) {
        if (!blacklistedFacades.contains(blockName)) blacklistedFacades.add(blockName);
    }

    public class FacadeRecipe implements IRecipe {

        @Override
        public boolean matches(InventoryCrafting inventorycrafting, World world) {
            Object[] facade = getFacadeBlockFromCraftingGrid(inventorycrafting);

            return facade != null && facade[0] != null && ((IBlockState[]) facade[0]).length == 1;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inventorycrafting) {
            Object[] facade = getFacadeBlockFromCraftingGrid(inventorycrafting);
            if (facade == null || ((IBlockState[]) facade[0]).length != 1) return null;

            IBlockState block = ((IBlockState[]) facade[0])[0];
            ItemStack originalFacade = (ItemStack) facade[1];

            if (block == null) return null;

            return getNextFacadeItemStack(block, originalFacade);
        }

        private Object[] getFacadeBlockFromCraftingGrid(InventoryCrafting inventorycrafting) {
            ItemStack slotmatch = null;
            int countOfItems = 0;
            for (int i = 0; i < inventorycrafting.getSizeInventory(); i++) {
                ItemStack slot = inventorycrafting.getStackInSlot(i);

                if (slot != null && slot.getItem() == ItemFacade.this && slotmatch == null) {
                    slotmatch = slot;
                    countOfItems++;
                } else if (slot != null) slotmatch = NO_MATCH;

                if (countOfItems > 1) return null;
            }

            if (slotmatch != null && slotmatch != NO_MATCH) return new Object[] { getBlockStatesForFacade(slotmatch), slotmatch };

            return null;
        }

        private ItemStack getNextFacadeItemStack(IBlockState state, ItemStack originalFacade) {
            // TODO: Add an API for me!
            IBlockState newState = state;
            if (newState.getPropertyNames().contains(BlockQuartz.VARIANT)) {
                BlockQuartz.EnumType type = newState.getValue(BlockQuartz.VARIANT);
                if ("lines".equals(type.toString())) {
                    newState = newState.withProperty(BlockQuartz.VARIANT, BlockQuartz.EnumType.byMetadata(((type.getMetadata() - 1) % 3) + 2));
                }
            } else {
                for (net.minecraft.block.properties.IProperty<?> prop : state.getProperties().keySet()) {
                    if (prop.getName().equals("axis")) {
                        newState = newState.cycleProperty(prop);
                    }
                }
            }

            return getFacadeForBlock(newState);
        }

        @Override
        public int getRecipeSize() {
            return 1;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return null;
        }

        @Override
        public ItemStack[] getRemainingItems(InventoryCrafting inv) {
            ItemStack[] itemStack = new ItemStack[inv.getSizeInventory()];

            for (int i = 0; i < itemStack.length; ++i) {
                ItemStack itemstack = inv.getStackInSlot(i);
                itemStack[i] = ForgeHooks.getContainerItem(itemstack);
            }

            return itemStack;
        }
    }

    @Override
    public ItemStack getFacadeForBlock(IBlockState state) {
        return getFacade(FacadeState.create(state));
    }

    public static ItemStack getAdvancedFacade(PipeWire wire, IBlockState state, IBlockState stateAlt) {
        return getFacade(FacadeState.create(state), FacadeState.create(stateAlt, wire));
    }

    public static ItemStack getFacade(FacadeState... states) {
        if (states == null || states.length == 0) return null;
        final boolean basic = states.length == 1 && states[0].wire == null;

        ItemStack stack = new ItemStack(BuildCraftTransport.facadeItem, 1, 0);

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setByte("type", (byte) (basic ? FacadeType.Basic : FacadeType.Phased).ordinal());
        nbt.setTag("states", FacadeState.writeArray(states));

        stack.setTagCompound(nbt);
        return stack;
    }

    @Override
    public PipePluggable createPipePluggable(IPipe pipe, EnumFacing side, ItemStack stack) {
        return new FacadePluggable(getFacadeStates(stack));
    }
}
