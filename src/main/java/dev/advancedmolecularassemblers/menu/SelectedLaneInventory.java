package dev.advancedmolecularassemblers.menu;

import appeng.api.inventories.BaseInternalInventory;
import appeng.api.stacks.AEItemKey;
import dev.advancedmolecularassemblers.machine.ParallelMolecularAssemblerBlockEntity;
import net.minecraft.world.item.ItemStack;

final class SelectedLaneInventory extends BaseInternalInventory {
    private final ParallelMolecularAssemblerBlockEntity host;
    private final ParallelMolecularAssemblerMenu menu;

    SelectedLaneInventory(ParallelMolecularAssemblerBlockEntity host, ParallelMolecularAssemblerMenu menu) {
        this.host = host;
        this.menu = menu;
    }

    private appeng.api.inventories.InternalInventory delegate() {
        int lane = Math.max(0, Math.min(menu.getSelectedLane(), host.getLaneCount() - 1));
        return host.getLaneInventory(lane);
    }

    @Override
    public int size() {
        return 11;
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        return delegate().getStackInSlot(slotIndex);
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        delegate().setItemDirect(slotIndex, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate().getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == 10) {
            return true;
        }
        if (slot < 0 || slot >= 9 || stack.isEmpty()) {
            return false;
        }
        var pattern = host.getCurrentPattern(menu.getSelectedLane());
        return pattern != null && pattern.isItemValid(slot, AEItemKey.of(stack), host.getLevel());
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) {
            return stack;
        }
        return delegate().insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return delegate().extractItem(slot, amount, simulate);
    }
}
