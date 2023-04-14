package com.dotscene.dronecontroller;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewManager;
import android.view.ViewParent;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import github.nisrulz.qreader.QRDataListener;
import github.nisrulz.qreader.QREader;

public class QrCodeActivity extends AppCompatActivity {

    public static final String RES_RESTART_KEY = "com.dotscene.dronecontroller.qrcodeactivity.restart";
    public static final String RES_DATA_KEY = "com.dotscene.dronecontroller.qrcodeactivity.code";
    public static final String FACING_KEY = "com.dotscene.dronecontroller.qrcodeactivity.facing";

    SurfaceView previewView = null;
    ImageButton switchCamera = null;

    QREader reader = null;

    boolean useFrontCamera = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        useFrontCamera = preferences.getBoolean(FACING_KEY, useFrontCamera);

        setContentView(R.layout.activity_qr_code);
        previewView = findViewById(R.id.videoTextureView);

        switchCamera = findViewById(R.id.switchCamera);
        switchCamera.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                useFrontCamera = !useFrontCamera;
                SharedPreferences.Editor e =  preferences.edit();
                e.putBoolean(FACING_KEY, useFrontCamera);
                e.apply();

                Intent res = new Intent();
                res.putExtra(RES_RESTART_KEY, true);
                setResult(Activity.RESULT_OK, res);
                finish();
            }
        });

        setupQReader();
    }

    @Override
    protected void onResume() {
        super.onResume();

        String permissions[] = {
                Manifest.permission.CAMERA
        };
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 0);
                break;
            }
        }

        reader.initAndStart(previewView);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        for (int i : grantResults) {
            if (i != PackageManager.PERMISSION_GRANTED) {
                Intent res = new Intent();
                setResult(Activity.RESULT_CANCELED, res);
                finish();
                return;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        reader.releaseAndCleanup();
    }

    private void setupQReader() {
        int src = useFrontCamera ? QREader.FRONT_CAM : QREader.BACK_CAM;
        if (reader != null) {
            reader.stop();
            reader.releaseAndCleanup();
        }

        // Get the size of the screen. The view might not have the correct size at this point.
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Depending on the phones rotation, swap the height and width.
            int tmp = height;
            height = width;
            width = tmp;
        }

        reader = new QREader.Builder(this, previewView, new QRDataListener() {
            @Override
            public void onDetected(String data) {
                returnData(data);
            }
        }).facing(src).enableAutofocus(true).width(width).height(height).build();
    }

    void returnData(String data) {
        Intent res = new Intent();
        res.putExtra(RES_DATA_KEY, data);
        setResult(Activity.RESULT_OK, res);
        finish();
    }
}
