package dev.advancedmolecularassemblers.machine;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import dev.advancedmolecularassemblers.content.AssemblerTier;
import dev.advancedmolecularassemblers.menu.ParallelMolecularAssemblerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class ParallelMolecularAssemblerBlock extends AEBaseEntityBlock<ParallelMolecularAssemblerBlockEntity> {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    private final AssemblerTier tier;

    public ParallelMolecularAssemblerBlock(AssemblerTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    public AssemblerTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, ParallelMolecularAssemblerBlockEntity host) {
        return state.setValue(POWERED, host.isPowered());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        var host = getBlockEntity(level, pos);
        if (host == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            MenuOpener.open(ParallelMolecularAssemblerMenu.TYPE, player, MenuLocators.forBlockEntity(host));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
