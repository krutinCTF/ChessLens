package com.chesslens.app.vision;

import android.graphics.Bitmap;

public final class SquareSampler {
    public static final int N=20;
    public static final class Sample {
        public final float[] pixels;
        public final float luminance, edges;
        Sample(float[] p,float l,float e){pixels=p;luminance=l;edges=e;}
    }
    public static Sample sample(Bitmap b,int cx,int cy,int radius){
        int x0=Math.max(0,cx-radius), y0=Math.max(0,cy-radius);
        int w=Math.min(b.getWidth(),cx+radius)-x0, h=Math.min(b.getHeight(),cy+radius)-y0;
        if(w<2||h<2)return new Sample(new float[N*N],.5f,0);
        int[] px=new int[w*h]; b.getPixels(px,0,w,x0,y0,w,h);
        float[] g=new float[N*N]; float lum=0;
        for(int y=0;y<N;y++)for(int x=0;x<N;x++){
            int p=px[(y*h/N)*w+(x*w/N)];
            float v=(.299f*((p>>16)&255)+.587f*((p>>8)&255)+.114f*(p&255))/255f;
            g[y*N+x]=v; lum+=v;
        }
        lum/=N*N; float edge=0;
        for(int y=1;y<N-1;y++)for(int x=1;x<N-1;x++){
            float dx=g[y*N+x+1]-g[y*N+x-1],dy=g[(y+1)*N+x]-g[(y-1)*N+x];
            edge+=dx*dx+dy*dy;
        }
        return new Sample(g,lum,(float)Math.sqrt(edge/((N-2)*(N-2))));
    }
    public static float distance(float[] a,float[] b){
        float d=0; for(int i=0;i<Math.min(a.length,b.length);i++){float v=a[i]-b[i];d+=v*v;} return d;
    }

    /**
     * Shape comparison resilient to global brightness/contrast changes caused
     * by square colors, highlights and display capture.
     */
    public static float normalizedDistance(float[] a,float[] b){
        int n=Math.min(a.length,b.length);
        if(n==0)return Float.MAX_VALUE;
        float ma=0,mb=0;
        for(int i=0;i<n;i++){ma+=a[i];mb+=b[i];}
        ma/=n;mb/=n;
        float va=0,vb=0;
        for(int i=0;i<n;i++){float da=a[i]-ma,db=b[i]-mb;va+=da*da;vb+=db*db;}
        float sa=(float)Math.sqrt(va/n),sb=(float)Math.sqrt(vb/n);
        sa=Math.max(sa,.035f);sb=Math.max(sb,.035f);
        float d=0;
        for(int i=0;i<n;i++){
            float da=(a[i]-ma)/sa,db=(b[i]-mb)/sb;
            float delta=da-db;d+=delta*delta;
        }
        return d/n;
    }
}
