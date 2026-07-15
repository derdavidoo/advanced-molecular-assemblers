package dev.advancedmolecularassemblers.machine;

import java.util.Objects;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import appeng.menu.AutoCraftingMenu;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

final class CraftingLane {
    static final int INPUT_SLOTS = 9;
    static final int OUTPUT_SLOT = 9;
    static final int SLOT_COUNT = 10;
    static final int QUEUE_CAPACITY = 2;
    private static final int MAX_CRAFTS_PER_TICK = 2;

    private final ParallelMolecularAssemblerBlockEntity host;
    private final AppEngInternalInventory inventory;
    private final AppEngInternalInventory patternInventory;
    private final AppEngInternalInventory queuedInventory;
    private final InternalInventory menuInventory;
    private final CraftingContainer craftingGrid;

    private Direction pushDirection;
    private ItemStack serializedPattern = ItemStack.EMPTY;
    private IMolecularAssemblerSupportedPattern plan;
    private double progress;
    private boolean forcedPlan;
    private boolean awake;
    private boolean reboot = true;
    private final ItemStack[] queuedPatterns = new ItemStack[QUEUE_CAPACITY];
    private final Direction[] queuedDirections = new Direction[QUEUE_CAPACITY];
    private final IMolecularAssemblerSupportedPattern[] queuedPlans =
            new IMolecularAssemblerSupportedPattern[QUEUE_CAPACITY];
    private int queuedCrafts;

    CraftingLane(ParallelMolecularAssemblerBlockEntity host) {
        this.host = host;
        this.inventory = new AppEngInternalInventory(host, SLOT_COUNT, 1);
        this.patternInventory = new AppEngInternalInventory(host, 1, 1);
        this.queuedInventory = new AppEngInternalInventory(host, QUEUE_CAPACITY * SLOT_COUNT, 1);
        this.menuInventory = new CombinedInternalInventory(inventory, patternInventory);
        this.craftingGrid = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        java.util.Arrays.fill(queuedPatterns, ItemStack.EMPTY);
    }

    AppEngInternalInventory inventory() {
        return inventory;
    }

    AppEngInternalInventory patternInventory() {
        return patternInventory;
    }

    AppEngInternalInventory queuedInventory() {
        return queuedInventory;
    }

    InternalInventory menuInventory() {
        return menuInventory;
    }

    boolean ownsInventory(AppEngInternalInventory changedInventory) {
        return inventory == changedInventory || patternInventory == changedInventory;
    }

    boolean isAwake() {
        return awake;
    }

    int progress() {
        // Provider jobs may retain sub-craft progress credit to provide smooth
        // throughput above five cards. Do not show that credit while the lane is
        // idle; it becomes visible again when the next job is accepted.
        return plan == null ? 0 : (int) progress;
    }

    IMolecularAssemblerSupportedPattern currentPattern() {
        if (host.getLevel() != null && host.getLevel().isClientSide()) {
            var decoded = PatternDetailsHelper.decodePattern(patternInventory.getStackInSlot(0), host.getLevel());
            return decoded instanceof IMolecularAssemblerSupportedPattern supportedPattern ? supportedPattern : null;
        }
        return plan;
    }

    boolean tryAccept(IPatternDetails details, KeyCounter[] inputs, Direction providerSide) {
        if (!patternInventory.isEmpty()) {
            return false;
        }
        if (!(details instanceof IMolecularAssemblerSupportedPattern supportedPattern)) {
            return false;
        }

        if (plan == null && inventory.isEmpty() && queuedCrafts > 0) {
            promoteQueuedCraft();
        }

        if (plan != null || !serializedPattern.isEmpty() || !inventory.isEmpty()) {
            return enqueue(supportedPattern, inputs, providerSide);
        }

        forcedPlan = true;
        plan = supportedPattern;
        pushDirection = providerSide;
        fillGrid(inputs, supportedPattern);
        updateAwake();
        host.saveChanges();
        return true;
    }

