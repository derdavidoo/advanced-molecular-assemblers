package dev.advancedmolecularassemblers.machine;

import appeng.api.inventories.BaseInternalInventory;
import net.minecraft.world.item.ItemStack;

final class OutputOnlyInventory extends BaseInternalInventory {
    private final CraftingLane[] lanes;

    OutputOnlyInventory(CraftingLane[] lanes) {
        this.lanes = lanes;
    }

    @Override
    public int size() {
        return lanes.length;
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        return lanes[slotIndex].inventory().getStackInSlot(CraftingLane.OUTPUT_SLOT);
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        lanes[slotIndex].inventory().setItemDirect(CraftingLane.OUTPUT_SLOT, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return lanes[slot].inventory().extractItem(CraftingLane.OUTPUT_SLOT, amount, simulate);
    }
}
