package com.dotscene.dronecontroller;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class CheckWifiActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

  private static final String WIFI_NAME_PREFIX = "dotcube";
  private boolean isFABOpen = false;
  FloatingActionButton fab_menu, fab_manual_long, fab_manual_short, fab_video;
  TextView txt_menu, txt_manual_short, txt_manual_long, txt_video;
  LinearLayout ll_help_video, ll_manual_long, ll_manual_short;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_check_wifi);

    Button b = findViewById(R.id.wifiPreferencesButton);
    b.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
      }
    });

    TextView textConnectToWifi = findViewById(R.id.textConnectToWifi);
    textConnectToWifi.setText(getResources().getString(R.string.checkWifiWrongWifi, WIFI_NAME_PREFIX));

    Toolbar bar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(bar);
    getSupportActionBar().setDisplayShowTitleEnabled(false);

    fab_menu = findViewById(R.id.fab_menu);
    txt_menu = findViewById(R.id.txt_help_menu);
    ll_manual_long = findViewById(R.id.ll_manual_long);
    fab_manual_long = findViewById(R.id.fab_manual_long);
    txt_manual_long = findViewById(R.id.txt_manual_long);
    ll_manual_short = findViewById(R.id.ll_manual_short);
    fab_manual_short = findViewById(R.id.fab_manual_short);
    txt_manual_short = findViewById(R.id.txt_manual_short);
    ll_help_video = findViewById(R.id.ll_help_video);
    fab_video = findViewById(R.id.fab_video);
    txt_video = findViewById(R.id.txt_help_video);
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (hasPermissions(this)) {
      setupWifiListener();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.dot_menu, menu);
    MenuCompat.setGroupDividerEnabled(menu, true);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch(item.getItemId()) {
      case R.id.menu_manual: {
        show_long_manual(item.getActionView());
        return true;
      }
      case R.id.menu_manual_scan: {
        show_short_manual(item.getActionView());
        return true;
      }
      case R.id.menu_video_manual: {
        show_video(item.getActionView());
        return true;
      }
      case R.id.menu_licenses: {
        Intent i = new Intent(CheckWifiActivity.this, LicensesActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.menu_info: {
        Intent i = new Intent(CheckWifiActivity.this, InfoActivity.class);
        startActivity(i);
        return true;
      }
      default:
        return false;
    }
  }

  private void setupWifiListener() {
    Log.d(getClass().getSimpleName(), "Api version " + Build.VERSION.SDK_INT);
    if (isConnectedToCorrectWifi(getApplication())) {
      // we are connected to the correct wifi so we can switch to the connect activity.
      Intent i = new Intent(this, ConnectActivity.class);
      i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
      startActivity(i);
      this.finish();
    } else if (Build.VERSION.SDK_INT >= 28) {
      Log.d(getClass().getSimpleName(), "");
      LocationManager provider = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
      if (!provider.isLocationEnabled()) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.checkWifiAndroid9Loc);
        builder.setPositiveButton(R.string.checkWifiOpenLocSettings, new android.content.DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(i);
          }
        });
        builder.setNegativeButton(R.string.checkWifiExit, new android.content.DialogInterface.OnClickListener() {

          @Override
          public void onClick(DialogInterface dialog, int which) {
            CheckWifiActivity.this.finish();
          }
        });
        builder.create().show();
      }
    }
  }

  public static boolean isConnectedToCorrectWifi(Application a) {
    WifiManager wifi = (WifiManager) a.getSystemService(WIFI_SERVICE);
    WifiInfo info = wifi.getConnectionInfo();
    if (info != null) {
      Log.d(CheckWifiActivity.class.getSimpleName(), "Wifi ssid: " + info.getSSID());
    }
    if ((info != null &&
        wifi.isWifiEnabled() &&
        (info.getSSID().startsWith("\"" + WIFI_NAME_PREFIX) || BuildConfig.USE_CUSTOM_SERVER ||
            BuildConfig.MOCK_NETWORK))) {
      return true;
    }
    return false;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    boolean hasPermissions = true;
    for (int i : grantResults) {
      if (i != PackageManager.PERMISSION_GRANTED) {
        hasPermissions = false;
      }
    }

    if (!hasPermissions) {
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(R.string.checkWifiMissingPermissions);
      builder.setPositiveButton(R.string.ok, null);
      builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
          CheckWifiActivity.this.finish();
        }
      });
      AlertDialog dialog = builder.create();
      dialog.show();
    } else {
      setupWifiListener();
    }
  }

  private static boolean hasPermissions(Activity activity) {
    String permissions[] = {
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    };
    for (String permission : permissions) {
      if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(activity, permissions, 0);
        return false;
      }
    }
    return true;
  }

  public void toggle_help_menu(View view) {
    if(!isFABOpen){
      open_help_menu();
    }
    else {
      close_help_menu();
    }
  }

  private void open_help_menu() {
    isFABOpen = true;
    ll_help_video.animate().translationY(-340);
    txt_video.setVisibility(View.VISIBLE);
    ll_manual_short.animate().translationY(-230);
    txt_manual_short.setVisibility(View.VISIBLE);
    ll_manual_long.animate().translationY(-120);
    txt_manual_long.setVisibility(View.VISIBLE);
  }

  private void close_help_menu() {
    isFABOpen = false;

    ll_help_video.animate().translationY(0);
    txt_video.setVisibility(View.INVISIBLE);
    ll_manual_short.animate().translationY(0);
    txt_manual_short.setVisibility(View.INVISIBLE);
    ll_manual_long.animate().translationY(0);
    txt_manual_long.setVisibility(View.INVISIBLE);
  }

  @Override
  public void onBackPressed() {
    if(!isFABOpen){
      super.onBackPressed();
    }else{
      close_help_menu();
    }
  }

  public void show_video(View view) {
      File file = new File(getResources().getString(R.string.video_path));
      if(file.exists()) {
        Intent start_video_intent = new Intent(Intent.ACTION_VIEW);
        start_video_intent.setDataAndType(Uri.parse(getResources().getString(R.string.video_path)), "video/*");
        startActivity(start_video_intent);
      }
      else {
        Toast.makeText(this, "The video can not be found. Please contact support.", Toast.LENGTH_SHORT).show();
      }
  }

  public void show_short_manual(View view) {
    Intent i = new Intent(CheckWifiActivity.this, ShortManualActivity.class);
    startActivity(i);
  }

  public void show_long_manual(View view) {
    Intent i = new Intent(CheckWifiActivity.this, ManualActivity.class);
    startActivity(i);
  }
}
