package com.dotscene.dronecontroller;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import java.util.Timer;
import java.util.TimerTask;

public class ShutdownActivity extends AppCompatActivity {

  Timer closeTimer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_shutdown);

    closeTimer = new Timer();
    closeTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            // go back to the connect screen
            Intent i = new Intent(ShutdownActivity.this, CheckWifiActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            startActivity(i);
            finish();
          }
        });
      }
    }, 10000);
  }
}
