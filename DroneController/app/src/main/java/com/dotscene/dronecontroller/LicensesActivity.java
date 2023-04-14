package com.dotscene.dronecontroller;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class LicensesActivity extends AppCompatActivity {
    private int readInt4(InputStream in) throws IOException {
        int i = in.read() & 0xFF;
        i |= (in.read() & 0xFF) << 8;
        i |= (in.read() & 0xFF) << 16;
        i |= (in.read() & 0xFF) << 24;
        return i;
    }

    class License {
        public String libraryName;
        public String licenseText;

        public boolean load(InputStream in) throws IOException {
            int libraryNameLen = readInt4(in);
            int licenseTextLen = readInt4(in);
            byte[] binLibraryName = new byte[libraryNameLen];
            byte[] binLicenseText = new byte[licenseTextLen];
            in.read(binLibraryName, 0, libraryNameLen);
            int status = in.read(binLicenseText, 0, licenseTextLen);
            libraryName = new String(binLibraryName, StandardCharsets.UTF_8);
            licenseText = new String(binLicenseText, StandardCharsets.UTF_8);
            return status > 0;
        }
    }

    private static final int ANIMATION_DURATION = 500;

    ArrayList<License> licenses = new ArrayList<>();

    ArrayAdapter<String> licensesAdapter;

    ListView licensesList;
    TextView licenseText;
    ScrollView licenseContainer;

    ObjectAnimator slideInAnimator;
    Timer slideInTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_licenses);

        // Load the data
        InputStream in = getResources().openRawResource(R.raw.licenses);
        try {
            int numLicenses = readInt4(in);
            Log.i(getClass().getSimpleName(), "Loading " + numLicenses + " licenses.");
            for (int i = 0; i < numLicenses; ++i) {
                License nextLicense = new License();
                nextLicense.load(in);
                licenses.add(nextLicense);
                Log.d(getClass().getSimpleName(), "Loaded license for library " + nextLicense.libraryName);
            }
        } catch (IOException e) {
            Toast.makeText(this, R.string.errorLoadingLicenses, Toast.LENGTH_LONG).show();
            Log.e(getClass().getSimpleName(), "An error occurred while loading the licenses: ", e);
        }



        // Setup the ui
        licensesAdapter = new ArrayAdapter<String>(this, R.layout.license_list_item);
        licensesList = findViewById(R.id.licensesList);
        for (License l : licenses) {
            licensesAdapter.add(l.libraryName);
        }
        licensesList.setAdapter(licensesAdapter);

        licensesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                showLicenseText(licenses.get((int)id));
            }
        });

        licenseText = findViewById(R.id.licenseText);
        licenseContainer = findViewById(R.id.licenseContainer);
    }

    private void showLicenseText(License l) {
        licenseText.setText(l.licenseText);
        licenseContainer.setVisibility(View.VISIBLE);
        licenseContainer.setTranslationX(getWindow().getDecorView().getWidth());
        if (slideInAnimator != null) {
            slideInAnimator.cancel();
        }
        slideInAnimator = ObjectAnimator.ofFloat(licenseContainer, "translationX", 0);
        slideInAnimator.setDuration(ANIMATION_DURATION);
        slideInAnimator.start();
    }

    private void hideLicenseText() {
        if (slideInAnimator != null) {
            slideInAnimator.cancel();
        }
        if (slideInTimer != null) {
            slideInTimer.cancel();
        }
        ObjectAnimator animator = ObjectAnimator.ofFloat(licenseContainer, "translationX", getWindow().getDecorView().getWidth());
        animator.setDuration(ANIMATION_DURATION);
        animator.start();
        slideInTimer = new Timer();
        slideInTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        licenseContainer.setVisibility(View.GONE);
                    }
                });
            }
        }, ANIMATION_DURATION);
    }

    @Override
    public void onBackPressed() {
        if (licenseContainer.getVisibility() == View.VISIBLE) {
          hideLicenseText();
        } else {
            super.onBackPressed();
        }
    }
}
