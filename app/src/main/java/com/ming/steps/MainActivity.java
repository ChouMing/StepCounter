package com.ming.steps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.ming.steps.Services.CountingService;

public class MainActivity extends AppCompatActivity implements ServiceConnection{

    private TextView textView ;
    private CountingService countingService;
    private boolean connected = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Intent intent = new Intent(this, CountingService.class);

        textView = (TextView) findViewById(R.id.main_activity_show);
        Button button  = (Button) findViewById(R.id.save);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                countingService.outputMaxMin();
            }
        });

        startService(intent);
        bindService(intent,MainActivity.this, Context.BIND_AUTO_CREATE);
    }
    public void setTextView(String string){
        textView.setText(string);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if (connected)
            countingService.detach();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        countingService = ((CountingService.MyBinder) iBinder).getService();
        countingService.attach(this);
        connected = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        if (connected)
            countingService.detach();
        connected = false;
    }
}
