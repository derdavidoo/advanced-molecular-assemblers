package dev.advancedmolecularassemblers.content;

import dev.advancedmolecularassemblers.AdvancedMolecularAssemblers;
import dev.advancedmolecularassemblers.machine.ParallelMolecularAssemblerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AdvancedMolecularAssemblers.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ParallelMolecularAssemblerBlockEntity>>
            PARALLEL_MOLECULAR_ASSEMBLER = REGISTER.register("parallel_molecular_assembler", () ->
                    BlockEntityType.Builder.of(
                            ParallelMolecularAssemblerBlockEntity::new,
                            ModBlocks.ASSEMBLERS.stream().map(entry -> entry.block().get()).toArray(net.minecraft.world.level.block.Block[]::new))
                            .build(null));

    private ModBlockEntities() {
    }
}
