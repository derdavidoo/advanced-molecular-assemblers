package dev.advancedmolecularassemblers.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class ParallelMolecularAssemblerRenderState extends BlockEntityRenderState {
    final ItemStackRenderState item = new ItemStackRenderState();
    boolean blockItem;
}
