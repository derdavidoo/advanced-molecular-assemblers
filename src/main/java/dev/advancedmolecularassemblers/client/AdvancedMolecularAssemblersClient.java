package dev.advancedmolecularassemblers.client;

import appeng.client.InitScreens;
import dev.advancedmolecularassemblers.AdvancedMolecularAssemblers;
import dev.advancedmolecularassemblers.menu.ParallelMolecularAssemblerMenu;
import dev.advancedmolecularassemblers.content.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = AdvancedMolecularAssemblers.MOD_ID, dist = Dist.CLIENT)
public final class AdvancedMolecularAssemblersClient {
    public AdvancedMolecularAssemblersClient(IEventBus modBus) {
        modBus.addListener(this::registerScreens);
        modBus.addListener(this::registerRenderers);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event, ParallelMolecularAssemblerMenu.TYPE,
                ParallelMolecularAssemblerScreen::new,
                "/screens/parallel_molecular_assembler.json");
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PARALLEL_MOLECULAR_ASSEMBLER.get(),
                ParallelMolecularAssemblerRenderer::new);
    }
}
