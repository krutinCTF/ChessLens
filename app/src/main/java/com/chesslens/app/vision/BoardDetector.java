package com.chesslens.app.vision;

import android.graphics.Bitmap;

public final class BoardDetector {
    public static final class Board {
        public final int left,top,side; public final float square; public final double confidence;
        Board(int x,int y,int s,double c){left=x;top=y;side=s;square=s/8f;confidence=c;}
    }
    public static Board locate(Bitmap src){
        if(src==null)return null;
        int ow=src.getWidth(),oh=src.getHeight();
        float scale=Math.min(1f,360f/Math.max(ow,oh));
        int w=Math.max(64,Math.round(ow*scale)),h=Math.max(64,Math.round(oh*scale));
        Bitmap b=scale<.999f?Bitmap.createScaledBitmap(src,w,h,true):src;
        int[] p=new int[w*h];b.getPixels(p,0,w,0,0,w,h);if(b!=src)b.recycle();
        long[] ii=new long[(w+1)*(h+1)];
        for(int y=0;y<h;y++){long row=0;for(int x=0;x<w;x++){
            int q=p[y*w+x],v=(int)(.299*((q>>16)&255)+.587*((q>>8)&255)+.114*(q&255));
            row+=v;ii[(y+1)*(w+1)+x+1]=ii[y*(w+1)+x+1]+row;
        }}
        Candidate best=null;int min=Math.max(80,Math.min(w,h)/3),max=(int)(Math.min(w,h)*.96);
        int step=Math.max(4,(max-min)/20);
        for(int s=min;s<=max;s+=step){int cell=s/8,side=cell*8;if(cell<5)continue;
            int pos=Math.max(3,cell/2);
            for(int y=0;y+side<h;y+=pos)for(int x=0;x+side<w;x+=pos){
                double score=score(ii,w+1,x,y,cell);
                if(best==null||score>best.score)best=new Candidate(x,y,side,cell,score);
            }
        }
        if(best==null||best.score<15)return null;
        return new Board(Math.round(best.x/scale),Math.round(best.y/scale),Math.round(best.side/scale),best.score);
    }
    private static double score(long[] ii,int stride,int x,int y,int c){
        double light=0,dark=0;int nl=0,nd=0;int a=Math.max(2,c/5),in=Math.max(2,c/6);
        for(int r=0;r<8;r++)for(int f=0;f<8;f++){
            int xx=x+f*c+c/2-a/2, yy=y+r*c+c/2-a/2;
            long sum=ii[(yy+a)*stride+xx+a]-ii[yy*stride+xx+a]-ii[(yy+a)*stride+xx]+ii[yy*stride+xx];
            double m=(double)sum/(a*a);
            if(((r+f)&1)==0){light+=m;nl++;}else{dark+=m;nd++;}
        }
        return Math.abs(light/nl-dark/nd);
    }
    private static final class Candidate{int x,y,side,cell;double score;Candidate(int a,int b,int c,int d,double e){x=a;y=b;side=c;cell=d;score=e;}}
}
