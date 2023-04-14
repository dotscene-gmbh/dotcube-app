package com.dotscene.dronecontroller;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;

public class ConnectActivity extends AppCompatActivity implements ServerResetThread.OnServerResetListener {

  private AlertDialog resetInProgressDialog;
  private boolean isFABOpen = false;
  FloatingActionButton fab_menu, fab_manual_long, fab_manual_short, fab_video;
  TextView txt_menu, txt_manual_short, txt_manual_long, txt_video;
  LinearLayout ll_help_video, ll_manual_long, ll_manual_short;

  final static String LAST_IP_KEY = "CONNECT_ACTIVITY.LAST_CONNECT_IP";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_connect);

    final SharedPreferences preferences = getPreferences(MODE_PRIVATE);

    Button b = findViewById(R.id.connectButton);
    b.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (BuildConfig.USE_CUSTOM_SERVER) {
          AlertDialog.Builder builder = new AlertDialog.Builder(ConnectActivity.this);
          final EditText ip = new EditText(ConnectActivity.this);
          ip.setText(preferences.getString(LAST_IP_KEY, "127.0.0.1"));
          builder.setView(ip);
          builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              String ipString = ip.getText().toString();
              preferences.edit().putString(LAST_IP_KEY, ipString).apply();
              // replace the activity with the main activity
              Intent i = new Intent(ConnectActivity.this, MainActivity.class);
              i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
              i.putExtra("ConnectActivity.IP_ADDR", ipString);
              startActivity(i);
              ConnectActivity.this.finish();
            }
          });
          builder.setNegativeButton(R.string.cancel, null);
          AlertDialog d = builder.create();
          ip.requestFocus();
          d.show();
        } else {
          openMainActivity();
        }
      }
    });

    if (BuildConfig.MOCK_NETWORK) {
      findViewById(R.id.connectIsNetworkMock).setVisibility(View.VISIBLE);
    }
    if (BuildConfig.USE_CUSTOM_SERVER) {
      findViewById(R.id.connectIsDebugMode).setVisibility(View.VISIBLE);
    }

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

  private void openMainActivity() {
    // Force a reconnect by resetting the server state model
    ServerStateModel.SINGLETON.clearEventListeners();
    ServerStateModel.SINGLETON.reset();

    // replace the activity with the main activity
    Intent i = new Intent(this, MainActivity.class);
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
    startActivity(i);
    finish();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!CheckWifiActivity.isConnectedToCorrectWifi(getApplication())) {
      Intent i = new Intent(this, CheckWifiActivity.class);
      i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
      startActivity(i);
      this.finish();
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
    // Handle item selection
    switch (item.getItemId()) {
      case R.id.connectMenuResetServer: {
        resetServer();
        return true;
      }
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
          Intent i = new Intent(ConnectActivity.this, LicensesActivity.class);
          startActivity(i);
          return true;
      }
      case R.id.menu_info: {
          Intent i = new Intent(ConnectActivity.this, InfoActivity.class);
          startActivity(i);
          return true;
      }
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private void resetServer() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(R.string.resetServerConfirm);
    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        new ServerResetThread(ConnectActivity.this).start();
        AlertDialog.Builder builder = new AlertDialog.Builder(ConnectActivity.this);
        builder.setCancelable(false);
        builder.setMessage(R.string.resettingServer);
        resetInProgressDialog = builder.create();
        resetInProgressDialog.show();
      }
    });
    builder.setNegativeButton(R.string.cancel, null);
    AlertDialog dialog = builder.create();
    dialog.show();
  }

  @Override
  public void onServerReset() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (resetInProgressDialog != null) {
          resetInProgressDialog.hide();
          resetInProgressDialog = null;
        }
      }
    });
  }

  @Override
  public void onError(final String message) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ConnectActivity.this, message, Toast.LENGTH_LONG).show();
      }
    });
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
    Intent i = new Intent(ConnectActivity.this, ShortManualActivity.class);
    startActivity(i);
    Log.i("test", "test");
  }

  public void show_long_manual(View view) {
    Intent i = new Intent(ConnectActivity.this, ManualActivity.class);
    startActivity(i);
  }

}
