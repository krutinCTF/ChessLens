package com.chesslens.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.chesslens.app.engine.StockfishEngine;
import com.chesslens.app.overlay.ArrowOverlayView;
import com.chesslens.app.vision.*;
import java.nio.ByteBuffer;
import java.util.*;

public class LiveCaptureService extends Service {
    public static final String EXTRA_RESULT_CODE="projection_result_code",EXTRA_DATA="projection_data",ACTION_STOP="com.chesslens.app.STOP_LIVE";
    private static final String CHANNEL="chesslens_live";private static final int NOTIFICATION=4201;
    private MediaProjection projection;private VirtualDisplay display;private ImageReader reader;private HandlerThread thread;private Handler worker;
    private WindowManager wm;private View panel;private TextView status,eval;private ArrowOverlayView arrows;
    private StockfishEngine engine;private final PieceClassifier classifier=new PieceClassifier();
    private BoardDetector.Board lastBoard;private char[][] confirmed,candidate;private int candidateCount=0;
    private boolean whiteToMove=true,paused=false,busy=false,engineReady=false;
    private int screenW,screenH,captureW,captureH,density;private float sx=1f,sy=1f;
    private final Runnable loop=new Runnable(){public void run(){if(!paused)sample();if(worker!=null)worker.postDelayed(this,550);}};

