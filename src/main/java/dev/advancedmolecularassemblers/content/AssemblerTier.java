package dev.advancedmolecularassemblers.content;

public enum AssemblerTier {
    TWO("molecular_assembler_2x", 2),
    FOUR("molecular_assembler_4x", 4),
    EIGHT("molecular_assembler_8x", 8),
    SIXTEEN("molecular_assembler_16x", 16),
    THIRTY_TWO("molecular_assembler_32x", 32);

    private final String id;
    private final int lanes;

    AssemblerTier(String id, int lanes) {
        this.id = id;
        this.lanes = lanes;
    }

    public String id() {
        return id;
    }

    public int lanes() {
        return lanes;
    }
}
