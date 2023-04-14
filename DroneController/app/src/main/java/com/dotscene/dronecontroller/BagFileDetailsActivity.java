package com.dotscene.dronecontroller;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BagFileDetailsActivity extends AppCompatActivity {
  // the name of the extra that should be added to the intent to start this activity
  public static final String FILENAME_EXTRA_NAME = "com.dotscene.bagfilename";

  private static final String FINISHED_LOADING = "com.dotscene.finishedloading";
  private static final String LOADED_TEXT = "com.dotscene.loadedtext";
  private static final int BAG_FILE_DETAILS_SUBJECT = 0;

  String filepath;
  boolean finishedLoading = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_bag_file_details);

    TextView textView = findViewById(R.id.bag_file_details_text);

    if (getIntent().getExtras() == null) {
      finish();
      return;
    }

    filepath = getIntent().getExtras().getString(FILENAME_EXTRA_NAME);
    if (savedInstanceState != null) {
      finishedLoading = savedInstanceState.getBoolean(FINISHED_LOADING);
    }
    // avoid reloading the data if we already loaded it once, and the system restarted
    // the activity (due to rotation, memory constraints, etc...)
    if (finishedLoading) {
      ProgressBar b = findViewById(R.id.bag_file_details_progress);
      textView.setText(savedInstanceState.getString(LOADED_TEXT));
      b.setVisibility(View.INVISIBLE);
      finishedLoading = true;
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    // if the activity was restored and not created from scratch there is no sshManager

  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);
    savedInstanceState.putBoolean(FINISHED_LOADING, finishedLoading);
    TextView v = findViewById(R.id.bag_file_details_text);
    savedInstanceState.putString(LOADED_TEXT, v.getText().toString());
  }

}
