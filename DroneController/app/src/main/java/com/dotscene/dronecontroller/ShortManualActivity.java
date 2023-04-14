package com.dotscene.dronecontroller;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.github.barteksc.pdfviewer.PDFView;

public class ShortManualActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_short_manual);
        PDFView pdfView = findViewById(R.id.pdfView);
        pdfView.fromAsset("Kurzanleitung_Checkliste.pdf").load();
    }
}
