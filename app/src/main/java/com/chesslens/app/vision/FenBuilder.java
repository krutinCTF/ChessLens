package com.chesslens.app.vision;

public final class FenBuilder {
    public static String build(char[][] grid,boolean blackAtBottom,boolean whiteToMove){
        StringBuilder out=new StringBuilder();
        for(int r=0;r<8;r++){int empty=0;for(int f=0;f<8;f++){
            char c=blackAtBottom?grid[7-r][7-f]:grid[r][f];
            if(c=='.'||c==0){empty++;continue;}if(empty>0){out.append(empty);empty=0;}out.append(c);
        }if(empty>0)out.append(empty);if(r<7)out.append('/');}
        return out+" "+(whiteToMove?"w":"b")+" - - 0 1";
    }
}
