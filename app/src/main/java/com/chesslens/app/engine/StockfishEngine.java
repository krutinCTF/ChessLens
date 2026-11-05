package com.chesslens.app.engine;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.*;
import java.util.*;

public final class StockfishEngine {
    public static final class Line {public final String move;public final int cp;public final boolean mate;Line(String m,int c,boolean mt){move=m;cp=c;mate=mt;}}
    private Process process;private BufferedWriter input;private BufferedReader output;private boolean ready;
    public synchronized void start(Context context)throws Exception{
        if(ready)return;ApplicationInfo ai=context.getApplicationInfo();
        File bin=new File(ai.nativeLibraryDir,"libstockfish.so");
        if(!bin.exists())throw new FileNotFoundException("Stockfish binary missing: "+bin);
        process=new ProcessBuilder(bin.getAbsolutePath()).redirectErrorStream(true).directory(context.getFilesDir()).start();
        input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));output=new BufferedReader(new InputStreamReader(process.getInputStream()));
        send("uci");waitFor("uciok",10000);send("setoption name Threads value 2");send("setoption name Hash value 48");
        send("setoption name MultiPV value 3");send("isready");waitFor("readyok",10000);ready=true;
    }
    public synchronized List<Line> analyze(String fen,int ms,int count)throws Exception{
        if(!ready)throw new IllegalStateException("Engine not ready");
        count=Math.max(1,Math.min(3,count));send("setoption name MultiPV value "+count);send("ucinewgame");send("position fen "+fen);send("go movetime "+Math.max(300,ms));
        String[] moves=new String[count+1];int[] scores=new int[count+1];boolean[] mates=new boolean[count+1];String line;
        while((line=output.readLine())!=null){if(line.startsWith("bestmove"))break;if(!line.startsWith("info ")||!line.contains(" pv "))continue;
            String[] t=line.split("\\s+");int idx=1,cp=0;boolean mate=false;String move=null;
            for(int i=0;i<t.length-1;i++){switch(t[i]){
                case "multipv":try{idx=Integer.parseInt(t[i+1]);}catch(Exception ignored){}break;
                case "cp":try{cp=Integer.parseInt(t[i+1]);}catch(Exception ignored){}break;
                case "mate":mate=true;try{int m=Integer.parseInt(t[i+1]);cp=m>0?100000-m:-100000-m;}catch(Exception ignored){}break;
                case "pv":move=t[i+1];break;
            }}
            if(move!=null&&idx>0&&idx<=count){moves[idx]=move;scores[idx]=cp;mates[idx]=mate;}
        }
        List<Line> result=new ArrayList<>();for(int i=1;i<=count;i++)if(moves[i]!=null)result.add(new Line(moves[i],scores[i],mates[i]));return result;
    }
    private void send(String s)throws Exception{input.write(s);input.newLine();input.flush();}
    private void waitFor(String token,long ms)throws Exception{long end=System.currentTimeMillis()+ms;String l;while(System.currentTimeMillis()<end&&(l=output.readLine())!=null)if(l.contains(token))return;throw new IOException("Stockfish timeout: "+token);}
    public synchronized void close(){try{if(input!=null){send("quit");input.close();}}catch(Exception ignored){}try{if(output!=null)output.close();}catch(Exception ignored){}if(process!=null)process.destroy();ready=false;}
}