    private boolean enqueue(IMolecularAssemblerSupportedPattern supportedPattern, KeyCounter[] inputs,
            Direction providerSide) {
        if (queuedCrafts >= QUEUE_CAPACITY) {
            return false;
        }

        int queueIndex = queuedCrafts;
        int slotOffset = queueIndex * SLOT_COUNT;
        supportedPattern.fillCraftingGrid(inputs,
                (slot, stack) -> queuedInventory.setItemDirect(slotOffset + slot, stack));
        ensureInputsConsumed(inputs);

        queuedPatterns[queueIndex] = supportedPattern.getDefinition().toStack();
        queuedDirections[queueIndex] = providerSide;
        queuedPlans[queueIndex] = supportedPattern;
        queuedCrafts++;
        updateAwake();
        host.saveChanges();
        return true;
    }

    private void fillGrid(KeyCounter[] inputs, IMolecularAssemblerSupportedPattern supportedPattern) {
        supportedPattern.fillCraftingGrid(inputs, inventory::setItemDirect);
        ensureInputsConsumed(inputs);
    }

    private static void ensureInputsConsumed(KeyCounter[] inputs) {
        for (var input : inputs) {
            input.removeZeros();
            if (!input.isEmpty()) {
                throw new IllegalStateException("Could not fill parallel assembler grid with " + input.iterator().next());
            }
        }
    }

    TickResult tick(int accelerationCards, int ticksSinceLastCall) {
        if (plan == null && inventory.isEmpty() && queuedCrafts > 0) {
            promoteQueuedCraft();
        }

        if (!inventory.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
            pushOut(inventory.getStackInSlot(OUTPUT_SLOT));
            ejectInvalidInputs();
            clearDirectionIfDrained();
            updateAwake();
            progress = 0;
            return TickResult.of(awake ? appeng.api.networking.ticking.TickRateModulation.IDLE
                    : appeng.api.networking.ticking.TickRateModulation.SLEEP);
        }

        if (hasInvalidInputs()) {
            ejectInvalidInputs();
            clearDirectionIfDrained();
            updateAwake();
            return TickResult.of(awake ? appeng.api.networking.ticking.TickRateModulation.IDLE
                    : appeng.api.networking.ticking.TickRateModulation.SLEEP);
        }

        if (plan == null) {
            ejectInvalidInputs();
            clearDirectionIfDrained();
            updateAwake();
            return TickResult.of(awake ? appeng.api.networking.ticking.TickRateModulation.IDLE
                    : appeng.api.networking.ticking.TickRateModulation.SLEEP);
        }

        if (reboot) {
            ticksSinceLastCall = 1;
        }
        if (!awake) {
            return TickResult.of(appeng.api.networking.ticking.TickRateModulation.SLEEP);
        }

        reboot = false;
        var speed = SpeedProfile.forCards(accelerationCards);
        int requestedProgress = speed.progressPerTick();
        int availableCrafts = forcedPlan ? 1 + queuedCrafts : 1;
        int usefulProgress = Math.max(0, availableCrafts * 100 - (int) progress);
        requestedProgress = Math.min(requestedProgress, usefulProgress);
        progress += host.usePower(ticksSinceLastCall, requestedProgress, speed.energyTax());

        ItemStack animationOutput = ItemStack.EMPTY;
        int animationRate = 0;
        TickResult latest = TickResult.of(appeng.api.networking.ticking.TickRateModulation.FASTER);
        int completed = 0;
        while (progress >= 100 && completed < MAX_CRAFTS_PER_TICK && plan != null && awake) {
            // AE2's animation status stores speed in a signed byte and has no
            // visual state faster than one tick, so cap only the animation rate.
            latest = complete(Math.min(speed.progressPerTick(), 100));
            if (animationOutput.isEmpty() && !latest.completedOutput().isEmpty()) {
                animationOutput = latest.completedOutput();
                animationRate = latest.animationRate();
            }
            completed++;

            if (!inventory.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
                break;
            }
        }
        return new TickResult(latest.modulation(), animationOutput, animationRate);
    }

