package com.dotscene.dronecontroller;

import static com.dotscene.dronecontroller.SystemStateFragment.IGNORED_BITS;
import static com.dotscene.dronecontroller.SystemStateFragment.WARNING_TEXTS;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ScanWarningService extends Service {
  private volatile boolean isRunning;
  private final Lock mutex = new ReentrantLock(true);
  private ServerStateModel serverStateModel;
  private ArrayList<ServerStateModel.FlowState> flowStates;
  private SimpleDateFormat warning_notification_sdf;
  boolean activeNotifications[] = new boolean[WARNING_TEXTS.length];

  @Override
  public void onCreate() {
    serverStateModel = ServerStateModel.SINGLETON;
    warning_notification_sdf = new SimpleDateFormat("HH:mm:ss");
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      CharSequence name = "Warnings";
      String description = "Warnings that pop up while using the dotcube.";
      int importance = NotificationManager.IMPORTANCE_HIGH;
      NotificationChannel channel = new NotificationChannel("", name, importance);
      channel.setDescription(description);
      // Register the channel with the system; you can't change the importance
      // or other notification behaviors after this
      NotificationManager notificationManager = getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    // Only start the notification thread once
    mutex.lock();
    try {
      if (isRunning) {
        return START_STICKY;
      }
      isRunning = true;
    } finally {
      mutex.unlock();
    }
    new Thread(new Runnable() {
      @Override
      public void run() {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(getApplicationContext());
        while(true) {
          try {
            // Check every 3s if there are warnings and trigger notifications for them.
            // TODO determine a good polling frequency
            Thread.sleep(3000);
            mutex.lock();
            if (!isRunning) {
              break;
            }
            flowStates = serverStateModel.getFlowStates();
            for (int i = 0; i < flowStates.size(); i++) {
              if (IGNORED_BITS.contains(i)) {
                continue;
              }
              ServerStateModel.FlowState s = flowStates.get(i);

              // Don't show warnings for values if no packets are arriving on a topic
              if (i % 2 == 1) {
                if (flowStates.get(i - 1) == ServerStateModel.FlowState.FAILED && s == ServerStateModel.FlowState.FAILED) {
                  s = ServerStateModel.FlowState.GOOD;
                }
              }
              if (flowStates.get(i) == ServerStateModel.FlowState.FAILED) {
                Intent resultIntent = new Intent(getApplicationContext(), MainActivity.class);
                PendingIntent resultPendingIntent = PendingIntent.getActivity(getApplicationContext(), i, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                NotificationCompat.Builder notification_builder = new NotificationCompat.Builder(getApplicationContext(), "")
                        .setContentTitle(getString(R.string.warningNotificationTitle) + warning_notification_sdf.format(Calendar.getInstance().getTime()))
                        .setSmallIcon(R.drawable.dotcontrol)
                        .setContentText(getString(WARNING_TEXTS[i]))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setOngoing(true)
                        .setContentIntent(resultPendingIntent);
                notificationManagerCompat.notify(i, notification_builder.build());
              } else {
                notificationManagerCompat.cancel(i);
              }
            }
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          } finally {
            mutex.unlock();
          }
        }
      }
    }).start();

    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    // We don't provide binding, so return null
    return null;
  }

  @Override
  public void onDestroy() {
    mutex.lock();
    try {
      isRunning = false;
      NotificationManagerCompat.from(getApplicationContext()).cancelAll();
    } finally {
      mutex.unlock();
    }
    super.onDestroy();
  }
}
