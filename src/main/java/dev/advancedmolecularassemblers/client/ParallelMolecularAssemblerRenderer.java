package dev.advancedmolecularassemblers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import appeng.client.render.crafting.AssemblerAnimationStatus;
import appeng.client.render.effects.ParticleTypes;
import appeng.core.AppEng;
import appeng.core.AppEngClient;
import dev.advancedmolecularassemblers.machine.ParallelMolecularAssemblerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class ParallelMolecularAssemblerRenderer
        implements BlockEntityRenderer<ParallelMolecularAssemblerBlockEntity> {
    private static final ModelResourceLocation LIGHTS_MODEL = ModelResourceLocation
            .standalone(AppEng.makeId("block/molecular_assembler_lights"));

    private final RandomSource particleRandom = RandomSource.create();

    public ParallelMolecularAssemblerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ParallelMolecularAssemblerBlockEntity assembler, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        AssemblerAnimationStatus status = assembler.getAnimationStatus();
        if (status != null) {
            if (!Minecraft.getInstance().isPaused()) {
                if (status.isExpired()) {
                    assembler.setAnimationStatus(null);
                }
                status.setAccumulatedTicks(status.getAccumulatedTicks() + partialTick);
                status.setTicksUntilParticles(status.getTicksUntilParticles() - partialTick);
            }
            renderStatus(assembler, poseStack, buffers, packedLight, status);
        }

        if (assembler.isPowered()) {
            renderPowerLight(poseStack, buffers, packedLight, packedOverlay);
        }
    }

    private void renderPowerLight(PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel lightsModel = minecraft.getModelManager().getModel(LIGHTS_MODEL);
        VertexConsumer consumer = buffers.getBuffer(RenderType.tripwire());
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), consumer, null, lightsModel,
                1, 1, 1, packedLight, packedOverlay, ModelData.EMPTY, null);
    }

    private void renderStatus(ParallelMolecularAssemblerBlockEntity assembler, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, AssemblerAnimationStatus status) {
        Minecraft minecraft = Minecraft.getInstance();
        double x = assembler.getBlockPos().getX() + 0.5;
        double y = assembler.getBlockPos().getY() + 0.5;
        double z = assembler.getBlockPos().getZ() + 0.5;

        if (status.getTicksUntilParticles() <= 0) {
            status.setTicksUntilParticles(4);
            if (AppEngClient.instance().shouldAddParticles(particleRandom)) {
                for (int i = 0; i < (int) Math.ceil(status.getSpeed() / 5.0); i++) {
                    minecraft.particleEngine.createParticle(ParticleTypes.CRAFTING, x, y, z, 0, 0, 0);
                }
            }
        }

        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        poseStack.pushPose();
        poseStack.translate(0.5, status.getIs().getItem() instanceof BlockItem ? 0.3 : 0.2, 0.5);
        itemRenderer.renderStatic(status.getIs(), ItemDisplayContext.GROUND, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffers, assembler.getLevel(), 0);
        poseStack.popPose();
    }
}