    @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);createChannel();}
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&ACTION_STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
        if(i==null){stopSelf();return START_NOT_STICKY;}
        int rc=i.getIntExtra(EXTRA_RESULT_CODE,0);
        Intent data=Build.VERSION.SDK_INT>=33?i.getParcelableExtra(EXTRA_DATA,Intent.class):i.getParcelableExtra(EXTRA_DATA);
        if(data==null||rc==0){stopSelf();return START_NOT_STICKY;}
        startForeground(NOTIFICATION,notification());
        try{
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=m.getMediaProjection(rc,data);
            if(projection==null){stopSelf();return START_NOT_STICKY;}
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},new Handler(Looper.getMainLooper()));
            DisplayMetrics dm=new DisplayMetrics();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
            screenW=dm.widthPixels;screenH=dm.heightPixels;density=dm.densityDpi;
            float scale=Math.min(1f,800f/Math.max(screenW,screenH));captureW=Math.max(1,Math.round(screenW*scale));captureH=Math.max(1,Math.round(screenH*scale));sx=(float)screenW/captureW;sy=(float)screenH/captureH;
            thread=new HandlerThread("ChessLensVision",Thread.NORM_PRIORITY);thread.start();worker=new Handler(thread.getLooper());
            reader=ImageReader.newInstance(captureW,captureH,PixelFormat.RGBA_8888,3);
            display=projection.createVirtualDisplay("ChessLensCapture",captureW,captureH,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
            engine=new StockfishEngine();worker.post(()->{try{engine.start(getApplicationContext());engineReady=true;setStatus("Stockfish готов · ищу доску");}catch(Exception e){setStatus("Ошибка Stockfish: "+e.getMessage());}});
            showOverlay();worker.postDelayed(loop,800);
        }catch(Exception e){stopSelf();}
        return START_NOT_STICKY;
    }
    private void sample(){
        if(busy||reader==null)return;Image image=null;Bitmap bitmap=null;busy=true;
        try{
            image=reader.acquireLatestImage();if(image==null)return;bitmap=toBitmap(image);
            BoardDetector.Board board=BoardDetector.locate(bitmap);
            if(board==null){setStatus("Ищу шахматную доску…");clearArrows();return;}
            lastBoard=board;setBoardFrame(board);
            if(!engineReady){setStatus("Доска найдена · запускаю Stockfish");return;}
            if(!classifier.isReady()){
                if(!classifier.calibrate(bitmap,board)){setStatus("Покажи начальную расстановку фигур");return;}
                confirmed=startingGrid();
                whiteToMove=true;
                setStatus(classifier.blackAtBottom()?"Калибровка: играешь чёрными":"Калибровка: играешь белыми");
                if(!classifier.blackAtBottom())analyze(board,confirmed,bitmap);
                return;
            }
            char[][] grid=classifier.classify(bitmap,board);if(grid==null)return;
            if(equal(grid,confirmed)){candidate=null;candidateCount=0;classifier.commit(confirmed,bitmap,board);return;}
            if(!equal(grid,candidate)){candidate=copy(grid);candidateCount=1;setStatus("Обнаружено изменение · проверяю…");return;}
            candidateCount++;
            if(candidateCount<2)return;
            if(!MoveAnalyzer.validPosition(grid)){setStatus("Позиция распознана неуверенно · жду");return;}
            MoveAnalyzer.Move move=MoveAnalyzer.detect(confirmed,grid,whiteToMove);
            if(!move.valid()){setStatus("Не удалось определить ход · жду следующий кадр");return;}
            confirmed=copy(grid);classifier.commit(confirmed,bitmap,board);whiteToMove=!whiteToMove;
            candidate=null;candidateCount=0;
            boolean userBlack=classifier.blackAtBottom();
            boolean userTurn= userBlack ? !whiteToMove : whiteToMove;
            if(userTurn)analyze(board,confirmed,bitmap);else{clearArrows();setEval("");setStatus("Ход соперника · жду ответа бота");}
        }catch(Throwable t){setStatus("Анализ: "+t.getClass().getSimpleName());}
        finally{if(image!=null)image.close();if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();busy=false;}
    }
    private void analyze(BoardDetector.Board b,char[][] grid,Bitmap bitmap){
        if(!engineReady||engine==null)return;
        String fen=FenBuilder.build(grid,classifier.blackAtBottom(),whiteToMove);
        setStatus("Stockfish анализирует позицию…");
        try{
            List<StockfishEngine.Line> lines=engine.analyze(fen,1200,3);
            if(lines.isEmpty()){setStatus("Нет доступных ходов");return;}
            List<float[]> coords=new ArrayList<>();float cellX=b.square*sx,cellY=b.square*sy,ox=b.left*sx,oy=b.top*sy;
            for(StockfishEngine.Line line:lines){
                String u=line.move;if(u.length()<4)continue;
                int fx=u.charAt(0)-'a',fy=8-(u.charAt(1)-'0'),tx=u.charAt(2)-'a',ty=8-(u.charAt(3)-'0');
                if(classifier.blackAtBottom()){fx=7-fx;fy=7-fy;tx=7-tx;ty=7-ty;}
                coords.add(new float[]{ox+(fx+.5f)*cellX,oy+(fy+.5f)*cellY,ox+(tx+.5f)*cellX,oy+(ty+.5f)*cellY});
            }
            if(arrows!=null)arrows.setPosition(new RectF(b.left*sx,b.top*sy,(b.left+b.side)*sx,(b.top+b.side)*sy),coords);
            StockfishEngine.Line best=lines.get(0);
            setEval(best.move+" · "+(best.mate?"мат":String.format(Locale.US,"%+.2f",best.cp/100.0)));
            setStatus("Готово · лучший ход показан стрелкой");
        }catch(Exception e){setStatus("Ошибка анализа Stockfish");}
    }
    private char[][] startingGrid(){char[][] g={{'r','n','b','q','k','b','n','r'},{'p','p','p','p','p','p','p','p'},{'.','.','.','.','.','.','.','.'},{'.','.','.','.','.','.','.','.'},{'.','.','.','.','.','.','.','.'},{'.','.','.','.','.','.','.','.'},{'P','P','P','P','P','P','P','P'},{'R','N','B','Q','K','B','N','R'}};if(classifier.blackAtBottom()){char[][] x=new char[8][8];for(int r=0;r<8;r++)for(int f=0;f<8;f++)x[r][f]=g[7-r][7-f];return x;}return g;}
    private boolean equal(char[][]a,char[][]b){if(a==null||b==null)return false;for(int r=0;r<8;r++)if(!Arrays.equals(a[r],b[r]))return false;return true;}
    private char[][] copy(char[][]a){char[][]b=new char[8][8];for(int r=0;r<8;r++)b[r]=a[r].clone();return b;}
    private Bitmap toBitmap(Image im){
        Image.Plane p=im.getPlanes()[0];ByteBuffer buf=p.getBuffer();int ps=p.getPixelStride(),rs=p.getRowStride(),w=im.getWidth(),h=im.getHeight(),bw=w+(rs-ps*w)/ps;
        Bitmap raw=Bitmap.createBitmap(bw,h,Bitmap.Config.ARGB_8888);buf.rewind();raw.copyPixelsFromBuffer(buf);
        if(bw==w)return raw;Bitmap crop=Bitmap.createBitmap(raw,0,0,w,h);raw.recycle();return crop;
    }
    private void showOverlay(){
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        arrows=new ArrowOverlayView(this);WindowManager.LayoutParams full=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);full.gravity=Gravity.TOP|Gravity.START;wm.addView(arrows,full);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(18,14,18,14);box.setBackgroundColor(Color.argb(238,25,23,38));
        TextView title=new TextView(this);title.setText("CHESSLENS LIVE");title.setTextColor(Color.WHITE);title.setTextSize(14);title.setTypeface(null,1);
        status=new TextView(this);status.setText("Запускаю…");status.setTextColor(Color.LTGRAY);status.setTextSize(11);status.setPadding(0,8,0,4);
        eval=new TextView(this);eval.setTextColor(Color.rgb(167,139,250));eval.setTextSize(14);eval.setPadding(0,4,0,8);
        Button pause=new Button(this);pause.setText("Пауза");pause.setOnClickListener(v->{paused=!paused;pause.setText(paused?"Продолжить":"Пауза");if(paused)clearArrows();});
        Button stop=new Button(this);stop.setText("Остановить");stop.setOnClickListener(v->stopSelf());
        box.addView(title);box.addView(status);box.addView(eval);box.addView(pause);box.addView(stop);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.START;lp.x=20;lp.y=120;
        panel=box;
        title.setOnTouchListener(new View.OnTouchListener(){int x,y,rx,ry;public boolean onTouch(View v,MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){x=(int)e.getRawX();y=(int)e.getRawY();rx=lp.x;ry=lp.y;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){lp.x=rx+(int)e.getRawX()-x;lp.y=ry+(int)e.getRawY()-y;wm.updateViewLayout(panel,lp);return true;}
            return e.getAction()==MotionEvent.ACTION_UP;
        }});
        wm.addView(panel,lp);
    }
    private void setBoardFrame(BoardDetector.Board b){if(arrows!=null)arrows.setBoardBounds(new RectF(b.left*sx,b.top*sy,(b.left+b.side)*sx,(b.top+b.side)*sy));}
    private void clearArrows(){if(arrows!=null)arrows.clear();}
    private void setStatus(String s){if(status!=null)status.post(()->status.setText(s));}
    private void setEval(String s){if(eval!=null)eval.post(()->eval.setText(s));}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"ChessLens Live",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    private Notification notification(){Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setContentTitle("ChessLens Live").setContentText("Захват экрана активен").setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build();}
    @Override public void onDestroy(){if(worker!=null)worker.removeCallbacksAndMessages(null);if(engine!=null)engine.close();if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(thread!=null)thread.quitSafely();try{if(panel!=null)wm.removeView(panel);}catch(Exception ignored){}try{if(arrows!=null)wm.removeView(arrows);}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
