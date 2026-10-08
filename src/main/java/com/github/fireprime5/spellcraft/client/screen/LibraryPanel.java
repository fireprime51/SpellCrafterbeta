package com.github.fireprime5.spellcraft.client.screen;

import com.github.fireprime5.spellcraft.data.SpellLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Панель библиотеки заклинаний (клавиша B): сохранение текущего заклинания
 * под именем, загрузка и удаление сохранённых.
 */
public class LibraryPanel {

    public static final int W = 250;
    public static final int H = 210;

    private static final int ROW_H = 16;

    private final int x;
    private final int y;
    private final Consumer<String> onSave;
    private final Consumer<Integer> onLoad;
    private final Consumer<Integer> onDelete;
    private final Runnable onClose;

    private EditBox nameBox;
    private int hoveredRow = -1;

    public LibraryPanel(int x, int y, Consumer<String> onSave, Consumer<Integer> onLoad,
                        Consumer<Integer> onDelete, Runnable onClose) {
        this.x = x;
        this.y = y;
        this.onSave = onSave;
        this.onLoad = onLoad;
        this.onDelete = onDelete;
        this.onClose = onClose;
    }

    public void init(Minecraft minecraft) {
        nameBox = new EditBox(minecraft.font, x + 8, y + 32, W - 76, 14,
                Component.translatable("library.spellcraft.name_hint"));
        nameBox.setMaxLength(24);
        nameBox.setHint(Component.translatable("library.spellcraft.name_hint"));
        nameBox.setFocused(false);
    }

    private List<String> names() {
        return ClientSpellStateBridge.names();
    }

    private List<Integer> sizes() {
        return ClientSpellStateBridge.sizes();
    }

    public boolean inside(double mx, double my) {
        return UiKit.inside(mx, my, x, y, W, H);
    }

    public EditBox searchBox() {
        return nameBox;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (nameBox != null && nameBox.mouseClicked(mx, my, button)) {
            return true;
        }
        if (!inside(mx, my)) {
            return false;
        }

        // кнопка «Сохранить»
        if (UiKit.inside(mx, my, x + W - 66, y + 32, 58, 14)) {
            onSave.accept(nameBox == null ? "" : nameBox.getValue());
            return true;
        }
        // кнопка «Закрыть»
        if (UiKit.inside(mx, my, x + W - 66, y + H - 20, 58, 14)) {
            onClose.run();
            return true;
        }

        int listTop = y + 54;
        List<String> list = names();
        if (my >= listTop && my < listTop + SpellLibrary.MAX_SPELLS * ROW_H) {
            int row = (int) ((my - listTop) / ROW_H);
            if (row >= 0 && row < SpellLibrary.MAX_SPELLS) {
                if (button == 1 && row < list.size()) {
                    onDelete.accept(row);
                } else if (button == 0 && row < list.size()) {
                    onLoad.accept(row);
                }
                return true;
            }
        }
        return true;
    }

    public boolean charTyped(char code, int modifiers) {
        return nameBox != null && nameBox.charTyped(code, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }

    public void render(GuiGraphics gfx, double mouseX, double mouseY) {
        Font font = UiKit.font();
        UiKit.panel(gfx, x, y, W, H, UiKit.PANEL, UiKit.ACCENT);
        gfx.drawString(font, Component.translatable("library.spellcraft.title").getString(),
                x + 8, y + 10, UiKit.ACCENT, false);
        gfx.drawString(font, Component.translatable("library.spellcraft.slots",
                        names().size(), SpellLibrary.MAX_SPELLS).getString(),
                x + W - 8 - font.width(Component.translatable("library.spellcraft.slots",
                        names().size(), SpellLibrary.MAX_SPELLS).getString()),
                y + 10, UiKit.TEXT_DIM, false);

        if (nameBox != null) {
            nameBox.setX(x + 8);
            nameBox.setY(y + 32);
            nameBox.setWidth(W - 76);
            nameBox.setHeight(14);
            nameBox.render(gfx, (int) mouseX, (int) mouseY, 0);
        }
        button(gfx, font, x + W - 66, y + 32, 58, 14,
                Component.translatable("library.spellcraft.save").getString(),
                UiKit.inside(mouseX, mouseY, x + W - 66, y + 32, 58, 14));

        int listTop = y + 54;
        List<String> list = names();
        List<Integer> sizes = sizes();
        hoveredRow = -1;
        for (int i = 0; i < SpellLibrary.MAX_SPELLS; i++) {
            int ry = listTop + i * ROW_H;
            boolean over = UiKit.inside(mouseX, mouseY, x + 8, ry, W - 16, ROW_H - 2);
            if (over) {
                hoveredRow = i;
                gfx.fill(x + 8, ry, x + W - 8, ry + ROW_H - 2, UiKit.PANEL_LIGHT);
            }
            if (i < list.size()) {
                gfx.drawString(font, UiKit.fit(font, list.get(i), W - 90),
                        x + 14, ry + 4, UiKit.TEXT, false);
                String info = Component.translatable("library.spellcraft.modules",
                        sizes.get(i)).getString();
                gfx.drawString(font, info, x + W - 12 - font.width(info), ry + 4,
                        UiKit.TEXT_DIM, false);
                if (over) {
                    String del = Component.translatable("library.spellcraft.delete_hint").getString();
                    gfx.drawString(font, del, x + W - 12 - font.width(del), ry + 4,
                            UiKit.error(), false);
                }
            } else {
                gfx.drawString(font, Component.translatable("library.spellcraft.empty_slot").getString(),
                        x + 14, ry + 4, UiKit.TEXT_DIM, false);
            }
            gfx.fill(x + 8, ry + ROW_H - 2, x + W - 8, ry + ROW_H - 1, UiKit.BORDER);
        }

        button(gfx, font, x + W - 66, y + H - 20, 58, 14,
                Component.translatable("library.spellcraft.close").getString(),
                UiKit.inside(mouseX, mouseY, x + W - 66, y + H - 20, 58, 14));
    }

    private void button(GuiGraphics gfx, Font font, int bx, int by, int bw, int bh, String label,
                        boolean hovered) {
        gfx.fill(bx, by, bx + bw, by + bh, hovered ? UiKit.HOVER : UiKit.PANEL_DARK);
        gfx.renderOutline(bx, by, bw, bh, UiKit.BORDER_LIGHT);
        gfx.drawString(font, UiKit.fit(font, label, bw - 6),
                bx + (bw - font.width(UiKit.fit(font, label, bw - 6))) / 2,
                by + (bh - font.lineHeight) / 2 + 1, UiKit.TEXT, false);
    }

    /** Доступ к состоянию библиотеки без прямой зависимости от класса состояния. */
    static final class ClientSpellStateBridge {
        private ClientSpellStateBridge() {
        }

        static List<String> names() {
            return com.github.fireprime5.spellcraft.client.ClientSpellState.libraryNames();
        }

        static List<Integer> sizes() {
            return com.github.fireprime5.spellcraft.client.ClientSpellState.librarySizes();
        }
    }
}