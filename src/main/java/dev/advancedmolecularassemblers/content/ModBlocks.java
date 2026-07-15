package dev.advancedmolecularassemblers.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import appeng.block.AEBaseBlock;
import dev.advancedmolecularassemblers.AdvancedMolecularAssemblers;
import dev.advancedmolecularassemblers.machine.ParallelMolecularAssemblerBlock;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(AdvancedMolecularAssemblers.MOD_ID);
    private static final List<AssemblerEntry> MUTABLE_ASSEMBLERS = new ArrayList<>();

    public static final AssemblerEntry ASSEMBLER_2X = register(AssemblerTier.TWO);
    public static final AssemblerEntry ASSEMBLER_4X = register(AssemblerTier.FOUR);
    public static final AssemblerEntry ASSEMBLER_8X = register(AssemblerTier.EIGHT);
    public static final AssemblerEntry ASSEMBLER_16X = register(AssemblerTier.SIXTEEN);
    public static final AssemblerEntry ASSEMBLER_32X = register(AssemblerTier.THIRTY_TWO);

    public static final List<AssemblerEntry> ASSEMBLERS = Collections.unmodifiableList(MUTABLE_ASSEMBLERS);

    private ModBlocks() {
    }

    private static AssemblerEntry register(AssemblerTier tier) {
        DeferredBlock<ParallelMolecularAssemblerBlock> block = REGISTER.registerBlock(tier.id(),
                properties -> new ParallelMolecularAssemblerBlock(tier,
                        AEBaseBlock.metalProps(properties).noOcclusion()));
        DeferredItem<BlockItem> item = ModItems.REGISTER.registerSimpleBlockItem(tier.id(), block);
        var entry = new AssemblerEntry(tier, block, item);
        MUTABLE_ASSEMBLERS.add(entry);
        return entry;
    }

    public record AssemblerEntry(
            AssemblerTier tier,
            DeferredBlock<ParallelMolecularAssemblerBlock> block,
            DeferredItem<BlockItem> item) {
    }
}