    private TickResult complete(int animationRate) {
        for (int slot = 0; slot < craftingGrid.getContainerSize(); slot++) {
            craftingGrid.setItem(slot, inventory.getStackInSlot(slot));
        }

        var positionedInput = craftingGrid.asPositionedCraftInput();
        var craftingInput = positionedInput.input();
        // Retain overflow for the next queued craft. The tick loop is explicitly
        // bounded, so progress above 100 cannot cause an unbounded completion loop.
        progress = Math.max(0, progress - 100);

        ItemStack output = plan.assemble(craftingInput, host.getLevel());
        if (output.isEmpty()) {
            progress = 0;
            ParallelMolecularAssemblerBlockEntity.LOGGER.warn(
                    "Parallel Molecular Assembler at {} could not assemble its accepted pattern; returning inputs",
                    host.getBlockPos());
            if (forcedPlan) {
                finishPlan();
            } else {
                restorePlan();
            }
            ejectInvalidInputs();
            clearDirectionIfDrained();
            host.saveChanges();
            updateAwake();
            return TickResult.of(appeng.api.networking.ticking.TickRateModulation.FASTER);
        }

        output.onCraftedBySystem(Objects.requireNonNull(host.getLevel()));
        CraftingEvent.fireAutoCraftingEvent(host.getLevel(), plan, output, craftingGrid);
        NonNullList<ItemStack> remainders = plan.getRemainingItems(craftingInput);

        pushOut(output.copy());

        int left = positionedInput.left();
        int top = positionedInput.top();
        for (int y = 0; y < craftingGrid.getHeight(); y++) {
            for (int x = 0; x < craftingGrid.getWidth(); x++) {
                if (y < top || x < left) {
                    inventory.setItemDirect(x + y * craftingGrid.getWidth(), ItemStack.EMPTY);
                }
            }
        }
        for (int y = 0; y < craftingInput.height(); y++) {
            for (int x = 0; x < craftingInput.width(); x++) {
                int slot = x + left + (y + top) * craftingGrid.getWidth();
                inventory.setItemDirect(slot, remainders.get(x + y * craftingInput.width()));
            }
        }

        if (forcedPlan) {
            finishPlan();
        } else {
            restorePlan();
        }
        ejectInvalidInputs();
        if (plan == null && inventory.isEmpty() && queuedCrafts > 0) {
            promoteQueuedCraft();
        }
        clearDirectionIfDrained();
        host.saveChanges();
        updateAwake();
        return new TickResult(awake ? appeng.api.networking.ticking.TickRateModulation.IDLE
                : appeng.api.networking.ticking.TickRateModulation.SLEEP, output.copy(), animationRate);
    }

    void restorePlan() {
        reboot = true;
        if (forcedPlan) {
            if (plan == null && !serializedPattern.isEmpty() && host.getLevel() != null) {
                var decoded = PatternDetailsHelper.decodePattern(serializedPattern, host.getLevel());
                if (decoded instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
                    plan = supportedPattern;
                } else {
                    ParallelMolecularAssemblerBlockEntity.LOGGER.warn(
                            "Unable to restore parallel assembler pattern at {}", host.getBlockPos());
                    forcedPlan = false;
                }
                serializedPattern = ItemStack.EMPTY;
            }
        } else {
            ItemStack insertedPattern = patternInventory.getStackInSlot(0);
            boolean validPattern = false;
            if (!insertedPattern.isEmpty()) {
                if (plan != null && ItemStack.isSameItemSameComponents(insertedPattern, serializedPattern)) {
                    validPattern = true;
                } else if (host.getLevel() != null) {
                    var decoded = PatternDetailsHelper.decodePattern(insertedPattern, host.getLevel());
                    if (decoded instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
                        progress = 0;
                        serializedPattern = insertedPattern.copy();
                        plan = supportedPattern;
                        pushDirection = null;
                        validPattern = true;
                    }
                }
            }
            if (!validPattern) {
                progress = 0;
                plan = null;
                serializedPattern = ItemStack.EMPTY;
                pushDirection = null;
            }
        }
        updateAwake();
    }

    void inventoryChanged() {
        restorePlan();
    }

