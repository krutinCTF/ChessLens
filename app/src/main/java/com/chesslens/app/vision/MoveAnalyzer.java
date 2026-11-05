package com.chesslens.app.vision;

public final class MoveAnalyzer {
    public static final class Move {
        public final char piece;
        public final int changes, sources, destinations;
        Move(char p,int n,int s,int d){piece=p;changes=n;sources=s;destinations=d;}
        public boolean valid(){return piece!='.'&&changes>=2&&changes<=4&&sources>=1&&destinations>=1;}
    }
    public static Move detect(char[][] oldB,char[][] newB,boolean whiteToMove){
        if(oldB==null||newB==null)return new Move('.',0,0,0);
        int changes=0,sources=0,destinations=0;char moved='.';
        for(int r=0;r<8;r++)for(int f=0;f<8;f++){
            char a=oldB[r][f],b=newB[r][f];if(a==b)continue;changes++;
            boolean oldOwn=a!='.'&&Character.isUpperCase(a)==whiteToMove;
            boolean newOwn=b!='.'&&Character.isUpperCase(b)==whiteToMove;
            boolean oldEnemy=a!='.'&&Character.isUpperCase(a)!=whiteToMove;
            if(oldOwn&&(b=='.'||oldEnemy||!newOwn)){sources++;if(moved=='.')moved=a;}
            if(newOwn&&(a=='.'||oldEnemy))destinations++;
        }
        return new Move(moved,changes,sources,destinations);
    }
    public static boolean validPosition(char[][] b){
        int wk=0,bk=0,wp=0,bp=0;
        for(int r=0;r<8;r++)for(int f=0;f<8;f++){
            char c=b[r][f];if(c=='K')wk++;if(c=='k')bk++;
            if(c=='P'){wp++;if(r==0||r==7)return false;}
            if(c=='p'){bp++;if(r==0||r==7)return false;}
        }
        return wk==1&&bk==1&&wp<=8&&bp<=8;
    }
}