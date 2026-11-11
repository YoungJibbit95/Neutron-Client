package me.youngjibbit.module.impl.render;

/** Pure compact geometry, shared by measurement, painting and native fixtures. */
final class CreativeHudLayout {
    private CreativeHudLayout() {}
    static final class Rect {
        final float x,y,width,height;
        Rect(float x,float y,float width,float height){this.x=x;this.y=y;this.width=width;this.height=height;}
        float right(){return x+width;}float bottom(){return y+height;}
    }
    static final class Fit {
        final float scale,x,y,width,height;
        Fit(float logicalWidth,float logicalHeight,float availableWidth,float availableHeight){
            float aw=positive(availableWidth),ah=positive(availableHeight);
            scale=Math.min(1,Math.min(aw/positive(logicalWidth),ah/positive(logicalHeight)));
            width=positive(logicalWidth)*scale;height=positive(logicalHeight)*scale;
            x=(aw-width)/2;y=(ah-height)/2;
        }
        Rect place(Rect r){return new Rect(x+r.x*scale,y+r.y*scale,r.width*scale,r.height*scale);}
    }
    private static float positive(float value){return Float.isFinite(value)&&value>0?value:1;}

    static final class Target {
        final float width=142,height=34,textX=44,nameY=4,infoY=17;
        final Rect portrait=new Rect(1,3,28,28),body=new Rect(35,0,107,34);
        final Rect health=new Rect(44,28,89,2.5f),absorption=new Rect(44,32,89,1);
        Target(float requestedWidth){}
        Fit fit(float width,float height){return new Fit(this.width,this.height,width,height);}
    }
    static final class Session {
        final float width=154,height=40;
        final Rect time=new Rect(0,0,61,40),statistics=new Rect(65,0,89,40);
        final Rect kills=new Rect(71,17,34,18),deaths=new Rect(113,17,34,18);
        Fit fit(float width,float height){return new Fit(this.width,this.height,width,height);}
    }
    static final class Armor {
        final Rect[] cells;final float width,height;
        Armor(boolean horizontal,boolean[] occupied,float[] labelWidths){
            if(occupied==null||labelWidths==null||occupied.length!=labelWidths.length)
                throw new IllegalArgumentException("One label width per equipment slot is required");
            cells=new Rect[occupied.length];if(cells.length==0){width=height=0;return;}
            float[] widths=new float[cells.length];float rowWidth=0,emptyWidth=0;int filled=0,missing=0;
            for(int i=0;i<cells.length;i++){
                float label=Float.isFinite(labelWidths[i])?Math.max(0,labelWidths[i]):0;
                widths[i]=occupied[i]?(horizontal?Math.max(24,Math.min(86,label+6)):Math.max(54,Math.min(130,label+29))):20;
                if(occupied[i]){filled++;rowWidth=Math.max(rowWidth,widths[i]);}
                else{missing++;emptyWidth+=widths[i];}
            }
            if(horizontal){
                float cursor=0,h=filled>0?30:20;
                for(int i=0;i<cells.length;i++){
                    float ch=occupied[i]?30:20;cells[i]=new Rect(cursor,(h-ch)/2,widths[i],ch);cursor+=widths[i]+3;
                }
                width=cursor-3;height=h;
            }else{
                if(missing>0)emptyWidth+=3*(missing-1);
                float w=Math.max(rowWidth,emptyWidth),cursor=0,emptyX=0;
                for(int i=0;i<cells.length;i++)if(occupied[i]){cells[i]=new Rect(0,cursor,w,22);cursor+=25;}
                float emptyY=filled>0?cursor:0;
                for(int i=0;i<cells.length;i++)if(!occupied[i]){cells[i]=new Rect(emptyX,emptyY,widths[i],20);emptyX+=widths[i]+3;}
                width=w;height=missing>0?emptyY+20:cursor-3;
            }
        }
    }
}
