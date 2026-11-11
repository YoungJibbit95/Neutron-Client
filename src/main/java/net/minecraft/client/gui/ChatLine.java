package net.minecraft.client.gui;

import net.minecraft.util.IChatComponent;

public class ChatLine
{
    /** GUI Update Counter value this Line was created at */
    private final int updateCounterCreated;
    private final IChatComponent lineString;
    /** Nonzero values identify replaceable messages. */
    private final int chatLineID;
    private final me.youngjibbit.gui.ChatMotion.Line presentation;
    private final IChatComponent source;
    private final int sourceOffset;

    public ChatLine(int counter, IChatComponent component, int id)
    {
        this(counter, component, id, true);
    }

    public ChatLine(int counter, IChatComponent component, int id, boolean animateEntrance)
    {
        this(counter, component, id, animateEntrance, component, 0);
    }

    public ChatLine(int counter, IChatComponent component, int id, boolean animateEntrance,
                    IChatComponent source, int sourceOffset)
    {
        this.lineString = component;
        this.updateCounterCreated = counter;
        this.chatLineID = id;
        this.presentation = new me.youngjibbit.gui.ChatMotion.Line(animateEntrance ? System.nanoTime() : Long.MIN_VALUE);
        this.source = source;
        this.sourceOffset = sourceOffset;
    }

    public me.youngjibbit.gui.ChatMotion.Line presentation() { return presentation; }
    public IChatComponent source() { return source; }
    public int sourceOffset() { return sourceOffset; }
    public IChatComponent getChatComponent() { return this.lineString; }
    public int getUpdatedCounter() { return this.updateCounterCreated; }
    public int getChatLineID() { return this.chatLineID; }
}