    void writeMetadata(ValueOutput data) {
        if (pushDirection != null) {
            data.putInt("direction", pushDirection.get3DDataValue());
        }
        if (forcedPlan) {
            ItemStack pattern = plan != null ? plan.getDefinition().toStack() : serializedPattern;
            if (!pattern.isEmpty()) {
                data.store("pattern", ItemStack.OPTIONAL_CODEC, pattern);
            }
        }
        var queuedJobs = data.childrenList("queued_jobs");
        for (int i = 0; i < queuedCrafts; i++) {
            var queuedJob = queuedJobs.addChild();
            if (!queuedPatterns[i].isEmpty()) {
                queuedJob.store("pattern", ItemStack.OPTIONAL_CODEC, queuedPatterns[i]);
            }
            if (queuedDirections[i] != null) {
                queuedJob.putInt("direction", queuedDirections[i].get3DDataValue());
            }
        }
    }

    void readMetadata(ValueInput data) {
        resetLaneState();
        int direction = data.getIntOr("direction", -1);
        if (direction >= 0 && direction < Direction.values().length) {
            pushDirection = Direction.from3DDataValue(direction);
        }
        ItemStack pattern = data.read("pattern", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        if (!pattern.isEmpty() && pushDirection != null) {
            forcedPlan = true;
            serializedPattern = pattern;
        }
        var queuedJobs = data.childrenListOrEmpty("queued_jobs");
        queuedCrafts = 0;
        for (var queuedJob : queuedJobs) {
            if (queuedCrafts >= QUEUE_CAPACITY) {
                break;
            }
            int i = queuedCrafts++;
            queuedPatterns[i] = queuedJob.read("pattern", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            int queuedDirection = queuedJob.getIntOr("direction", -1);
            if (queuedDirection >= 0 && queuedDirection < Direction.values().length) {
                queuedDirections[i] = Direction.from3DDataValue(queuedDirection);
            }
        }
        restorePlan();
    }

    private void promoteQueuedCraft() {
        if (queuedCrafts == 0 || !inventory.isEmpty()) {
            return;
        }

        IMolecularAssemblerSupportedPattern promotedPlan = queuedPlans[0];
        if (promotedPlan == null && host.getLevel() != null) {
            var decoded = PatternDetailsHelper.decodePattern(queuedPatterns[0], host.getLevel());
            if (decoded instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
                promotedPlan = supportedPattern;
            }
        }

        forcedPlan = promotedPlan != null;
        plan = promotedPlan;
        pushDirection = queuedDirections[0];
        serializedPattern = ItemStack.EMPTY;

        // Set the forced plan before moving its inputs. Inventory callbacks invoke
        // restorePlan(), and must see the promoted provider plan so they do not reset
        // the carried progress between back-to-back crafts.
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setItemDirect(slot, queuedInventory.getStackInSlot(slot));
            queuedInventory.setItemDirect(slot, ItemStack.EMPTY);
        }

        for (int queueIndex = 1; queueIndex < queuedCrafts; queueIndex++) {
            int fromOffset = queueIndex * SLOT_COUNT;
            int toOffset = (queueIndex - 1) * SLOT_COUNT;
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                queuedInventory.setItemDirect(toOffset + slot,
                        queuedInventory.getStackInSlot(fromOffset + slot));
                queuedInventory.setItemDirect(fromOffset + slot, ItemStack.EMPTY);
            }
            queuedPatterns[queueIndex - 1] = queuedPatterns[queueIndex];
            queuedDirections[queueIndex - 1] = queuedDirections[queueIndex];
            queuedPlans[queueIndex - 1] = queuedPlans[queueIndex];
        }

        int last = --queuedCrafts;
        queuedPatterns[last] = ItemStack.EMPTY;
        queuedDirections[last] = null;
        queuedPlans[last] = null;

        if (plan == null) {
            ParallelMolecularAssemblerBlockEntity.LOGGER.warn(
                    "Unable to decode queued parallel assembler pattern at {}", host.getBlockPos());
        }
        updateAwake();
        host.saveChanges();
    }

