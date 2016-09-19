package com.ming.steps.Services;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.ming.steps.MainActivity;
import com.ming.steps.R;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;


public class CountingService extends Service implements SensorEventListener{

    private MainActivity attachActivity;
    private RemoteViews notificationViews;
    private SensorManager sensorManager;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock CPUWakeLock;
    private long lastTime = 0;
    private long adjustTime = 0;

    private boolean attached = false;//有客户端绑定的标志
    private boolean reachedTop = true;//和向量的模高于上阈值的标志
    private boolean reachedBottom = false;//和向量的模低于下阈值的标志
    private int steps = 0;
    private float TopThreshold = 13f;//上阈值
    private float BottomThreshold = 9f;//下阈值

    private float maxAsbV = -10.0f;
    private float minAsbV = 30.0f;

    private float[] maxValues;
    private float[] minValues;
    private int maxValuesPointer = 0;
    private int minValuesPointer = 0;

    private ArrayList<Float> maxList;
    private ArrayList<Float> minList;
    private ArrayList<Float> pointList;

    public CountingService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        return START_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) {

        startForeground(1,publishNotify());
        acquireCPULock();
        return new MyBinder();
    }

    public Notification publishNotify(){

        PendingIntent pendingIntent = PendingIntent.getActivity(this,1,
                new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(pendingIntent)
                .setContent(notificationViews)
                .build();
    }

    //当客户端绑定时进行初始化
    public void attach(MainActivity activity){
        attached = true;
        attachActivity = activity;
        updateSteps(steps+"");
    }

    //当客户端解绑是进行解绑工作，保证无客户端期间服务正常进行
    public void detach(){
        attached = false;
        attachActivity = null;
    }

    //更新客户端显示步数界面
    private void updateSteps(String step){
        if (attached)
            attachActivity.setTextView(step);
    }

    //设置最大值与最小值
    private void setMaxMin(float value){
        if (value > maxAsbV)
            maxAsbV = value;
        else if (value < minAsbV)
            minAsbV = value;
    }

    public void setThreshold(float topThreshold,float bottomThreshold){
        TopThreshold = topThreshold;
        BottomThreshold = bottomThreshold;
    }

    //恢复初始的最大值与最小值，为获取下一阶段的最值做准备
    private void restoreMaxMin(){
        maxAsbV = -10;
        minAsbV = 30;
    }

    //释放CPU锁，让CPU可以按照系统设定执行休眠动作
    public void releaseCPULock(){
        if (CPUWakeLock.isHeld())
            CPUWakeLock.release();
    }
    //保持CPU运行状态，阻止其关屏休眠
    public void acquireCPULock(){
        if (!CPUWakeLock.isHeld())
            CPUWakeLock.acquire();
    }

//    boolean up = true;
//    boolean down = false;
    private void counting(SensorEvent sensorEvent){

        //算出三个方向的向量的和向量的模
        float midResult = (float) Math.sqrt(Math.pow(sensorEvent.values[0],2)
                +Math.pow(sensorEvent.values[1],2)
                +Math.pow(sensorEvent.values[2],2));
//        if (up) {
//            if (midResult > tempPoint) {
//                tempPoint = midResult;
//            }
//            else {
//                pointList.add(tempPoint);
//                up = false;
//            }
//        }
//        else {
//            if (midResult < tempPoint) {
//                tempPoint = midResult;
//            }
//            else {
//                pointList.add(tempPoint);
//                up = true;
//            }
//        }

        setMaxMin(midResult);//设置本阶段最大值与最小值
        //判断是否超过上限
        if (reachedTop){
            if (midResult < BottomThreshold){
                if ((sensorEvent.timestamp - lastTime) > 200000000) {
                    reachedTop = false;
                    reachedBottom = true;

                   if (minValuesPointer == 5)
                       minValuesPointer = 0;
                    minValues[minValuesPointer] = minAsbV;

                    minList.add(minAsbV);
                    minAsbV = 30;

                    float average = (minValues[0]+minValues[1]+minValues[2]+minValues[3]+minValues[4])/5;

                    pointList.add(average);
                    //步数增加
                    steps++;
                    lastTime = sensorEvent.timestamp;
                    updateSteps(steps + "");

                    notificationViews.setTextViewText(R.id.notification_step,steps+"");
                    notificationManager.notify(1,publishNotify());
                }
            }
        }
        //判断是否超过下限
        else if (reachedBottom){
            if (midResult > TopThreshold){
                reachedTop = true;
                reachedBottom = false;

                maxValues[0] = maxValues[1];
                maxValues[1] = maxValues[2];
                maxValues[2] = maxAsbV;
                maxList.add(maxAsbV);
                maxAsbV = -10;
            }
        }
    }

    public void getAverage(){

    }

    public void outputMaxMin(){
        File file = Environment.getExternalStorageDirectory();
        file = new File(file.getPath()+"/Steps");
        file.mkdirs();
        file = new File(file.getPath()+"/maxmin.txt");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            Iterator<Float> iterator = minList.iterator();
            for (Float f:maxList) {
                writer.append("\nmax:"+f);
                writer.append("\tmin:"+iterator.next());
            }

            for (Float f:pointList)
                writer.append("\naverage:"+f);

            writer.flush();

        }catch (IOException e){}
    }

    public float calcCalories(){
//        步行能量消耗(千卡)=0．43×身高(厘米)+0．57×体重(公斤)4-0．26×步频(步玢钟)‘+0．92×时间(分钟)一108．44。
//        一Et能量消耗(千卡)=0．05×一日计步器计数(步)+2213．09×体表面积(米2)一1993．57。

        return 0;
    }
    @Override
    public void onCreate(){
        //注册监听
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL);

        notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        //CPU 休眠锁
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        CPUWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "CountingServiceCPUWakeLock");

        notificationViews = new RemoteViews("com.ming.steps",R.layout.notification_layout);

        //最值初始化
        maxValues = new float[5];
        maxValues[0] = maxValues[1] = maxValues[2] = 13;
        minValues = new float[5];
        minValues[0] = minValues[1] = minValues[2] = 10;

        maxList = new ArrayList<>();
        minList = new ArrayList<>();
        pointList = new ArrayList<>();

        setThreshold(15.0f,10.0f);
    }

    @Override
    public void onDestroy(){
        //解挂监听
        sensorManager.unregisterListener(this);
        stopForeground(true);

        releaseCPULock();
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

//        if (sensorEvent.timestamp - adjustTime > 5000000000l){
//            Toast.makeText(CountingService.this, "时间到", Toast.LENGTH_SHORT).show();
//            adjustTime = sensorEvent.timestamp;
//        }
        //算法是每次经过一个上限和一个下限就让步数+1

        counting(sensorEvent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public class MyBinder extends Binder {
        public CountingService getService(){
            return CountingService.this;
        }
    }
}
