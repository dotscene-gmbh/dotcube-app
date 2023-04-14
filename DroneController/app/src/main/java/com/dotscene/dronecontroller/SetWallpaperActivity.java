package com.dotscene.dronecontroller;

import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class SetWallpaperActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setWallpaper();
        setContentView(R.layout.activity_set_wallpaper);
        Intent i = new Intent(this, CheckWifiActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        startActivity(i);
        this.finish();
    }

    private void setWallpaper() {
        WallpaperManager manager = WallpaperManager.getInstance(getApplicationContext());
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dotscene_wallpaper);

        // Get actual display resolution of the phone.
        WindowManager w = getWindowManager();
        Display d = w.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        d.getMetrics(metrics);
        double screen_width = metrics.widthPixels;
        double screen_height = metrics.heightPixels;
        // includes window decorations (status bar/menu bar)
        if (Build.VERSION.SDK_INT >= 17)
            try {
                Point realSize = new Point();
                Display.class.getMethod("getRealSize", Point.class).invoke(d, realSize);
                screen_width = realSize.x;
                screen_height = realSize.y;
            } catch (Exception ignored) {
            }

        // Match wallpaper resolution to phone resolution
        double pic_width = bitmap.getWidth();
        double pic_height = bitmap.getHeight();
        double x1;
        // To find how much the wallpaper has to be cut:
        // (pic_width - x1)/pic_height = screen_width/screen_height
        x1 = pic_width - (screen_width / screen_height) * pic_height;

        // Cut and scale bitmap
        Bitmap cbitmap = Bitmap.createBitmap(bitmap, (int) (x1 / 2), 0, (int) (pic_width - x1), (int) pic_height);
        Bitmap sbitmap = Bitmap.createScaledBitmap(cbitmap, (int) screen_width, (int) screen_height, true);

        // Use scaled and cut bitmap as wallpaper
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.setBitmap(sbitmap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
