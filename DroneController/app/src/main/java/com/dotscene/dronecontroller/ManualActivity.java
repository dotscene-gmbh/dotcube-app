package com.dotscene.dronecontroller;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.github.barteksc.pdfviewer.PDFView;

public class ManualActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);
        PDFView pdfView = findViewById(R.id.pdfView);
        pdfView.fromAsset("Bedienungsanleitung_dotcube6.pdf").load();
    }
}
