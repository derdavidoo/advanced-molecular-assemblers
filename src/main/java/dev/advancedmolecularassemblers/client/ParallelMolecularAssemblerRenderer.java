package dev.advancedmolecularassemblers.client;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import appeng.core.AEConfig;
import appeng.core.particles.ParticleTypes;
import dev.advancedmolecularassemblers.machine.ParallelMolecularAssemblerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.phys.Vec3;

public final class ParallelMolecularAssemblerRenderer implements
        BlockEntityRenderer<ParallelMolecularAssemblerBlockEntity, ParallelMolecularAssemblerRenderState> {
    private final ItemModelResolver itemModelResolver;

    public ParallelMolecularAssemblerRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
    }

    @Override
    public ParallelMolecularAssemblerRenderState createRenderState() {
        return new ParallelMolecularAssemblerRenderState();
    }

    @Override
    public void extractRenderState(ParallelMolecularAssemblerBlockEntity assembler,
            ParallelMolecularAssemblerRenderState state, float partialTicks, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(assembler, state, partialTicks, cameraPos, crumblingOverlay);
        state.item.clear();

        var status = assembler.getAnimationStatus();
        if (status == null) {
            return;
        }

        if (!Minecraft.getInstance().isPaused()) {
            if (status.isExpired()) {
                assembler.setAnimationStatus(null);
            }
            status.setAccumulatedTicks(status.getAccumulatedTicks() + partialTicks);
            status.setTicksUntilParticles(status.getTicksUntilParticles() - partialTicks);
        }

        var stack = status.getIs();
        var level = assembler.getLevel();
        if (level != null && AEConfig.instance().isEnableEffects() && status.getTicksUntilParticles() <= 0) {
            status.setTicksUntilParticles(4);
            double x = assembler.getBlockPos().getX() + 0.5;
            double y = assembler.getBlockPos().getY() + 0.5;
            double z = assembler.getBlockPos().getZ() + 0.5;
            for (int i = 0; i < (int) Math.ceil(status.getSpeed() / 5.0); i++) {
                level.addParticle(new ItemParticleOption(ParticleTypes.CRAFTING,
                        ItemStackTemplate.fromNonEmptyStack(stack)), x, y, z, 0, 0, 0);
            }
        }

        itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED,
                level, null, (int) assembler.getBlockPos().asLong());
        state.blockItem = stack.getItem() instanceof BlockItem;
    }

    @Override
    public void submit(ParallelMolecularAssemblerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodes, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.translate(0.5, state.blockItem ? 0.3 : 0.2, 0.5);
        state.item.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
