package me.youngjibbit.module.impl.render;

/** Pure geometry consumed by the three explicit Creative renderers. */
final class CreativeHudLayout {
    private CreativeHudLayout() {}

    static final class Rect {
        final float x,y,width,height;
        Rect(float x,float y,float width,float height) {
            this.x=x;this.y=y;this.width=width;this.height=height;
        }
        float right(){return x+width;}
        float bottom(){return y+height;}
    }

    /** Fits a complete composition uniformly, including very small morph/view rectangles. */
    static final class Fit {
        final float scale,x,y,width,height;
        Fit(float logicalWidth,float logicalHeight,float availableWidth,float availableHeight) {
            float aw=positive(availableWidth),ah=positive(availableHeight);
            scale=Math.min(1,Math.min(aw/positive(logicalWidth),ah/positive(logicalHeight)));
            width=positive(logicalWidth)*scale;height=positive(logicalHeight)*scale;
            x=(aw-width)/2;y=(ah-height)/2;
        }
        Rect place(Rect r){return new Rect(x+r.x*scale,y+r.y*scale,r.width*scale,r.height*scale);}
    }
    private static float positive(float value){return Float.isFinite(value)&&value>0?value:1;}

    static final class Target {
        final float width,height=46,textX=49,nameY=4,infoY=19;
        final Rect portrait=new Rect(0,7,32,32),body,health,absorption;
        Target(float requestedWidth) {
            width=Math.max(196,Math.min(244,positive(requestedWidth)));
            body=new Rect(40,0,width-40,44);
            health=new Rect(textX,33,width-textX-9,3);
            absorption=new Rect(textX,39,width-textX-9,1.5f);
        }
        Fit fit(float width,float height){return new Fit(this.width,this.height,width,height);}
    }

    static final class Session {
        final float width=236,height=64;
        final Rect time=new Rect(0,0,86,64),statistics=new Rect(90,0,146,64);
        final Rect kills=new Rect(100,29,56,28),deaths=new Rect(168,29,56,28);
        Fit fit(float width,float height){return new Fit(this.width,this.height,width,height);}
    }

    static final class Armor {
        final Rect[] cells;
        final float width,height;
        Armor(boolean horizontal,boolean[] occupied,float[] labelWidths) {
            if(occupied==null||labelWidths==null||occupied.length!=labelWidths.length)
                throw new IllegalArgumentException("One label width per equipment slot is required");
            cells=new Rect[occupied.length];
            if(cells.length==0){width=height=0;return;}
            float[] widths=new float[cells.length];
            float rowWidth=0,emptyWidth=0;int filled=0,missing=0;
            for(int i=0;i<cells.length;i++) {
                float label=Float.isFinite(labelWidths[i])?Math.max(0,labelWidths[i]):0;
                widths[i]=occupied[i]?(horizontal?Math.max(28,Math.min(96,label+8)):
                        Math.max(62,Math.min(144,label+32))):Math.max(22,Math.min(66,label+6));
                if(occupied[i]){filled++;rowWidth=Math.max(rowWidth,widths[i]);}
                else{missing++;emptyWidth+=widths[i];}
            }
            if(horizontal) {
                float cursor=0;float h=filled>0?34:18;
                for(int i=0;i<cells.length;i++) {
                    float ch=occupied[i]?34:18;
                    cells[i]=new Rect(cursor,(h-ch)/2,widths[i],ch);
                    cursor+=widths[i]+3;
                }
                width=cursor-3;height=h;
            } else {
                if(missing>0)emptyWidth+=3*(missing-1);
                float w=Math.max(rowWidth,emptyWidth),cursor=0,emptyX=0;
                for(int i=0;i<cells.length;i++)if(occupied[i]) {
                    cells[i]=new Rect(0,cursor,w,22);cursor+=25;
                }
                float emptyY=filled>0?cursor:0;
                for(int i=0;i<cells.length;i++)if(!occupied[i]) {
                    cells[i]=new Rect(emptyX,emptyY,widths[i],18);emptyX+=widths[i]+3;
                }
                width=w;height=missing>0?emptyY+18:cursor-3;
            }
        }
    }
}
