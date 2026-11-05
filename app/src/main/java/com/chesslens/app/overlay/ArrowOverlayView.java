package com.chesslens.app.overlay;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

public final class ArrowOverlayView extends View {
    private final Paint paint=new Paint(3);private final List<float[]> arrows=new ArrayList<>();private RectF board;
    public ArrowOverlayView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    public void setPosition(RectF bounds,List<float[]> values){board=bounds;arrows.clear();arrows.addAll(values);postInvalidate();}
    public void clear(){board=null;arrows.clear();postInvalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(board!=null){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setColor(0x8800E676);c.drawRect(board,paint);}
        for(int i=0;i<arrows.size();i++){float[] a=arrows.get(i);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(Math.max(7,16-i*3));paint.setColor(i==0?0xEE00E676:i==1?0xDDFBC02D:0xCC29B6F6);c.drawLine(a[0],a[1],a[2],a[3],paint);
            double ang=Math.atan2(a[3]-a[1],a[2]-a[0]);float len=24,wing=.55f;Path p=new Path();p.moveTo(a[2],a[3]);p.lineTo(a[2]-(float)Math.cos(ang-wing)*len,a[3]-(float)Math.sin(ang-wing)*len);p.lineTo(a[2]-(float)Math.cos(ang+wing)*len,a[3]-(float)Math.sin(ang+wing)*len);p.close();paint.setStyle(Paint.Style.FILL);c.drawPath(p,paint);}
    }
}
