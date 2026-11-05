package com.chesslens.app.vision;

public final class MoveAnalyzer {
    public static final class Move { public final char piece; public final int changes; Move(char p,int n){piece=p;changes=n;} public boolean valid(){return piece!='.'&&changes>=2;} }
    public static Move detect(char[][] oldB,char[][] newB,boolean whiteToMove){
        if(oldB==null||newB==null)return new Move('.',0);
        char expected=whiteToMove?'P':'p';int changes=0;char moved='.';
        for(int r=0;r<8;r++)for(int f=0;f<8;f++){
            char a=oldB[r][f],b=newB[r][f];if(a==b)continue;changes++;
            if(a!='.'&&Character.isUpperCase(a)==whiteToMove&&(b=='.'||Character.isUpperCase(b)!=whiteToMove))moved=a;
        }
        return new Move(moved,changes);
    }
    public static boolean validPosition(char[][] b){
        int wk=0,bk=0,wp=0,bp=0;
        for(int r=0;r<8;r++)for(int f=0;f<8;f++){char c=b[r][f];if(c=='K')wk++;if(c=='k')bk++;if(c=='P'){wp++;if(r==0||r==7)return false;}if(c=='p'){bp++;if(r==0||r==7)return false;}}
        return wk==1&&bk==1&&wp<=8&&bp<=8;
    }
}
