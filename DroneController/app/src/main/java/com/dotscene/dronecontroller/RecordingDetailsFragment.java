package com.dotscene.dronecontroller;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.dotscene.dronecontroller.ServerStateModel.OnDiskInfoUpdatedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnRecordingStateChangedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnStatusLoadedListener;
import com.dotscene.dronecontroller.ServerStateModel.RecordingState;
import com.dotscene.dronecontroller.ServerStateModel.ServerStateProvider;

public class RecordingDetailsFragment extends Fragment implements OnDiskInfoUpdatedListener,
    OnRecordingStateChangedListener, OnStatusLoadedListener {

  ServerStateModel serverStateModel;

  public RecordingDetailsFragment() {

  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.fragment_recording_details, container, false);
  }

  @Override
  public void onResume() {
    super.onResume();
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't access the server state model update because it was not associated with an activity.");
    } else {
      serverStateModel = ((ServerStateProvider) getActivity()).getServerStateModel();
      // register the listener
      serverStateModel.addOnDiskInfoUpdatedListener(this);
      serverStateModel.addOnRecordingStateChangedListener(this);
      serverStateModel.addOnStatusLoadedListener(this);
      // Init ui based upon the current server model. If the model hasn't loaded yet the view
      // will be reinitialized with valid data once the model has loaded.
      onStatusLoaded();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    // deregister the listener
    serverStateModel.removeOnDiskInfoUpdatedListener(this);
    serverStateModel.removeOnRecordingStateChangedListener(this);
    serverStateModel.removeOnStatusLoadedListener(this);
  }

  @Override
  public void onDiskInfoUpdated() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Unable to update the disk info: no activity.");
      return;
    }

    final long newTimeRemaining = serverStateModel.getTimeRemaining();
    final long newDiskCapacityMB = serverStateModel.getDiskCapacityMb();
    final long newDiskFreeMB = serverStateModel.getDiskFreeMb();

      // update the time remaining text view
    activity.runOnUiThread(
      new Runnable() {
        @Override
        public void run() {
          if (getView() != null) {
            Resources res = activity.getResources();
            Spannable s =
                new SpannableString(
                    String.format("%02d", newTimeRemaining / 60)
                        + res.getString(R.string.recDetailsMin)
                        + " "
                        + String.format("%02d", newTimeRemaining % 60)
                        + res.getString(R.string.recDetailsSec));
            // color time red if there are fewer than five minutes left
            if (newTimeRemaining < 300) {
              s.setSpan(
                  new ForegroundColorSpan(
                      ContextCompat.getColor(getContext(), R.color.colorNegative)),
                  0,
                  s.length(),
                  Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            TextView v = (TextView) getView().findViewById(R.id.timeRemaining);
            v.setText(s);
            ProgressBar storageBar = (ProgressBar) getView().findViewById(R.id.storageBar);
            storageBar.setMax((int) newDiskCapacityMB);
            storageBar.setProgress((int) (newDiskCapacityMB - newDiskFreeMB));
          }
        }
      }
    );

  }

  @Override
  public void onStartRecordingProgress(float progress) {

  }

  @Override
  public void onRecordingStarted(final String filename, String recordingName) {

  }

  @Override
  public void onRecordingStopped() {
    // update the lastFile text view
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (getView() != null) {
          TextView lastFile = getView().findViewById(R.id.lastFile);
          lastFile.setText(serverStateModel.getLastRecordingName());
        }
      }
    });
  }

  @Override
  public void onStatusLoaded() {
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (getView() != null) {
          TextView lastFile = (TextView) getView().findViewById(R.id.lastFile);
          lastFile.setText(serverStateModel.getLastRecordingName());
        }
      }
    });
  }
}
