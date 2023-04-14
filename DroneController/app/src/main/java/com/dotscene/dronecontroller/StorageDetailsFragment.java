package com.dotscene.dronecontroller;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.dotscene.dronecontroller.ServerStateModel.OnStatusLoadedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnDiskInfoUpdatedListener;
import com.dotscene.dronecontroller.ServerStateModel.ServerStateProvider;
import java.util.Locale;

/**
 * Created by Florian Kramer on 4/16/17.
 */

public class StorageDetailsFragment extends Fragment implements OnStatusLoadedListener,
    OnDiskInfoUpdatedListener {

  private ServerStateModel serverStateModel;
  private boolean showExternal;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.fragment_storage_details, container, false);

    return v;
  }

  @Override
  public void onResume() {
    super.onResume();
    serverStateModel = ((ServerStateProvider) getActivity()).getServerStateModel();
    serverStateModel.addOnStatusLoadedListener(this);
    serverStateModel.addOnDiskInfoUpdatedListener(this);
    // Init ui based upon the current server model. If the model hasn't loaded yet the view
    // will be reinitialized with valid data once the model has loaded.
    onStatusLoaded();
  }

  @Override
  public void onPause() {
    super.onPause();
    serverStateModel.removeOnStatusLoadedListener(this);
    serverStateModel.removeOnDiskInfoUpdatedListener(this);
  }

  @Override
  public void onDiskInfoUpdated() {
    // update the disk info texts
    final long newTimeRemainingMB = serverStateModel.getTimeRemaining();
    final long newDiskCapacityMB = serverStateModel.getDiskCapacityMb();
    final long newDiskFreeMB = serverStateModel.getDiskFreeMb();

    // update the time remaining text view
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (getView() != null) {
          Spannable s = new SpannableString(
              getResources().getString(R.string.timeRemaining) + " "
                  + String.format("%02d", newTimeRemainingMB / 60) + ":"
                  + String.format("%02d", newTimeRemainingMB % 60));
          // color time red if there are fewer than five minutes left
          if (newTimeRemainingMB < 300) {
            s.setSpan(
                new ForegroundColorSpan(
                    ContextCompat.getColor(getContext(), R.color.colorNegative)),
                getResources().getString(R.string.timeRemaining).length(), s.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          }
          TextView timeRemaining = (TextView) getView().findViewById(R.id.timeRemaining);
          timeRemaining.setText(s);
          TextView capacity = (TextView) getView().findViewById(R.id.capacity);
          capacity
              .setText(
                  getResources().getString(R.string.capacity) + "\t" + String.format(Locale.ENGLISH, "%.2f", newDiskCapacityMB / 1024.0)
                      + " GB");
          TextView remaining = (TextView) getView().findViewById(R.id.free);
          remaining.setText(
              getResources().getString(R.string.free) + "\t\t\t" + String.format(Locale.ENGLISH, "%.2f", newDiskFreeMB / 1024.0) + " GB");
          TextView used = (TextView) getView().findViewById(R.id.used);
          used.setText(
              getResources().getString(R.string.used) + "\t\t" + String.format(Locale.ENGLISH, "%.2f", (newDiskCapacityMB - newDiskFreeMB)
                  / 1024.0)
                  + " GB");
          ProgressBar storageBar = (ProgressBar) getView().findViewById(R.id.storageBar);
          storageBar.setMax((int)newDiskCapacityMB);
          storageBar.setProgress((int)(newDiskCapacityMB - newDiskFreeMB));
        }
      }
    });
  }

  public void setShowExternal(boolean showExternal) {
    this.showExternal = showExternal;
  }


  @Override
  public void onStatusLoaded() {
    onDiskInfoUpdated();
  }
}
