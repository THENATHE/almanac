package com.thenathe.almanac.client.settings;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A settings screen in either look: tabs on the left with the description under them, a
 * scrolling panel of rows on the right, and the footer along the bottom.
 * Changes apply as they are made and are written to disk once, when the screen goes away.
 */
public final class KitSettingsScreen extends Screen {
    private static final int SCROLL_STEP = 20;
    private static final int FIRST_CAPTION_H = 16;
    private static final int CAPTION_H = 26;
    private static final int TILE_GAP = 4;
    private static final int CHIP_PITCH = 16;
    private static final int ADD_W = 40;

    private final Screen parent;
    private final List<SettingsPage> pages;
    private final LookPreferences preferences;
    private final Runnable save;
    private final PopupList popup = new PopupList();

    private Look look;
    private SettingsLayout layout;
    private boolean scrollRoom;
    private FramedTextField search;
    private int tab;
    private String query = "";
    private int scroll;
    private int contentHeight;
    private boolean draggingScrollbar;
    private Described described;
    private TextListSetting refocusList;
    private final Map<PageRow.Caption, Boolean> folded = new HashMap<>();
    private final List<ListRow> rows = new ArrayList<>();
    private final List<AbstractWidget> listWidgets = new ArrayList<>();
    private final List<KitButton> navButtons = new ArrayList<>();

    private enum RowKind { CAPTION, HEADER, NOTE, SETTING, LIST, TILES }

    /** A laid-out line of the list. {@code source} is the caption, note or setting it shows. */
    private record ListRow(RowKind kind, Object source, int y, int h, boolean grouped, List<Placed> widgets) {
    }

    /** A widget and its offset from the row's top-left corner. */
    private record Placed(AbstractWidget widget, int dx, int dy) {
    }

    public KitSettingsScreen(Component title, Screen parent, List<SettingsPage> pages, LookPreferences preferences, Runnable save) {
        super(title);
        this.parent = parent;
        this.pages = pages;
        this.preferences = preferences;
        this.save = save;
    }

    private boolean searching() {
        return !query.isBlank();
    }

