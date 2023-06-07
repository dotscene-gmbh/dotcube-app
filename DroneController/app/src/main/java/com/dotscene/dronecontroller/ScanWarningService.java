package com.dotscene.dronecontroller;

import static com.dotscene.dronecontroller.SystemStateFragment.IGNORED_BITS;
import static com.dotscene.dronecontroller.SystemStateFragment.WARNING_TEXTS;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ScanWarningService extends Service {
  private volatile boolean isRunning;
  private volatile byte warningCounter[];
  private final byte triggerWarningCount = 10; // Interval (s) for triggering warning notifications
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
      // Create the notification channel of lower importance for the foreground service notification.
      NotificationChannel channel_foreground = new NotificationChannel("scan_channel", "Scanning", NotificationManager.IMPORTANCE_LOW);
      channel_foreground.setDescription("Shows if a scan is running at the moment");
      notificationManager.createNotificationChannel(channel_foreground);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    // Create a low priority notification for the foreground service notification
    Intent resultIntent = new Intent(getApplicationContext(), MainActivity.class);
    PendingIntent resultPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    NotificationCompat.Builder notification_builder = new NotificationCompat.Builder(getApplicationContext(), "")
            .setContentTitle(getString(R.string.foreground_service_title))
            .setSmallIcon(R.drawable.dotcontrol)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(resultPendingIntent)
            .setSilent(true);
    startForeground(42, notification_builder.build());

    // Only start the notification thread once
    mutex.lock();
    try {
      if (isRunning) {
        return START_STICKY;
      }
      isRunning = true;
      // Initialize warning counter to zeros
      warningCounter = new byte[WARNING_TEXTS.length];
    } finally {
      mutex.unlock();
    }
    new Thread(new Runnable() {
      @Override
      public void run() {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(getApplicationContext());
        while(true) {
          try {
            // Poll once per second for updates
            Thread.sleep(1000);
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
                warningCounter[i] += 1;
                // If the warning is new make a notification
                if (warningCounter[i] == 1) {
                  // Adapt notification builder for the warning notifications
                  notification_builder
                  .setContentTitle(getString(R.string.warningNotificationTitle) + warning_notification_sdf.format(Calendar.getInstance().getTime()))
                  .setContentText(getString(WARNING_TEXTS[i]))
                  .setSilent(false)
                  .setPriority(NotificationCompat.PRIORITY_MAX);
                  if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return;
                  }
                  notificationManagerCompat.notify(i, notification_builder.build());
                } else if (warningCounter[i] == triggerWarningCount) {
                  // Reset warning counter to trigger the Notification every triggerWarningCount seconds
                  warningCounter[i] = 0;
                }
              } else {
                // If the FlowState is good cancel the notification if it's there and reset the counter
                notificationManagerCompat.cancel(i);
                warningCounter[i] = 0;
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
