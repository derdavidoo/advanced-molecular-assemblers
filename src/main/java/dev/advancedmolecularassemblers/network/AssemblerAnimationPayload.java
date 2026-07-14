package dev.advancedmolecularassemblers.network;

import dev.advancedmolecularassemblers.AdvancedMolecularAssemblers;
import dev.advancedmolecularassemblers.machine.ParallelMolecularAssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AssemblerAnimationPayload(BlockPos pos, byte rate, ItemStack output) implements CustomPacketPayload {
    public static final Type<AssemblerAnimationPayload> TYPE =
            new Type<>(AdvancedMolecularAssemblers.id("assembler_animation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblerAnimationPayload> STREAM_CODEC =
            StreamCodec.ofMember(AssemblerAnimationPayload::write, AssemblerAnimationPayload::decode);

    private static AssemblerAnimationPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AssemblerAnimationPayload(
                buffer.readBlockPos(), buffer.readByte(), ItemStack.STREAM_CODEC.decode(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeByte(rate);
        ItemStack.STREAM_CODEC.encode(buffer, output);
    }

    public static void handle(AssemblerAnimationPayload payload, IPayloadContext context) {
        var blockEntity = context.player().level().getBlockEntity(payload.pos());
        if (blockEntity instanceof ParallelMolecularAssemblerBlockEntity assembler) {
            assembler.setAnimationFromNetwork(payload.output(), Byte.toUnsignedInt(payload.rate()));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