    @Override
    protected void init() {
        look = preferences.look();
        popup.setLook(look);
        popup.close();
        layout = SettingsLayout.of(width, height, look.isHouse(), pages.size(), scrollRoom);
        navButtons.clear();

        search = new FramedTextField(look, layout.navX(), layout.panelY(), layout.navW(), SettingsLayout.SEARCH_H,
                Component.translatable("almanac.search"), true);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            scroll = 0;
            rebuildList();
        });
        addRenderableWidget(search);

        for (int i = 0; i < pages.size(); i++) {
            int page = i;
            SettingsPage settingsPage = pages.get(i);
            if (layout.sideTabs()) {
                addRenderableWidget(new SideTab(layout.navX(), layout.navY(i), layout.panelX(), settingsPage.name(),
                        () -> !searching() && tab == page, settingsPage::changed, () -> chooseTab(page)));
            } else {
                KitButton button = new KitButton(look, layout.navX(), layout.navY(i), layout.navW(), layout.navH(),
                        settingsPage.name(), () -> chooseTab(page)).chosenWhen(() -> !searching() && tab == page);
                navButtons.add(button);
                addRenderableWidget(button);
            }
        }
        addFooter();
        rebuildList();
    }

    private void addFooter() {
        int x = layout.contentX();
        int y = layout.footerY();
        Component lookLabel = Component.translatable("almanac.look", look.style().displayName());
        KitButton lookButton = new KitButton(look, x, y, Math.max(40, font.width(lookLabel) + 16), 20, lookLabel, () -> {
            preferences.setStyle(look.style().next());
            scrollRoom = false;
            rebuildWidgets();
        });
        lookButton.setTooltip(Tooltip.create(Component.translatable("almanac.look.tooltip")));
        addRenderableWidget(lookButton);
        x += lookButton.getWidth() + 4;

        if (look.isHouse()) {
            FooterButtons.AccentButton accentButton = new FooterButtons.AccentButton(look, x, y,
                    FooterButtons.AccentButton.width(font), this::openAccentPicker);
            addRenderableWidget(accentButton);
            x += accentButton.getWidth() + 4;
            addRenderableWidget(new FooterButtons.ModeButton(look, x, y, () -> {
                preferences.setLight(!preferences.light());
                rebuildWidgets();
            }));
        }

        int doneW = Math.max(70, font.width(CommonComponents.GUI_DONE) + 16);
        addRenderableWidget(new KitButton(look, layout.contentX() + layout.contentW() - doneW, y, doneW, 20,
                CommonComponents.GUI_DONE, this::onClose));
    }

    private void openAccentPicker(AbstractWidget anchor) {
        if (popup.isOpenFor(anchor)) {
            popup.close();
            return;
        }
        List<PopupList.Option> options = new ArrayList<>();
        for (Accent accent : Accent.values()) {
            options.add(new PopupList.Option(accent.displayName(), accent.argb() & 0xFFFFFF));
        }
        popup.open(anchor, options, preferences.accent().ordinal(), index -> {
            preferences.setAccent(Accent.values()[index]);
            rebuildWidgets();
        });
    }

    private void chooseTab(int page) {
        popup.close();
        tab = page;
        scroll = 0;
        search.setValue("");
        rebuildList();
    }

    // Building the list

    private void rebuildList() {
        for (AbstractWidget widget : listWidgets) {
            removeWidget(widget);
        }
        listWidgets.clear();
        rows.clear();

        int y = 2;
        if (searching()) {
            y = buildSearchResults(y);
        } else {
            y = buildPage(pages.get(tab), y);
        }
        contentHeight = rows.isEmpty() ? 0 : y + 4;

        // The vanilla scrollbar takes room from the list, so lay out again if that changed.
        boolean needsRoom = !look.isHouse() && contentHeight > layout.listH();
        if (needsRoom != scrollRoom) {
            scrollRoom = needsRoom;
            layout = SettingsLayout.of(width, height, look.isHouse(), pages.size(), scrollRoom);
            rebuildList();
            return;
        }
        scroll = Math.clamp(scroll, 0, maxScroll());
        placeWidgets();
        restoreListFocus();
    }

    private int buildPage(SettingsPage page, int y) {
        boolean grouped = false;
        boolean hidden = false;
        List<Preset> tiles = new ArrayList<>();
        List<PageRow> source = page.rows();
        for (int i = 0; i < source.size(); i++) {
            PageRow row = source.get(i);
            if (row instanceof Preset preset) {
                tiles.add(preset);
                boolean lastOfRun = i + 1 == source.size() || !(source.get(i + 1) instanceof Preset);
                if (lastOfRun && !hidden) {
                    y = addTiles(List.copyOf(tiles), y);
                }
                if (lastOfRun) {
                    tiles.clear();
                }
                continue;
            }
            if (row instanceof PageRow.Caption caption) {
                y = addCaption(caption, sectionChanged(source, i), y);
                hidden = caption.foldable() && isFolded(caption, source, i);
                grouped = true;
            } else if (hidden) {
                continue;
            } else if (row instanceof PageRow.Note note) {
                y = addNote(note, y);
                grouped = false;
            } else if (row instanceof Setting setting) {
                y = addSetting(setting, y, grouped);
            } else if (row instanceof TextListSetting list) {
                y = addList(list, y, grouped);
            }
        }
        return y;
    }

    private int buildSearchResults(int y) {
        String needle = query.toLowerCase(Locale.ROOT);
        for (SettingsPage page : pages) {
            List<PageRow> matches = page.rows().stream()
                    .filter(row -> (row instanceof Setting || row instanceof TextListSetting)
                            && ((Described) row).label().getString().toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
            if (matches.isEmpty()) {
                continue;
            }
            int headerH = rows.isEmpty() ? FIRST_CAPTION_H : CAPTION_H;
            rows.add(new ListRow(RowKind.HEADER, page.name(), y, headerH, false, List.of()));
            y += headerH;
            for (PageRow row : matches) {
                y = row instanceof Setting setting ? addSetting(setting, y, true) : addList((TextListSetting) row, y, true);
            }
        }
        return y;
    }

    /** A folded group starts closed unless something inside it differs from its default. */
    private boolean isFolded(PageRow.Caption caption, List<PageRow> source, int index) {
        return folded.computeIfAbsent(caption, key -> !sectionChanged(source, index));
    }

    private static boolean sectionChanged(List<PageRow> source, int captionIndex) {
        for (int i = captionIndex + 1; i < source.size() && !(source.get(i) instanceof PageRow.Caption); i++) {
            if (source.get(i) instanceof Described described && described.changed()) {
                return true;
            }
        }
        return false;
    }

    private int addCaption(PageRow.Caption caption, boolean changedInside, int y) {
        int h = rows.isEmpty() ? FIRST_CAPTION_H : CAPTION_H;
        List<Placed> placed = new ArrayList<>();
        if (caption.foldable()) {
            FoldToggle toggle = new FoldToggle(layout.listW(), caption.text(), () -> {
                folded.put(caption, !folded.getOrDefault(caption, true));
                rebuildList();
            });
            placed.add(new Placed(toggle, 0, h - 14));
        }
        register(placed);
        rows.add(new ListRow(RowKind.CAPTION, new CaptionState(caption, changedInside), y, h, false, placed));
        return y + h;
    }

    /** A caption with what the painter needs to know about its group. */
    private record CaptionState(PageRow.Caption caption, boolean changedInside) {
    }

    private int addNote(PageRow.Note note, int y) {
        int lines = font.split(note.text(), layout.listW()).size();
        int h = lines * 10 + 8;
        rows.add(new ListRow(RowKind.NOTE, note, y, h, false, List.of()));
        return y + h;
    }

    private int addSetting(Setting setting, int y, boolean grouped) {
        List<Placed> placed = new ArrayList<>();
        int controlDx = layout.controlDx();
        int controlW = layout.controlW();
        int rowH = SettingsLayout.ROW_H;
        int controlEnd = controlDx + controlW;

        switch (setting.kind()) {
            case TOGGLE -> placed.add(new Placed(new ToggleSwitch(look, setting),
                    controlEnd - ToggleSwitch.WIDTH, (rowH - ToggleSwitch.HEIGHT) / 2));
            case SLIDER -> placed.add(new Placed(new ValueSlider(look, controlW, setting), controlDx, 2));
            case NUMBER -> {
                int sliderW = controlW - NumberField.WIDTH - 4;
                placed.add(new Placed(new ValueSlider(look, sliderW, setting), controlDx, 2));
                placed.add(new Placed(new NumberField(look, setting), controlEnd - NumberField.WIDTH, 2));
            }
            case CHOICE -> placed.addAll(choiceWidgets(setting, controlDx, controlW));
            case ITEMS -> placed.add(new Placed(new ItemSlots(look, setting), controlEnd - ItemSlots.width(setting), 3));
            case COLOUR -> {
                ColourSwatches swatches = new ColourSwatches(look, setting, ColourSwatches.countFor(controlW, setting));
                placed.add(new Placed(swatches, controlEnd - swatches.getWidth(), 1));
            }
        }
        placed.add(new Placed(new ResetButton(look, setting, this::rebuildList), layout.resetDx(), 2));
        register(placed);
        rows.add(new ListRow(RowKind.SETTING, setting, y, rowH, grouped, placed));
        return y + rowH;
    }

    /** Up to three short choices become segments; anything else a dropdown. */
    private List<Placed> choiceWidgets(Setting setting, int controlDx, int controlW) {
        List<Component> choices = setting.choices();
        int gap = 2;
        int segmentW = (controlW - gap * (choices.size() - 1)) / choices.size();
        boolean fits = choices.size() <= 3 && choices.stream().allMatch(choice -> font.width(choice) + 8 <= segmentW);
        if (!fits) {
            return List.of(new Placed(new DropdownButton(look, controlW, setting, popup), controlDx, 2));
        }
        List<Placed> segments = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++) {
            int choice = i;
            boolean last = i == choices.size() - 1;
            int w = last ? controlW - (segmentW + gap) * i : segmentW;
            KitButton segment = new KitButton(look, 0, 0, w, 20, choices.get(i), () -> setting.set(choice))
                    .chosenWhen(() -> setting.value() == choice);
            segments.add(new Placed(segment, controlDx + (segmentW + gap) * i, 2));
        }
        return segments;
    }

    /** A wide row: label and reset on the first line, chips wrapping below, then a field and Add. */
    private int addList(TextListSetting list, int y, boolean grouped) {
        List<Placed> placed = new ArrayList<>();
        placed.add(new Placed(new ResetButton(look, list, this::rebuildList), layout.resetDx(), 2));

        int chipX = 0;
        int chipY = SettingsLayout.ROW_H + 2;
        for (String value : list.values()) {
            int chipW = Math.min(layout.listW(), Chip.width(value));
            if (chipX > 0 && chipX + chipW > layout.listW()) {
                chipX = 0;
                chipY += CHIP_PITCH;
            }
            placed.add(new Placed(new Chip(look, value, () -> {
                list.remove(value);
                rebuildList();
            }), chipX, chipY));
            chipX += chipW + 2;
        }
        int fieldY = list.values().isEmpty() ? chipY : chipY + CHIP_PITCH + 2;

        FramedTextField field = new FramedTextField(look, 0, 0, layout.listW() - ADD_W - 4, 20, list.placeholder(), false);
        placed.add(new Placed(field, 0, fieldY));
        placed.add(new Placed(new KitButton(look, 0, 0, ADD_W, 20, Component.translatable("almanac.list.add"),
                () -> addToList(list, field)), layout.listW() - ADD_W, fieldY));

        register(placed);
        int h = fieldY + 20 + 6;
        rows.add(new ListRow(RowKind.LIST, list, y, h, grouped, placed));
        return y + h;
    }

    private void addToList(TextListSetting list, FramedTextField field) {
        if (list.add(field.getValue())) {
            refocusList = list;
            rebuildList();
        }
    }

    private int addTiles(List<Preset> presets, int y) {
        int columns = presets.size() == 4 ? 2 : Math.min(presets.size(), layout.listW() >= 330 ? 3 : 2);
        int tileW = (layout.listW() - TILE_GAP * (columns - 1)) / columns;
        List<Placed> placed = new ArrayList<>();
        for (int i = 0; i < presets.size(); i++) {
            int column = i % columns;
            int line = i / columns;
            PresetTile tile = new PresetTile(look, tileW, presets.get(i), this::rebuildList);
            placed.add(new Placed(tile, column * (tileW + TILE_GAP), line * (PresetTile.HEIGHT + TILE_GAP)));
        }
        register(placed);
        int lines = (presets.size() + columns - 1) / columns;
        int h = lines * (PresetTile.HEIGHT + TILE_GAP);
        rows.add(new ListRow(RowKind.TILES, presets, y, h, false, placed));
        return y + h;
    }

    private void register(List<Placed> placed) {
        for (Placed widget : placed) {
            listWidgets.add(widget.widget());
            addWidget(widget.widget());
        }
    }

    private void placeWidgets() {
        for (ListRow row : rows) {
            int top = layout.listY() + row.y() - scroll;
            for (Placed placed : row.widgets()) {
                AbstractWidget widget = placed.widget();
                int x = layout.listX() + placed.dx();
                int y = top + placed.dy();
                if (widget instanceof FramedTextField field) {
                    field.setFrame(x, y, field.frameWidth(), field.frameHeight());
                } else {
                    widget.setX(x);
                    widget.setY(y);
                }
                int widgetTop = widget instanceof FramedTextField field ? field.frameY() : widget.getY();
                int widgetBottom = widget instanceof FramedTextField field ? field.frameY() + field.frameHeight() : widget.getBottom();
                widget.visible = widgetBottom > layout.listY() && widgetTop < layout.listBottom();
            }
        }
    }

    /** After adding to a list the rows are rebuilt; put the cursor back in that list's field. */
    private void restoreListFocus() {
        if (refocusList == null) {
            return;
        }
        for (ListRow row : rows) {
            if (row.source() == refocusList) {
                for (Placed placed : row.widgets()) {
                    if (placed.widget() instanceof FramedTextField field) {
                        setFocused(field);
                    }
                }
            }
        }
        refocusList = null;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - layout.listH());
    }

    private void scrollTo(int next) {
        int clamped = Math.clamp(next, 0, maxScroll());
        if (clamped != scroll) {
            scroll = clamped;
            placeWidgets();
        }
    }

    // Drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // In a world the vanilla look keeps the world visible behind a plain dim, with no blur.
        if (!look.isHouse() && minecraft.level != null) {
            extractTransparentBackground(graphics);
        } else {
            super.extractBackground(graphics, mouseX, mouseY, delta);
        }

        graphics.text(font, title, layout.contentX(), SettingsLayout.TITLE_Y, Palette.TITLE, true);
        String changed = changedLine();
        int changedX = layout.contentX() + font.width(title) + 10;
        if (changedX + font.width(changed) < layout.contentX() + layout.contentW()) {
            graphics.text(font, changed, changedX, SettingsLayout.TITLE_Y, Palette.TITLE_NOTE, true);
        }

        if (look.isHouse()) {
            Bevels.panel(graphics, look.house(), layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH());
        } else {
            VanillaFrame.panel(graphics, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH());
        }
    }

    private String changedLine() {
        long count = pages.stream().flatMap(page -> page.rows().stream())
                .filter(row -> row instanceof Described described && described.changed())
                .count();
        if (count == 0) {
            return Component.translatable("almanac.changed.none").getString();
        }
        return count == 1
                ? Component.translatable("almanac.changed.one").getString()
                : Component.translatable("almanac.changed.many", count).getString();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // While the popup is open, nothing under it reacts to the pointer.
        int pointerX = popup.isOpen() ? -1 : mouseX;
        int pointerY = popup.isOpen() ? -1 : mouseY;
        super.extractRenderState(graphics, pointerX, pointerY, delta);
        drawNavChangedMarks(graphics);

        boolean inList = pointerY >= layout.listY() && pointerY < layout.listBottom()
                && pointerX >= layout.panelX() && pointerX < layout.panelX() + layout.panelW();
        int listMouseX = inList ? pointerX : -1;
        int listMouseY = inList ? pointerY : -1;
        Described pointed = null;

        graphics.enableScissor(layout.panelX(), layout.listY(), layout.panelX() + layout.panelW(), layout.listBottom());
        if (rows.isEmpty()) {
            drawEmptyState(graphics);
        }
        drawGroupCards(graphics);
        for (ListRow row : rows) {
            int top = layout.listY() + row.y() - scroll;
            if (top + row.h() < layout.listY() || top > layout.listBottom()) {
                continue;
            }
            drawRow(graphics, row, top);
            if (listMouseY >= top && listMouseY < top + row.h()) {
                pointed = pointedIn(row, listMouseX, listMouseY);
            }
            for (Placed placed : row.widgets()) {
                if (placed.widget().visible) {
                    placed.widget().extractRenderState(graphics, listMouseX, listMouseY, delta);
                }
            }
        }
        graphics.disableScissor();
        drawScrollbar(graphics, pointerX, pointerY);

        if (pointed == null) {
            pointed = focusedRow();
        }
        if (pointed != null) {
            described = pointed;
        }
        drawDescription(graphics);
        popup.extract(graphics, mouseX, mouseY);
    }

    private Described pointedIn(ListRow row, int mouseX, int mouseY) {
        if (row.kind() == RowKind.TILES) {
            for (Placed placed : row.widgets()) {
                if (placed.widget() instanceof PresetTile tile && tile.isMouseOver(mouseX, mouseY)) {
                    return tile.preset();
                }
            }
            return null;
        }
        return row.source() instanceof Described described ? described : null;
    }

    private void drawNavChangedMarks(GuiGraphicsExtractor graphics) {
        for (int i = 0; i < navButtons.size(); i++) {
            if (pages.get(i).changed()) {
                KitButton button = navButtons.get(i);
                drawChangedMark(graphics, button.getX() + 2, button.getY() + 2);
            }
        }
    }

    private void drawEmptyState(GuiGraphicsExtractor graphics) {
        Component text = Component.translatable("almanac.search.empty", query);
        int centreY = layout.listY() + layout.listH() / 2 - 4;
        String line = Ink.trim(font, text.getString(), layout.listW());
        graphics.text(font, line, layout.listX() + (layout.listW() - font.width(line)) / 2, centreY, look.dim(), look.panelShadow());
    }

    /** One card behind each run of rows that follows a caption. */
    private void drawGroupCards(GuiGraphicsExtractor graphics) {
        int start = -1;
        int end = -1;
        for (ListRow row : rows) {
            if (row.grouped()) {
                if (start < 0) {
                    start = row.y();
                }
                end = row.y() + row.h();
            } else if (start >= 0) {
                drawGroupCard(graphics, start, end);
                start = -1;
            }
        }
        if (start >= 0) {
            drawGroupCard(graphics, start, end);
        }
    }

    private void drawGroupCard(GuiGraphicsExtractor graphics, int startY, int endY) {
        int x = layout.listX() - 4;
        int y = layout.listY() + startY - scroll - 2;
        int w = layout.listW() + 8;
        int h = endY - startY + 3;
        if (look.isHouse()) {
            Bevels.group(graphics, look.house(), x, y, w, h);
        } else {
            graphics.fill(x, y, x + w, y + h, 0x14000000);
            graphics.fill(x, y, x + w, y + 1, 0x50000000);
            graphics.fill(x, y, x + 1, y + h, 0x50000000);
            graphics.fill(x, y + h - 1, x + w, y + h, 0x70FFFFFF);
            graphics.fill(x + w - 1, y, x + w, y + h, 0x70FFFFFF);
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, ListRow row, int top) {
        switch (row.kind()) {
            case CAPTION -> drawCaption(graphics, (CaptionState) row.source(), row, top + row.h() - 12);
            case HEADER -> drawPageHeader(graphics, (Component) row.source(), top + row.h() - 13);
            case NOTE -> drawNote(graphics, (PageRow.Note) row.source(), top);
            case SETTING, LIST -> drawSettingLabel(graphics, (Described) row.source(), row, top);
            case TILES -> {
            }
        }
    }

    private void drawSettingLabel(GuiGraphicsExtractor graphics, Described setting, ListRow row, int top) {
        int labelColour = look.label(true);
        int roomDx = row.kind() == RowKind.LIST ? layout.resetDx() : layout.controlDx();
        String label = Ink.trim(font, setting.label().getString(), roomDx - 8);
        graphics.text(font, label, layout.listX(), top + 8, labelColour, look.panelShadow());
        if (setting.changed()) {
            drawChangedMark(graphics, layout.listX() + font.width(label) + 3, top + 9);
        }
        int index = rows.indexOf(row);
        boolean nextInGroup = index + 1 < rows.size() && rows.get(index + 1).grouped();
        if (row.grouped() && nextInGroup) {
            graphics.fill(layout.listX(), top + row.h() - 1, layout.listX() + layout.listW(), top + row.h(), look.rule());
        }
    }

    private void drawChangedMark(GuiGraphicsExtractor graphics, int x, int y) {
        if (look.isHouse()) {
            graphics.fill(x + 1, y + 1, x + 4, y + 4, look.house().changedMark());
        } else {
            VanillaFrame.changedMark(graphics, x, y);
        }
    }

    private void drawCaption(GuiGraphicsExtractor graphics, CaptionState state, ListRow row, int y) {
        PageRow.Caption caption = state.caption();
        int x = layout.listX();
        if (!caption.foldable()) {
            drawPlainCaption(graphics, caption.text(), x, y);
            return;
        }
        FoldToggle toggle = (FoldToggle) row.widgets().getFirst().widget();
        boolean hot = toggle.lit();
        boolean isFolded = folded.getOrDefault(caption, true);
        int colour = look.isHouse() ? (hot ? look.house().textStrong() : look.house().textDim()) : Palette.PANEL_TEXT;
        if (hot) {
            graphics.fill(x - 2, y - 2, x + layout.listW(), y + 10, look.isHouse() ? look.house().faceHigh() : Palette.PANEL_RULE);
        }
        Glyphs chevron = isFolded ? Glyphs.CHEVRON_RIGHT : Glyphs.CHEVRON_DOWN;
        chevron.draw(graphics, x + (7 - chevron.width()) / 2, y + (8 - chevron.height()) / 2, colour);
        String text = look.isHouse() ? caption.text().getString().toUpperCase(Locale.ROOT) : caption.text().getString();
        graphics.text(font, text, x + 9, y, colour, false);
        int textEnd = x + 9 + font.width(text);
        if (isFolded && state.changedInside()) {
            drawChangedMark(graphics, textEnd + 3, y + 1);
        }
        if (!look.isHouse() && textEnd + 6 < x + layout.listW()) {
            graphics.fill(textEnd + 6, y + 4, x + layout.listW(), y + 5, Palette.PANEL_RULE);
        }
    }

    private void drawPlainCaption(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        if (look.isHouse()) {
            graphics.text(font, text.getString().toUpperCase(Locale.ROOT), x, y, look.house().textDim(), false);
            return;
        }
        graphics.text(font, text, x, y, Palette.PANEL_TEXT, false);
        int ruleX = x + font.width(text) + 6;
        graphics.fill(ruleX, y + 4, x + layout.listW(), y + 5, Palette.PANEL_RULE);
    }

    /** Search results are grouped under each page's name, with a thicker rule. */
    private void drawPageHeader(GuiGraphicsExtractor graphics, Component name, int y) {
        Component text = Component.literal(name.getString().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
        graphics.text(font, text, layout.listX(), y, look.strong(), false);
        int ruleColour = look.isHouse() ? look.house().accent() : 0xFF707070;
        graphics.fill(layout.listX() + font.width(text) + 6, y + 3, layout.listX() + layout.listW(), y + 5, ruleColour);
    }

    private void drawNote(GuiGraphicsExtractor graphics, PageRow.Note note, int top) {
        int y = top + 4;
        for (FormattedCharSequence line : font.split(note.text(), layout.listW())) {
            graphics.text(font, line, layout.listX(), y, look.label(true), look.panelShadow());
            y += 10;
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int max = maxScroll();
        float position = max == 0 ? 0 : (float) scroll / max;
        if (!look.isHouse()) {
            if (scrollRoom) {
                VanillaFrame.creativeScrollbar(graphics, layout.vanillaScrollbarX(), layout.listY(), layout.listH(), position, max > 0);
            }
            return;
        }
        if (max == 0) {
            return;
        }
        HouseColors house = look.house();
        int x = layout.panelX() + layout.panelW() - 5;
        int thumbH = Math.max(12, layout.listH() * layout.listH() / contentHeight);
        int thumbY = layout.listY() + Math.round((layout.listH() - thumbH) * position);
        boolean hot = draggingScrollbar || onScrollbar(mouseX, mouseY);
        graphics.fill(x, layout.listY(), x + 3, layout.listBottom(), house.light() ? 0x24000000 : 0x22FFFFFF);
        int thumb = house.light() ? (hot ? 0xFF4A4A4A : 0xFF7E7E7E) : (hot ? 0xCCFFFFFF : 0x66FFFFFF);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbH, thumb);
    }

    private boolean onScrollbar(double mouseX, double mouseY) {
        if (maxScroll() == 0 || mouseY < layout.listY() || mouseY >= layout.listBottom()) {
            return false;
        }
        int left = look.isHouse() ? layout.panelX() + layout.panelW() - 8 : layout.vanillaScrollbarX();
        int right = look.isHouse() ? layout.panelX() + layout.panelW() - 2 : layout.vanillaScrollbarX() + VanillaFrame.SCROLLBAR_W;
        return mouseX >= left && mouseX < right;
    }

    private void scrollToPointer(double mouseY) {
        float position = (float) ((mouseY - layout.listY()) / layout.listH());
        scrollTo(Math.round(position * maxScroll()));
    }

    private void drawDescription(GuiGraphicsExtractor graphics) {
        int x = layout.describeX();
        int y = layout.describeY();
        int w = layout.describeW();
        int h = layout.describeH();
        if (layout.describeInNav()) {
            if (look.isHouse()) {
                Bevels.panel(graphics, look.house(), x, y, w, h);
            } else {
                VanillaFrame.panel(graphics, x, y, w, h);
            }
        }
        int pad = look.isHouse() ? 6 : 9;
        int textX = x + pad;
        int textW = w - 2 * pad;
        int lineY = y + pad;
        drawDescriptionHeading(graphics, textX, lineY, textW);
        lineY += look.isHouse() ? 15 : 12;

        graphics.enableScissor(x, lineY, x + w, y + h - 4);
        if (described == null) {
            for (FormattedCharSequence line : font.split(Component.translatable("almanac.description.empty"), textW)) {
                graphics.text(font, line, textX, lineY, look.dim(), look.panelShadow());
                lineY += 10;
            }
            graphics.disableScissor();
            return;
        }
        graphics.text(font, described.label().copy().withStyle(ChatFormatting.BOLD), textX, lineY, look.strong(), look.panelShadow());
        lineY += 10;
        Component value = described.valueText();
        if (value != null) {
            Component bold = value.copy().withStyle(ChatFormatting.BOLD);
            graphics.text(font, bold, textX, lineY, look.value(), look.panelShadow());
            Component note = Component.translatable(described.changed() ? "almanac.description.changed" : "almanac.description.default")
                    .withStyle(ChatFormatting.ITALIC);
            graphics.text(font, note, textX + font.width(bold) + font.width("  "), lineY, look.dim(), look.panelShadow());
            lineY += 10;
        }
        lineY += 2;
        for (FormattedCharSequence line : font.split(described.description(), textW)) {
            graphics.text(font, line, textX, lineY, look.label(true), look.panelShadow());
            lineY += 10;
        }
        if (!described.details().isEmpty()) {
            lineY += 6;
            for (Component detail : described.details()) {
                for (FormattedCharSequence line : font.split(detail, textW)) {
                    graphics.text(font, line, textX, lineY, look.label(true), look.panelShadow());
                    lineY += 10;
                }
            }
        }
        graphics.disableScissor();
    }

    private void drawDescriptionHeading(GuiGraphicsExtractor graphics, int x, int y, int w) {
        Component heading = Component.translatable("almanac.description");
        if (look.isHouse()) {
            graphics.text(font, heading.getString().toUpperCase(Locale.ROOT), x, y, look.house().textDim(), false);
            graphics.fill(x, y + 10, x + w, y + 11, look.house().accent());
        } else {
            graphics.text(font, heading, x, y, Palette.PANEL_TEXT, false);
            graphics.fill(x + font.width(heading) + 6, y + 4, x + w, y + 5, Palette.PANEL_RULE);
        }
    }

    private Described focusedRow() {
        GuiEventListener focused = getFocused();
        for (ListRow row : rows) {
            for (Placed placed : row.widgets()) {
                if (placed.widget() == focused) {
                    return placed.widget() instanceof PresetTile tile ? tile.preset() : pointedIn(row, -1, -1);
                }
            }
        }
        return null;
    }

    // Input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (popup.mouseClicked(event)) {
            return true;
        }
        if (onScrollbar(event.x(), event.y())) {
            draggingScrollbar = true;
            scrollToPointer(event.y());
            return true;
        }
        boolean inList = event.y() >= layout.listY() && event.y() < layout.listBottom()
                && event.x() >= layout.panelX() && event.x() < layout.panelX() + layout.panelW();
        if (inList) {
            return super.mouseClicked(event, doubleClick);
        }
        // Rows scrolled under the panel edge are still partly drawn; they must not take clicks there.
        for (GuiEventListener child : children()) {
            if (!listWidgets.contains(child) && child.mouseClicked(event, doubleClick)) {
                setFocused(child);
                setDragging(true);
                return true;
            }
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollToPointer(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!popup.mouseScrolled(vertical)) {
            scrollTo(scroll - (int) Math.round(vertical * SCROLL_STEP));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popup.keyPressed(event)) {
            return true;
        }
        boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
        if (enter && getFocused() instanceof NumberField field) {
            field.commit();
            setFocused(null);
            return true;
        }
        if (enter && getFocused() instanceof FramedTextField field && field != search) {
            for (ListRow row : rows) {
                if (row.source() instanceof TextListSetting list && row.widgets().stream().anyMatch(placed -> placed.widget() == field)) {
                    addToList(list, field);
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        super.setFocused(focused);
        // Keyboard focus moving onto a row out of view scrolls it into view.
        if (focused instanceof AbstractWidget widget && listWidgets.contains(widget) && minecraft.getLastInputType().isKeyboard()) {
            int above = widget.getY() - layout.listY();
            int below = widget.getBottom() - layout.listBottom();
            if (above < 0) {
                scrollTo(scroll + above - 4);
            } else if (below > 0) {
                scrollTo(scroll + below + 4);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    /** Runs however the screen goes away (Done, Escape, or another screen replacing it), so changes are never lost. */
    @Override
    public void removed() {
        save.run();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
