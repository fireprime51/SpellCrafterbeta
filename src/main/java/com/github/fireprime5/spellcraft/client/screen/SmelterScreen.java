package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.energy.SmelterBlock;
import com.github.fireprime5.spellcraft.energy.SmelterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Экран плавильни.
 *
 * <p>Нарисован сам, а не взят из ванильного сундука: сундуок не подходит по
 * смыслу (у него девять слотов и другое расположение), а свой стиль совпадает
 * с остальным интерфейсом мода.
 */
public class SmelterScreen extends AbstractContainerScreen<SmelterMenu> {

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;

    public SmelterScreen(SmelterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        UiKit.panel(gfx, x, y, PANEL_W, PANEL_H,
                com.github.fireprime5.spellcraft.palette.Palette.PANEL,
                com.github.fireprime5.spellcraft.palette.Palette.BORDER);

        // рамка печи: два слота и стрелка между ними
        UiKit.slot(gfx, x + 55, y + 16);
        UiKit.slot(gfx, x + 55, y + 52);

        // кнопка «Модули» слева от слотов
        int bx = x + 24;
        int by = y + 16;
        int bw = 24;
        int bh = 18;
        buttonRight = bx + bw;
        buttonBottom = by + bh;
        // параметры renderBg называются mouseX/mouseY, а не mx/my
        boolean hovered = mouseX >= bx && mouseX <= buttonRight
                && mouseY >= by && mouseY <= buttonBottom;
        gfx.fill(bx, by, buttonRight, buttonBottom,
                hovered
                        ? com.github.fireprime5.spellcraft.palette.Palette.PANEL_LIGHT
                        : com.github.fireprime5.spellcraft.palette.Palette.PANEL);
        gfx.fill(bx, by, buttonRight, by + 1,
                com.github.fireprime5.spellcraft.palette.Palette.BORDER_LIGHT);
        gfx.fill(bx, by, bx + 1, buttonBottom,
                com.github.fireprime5.spellcraft.palette.Palette.BORDER_LIGHT);
        gfx.fill(bx, buttonBottom - 1, buttonRight, buttonBottom,
                com.github.fireprime5.spellcraft.palette.Palette.BORDER_LIGHT);
        gfx.fill(buttonRight - 1, by, buttonRight, buttonBottom,
                com.github.fireprime5.spellcraft.palette.Palette.BORDER_LIGHT);
        var label = Component.translatable("gui.spellcraft.machine_modules");
        gfx.drawString(font, UiKit.fit(font, label.getString(), bw - 2),
                bx + (bw - font.width(UiKit.fit(font, label.getString(), bw - 2))) / 2,
                by + 5, UiKit.TEXT, false);

        // стрелка прогресса между входом и выходом
        int progress = menu.getProgress();
        int arrowWidth = progress * 24 / SmelterBlock.COOK_TICKS;
        gfx.fill(x + 62, y + 39, x + 86, y + 41, UiKit.TEXT_DIM);
        if (arrowWidth > 0) {
            gfx.fill(x + 62, y + 39, x + 62 + arrowWidth, y + 41, UiKit.ACCENT);
        }
        // наконечник стрелки
        gfx.fill(x + 84, y + 38, x + 86, y + 42, UiKit.ACCENT);

        // подсказка результата под входом: что вообще получится
        net.minecraft.world.item.ItemStack result = menu.getResultFor();
        if (!result.isEmpty()) {
            gfx.renderItem(result, x + 57, y + 30);
            gfx.renderItemDecorations(font, result, x + 57, y + 30);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, title, 8, 6, UiKit.TEXT, false);
        gfx.drawString(font, Component.translatable("container.spellcraft.smelter.player"),
                8, inventoryLabelY, UiKit.TEXT_DIM, false);
    }

    /** Прямоугольник кнопки «Модули» в координатах экрана. */
    private int buttonRight;
    private int buttonBottom;

    /**
     * Кнопка «Модули»: открывает модульную сетку машины.
     *
     * <p>Слева от слотов, чтобы не наезжать на инвентарь игрока.
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= leftPos + 24 && mx <= buttonRight
                && my >= topPos + 16 && my <= buttonBottom) {
            // сеть в этом экране ещё не открыта: сервер пошлёт пакет после
            // проверки прав, поэтому просто закрываем панель
            menu.openMachineGrid(minecraft.player);
            minecraft.player.closeContainer();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
    }
}