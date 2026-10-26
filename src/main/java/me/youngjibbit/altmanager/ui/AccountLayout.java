package me.youngjibbit.altmanager.ui;

import me.youngjibbit.clickgui.utils.ClipRect;

/** The same responsive geometry drives drawing, scrolling, and hit testing. */
public final class AccountLayout {
    public final ClipRect window, search, list, details, add, importButton, close, more, login, remove;
    public final float sidebar, contentX, contentWidth;
    public final boolean compact;
    public static final float ROW = 56;

    public AccountLayout(float screenWidth, float screenHeight) {
        float w = Math.min(820, Math.max(1, screenWidth - 28));
        float h = Math.min(490, Math.max(1, screenHeight - 24));
        window = new ClipRect((screenWidth-w)/2, (screenHeight-h)/2, w, h);
        compact = h < 320;
        sidebar = w >= 560 && !compact ? 108 : 0;
        contentX = window.x + sidebar + 18;
        contentWidth = w - sidebar - 36;
        close = new ClipRect(window.x+w-31, window.y+7, 22, 20);
        more = new ClipRect(window.x+w-59, window.y+7, 22, 20);
        float addWidth = w < 440 ? 72 : 106;
        add = new ClipRect(contentX+contentWidth-addWidth, window.y+(compact?36:46), addWidth, compact?22:28);
        importButton = new ClipRect(add.x-70, add.y, 62, add.height);
        boolean split = w >= 590 && !compact;
        float detailWidth = split ? 174 : 0;
        float leftWidth = contentWidth - (split ? detailWidth+14 : 0);
        float searchY = window.y + (compact ? 64 : 101);
        search = new ClipRect(contentX, searchY, leftWidth, 28);
        float top = searchY + (compact?36:sidebar == 0 ? 66 : 48);
        float bottom = window.y+h-(split ? 32 : compact?63:70);
        list = new ClipRect(contentX, top, leftWidth, Math.max(1,bottom-top));
        details = split ? new ClipRect(contentX+leftWidth+14, searchY, detailWidth, bottom-searchY) : new ClipRect(0,0,0,0);
        login = split ? new ClipRect(details.x+12, details.y+details.height-76, details.width-24,28)
                : new ClipRect(contentX, window.y+h-(compact?55:61), Math.max(72,leftWidth-82),compact?24:27);
        remove = split ? new ClipRect(details.x+12, details.y+details.height-41, details.width-24,25)
                : new ClipRect(contentX+leftWidth-74, login.y,74,login.height);
    }

    public float maxScroll(int count) { return Math.max(0, count*ROW-6-list.height); }
    public ClipRect row(int index, float scroll) { return new ClipRect(list.x,list.y+index*ROW-scroll,list.width-5,ROW-6); }
}
