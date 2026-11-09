package me.youngjibbit.module.impl.client.island;

/** Screen-space GUI geometry; no Minecraft, GL, font, config or gameplay dependencies. */
public final class IslandLayout {
    private IslandLayout() {}

    public static final class Frame {
        public final int viewportWidth, viewportHeight;
        public final float x, y, width, height, radius, opacity;

        private Frame(int screenW, int screenH, float width, float height, float radius, float opacity) {
            viewportWidth = Math.max(1, screenW);
            viewportHeight = Math.max(1, screenH);
            float limit = Math.min(380, Math.min(viewportWidth * .85f, Math.max(1, viewportWidth - 16)));
            this.width = clamp(width, Math.min(1, limit), limit);
            this.height = clamp(height, Math.min(1,limit), Math.max(1, viewportHeight - 8));
            this.radius = clamp(radius, 0, Math.min(this.width,this.height) / 2);
            this.opacity = clamp(opacity, 0, 1);
            this.x = clamp((viewportWidth - this.width) / 2, 0, viewportWidth - this.width);
            this.y = clamp(8, 0, viewportHeight - this.height);
        }

        /** Retargetable geometry centered at the same safe-area anchor. */
        public Frame withGeometry(float width, float height, float radius, float opacity) {
            return new Frame(viewportWidth, viewportHeight, finite(width, this.width),
                    finite(height, this.height), finite(radius, this.radius), finite(opacity, this.opacity));
        }
    }

    public static Frame forState(IslandStateMachine.State state, int width, int height) {
        if (state == null) state = IslandStateMachine.State.IDLE;
        switch (state) {
            case TARGET: return new Frame(width, height, 280, 56, 20, 1);
            case SCAFFOLD: return new Frame(width, height, 260, 48, 20, .98f);
            case MODULE_TOAST: return new Frame(width, height, 240, 44, 20, 1);
            case POSSIBLE_SETBACK: return new Frame(width, height, 300, 55, 22, 1);
            case CLOCK_REMINDER: return clockFrame(width,height);
            case INVENTORY_ACTIVITY: return new Frame(width, height, 250, 46, 19, 1);
            case IDLE:
            default: return new Frame(width, height, 245, 34, 17, .96f);
        }
    }

    /**
     * The neutral-glass Neutron 2.0 silhouette is compact in idle,
     * and can grow only for independently verified target health. Legacy
     * dimensions remain byte-for-byte equivalent to forState(state,w,h).
     */
    public static Frame forState(IslandStateMachine.State state,int width,int height,boolean modern){
        if(!modern)return forState(state,width,height);
        if(state==null)state=IslandStateMachine.State.IDLE;
        switch(state){
            case TARGET:return new Frame(width,height,266,52,17,1);
            case SCAFFOLD:return new Frame(width,height,248,44,17,1);
            case MODULE_TOAST:return new Frame(width,height,240,38,17,1);
            case POSSIBLE_SETBACK:return new Frame(width,height,270,50,17,1);
            case CLOCK_REMINDER:return clockFrame(width,height);
            case INVENTORY_ACTIVITY:return new Frame(width,height,236,42,17,1);
            case IDLE:
            default:return new Frame(width,height,234,34,17,1);
        }
    }

    /** The whole Island becomes a circular clock; shrink both axes together in tiny viewports. */
    private static Frame clockFrame(int width,int height){
        Frame bounded=new Frame(width,height,64,64,32,1);
        float diameter=Math.min(bounded.width,bounded.height);
        return bounded.withGeometry(diameter,diameter,diameter/2,1);
    }

    /** Trim by code points: never split a supplementary Unicode character. */
    public static String limitText(String input, int maxCodePoints) {
        if (input == null || maxCodePoints <= 0) return "";
        String text = input.replace('\r', ' ').replace('\n', ' ').trim();
        int points = text.codePointCount(0, text.length());
        if (points <= maxCodePoints) return text;
        if (maxCodePoints <= 3) return text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints - 3)) + "...";
    }

    private static float finite(float n, float fallback) { return Float.isFinite(n) ? n : fallback; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
