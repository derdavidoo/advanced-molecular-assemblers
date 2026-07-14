package dev.advancedmolecularassemblers;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import appeng.api.AECapabilities;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import dev.advancedmolecularassemblers.content.ModBlockEntities;
import dev.advancedmolecularassemblers.content.ModBlocks;
import dev.advancedmolecularassemblers.content.ModItems;
import dev.advancedmolecularassemblers.content.ModMenus;
import dev.advancedmolecularassemblers.machine.ParallelMolecularAssemblerBlockEntity;
import dev.advancedmolecularassemblers.network.AssemblerAnimationPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(AdvancedMolecularAssemblers.MOD_ID)
public final class AdvancedMolecularAssemblers {
    public static final String MOD_ID = "advanced_molecular_assemblers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AdvancedMolecularAssemblers(IEventBus modBus) {
        ModBlocks.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModBlockEntities.REGISTER.register(modBus);
        ModMenus.REGISTER.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::addCreativeTabEntries);
        modBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                AssemblerAnimationPayload.TYPE,
                AssemblerAnimationPayload.STREAM_CODEC,
                AssemblerAnimationPayload::handle);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var type = ModBlockEntities.PARALLEL_MOLECULAR_ASSEMBLER.get();
            for (var entry : ModBlocks.ASSEMBLERS) {
                var block = entry.block().get();
                block.setBlockEntity(ParallelMolecularAssemblerBlockEntity.class, type, null, null);
                Upgrades.add(AEItems.SPEED_CARD, block, ParallelMolecularAssemblerBlockEntity.UPGRADE_SLOTS);
            }
        });
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        var type = ModBlockEntities.PARALLEL_MOLECULAR_ASSEMBLER.get();
        event.registerBlockEntity(AECapabilities.CRAFTING_MACHINE, type, (host, side) -> host);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type, (host, side) -> host);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type,
                (host, side) -> host.getExposedItemHandler(side));
    }

    private void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            for (var entry : ModBlocks.ASSEMBLERS) {
                event.accept(entry.item());
            }
        }
    }
}
