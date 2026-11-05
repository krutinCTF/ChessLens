package com.chesslens.app.vision;

import android.graphics.Bitmap;
import java.util.Arrays;

public final class PieceClassifier {
    private static final char[] START={
        'r','n','b','q','k','b','n','r','p','p','p','p','p','p','p','p',
        '.','.','.','.','.','.','.','.','.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.','.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.','.','.','.','.','.','.','.','.',
        'P','P','P','P','P','P','P','P','R','N','B','Q','K','B','N','R'
    };
    private SquareSampler.Sample[] templates,previous;
    private char[] labels,previousLabels;
    private float edgeThreshold,whiteLum,blackLum,changeThreshold;
    private boolean ready; private boolean blackAtBottom;
    public boolean isReady(){return ready;}
    public boolean blackAtBottom(){return blackAtBottom;}
    private SquareSampler.Sample[] samples(Bitmap b,BoardDetector.Board board){
        SquareSampler.Sample[] s=new SquareSampler.Sample[64];
        int rad=Math.max(4,(int)(board.square*.32f));
        for(int r=0;r<8;r++)for(int f=0;f<8;f++)
            s[r*8+f]=SquareSampler.sample(b,Math.round(board.left+(f+.5f)*board.square),Math.round(board.top+(r+.5f)*board.square),rad);
        return s;
    }
    private static float median(float[] values){
        float[] copy=values.clone();Arrays.sort(copy);
        int n=copy.length;
        return n%2==0?(copy[n/2-1]+copy[n/2])*.5f:copy[n/2];
    }
    public boolean calibrate(Bitmap b,BoardDetector.Board board){
        SquareSampler.Sample[] s=samples(b,board);
        float[] emptyEdges=new float[32],occupiedEdges=new float[32];
        int ei=0,oi=0;
        for(int r=2;r<=5;r++)for(int f=0;f<8;f++)emptyEdges[ei++]=s[r*8+f].edges;
        for(int r=0;r<8;r++)if(r<2||r>5)for(int f=0;f<8;f++)occupiedEdges[oi++]=s[r*8+f].edges;
        float empty=median(emptyEdges),occupied=median(occupiedEdges);
        // Median separation tolerates a few unusually textured squares and piece highlights.
        if(occupied<Math.max(empty*1.15f,empty+.004f))return false;
        float top=0,bottom=0;
        for(int f=0;f<8;f++){top+=s[f].luminance+s[8+f].luminance;bottom+=s[48+f].luminance+s[56+f].luminance;}
        blackAtBottom=bottom<top;
        labels=new char[64];
        for(int r=0;r<8;r++)for(int f=0;f<8;f++)labels[r*8+f]=blackAtBottom?START[(7-r)*8+7-f]:START[r*8+f];
        templates=s;previous=s;previousLabels=labels.clone();edgeThreshold=(empty+occupied)*.5f;
        float wl=0,bl=0;int wn=0,bn=0;
        for(int i=0;i<64;i++)if(labels[i]!='.'){
            if(Character.isUpperCase(labels[i])){wl+=s[i].luminance;wn++;}
            else{bl+=s[i].luminance;bn++;}
        }
        whiteLum=wl/Math.max(1,wn);blackLum=bl/Math.max(1,bn);
        changeThreshold=.30f;ready=true;return true;
    }
    public char[][] classify(Bitmap b,BoardDetector.Board board){
        if(!ready)return null;SquareSampler.Sample[] s=samples(b,board);char[][] g=new char[8][8];
        for(int i=0;i<64;i++){
            if(SquareSampler.distance(s[i].pixels,previous[i].pixels)<changeThreshold){g[i/8][i%8]=previousLabels[i];continue;}
            g[i/8][i%8]=classifyOne(s[i]);
        }return g;
    }
    private char classifyOne(SquareSampler.Sample s){
        if(s.edges<edgeThreshold)return '.';
        boolean white=Math.abs(s.luminance-whiteLum)<Math.abs(s.luminance-blackLum);
        float best=Float.MAX_VALUE;char chosen=white?'P':'p';
        for(int i=0;i<64;i++){char c=labels[i];if(c=='.'||Character.isUpperCase(c)!=white)continue;
            float d=SquareSampler.distance(s.pixels,templates[i].pixels);if(d<best){best=d;chosen=c;}}
        return chosen;
    }
    public void commit(char[][] grid,Bitmap b,BoardDetector.Board board){
        if(!ready||grid==null)return;previous=samples(b,board);previousLabels=new char[64];
        for(int r=0;r<8;r++)for(int f=0;f<8;f++)previousLabels[r*8+f]=grid[r][f];
    }
    public void reset(){ready=false;templates=previous=null;labels=previousLabels=null;}
}