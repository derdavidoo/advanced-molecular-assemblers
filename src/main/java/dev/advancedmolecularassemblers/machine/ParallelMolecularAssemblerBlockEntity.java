package dev.advancedmolecularassemblers.machine;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.client.render.crafting.AssemblerAnimationStatus;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import dev.advancedmolecularassemblers.AdvancedMolecularAssemblers;
import dev.advancedmolecularassemblers.content.ModBlockEntities;
import dev.advancedmolecularassemblers.network.AssemblerAnimationPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class ParallelMolecularAssemblerBlockEntity extends AENetworkedInvBlockEntity
        implements IUpgradeableObject, IGridTickable, ICraftingMachine, IPowerChannelState {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int UPGRADE_SLOTS = 10;
    public static final ResourceLocation INV_MAIN = AdvancedMolecularAssemblers.id("parallel_molecular_assembler");

    private final CraftingLane[] lanes;
    private final InternalInventory internalInventory;
    private final InternalInventory outputInventory;
    private final IUpgradeInventory upgrades;

    private int activeLanes;
    private int allocationCursor;
    private int tickCursor;
    private boolean powered;

    @OnlyIn(Dist.CLIENT)
    private AssemblerAnimationStatus animationStatus;

    public ParallelMolecularAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARALLEL_MOLECULAR_ASSEMBLER.get(), pos, state);

        int laneCount = state.getBlock() instanceof ParallelMolecularAssemblerBlock block
                ? block.tier().lanes()
                : 1;
        lanes = new CraftingLane[laneCount];
        InternalInventory[] laneInventories = new InternalInventory[laneCount * 3];
        for (int i = 0; i < laneCount; i++) {
            lanes[i] = new CraftingLane(this);
            // Keep every original 10-slot lane first so worlds saved before manual-pattern
            // support retain the same combined-inventory indices. Pattern slots are appended.
            laneInventories[i] = lanes[i].inventory();
            laneInventories[laneCount + i] = lanes[i].patternInventory();
            laneInventories[laneCount * 2 + i] = lanes[i].queuedInventory();
        }
        internalInventory = new CombinedInternalInventory(laneInventories);
        outputInventory = new OutputOnlyInventory(lanes);
        upgrades = UpgradeInventories.forMachine(state.getBlock(), UPGRADE_SLOTS, this::saveChanges);

        getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return getBlockState().getBlock().asItem();
    }

    public int getLaneCount() {
        return lanes.length;
    }

    public int getActiveLaneCount() {
        return activeLanes;
    }

    public InternalInventory getLaneInventory(int lane) {
        return lanes[checkedLane(lane)].menuInventory();
    }

    public int getCraftingProgress(int lane) {
        return lanes[checkedLane(lane)].progress();
    }

    public IMolecularAssemblerSupportedPattern getCurrentPattern(int lane) {
        return lanes[checkedLane(lane)].currentPattern();
    }

    private int checkedLane(int lane) {
        if (lane < 0 || lane >= lanes.length) {
            throw new IndexOutOfBoundsException("Lane " + lane + " outside 0.." + (lanes.length - 1));
        }
        return lane;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        Component name = hasCustomName() ? getCustomName() : getBlockState().getBlock().getName();
        var icon = AEItemKey.of(getBlockState().getBlock().asItem());
        int cards = upgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
        List<Component> tooltip = cards == 0 ? List.of()
                : List.of(GuiText.CompatibleUpgrade.text(
                        Tooltips.of(AEItems.SPEED_CARD.asItem().getDescription()),
                        Tooltips.ofUnformattedNumber(cards)));
        return new PatternContainerGroup(icon, name, tooltip);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        for (int offset = 0; offset < lanes.length; offset++) {
            int index = (allocationCursor + offset) % lanes.length;
            if (lanes[index].tryAccept(patternDetails, inputs, ejectionDirection)) {
                allocationCursor = (index + 1) % lanes.length;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean acceptsPlans() {
        return true;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        for (var lane : lanes) {
            lane.restorePlan();
        }
        return new TickingRequest(1, 1, activeLanes == 0);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        TickRateModulation modulation = TickRateModulation.SLEEP;
        ItemStack animationOutput = ItemStack.EMPTY;
        int animationRate = 0;
        int cards = upgrades.getInstalledUpgrades(AEItems.SPEED_CARD);

        for (int offset = 0; offset < lanes.length; offset++) {
            int index = (tickCursor + offset) % lanes.length;
            CraftingLane lane = lanes[index];
            if (!lane.isAwake()) {
                continue;
            }
            var result = lane.tick(cards, ticksSinceLastCall);
            if (result.modulation().ordinal() > modulation.ordinal()) {
                modulation = result.modulation();
            }
            if (animationOutput.isEmpty() && !result.completedOutput().isEmpty()) {
                animationOutput = result.completedOutput();
                animationRate = result.animationRate();
            }
        }
        tickCursor = (tickCursor + 1) % lanes.length;

        if (!animationOutput.isEmpty()) {
            onCraftCompleted(animationOutput, animationRate);
        }
        return modulation;
    }

    void laneAwakeChanged(boolean awake) {
        int oldActive = activeLanes;
        activeLanes = awake ? activeLanes + 1 : Math.max(0, activeLanes - 1);
        if (oldActive == 0 && activeLanes > 0) {
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        } else if (oldActive > 0 && activeLanes == 0) {
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().sleepDevice(node));
        }
    }

    int usePower(int ticksPassed, int progressPerTick, double energyTax) {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        double extracted = grid.getEnergyService().extractAEPower(
                ticksPassed * progressPerTick * energyTax, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return (int) (extracted / energyTax);
    }

    private void onCraftCompleted(ItemStack output, int animationRate) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersNear(serverLevel, null,
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 32,
                    new AssemblerAnimationPayload(worldPosition, (byte) animationRate, output));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void setAnimationFromNetwork(ItemStack output, int rate) {
        animationStatus = new AssemblerAnimationStatus((byte) rate, output);
    }

    @OnlyIn(Dist.CLIENT)
    public void setAnimationStatus(@Nullable AssemblerAnimationStatus animationStatus) {
        this.animationStatus = animationStatus;
    }

    @OnlyIn(Dist.CLIENT)
    @Nullable
    public AssemblerAnimationStatus getAnimationStatus() {
        return animationStatus;
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.COVERED;
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return upgrades;
        }
        if (INV_MAIN.equals(id)) {
            return internalInventory;
        }
        return super.getSubInventory(id);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return internalInventory;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction side) {
        return outputInventory;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        for (var lane : lanes) {
            if (lane.ownsInventory(inventory)) {
                lane.inventoryChanged();
                return;
            }
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var upgrade : upgrades) {
            drops.add(upgrade);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        var laneData = new ListTag();
        for (var lane : lanes) {
            laneData.add(lane.writeMetadata(registries));
        }
        data.put("crafting_lanes", laneData);
        data.putInt("data_version", 3);
        upgrades.writeToNBT(data, "upgrades", registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        upgrades.readFromNBT(data, "upgrades", registries);
        ListTag laneData = data.getList("crafting_lanes", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(lanes.length, laneData.size()); i++) {
            lanes[i].readMetadata(laneData.getCompound(i), registries);
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean oldPowered = powered;
        powered = data.readBoolean();
        return changed || oldPowered != powered;
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(powered);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason == IGridNodeListener.State.GRID_BOOT) {
            return;
        }
        boolean newPowered = false;
        var grid = getMainNode().getGrid();
        if (grid != null) {
            newPowered = getMainNode().isPowered()
                    && grid.getEnergyService().extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.0001;
        }
        if (newPowered != powered) {
            powered = newPowered;
            markForUpdate();
        }
    }

    @Override
    public boolean isPowered() {
        return powered;
    }

    @Override
    public boolean isActive() {
        return powered;
    }
}