    private void finishPlan() {
        forcedPlan = false;
        plan = null;
        serializedPattern = ItemStack.EMPTY;
    }

    private void resetLaneState() {
        finishPlan();
        pushDirection = null;
        queuedCrafts = 0;
        java.util.Arrays.fill(queuedPatterns, ItemStack.EMPTY);
        java.util.Arrays.fill(queuedDirections, null);
        java.util.Arrays.fill(queuedPlans, null);
    }

    private void clearDirectionIfDrained() {
        if (plan == null && inventory.isEmpty()) {
            pushDirection = null;
        }
    }

    private void ejectInvalidInputs() {
        if (!inventory.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
            return;
        }
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && (plan == null || !plan.isItemValid(slot, AEItemKey.of(stack), host.getLevel()))) {
                inventory.setItemDirect(OUTPUT_SLOT, stack);
                inventory.setItemDirect(slot, ItemStack.EMPTY);
                host.saveChanges();
                return;
            }
        }
    }

    private boolean hasInvalidInputs() {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && (plan == null || !plan.isItemValid(slot, AEItemKey.of(stack), host.getLevel()))) {
                return true;
            }
        }
        return false;
    }

    private void pushOut(ItemStack output) {
        if (output.isEmpty() || host.getLevel() == null) {
            return;
        }

        if (pushDirection == null) {
            for (Direction direction : Direction.values()) {
                output = pushTo(output, direction);
                if (output.isEmpty()) {
                    break;
                }
            }
        } else {
            output = pushTo(output, pushDirection);
        }
        inventory.setItemDirect(OUTPUT_SLOT, output);
    }

    private ItemStack pushTo(ItemStack output, Direction direction) {
        var transfer = InternalInventory.wrapExternal(host.getLevel(), host.getBlockPos().relative(direction),
                direction.getOpposite());
        if (transfer == null) {
            return output;
        }
        int oldCount = output.getCount();
        ItemStack remainder = transfer.addItems(output);
        if ((remainder.isEmpty() ? 0 : remainder.getCount()) != oldCount) {
            host.saveChanges();
        }
        return remainder;
    }

    private boolean hasMaterials() {
        if (plan == null || host.getLevel() == null) {
            return false;
        }
        for (int slot = 0; slot < craftingGrid.getContainerSize(); slot++) {
            craftingGrid.setItem(slot, inventory.getStackInSlot(slot));
        }
        return !plan.assemble(craftingGrid.asCraftInput(), host.getLevel()).isEmpty();
    }

    private void updateAwake() {
        boolean oldAwake = awake;
        awake = !inventory.getStackInSlot(OUTPUT_SLOT).isEmpty()
                || hasInvalidInputs()
                || (plan != null && hasMaterials())
                || queuedCrafts > 0;
        if (oldAwake != awake) {
            host.laneAwakeChanged(awake);
        }
    }

    record TickResult(appeng.api.networking.ticking.TickRateModulation modulation, ItemStack completedOutput,
            int animationRate) {
        static TickResult of(appeng.api.networking.ticking.TickRateModulation modulation) {
            return new TickResult(modulation, ItemStack.EMPTY, 0);
        }
    }

    record SpeedProfile(int progressPerTick, double energyTax) {
        static SpeedProfile forCards(int cards) {
            if (cards >= 10) {
                return new SpeedProfile(200, 20.0);
            }
            return switch (cards) {
                case 1 -> new SpeedProfile(13, 1.3);
                case 2 -> new SpeedProfile(17, 1.7);
                case 3 -> new SpeedProfile(20, 2.0);
                case 4 -> new SpeedProfile(25, 2.5);
                case 5 -> new SpeedProfile(50, 5.0);
                case 6 -> new SpeedProfile(75, 7.5);
                case 7 -> new SpeedProfile(100, 10.0);
                case 8 -> new SpeedProfile(125, 12.5);
                case 9 -> new SpeedProfile(150, 15.0);
                default -> new SpeedProfile(10, 1.0);
            };
        }
    }
}
