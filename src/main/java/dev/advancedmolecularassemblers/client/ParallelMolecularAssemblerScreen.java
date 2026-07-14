package dev.advancedmolecularassemblers.client;

import java.util.ArrayList;
import java.util.List;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import dev.advancedmolecularassemblers.menu.ParallelMolecularAssemblerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ParallelMolecularAssemblerScreen
        extends AEBaseScreen<ParallelMolecularAssemblerMenu> {
    private final ProgressBar progressBar;
    private final IconButton previous;
    private final IconButton next;

    public ParallelMolecularAssemblerScreen(ParallelMolecularAssemblerMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        widgets.add("upgrades", new TwoColumnUpgradesPanel(
                menu.getSlots(SlotSemantics.UPGRADE), this::getCompatibleUpgrades));
        if (menu.getToolbox().isPresent()) {
            widgets.add("toolbox", new ToolboxPanel(style, menu.getToolbox().getName()));
        }
        progressBar = new ProgressBar(menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        widgets.add("progressBar", (AbstractWidget) progressBar);
        previous = new LaneNavigationButton(Icon.ARROW_LEFT,
                Component.translatable("gui.advanced_molecular_assemblers.previous_lane"),
                button -> selectLane(ParallelMolecularAssemblerMenu.PREVIOUS_LANE_BUTTON));
        next = new LaneNavigationButton(Icon.ARROW_RIGHT,
                Component.translatable("gui.advanced_molecular_assemblers.next_lane"),
                button -> selectLane(ParallelMolecularAssemblerMenu.NEXT_LANE_BUTTON));
        addToLeftToolbar(previous);
        addToLeftToolbar(next);
    }

    private List<Component> getCompatibleUpgrades() {
        var lines = new ArrayList<Component>();
        lines.add(GuiText.CompatibleUpgrades.text());
        lines.addAll(Upgrades.getTooltipLinesForMachine(menu.getUpgrades().getUpgradableItem()));
        return lines;
    }

    private void selectLane(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        progressBar.setFullMsg(Component.literal(menu.getCurrentProgress() + "%"));
        if (previous != null) {
            previous.active = menu.getSelectedLane() > 0;
        }
        if (next != null) {
            next.active = menu.getSelectedLane() + 1 < menu.getLaneCount();
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        int color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        Component laneText = Component.translatable("gui.advanced_molecular_assemblers.lane",
                menu.getSelectedLane() + 1, menu.getLaneCount());
        graphics.drawString(font, laneText, 56 - font.width(laneText) / 2, 18, color, false);
        graphics.drawString(font,
                Component.translatable("gui.advanced_molecular_assemblers.active",
                        menu.getActiveLanes(), menu.getLaneCount()),
                8, 91, color, false);
    }

    private static final class LaneNavigationButton extends IconButton {
        private final Icon icon;

        private LaneNavigationButton(Icon icon, Component tooltip, Button.OnPress onPress) {
            super(onPress);
            this.icon = icon;
            setMessage(tooltip);
        }

        @Override
        protected Icon getIcon() {
            return icon;
        }
    }
}
