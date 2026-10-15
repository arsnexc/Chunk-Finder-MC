package dev.chunkfinder.ui;

import dev.chunkfinder.ui.UiGeometry.Rect;

/** Responsive normalized placement, clamped independently of window size and HUD scale. */
public final class HudAnchor {
    private HudAnchor(){}
    public static Rect place(int width,int height,int boxWidth,int boxHeight,int x,int y){
        int minX=Math.min(8,Math.max(0,width-boxWidth)),minY=Math.min(8,Math.max(0,height-boxHeight));
        int travelX=Math.max(0,width-boxWidth-minX-8),travelY=Math.max(0,height-boxHeight-minY-8);
        return new Rect(minX+(int)Math.round(travelX*Math.max(0,Math.min(1000,x))/1000.0),
                minY+(int)Math.round(travelY*Math.max(0,Math.min(1000,y))/1000.0),boxWidth,boxHeight);
    }
    public static int normalized(double pixel,int available,int extent,boolean snap){
        int min=Math.min(8,Math.max(0,available-extent)),travel=Math.max(0,available-extent-min-8);
        if(travel==0||!Double.isFinite(pixel))return 0;
        if(snap)pixel=Math.rint(pixel/8)*8;
        return Math.max(0,Math.min(1000,(int)Math.round((pixel-min)*1000/travel)));
    }
}
