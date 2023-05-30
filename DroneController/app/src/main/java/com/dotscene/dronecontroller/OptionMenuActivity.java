package com.dotscene.dronecontroller;

import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuCompat;

import java.io.File;

public abstract class OptionMenuActivity extends AppCompatActivity {
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
      case R.id.menu_manual: {
        Intent i = new Intent(OptionMenuActivity.this, ManualActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.menu_manual_scan: {
        Intent i = new Intent(OptionMenuActivity.this, ShortManualActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.menu_video_manual: {
        File file = new File(getResources().getString(R.string.video_path));
        if (file.exists()) {
          Intent start_video_intent = new Intent(Intent.ACTION_VIEW);
          start_video_intent.setDataAndType(Uri.parse(getResources().getString(R.string.video_path)), "video/*");
          startActivity(start_video_intent);
        } else {
          Toast.makeText(this, "The video can not be found. Please contact support.", Toast.LENGTH_SHORT).show();
        }
        return true;
      }
      case R.id.menu_notifications: {
        // Links to this app's notification settings.
        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
        startActivity(intent);
        return true;
      }
      case R.id.menu_licenses: {
        Intent i = new Intent(OptionMenuActivity.this, LicensesActivity.class);
        startActivity(i);
        return true;
      }
      case R.id.menu_info: {
        Intent i = new Intent(OptionMenuActivity.this, InfoActivity.class);
        startActivity(i);
        return true;
      }
      default:
        return super.onOptionsItemSelected(item);
    }
  }
}
