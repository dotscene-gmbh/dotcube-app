package com.dotscene.dronecontroller;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.widget.ImageViewCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class SyncStatusFragment extends Fragment implements ServerStateModel.OnSyncStatusReceivedListener {

  ServerStateModel serverStateModel = null;

  Timer syncStatusPollTimer = null;

  ProgressBar statusProgress;
  ImageView statusIcon;
  TextView statusText;
  TextView etaText;
  TextView uploadSpeedText;
  TextView uploadNumFiles;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_sync_status, container, false);
    statusProgress = view.findViewById(R.id.syncStatusProgress);
    statusIcon = view.findViewById(R.id.syncStatusIcon);
    statusText = view.findViewById(R.id.syncStatusText);
    etaText = view.findViewById(R.id.syncUploadEta);
    uploadSpeedText = view.findViewById(R.id.syncUploadSpeed);
    uploadNumFiles = view.findViewById(R.id.syncUploadFiles);
    return view;
  }


  @Override
  public void onResume() {
    super.onResume();
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "System state fragment couldn't access the server state model update because it was not associated with an activity.");
    } else {
      serverStateModel = ((ServerStateModel.ServerStateProvider) activity).getServerStateModel();
    }
    // register the listener
    serverStateModel.addOnSyncStatusReceivedListener(this);
    if (syncStatusPollTimer != null) {
      syncStatusPollTimer.cancel();
      syncStatusPollTimer = null;
    }
    syncStatusPollTimer = new Timer();
    syncStatusPollTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        if (serverStateModel != null) {
          serverStateModel.queryForSyncStatus();
        }
      }
    }, 0, 1000);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (syncStatusPollTimer != null) {
      syncStatusPollTimer.cancel();
      syncStatusPollTimer = null;
    }
  }

  @Override
  public void onSyncStatusReceived(final boolean requestSuccessful, final boolean isSyncing, final boolean isConnected,
                                   final long etaMs, final long uploadSpeedKbps,
                                   final short uploadPercentage) {
    Activity a = getActivity();
    if (a == null) {
      Log.w(getClass().getSimpleName(),
          "Unable to update the sync status fragment, the activity is null");
      return;
    }
    a.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (!requestSuccessful) {
          statusProgress.setVisibility(View.GONE);
          ImageViewCompat.setImageTintList(statusIcon, ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
          statusIcon.setImageResource(R.drawable.ic_question_mark);
          statusText.setText(R.string.storageSyncStatusUnknown);
          etaText.setText("");
          uploadSpeedText.setText("");
          uploadNumFiles.setText("");
        } else if (!isConnected) {
          statusProgress.setVisibility(View.GONE);
          ImageViewCompat.setImageTintList(statusIcon, ColorStateList.valueOf(getResources().getColor(R.color.colorNegative)));
          statusIcon.setImageResource(R.drawable.ic_x);
          statusText.setText(R.string.storageSyncStatusDisconnected);
          etaText.setText("");
          uploadSpeedText.setText("");
          uploadNumFiles.setText("");
        } else if (isSyncing) {
          statusProgress.setVisibility(View.VISIBLE);

          ImageViewCompat.setImageTintList(statusIcon, ColorStateList.valueOf(getResources().getColor(R.color.colorPositive)));
          statusIcon.setImageResource(R.drawable.ic_loop_circular);

          statusProgress.setIndeterminate(false);
          statusProgress.setMax(100);
          statusProgress.setProgress(uploadPercentage);
          statusText.setText(R.string.storageSyncStatusUploading);

          long etaMin = (etaMs / 1000) / 60;
          long etaSec = (etaMs / 1000) % 60;

          etaText.setText(getResources().getString(R.string.storageSyncEta, etaMin, etaSec));

          String speedUnit = "Kbps";
          float speed = uploadSpeedKbps;
          if (speed > 512) {
            speed /= 1024;
            speedUnit = "Mbps";
          }
          uploadSpeedText.setText(getResources().getString(R.string.uploadSpeed, speed, speedUnit));
        } else {
          statusProgress.setVisibility(View.GONE);

          ImageViewCompat.setImageTintList(statusIcon, ColorStateList.valueOf(getResources().getColor(R.color.colorPositive)));
          statusIcon.setImageResource(R.drawable.ic_check);
          statusText.setText(R.string.storageSyncStatusSynced);
          etaText.setText("");
          uploadSpeedText.setText("");
          uploadNumFiles.setText("");
        }
      }
    });
  }
}
