package dev.advancedmolecularassemblers.content;

import dev.advancedmolecularassemblers.AdvancedMolecularAssemblers;
import dev.advancedmolecularassemblers.menu.ParallelMolecularAssemblerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(Registries.MENU, AdvancedMolecularAssemblers.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ParallelMolecularAssemblerMenu>> PARALLEL_MOLECULAR_ASSEMBLER =
            REGISTER.register("parallel_molecular_assembler", () -> ParallelMolecularAssemblerMenu.TYPE);

    private ModMenus() {
    }
}
