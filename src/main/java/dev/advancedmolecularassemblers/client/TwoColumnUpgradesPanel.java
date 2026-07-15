package dev.advancedmolecularassemblers.client;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.client.Point;
import appeng.util.Icon;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Rects;
import appeng.client.gui.Tooltip;
import appeng.client.gui.style.Blitter;
import appeng.menu.slot.AppEngSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

/** AE2-style upgrade panel arranged in columns of five slots. */
final class TwoColumnUpgradesPanel implements ICompositeWidget {
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 5;
    private static final int PANEL_BACKGROUND = 0xFFCBCCD4;
    private static final int PANEL_TOP_HIGHLIGHT = 0xFFF2F2F2;
    private static final int PANEL_EDGE_SHADOW = 0xFF878FA5;
    private static final int PANEL_OUTLINE = 0xFF413F54;
    private static final Blitter BACKGROUND = Blitter.texture("guis/extra_panels.png", 128, 128);
    private static final Blitter INNER_CORNER = BACKGROUND.copy().src(12, 33, 18, 18);

    private final List<Slot> slots;
    private final Supplier<List<Component>> tooltipSupplier;

    private Point screenOrigin = Point.ZERO;
    private int x;
    private int y;

    TwoColumnUpgradesPanel(List<Slot> slots, Supplier<List<Component>> tooltipSupplier) {
        this.slots = slots;
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    public void setPosition(Point position) {
        x = position.getX();
        y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rect2i getBounds() {
        int count = getUpgradeSlotCount();
        if (count == 0) {
            return new Rect2i(x, y, 0, 0);
        }
        int columns = Math.min(2, count);
        int rows = (count + columns - 1) / columns;
        return new Rect2i(x, y,
                PADDING * 2 + columns * SLOT_SIZE,
                PADDING * 2 + rows * SLOT_SIZE);
    }

    @Override
    public void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
        screenOrigin = Point.fromTopLeft(bounds);
    }

    @Override
    public void drawBackgroundLayer(GuiGraphicsExtractor graphics, Rect2i bounds, Point mouse) {
        int count = getUpgradeSlotCount();
        if (count == 0) {
            return;
        }

        int columns = Math.min(2, count);
        int rows = (count + columns - 1) / columns;
        int originX = screenOrigin.getX() + x + PADDING;
        int originY = screenOrigin.getY() + y + PADDING;
        for (int index = 0; index < count; index++) {
            int row = index / columns;
            int column = index % columns;
            int slotX = originX + column * SLOT_SIZE;
            int slotY = originY + row * SLOT_SIZE;
            boolean lastSlot = index + 1 == count;
            boolean lastRow = row + 1 == rows;

            drawSlot(graphics, slotX, slotY,
                    column == 0,
                    row == 0,
                    column + 1 == columns || lastSlot,
                    lastRow);

            if (lastSlot && column + 1 < columns) {
                INNER_CORNER.copy().dest(slotX + SLOT_SIZE, slotY).blit(graphics);
            }
        }

        // The source texture contains a complete outer edge for every column.
        // Repaint only their shared two-pixel edge, including the exact top and
        // bottom cap colors, so the original AE2 attachment frame stays intact.
        if (columns == 2) {
            int seamX = originX + SLOT_SIZE - 2;
            int panelTop = screenOrigin.getY() + y;
            int panelBottom = panelTop + getBounds().getHeight() - 1;

            graphics.fill(seamX, panelTop, seamX + 2, panelTop + 1, PANEL_OUTLINE);
            graphics.fill(seamX, panelTop + 1, seamX + 2, panelTop + 2, PANEL_TOP_HIGHLIGHT);
            graphics.fill(seamX, panelTop + 2, seamX + 2, panelBottom - 1, PANEL_BACKGROUND);
            graphics.fill(seamX, panelBottom - 1, seamX + 2, panelBottom, PANEL_TOP_HIGHLIGHT);
            graphics.fill(seamX, panelBottom, seamX + 2, panelBottom + 2, PANEL_EDGE_SHADOW);
            graphics.fill(seamX, panelBottom + 2, seamX + 2, panelBottom + 3, PANEL_OUTLINE);
        }

        // AE2's standard slot sprite gives the card area the same connected-grid
        // treatment as the assembler's crafting grid.
        int gridX = screenOrigin.getX() + x;
        int gridY = screenOrigin.getY() + y + PADDING;
        for (int index = 0; index < count; index++) {
            int row = index / columns;
            int column = index % columns;
            Blitter.icon(Icon.SLOT_BACKGROUND)
                    .dest(gridX + column * SLOT_SIZE, gridY + row * SLOT_SIZE)
                    .blit(graphics);
        }
    }

    @Override
    public void addExclusionZones(List<Rect2i> exclusionZones, Rect2i screenBounds) {
        Rect2i bounds = getBounds();
        if (bounds.getWidth() > 0 && bounds.getHeight() > 0) {
            exclusionZones.add(Rects.expand(new Rect2i(
                    screenBounds.getX() + bounds.getX(),
                    screenBounds.getY() + bounds.getY(),
                    bounds.getWidth(), bounds.getHeight()), 2));
        }
    }

    @Override
    @Nullable
    public Tooltip getTooltip(int mouseX, int mouseY) {
        if (getUpgradeSlotCount() == 0) {
            return null;
        }
        List<Component> tooltip = tooltipSupplier.get();
        return tooltip.isEmpty() ? null : new Tooltip(tooltip);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y,
            boolean borderLeft, boolean borderTop, boolean borderRight, boolean borderBottom) {
        int sourceX = PADDING;
        int sourceY = PADDING;
        int sourceWidth = SLOT_SIZE;
        int sourceHeight = SLOT_SIZE;

        if (borderLeft) {
            x -= PADDING;
            sourceX = 0;
            sourceWidth += PADDING;
        }
        if (borderRight) {
            sourceWidth += PADDING;
        }
        if (borderTop) {
            y -= PADDING;
            sourceY = 0;
            sourceHeight += PADDING;
        }
        if (borderBottom) {
            sourceHeight += 7;
        }

        BACKGROUND.copy()
                .src(sourceX, sourceY, sourceWidth, sourceHeight)
                .dest(x, y)
                .blit(graphics);
    }

    private int getUpgradeSlotCount() {
        int count = 0;
        for (Slot slot : slots) {
            if (slot instanceof AppEngSlot appEngSlot && appEngSlot.isSlotEnabled()) {
                count++;
            }
        }
        return count;
    }
}
