package me.youngjibbit.utils.render;

/** One left-button gesture has one HUD owner, even when HUDs overlap or an owner disappears. */
public final class HudDrag {
    private static Object owner;
    private static boolean consumed, wasChat;
    private HudDrag() {}
    public static void context(boolean chat, boolean down) {
        if (!down) { owner = null; consumed = false; }
        if (!chat) { owner = null; consumed = down; }
        else if (!wasChat && down) consumed = true;
        wasChat = chat;
    }
    public static boolean capture(Object candidate, boolean chat, boolean down, boolean pressed, boolean hit) {
        chat=chat&&me.youngjibbit.module.impl.client.HUD.editable();
        if (!chat || !down) {
            cancel(candidate);
            if (!down) consumed = false;
            else consumed = true;
            return false;
        }
        if (pressed && hit && !consumed) { owner = candidate; consumed = true; }
        return owner == candidate;
    }
    public static void cancel(Object candidate) { if (owner == candidate) owner = null; }
    public static boolean roundedContains(float mx,float my,float x,float y,float w,float h,float radius) {
        if(w<=0||h<=0||mx<x||my<y||mx>=x+w||my>=y+h)return false;
        float r=Math.min(radius,Math.min(w,h)/2),dx=Math.max(Math.abs(mx-x-w/2)-(w/2-r),0),dy=Math.max(Math.abs(my-y-h/2)-(h/2-r),0);
        return dx*dx+dy*dy<=r*r;
    }
}
