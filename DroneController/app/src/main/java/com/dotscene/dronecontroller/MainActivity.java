package com.dotscene.dronecontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.dotscene.dronecontroller.ServerStateModel.OnConnectionStateChangedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnShutdownResponseListener;
import com.dotscene.dronecontroller.ServerStateModel.OnStatusLoadedListener;
import com.dotscene.dronecontroller.ServerStateModel.ServerErrorDisplay;
import com.dotscene.dronecontroller.ServerStateModel.ServerStateProvider;
import com.google.android.material.tabs.TabLayout;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
    implements ServerStateProvider,
        ServerErrorDisplay,
        OnStatusLoadedListener,
        OnConnectionStateChangedListener,
        OnShutdownResponseListener {

  class FragmentAdapter extends FragmentPagerAdapter {

    private ArrayList<Pair<Fragment, String>> fragments;

    public FragmentAdapter(FragmentManager m) {
      super(m);
      fragments = new ArrayList<>();
    }

    public void addFragment(Fragment f, String title) {
      fragments.add(new Pair<>(f, title));
    }

    @Override
    public Fragment getItem(int position) {
      return fragments.get(position).first;
    }

    @Override
    public int getCount() {
      return fragments.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
      return fragments.get(position).second;
    }
  }

  private FragmentAdapter adapter;

  // Used to delay the disconnect after a pause to prevent unnecessary reconnects.
  ServerStateModel serverState;
  WifiConnectionReceiver wifiConnectionReceiver;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setLogToFile();

    if (!BuildConfig.MOCK_NETWORK) {
      // Use the global instance
      serverState = ServerStateModel.SINGLETON;
    } else {
      serverState = new ServerStateModelMock();
    }

    setContentView(R.layout.activity_main);
    // register listener for displaying error messages
    serverState.setServerErrorDisplay(this);

    Toolbar bar = findViewById(R.id.toolbar);
    setSupportActionBar(bar);
    try {
      getSupportActionBar().setDisplayShowTitleEnabled(false);
    } catch (NullPointerException e) {
      e.printStackTrace();
    }

    adapter = new FragmentAdapter(getSupportFragmentManager());
    adapter.addFragment(
        new TabRecordingFragment(), getResources().getString(R.string.tabTitleRecord));
    adapter.addFragment(
        new TabStorageFragment(), getResources().getString(R.string.tabTitleStorage));
    adapter.addFragment(
        new TabNetworkingFragment(), getResources().getString(R.string.tabTitleNetwork));

    ViewPager pager = findViewById(R.id.viewPager);
    pager.setAdapter(adapter);

    TabLayout layout = findViewById(R.id.tabLayout);
    layout.setupWithViewPager(pager);
  }

  @Override
  public void onResume() {
    super.onResume();
    wifiConnectionReceiver = new WifiConnectionReceiver();
    wifiConnectionReceiver.setMainActivity(this);
    IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    intentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
    intentFilter.addAction("android.net.wifi.STATE_CHANGED");
    this.registerReceiver(wifiConnectionReceiver, intentFilter);
    serverState.addOnStatusLoadedListener(this);
    serverState.addOnConnectionStateChangedListener(this);
    serverState.addOnShutdownResponseListener(this);
    // the connection should be resumed, as we should always be connected while in the main activity
    if (BuildConfig.USE_CUSTOM_SERVER) {
      String ip = getIntent().getStringExtra("ConnectActivity.IP_ADDR");
      serverState.connect(this, ip);
    } else {
      serverState.connect(this, null);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    serverState.removeOnStatusLoadedListener(this);
    serverState.removeOnConnectionStateChangedListener(this);
    serverState.removeOnShutdownResponseListener(this);
    unregisterReceiver(wifiConnectionReceiver);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.dot_menu, menu);
    MenuCompat.setGroupDividerEnabled(menu, true);

    MenuItem factoryReset = menu.add(R.id.other_group, 0, 0, R.string.factoryReset);
    factoryReset.setOnMenuItemClickListener(
        new MenuItem.OnMenuItemClickListener() {
          @Override
          public boolean onMenuItemClick(MenuItem menuItem) {
            AlertDialog.Builder builder = new Builder(MainActivity.this);

            builder.setTitle(R.string.factoryReset);
            builder.setMessage(R.string.factoryResetInfo);
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(
                R.string.ok,
                new OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                    serverState.resetServerToFactorySettings();
                  }
                });

            builder.create().show();
            return true;
          }
        });
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch(item.getItemId()) {
      case R.id.menu_manual: {
        Intent i = new Intent(MainActivity.this, ManualActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.menu_manual_scan: {
        Intent i = new Intent(MainActivity.this, ShortManualActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.menu_video_manual: {
        File file = new File(getResources().getString(R.string.video_path));
        if(file.exists()) {
          Intent start_video_intent = new Intent(Intent.ACTION_VIEW);
          start_video_intent.setDataAndType(Uri.parse(getResources().getString(R.string.video_path)), "video/*");
          startActivity(start_video_intent);
        }
        else {
          Toast.makeText(this, "The video can not be found. Please contact support.", Toast.LENGTH_SHORT).show();
        }
        return true;
      }
      case R.id.menu_licenses: {
        Intent i = new Intent(MainActivity.this, LicensesActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.menu_info: {
        Intent i = new Intent(MainActivity.this, InfoActivity.class);
        startActivity(i);
        return true;
      }
      default:
        return super.onOptionsItemSelected(item);
    }  }

  @Override
  public ServerStateModel getServerStateModel() {
    return serverState;
  }

  @Override
  public void displayServerStateError(int stringId, Object... args) {
    final String message = String.format(getResources().getString(stringId), args);
    DisplayToastRunnable r = new DisplayToastRunnable(this, message);
    runOnUiThread(r);
  }

  @Override
  public void displayImportantServerStateError(int stringId, final boolean fatal, Object... args) {
    final String message = String.format(getResources().getString(stringId), args);
    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(message);
            builder.setPositiveButton(R.string.ok, null);
            builder.setOnDismissListener(
                new OnDismissListener() {
                  @Override
                  public void onDismiss(DialogInterface dialog) {
                    if (fatal) {
                      // Given a fatal error the server state might not be in a functional state.
                      try {
                        if (serverState != null) {
                          serverState.disconnect();
                        }
                      } catch (Exception e) {
                        e.printStackTrace();
                      }
                      // switch back to CheckWifi activity
                      Intent i = new Intent(MainActivity.this, CheckWifiActivity.class);
                      i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                      startActivity(i);
                      MainActivity.this.finish();
                    }
                  }
                });
            AlertDialog dialog = builder.create();
            dialog.show();
          }
        });
  }

  @Override
  public void onStatusLoaded() {
    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            findViewById(R.id.mainProgressBar).setVisibility(View.GONE);
            findViewById(R.id.viewPager).setVisibility(View.VISIBLE);
          }
        });
  }

  @Override
  public void onConnectionEstablished() {}

  @Override
  public void onConnectionClosed() {
    // go back to the connect screen
    Intent i = new Intent(this, CheckWifiActivity.class);
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
    startActivity(i);
    finish();
  }

  @Override
  public void onShutdownResponse(int status) {
    // go back to the shutdown screen
    Intent i = new Intent(this, ShutdownActivity.class);
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
    startActivity(i);
    finish();
  }

  @Override
  public void onShutdownBrokenBagfiles(String[] bagfiles) {}

  @Override
  public void onBackPressed() {
    serverState.disconnect();
    // go back to the connect screen
    Intent i = new Intent(this, CheckWifiActivity.class);
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
    startActivity(i);
    finish();
  }

  private void setLogToFile() {
    File logfile = new File(Environment.getExternalStorageDirectory() + "/Documents/dotscene.log");
    try {
      Log.d(getClass().getSimpleName(), "===================================");
      Log.d(getClass().getSimpleName(), "Beginning of DotController app log");
      Log.d(getClass().getSimpleName(), "logging to file");
      // limit the number of lines to 1000
      if (logfile.exists()) {
        StringBuilder fileContent = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(logfile));
        int numLines = 0;
        while (reader.readLine() != null) {
          numLines++;
        }
        reader.close();
        reader = new BufferedReader(new FileReader(logfile));
        int firstLineToKeep = numLines - 1000;
        String line;
        int currentLine = 0;
        while ((line = reader.readLine()) != null) {
          if (currentLine > firstLineToKeep) {
            fileContent.append(line).append("\n");
          }
          currentLine++;
        }
        reader.close();

        // delete the file and write the cut down content into it
        logfile.delete();
        BufferedWriter writer = new BufferedWriter(new FileWriter(logfile));
        writer.write(fileContent.toString());
        writer.close();
      } else {
        Runtime.getRuntime().exec("touch " + logfile);
      }
      Runtime.getRuntime().exec("logcat -f" + logfile);
    } catch (IOException e) {
      Log.e(getClass().getSimpleName(), e.getMessage());
    }
  }

  private class WifiConnectionReceiver extends BroadcastReceiver {

    private MainActivity mainActivity;

    public void setMainActivity(MainActivity mainActivity) {
      this.mainActivity = mainActivity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      ConnectivityManager manager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

      boolean isStillConnected = false;
      // use getAllNetworks if it is available
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
        for (Network n : manager.getAllNetworks()) {
          NetworkInfo nInfo = manager.getNetworkInfo(n);
          if (nInfo != null
              && nInfo.getType() == ConnectivityManager.TYPE_WIFI
              && nInfo.isConnected()) {
            isStillConnected = true;
          }
        }
      } else { // use the pre lollipop getAllNetworkInfo
        for (NetworkInfo nInfo : manager.getAllNetworkInfo()) {
          if (nInfo != null
              && nInfo.getType() == ConnectivityManager.TYPE_WIFI
              && nInfo.isConnected()) {
            isStillConnected = true;
          }
        }
      }

      if (!isStillConnected) {
        Log.w(getClass().getSimpleName(), "wifi connection lost");
        mainActivity.displayImportantServerStateError(R.string.onSSHConnectionLost, true);
      }
    }
  }
}
