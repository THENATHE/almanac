package com.thenathe.almanac.client.settings;

/**
 * Screen geometry, worked out on every resize: a centred block at most 640 wide, a navigation
 * column of about three tenths, the panel 24 from the top and the footer 26 from the bottom.
 */
public record SettingsLayout(
        int contentX, int contentW,
        int navX, int navW, int navTop, int navStep, int navH, boolean sideTabs,
        int panelX, int panelY, int panelW, int panelH,
        int listX, int listY, int listW, int listBottom,
        int describeX, int describeY, int describeW, int describeH, boolean describeInNav,
        int footerY) {

    public static final int TITLE_Y = 8;
    public static final int PANEL_Y = 24;
    public static final int FOOTER_FROM_BOTTOM = 26;
    public static final int ROW_H = 24;
    public static final int SEARCH_H = 18;
    private static final int SCROLLBAR_ROOM = 18;

    /**
     * @param scrollRoom whether the vanilla look's scrollbar needs room beside the list. The
     *                   house look's thin bar sits in the panel padding and needs none.
     */
    public static SettingsLayout of(int width, int height, boolean house, int tabs, boolean scrollRoom) {
        int contentW = Math.min(640, width - 20);
        int contentX = (width - contentW) / 2;
        int navW = Math.clamp(contentW * 3 / 10, 90, 180);
        int panelX = contentX + navW + 6;
        int panelW = contentW - navW - 6;
        int footerY = height - FOOTER_FROM_BOTTOM;
        int panelH = Math.max(40, footerY - 6 - PANEL_Y);
        int navTop = PANEL_Y + 24;

        // Vanilla side tabs need 28 px each; when they don't fit, both looks use plain buttons.
        int navRoom = panelH - 24;
        boolean sideTabs = !house && tabs * 28 <= navRoom;
        int navStep = sideTabs ? 28 : Math.clamp(navRoom / Math.max(1, tabs), 12, 22);
        int navH = sideTabs ? 27 : Math.min(20, navStep - 1);

        int listX = panelX + (house ? 10 : 12);
        int listY = PANEL_Y + (house ? 6 : 9);
        int listW = panelW - (house ? 20 : 24 + (scrollRoom ? SCROLLBAR_ROOM : 0));
        int listBottom = PANEL_Y + panelH - (house ? 4 : 8);

        // The description sits under the tabs, or in a strip under the list when the tabs leave no room.
        int describeX = contentX;
        int describeY = navTop + tabs * navStep + 4;
        int describeW = navW;
        int describeH = PANEL_Y + panelH - describeY;
        boolean describeInNav = describeH >= 44;
        if (!describeInNav) {
            describeH = 50;
            listBottom -= describeH + 4;
            int inset = house ? 4 : 10;
            describeX = panelX + inset;
            describeY = listBottom + 4;
            describeW = panelW - 2 * inset;
        }

        return new SettingsLayout(contentX, contentW, contentX, navW, navTop, navStep, navH, sideTabs,
                panelX, PANEL_Y, panelW, panelH, listX, listY, listW, listBottom,
                describeX, describeY, describeW, describeH, describeInNav, footerY);
    }

    public int navY(int tab) {
        return navTop + tab * navStep;
    }

    public int listH() {
        return listBottom - listY;
    }

    public int controlW() {
        return Math.clamp(listW * 2 / 5, 90, 150);
    }

    /** Offsets from listX: the reset button is flush right, the control column just before it. */
    public int resetDx() {
        return listW - ResetButton.SIZE;
    }

    public int controlDx() {
        return resetDx() - 4 - controlW();
    }

    public int vanillaScrollbarX() {
        return panelX + panelW - 8 - VanillaFrame.SCROLLBAR_W;
    }
}
