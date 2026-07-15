package dev.advancedmolecularassemblers.menu;

import appeng.client.Point;
import appeng.api.stacks.AEItemKey;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.IOptionalSlot;
import appeng.menu.slot.OutputSlot;
import appeng.menu.slot.RestrictedInputSlot;
import dev.advancedmolecularassemblers.AdvancedMolecularAssemblers;
import dev.advancedmolecularassemblers.machine.ParallelMolecularAssemblerBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ParallelMolecularAssemblerMenu extends UpgradeableMenu<ParallelMolecularAssemblerBlockEntity>
        implements IProgressProvider {
    public static final int PREVIOUS_LANE_BUTTON = 0;
    public static final int NEXT_LANE_BUTTON = 1;

    public static final MenuType<ParallelMolecularAssemblerMenu> TYPE = MenuTypeBuilder
            .create(ParallelMolecularAssemblerMenu::new, ParallelMolecularAssemblerBlockEntity.class)
            .buildUnregistered(AdvancedMolecularAssemblers.id("parallel_molecular_assembler"));

    @GuiSync(4)
    private int craftProgress;
    @GuiSync(7)
    private int selectedLane;
    @GuiSync(8)
    private int activeLanes;
    @GuiSync(9)
    private int laneCount;

    private SelectedLaneInventory selectedInventory;
    private Slot encodedPatternSlot;

    public ParallelMolecularAssemblerMenu(int id, Inventory playerInventory,
            ParallelMolecularAssemblerBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        laneCount = host.getLaneCount();
    }

    @Override
    protected void setupInventorySlots() {
        selectedInventory = new SelectedLaneInventory(getHost(), this);
        for (int slot = 0; slot < 9; slot++) {
            addSlot(new ManualCraftingSlot(this, selectedInventory, slot), SlotSemantics.MACHINE_CRAFTING_GRID);
        }
        encodedPatternSlot = addSlot(new RestrictedInputSlot(
                RestrictedInputSlot.PlacableItemType.MOLECULAR_ASSEMBLER_PATTERN,
                selectedInventory, 10), SlotSemantics.ENCODED_PATTERN);
        addSlot(new OutputSlot(selectedInventory, 9, null), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            laneCount = getHost().getLaneCount();
            selectedLane = Math.max(0, Math.min(selectedLane, laneCount - 1));
            craftProgress = getHost().getCraftingProgress(selectedLane);
            activeLanes = getHost().getActiveLaneCount();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == PREVIOUS_LANE_BUTTON) {
            selectedLane = Math.max(0, selectedLane - 1);
            refreshSelectedLane();
            return true;
        }
        if (id == NEXT_LANE_BUTTON) {
            selectedLane = Math.min(getHost().getLaneCount() - 1, selectedLane + 1);
            refreshSelectedLane();
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    private void refreshSelectedLane() {
        if (isServerSide()) {
            craftProgress = getHost().getCraftingProgress(selectedLane);
            activeLanes = getHost().getActiveLaneCount();
            sendAllDataToRemote();
        }
        for (Slot slot : slots) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.resetCachedValidation();
            }
        }
    }

    public int getSelectedLane() {
        return selectedLane;
    }

    public int getActiveLanes() {
        return activeLanes;
    }

    public int getLaneCount() {
        return laneCount > 0 ? laneCount : getHost().getLaneCount();
    }

    public boolean isValidItemForSlot(int slotIndex, ItemStack stack) {
        var pattern = getHost().getCurrentPattern(selectedLane);
        return pattern != null && pattern.isItemValid(slotIndex, AEItemKey.of(stack), getHost().getLevel());
    }

    @Override
    public void onSlotChange(Slot changedSlot) {
        if (changedSlot == encodedPatternSlot) {
            for (Slot slot : slots) {
                if (slot != changedSlot && slot instanceof AppEngSlot appEngSlot) {
                    appEngSlot.resetCachedValidation();
                }
            }
        }
    }

    @Override
    public int getCurrentProgress() {
        return craftProgress;
    }

    @Override
    public int getMaxProgress() {
        return 100;
    }

    private static final class ManualCraftingSlot extends AppEngSlot implements IOptionalSlot {
        private final ParallelMolecularAssemblerMenu menu;

        private ManualCraftingSlot(ParallelMolecularAssemblerMenu menu, SelectedLaneInventory inventory, int slot) {
            super(inventory, slot);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return super.mayPlace(stack) && menu.isValidItemForSlot(getSlotIndex(), stack);
        }

        @Override
        protected boolean getCurrentValidationState() {
            ItemStack stack = getItem();
            return stack.isEmpty() || mayPlace(stack);
        }

        @Override
        public boolean isRenderDisabled() {
            return true;
        }

        @Override
        public boolean isSlotEnabled() {
            int slotIndex = getSlotIndex();
            if (!getInventory().getStackInSlot(slotIndex).isEmpty()) {
                return true;
            }
            var pattern = menu.getHost().getCurrentPattern(menu.getSelectedLane());
            return slotIndex >= 0 && slotIndex < 9 && pattern != null && pattern.isSlotEnabled(slotIndex);
        }

        @Override
        public Point getBackgroundPos() {
            return new Point(x - 1, y - 1);
        }
    }
}
