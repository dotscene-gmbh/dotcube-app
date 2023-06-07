package com.dotscene.dronecontroller;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dotscene.dronecontroller.ServerStateModel.FlowState;
import com.dotscene.dronecontroller.ServerStateModel.OnRosErrorsLoadedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnStatusLoadedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnStatusUpdateListener;
import com.dotscene.dronecontroller.ServerStateModel.ServerStateProvider;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by Florian Kramer on 1/28/17.
 */

public class SystemStateFragment extends Fragment implements ServerStateModel.OnRecordingStateChangedListener,
    OnStatusUpdateListener, OnStatusLoadedListener, OnRosErrorsLoadedListener {

  // Bits in the servers bit field that should not be displayed
  static final HashSet<Integer> IGNORED_BITS = new HashSet<>();
  static {
    // the gps lock
    IGNORED_BITS.add(1);
    IGNORED_BITS.add(3);
    IGNORED_BITS.add(7);
    IGNORED_BITS.add(9);
    IGNORED_BITS.add(15);
    IGNORED_BITS.add(17);
    IGNORED_BITS.add(27);
  }
  // For every warning there are two strings:
  // The first is displayed if the ROS Topic Watcher detects a problem with the frequency of the
  // messages.
  // The second is displayed if the ROS Topic Watcher detects something in the messages that should
  // trigger a warning.
  static final int WARNING_TEXTS[] = {
      R.string.systemStateGpsFailure,
      R.string.app_name,
      R.string.systemStateImuFailure,
      R.string.app_name,
      R.string.systemStateImuSyncFailure,
      R.string.systemStateImuSyncNotSynced,
      R.string.systemStateFrontVelodyneFailure,
      R.string.app_name,
      R.string.systemStateTopVelodyneFailure,
      R.string.app_name,
      R.string.systemStateFrontVelodyneCapFailure,
      R.string.systemStateFrontVelodyneCapOn,
      R.string.systemStateTopVelodyneCapFailure,
      R.string.systemStateTopVelodyneCapOn,
      R.string.systemStateFrontVelodynePositionFailure,
      R.string.app_name,
      R.string.systemStateTopVelodynePositionFailure,
      R.string.app_name,
      R.string.systemStateFrontVelodyneInSyncFailure,
      R.string.systemStateFrontVelodyneInSyncWrongValue,
      R.string.systemStateTopVelodyneInSyncFailure,
      R.string.systemStateTopVelodyneInSyncWrongValue,
      R.string.systemStateFrontVelodyneValidGpsFailure,
      R.string.systemStateFrontVelodyneValidGpsWrongValue,
      R.string.systemStateTopVelodyneValidGpsFailure,
      R.string.systemStateTopVelodyneValidGpsWrongValue,
      R.string.systemStateGpioPpsGprmcFailure,
      R.string.app_name,
      R.string.systemStateImuTempFailure,
      R.string.systemStateImuTempWrongValue,
      R.string.systemStateCpuTempFailure,
      R.string.systemStateCpuTempWrongValue,
      R.string.systemStateGpioPpsTimeOffsetFailure,
      R.string.app_name,
      R.string.systemStateImuUncertaintyFailure,
      R.string.systemStateImuUncertaintyWrongValue,
      R.string.systemStateFanStatusFailure,
      R.string.systemStateFanWrongStatus
  };


  static final boolean SCAN_ONLY_WARNINGS[] = {
      false, // Gps
      false,
      false, // imu data
      false,
      false, // imu synchronization
      false,
      true, // front laser data, Begin velodyne dependant data
      true,
      true, // top laster
      true,
      true, // front laser cap detection
      true,
      true, // top laser cap detection
      true,
      true, // top laser position packets
      true,
      true, // front laser position packets
      true,
      true, // front laser time sync
      true,
      true, // top laser time sync
      true,
      true, // front laser gprmc
      true,
      true, // top laser gprmc
      true,
      false, // pps, End velodyne dependant data
      false,
      false, // imu temp
      false,
      false, // cpu temp
      false,
      false, // pps imu time offset
      false,
      true,  // imu uncertainty
      true,
      false, // fan state
      false
  };

  ServerStateModel serverStateModel;

  TextView textViews[] = new TextView[WARNING_TEXTS.length];
  ImageView imageViews[] = new ImageView[WARNING_TEXTS.length];

  TextView textGpsLock;
  StateView stateGpsLock;

  TextView rosErrorText;
  ImageView rosErrorImage;

  AlertDialog rosErrorsAlertDialog = null;

  float density = 96;

  private int dp(float d) {
    return (int)(d * density);
  }

  private int pt(float d) {
    return (int)(d * density / 72.0 * 160.0);
  }


  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_system_state, container, false);

    Context c = getContext();
    density = c.getResources().getDisplayMetrics().density;

    // create the views
    GridLayout failedChecks = view.findViewById(R.id.systemStateFailedChecks);
    for (int i = 0; i < WARNING_TEXTS.length; i++) {
      if (IGNORED_BITS.contains(i)) {
        continue;
      }
      TextView t = new TextView(c);
      t.setVisibility(View.GONE);
      t.setText(WARNING_TEXTS[i]);
      t.setPadding(0, 0, pt(10), 0);
      t.setGravity(Gravity.FILL_HORIZONTAL);


      ImageView img = new ImageView(c);
      img.setImageResource(R.drawable.ic_warning);
      img.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorWarning)));
      img.setVisibility(View.GONE);
      GridLayout.LayoutParams l = new GridLayout.LayoutParams();
      l.width = pt(10);
      l.height = pt(10);
      img.setLayoutParams(l);

      failedChecks.addView(img);
      failedChecks.addView(t);

      textViews[i] = t;
      imageViews[i] = img;
    }


    rosErrorText = view.findViewById(R.id.systemStateTextRosError);
    rosErrorImage = view.findViewById(R.id.systemStateImageRosError);
    {
      GridLayout.LayoutParams l = new GridLayout.LayoutParams();
      l.width = pt(10);
      l.height = pt(10);
      rosErrorImage.setLayoutParams(l);
    }

    stateGpsLock = view.findViewById(R.id.stateGPSLock);
    stateGpsLock.setState(StateView.State.NEGATIVE);
    stateGpsLock.setEnabled(true);
    textGpsLock = view.findViewById(R.id.textGPSLock);
    if (BuildConfig.HAS_GPS) {
      stateGpsLock.setVisibility(view.VISIBLE);
      textGpsLock.setVisibility(view.VISIBLE);
    }

    rosErrorText.setMovementMethod(LinkMovementMethod.getInstance());
    String rosError = c.getResources().getString(R.string.systemStateRosError);
    SpannableString s = new SpannableString(rosError);
    ClickableSpan clickableSpan = new ClickableSpan() {
      @Override
      public void onClick(@NonNull View widget) {
        showRosErrors();
      }
    };
    s.setSpan(clickableSpan, 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    rosErrorText.setText(s);

    return view;
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getView() != null) {
      rosErrorText.setMovementMethod(LinkMovementMethod.getInstance());
      SpannableString s = new SpannableString(rosErrorText.getText());
      ClickableSpan c = new ClickableSpan() {
        @Override
        public void onClick(@NonNull View widget) {
          showRosErrors();
        }
      };
      s.setSpan(c, 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      rosErrorText.setText(s);
    }
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "System state fragment couldn't access the server state model update because it was not associated with an activity.");
    } else {
      serverStateModel = ((ServerStateProvider) activity).getServerStateModel();
    }
    // register the listener
    serverStateModel.addOnRecordingStateChangedListener(this);
    serverStateModel.addOnStatusUpdateListener(this);
    serverStateModel.addOnStatusLoadedListener(this);
    serverStateModel.addOnRosErrorsLoadedListener(this);
    serverStateModel.loadRosStatus();
    // Init ui based upon the current server model. If the model hasn't loaded yet the view
    // will be reinitialized with valid data once the model has loaded.
    onStatusLoaded();
  }

  @Override
  public void onPause() {
    super.onPause();
    // deregister the listener
    serverStateModel.removeOnRecordingStateChangedListener(this);
    serverStateModel.removeOnStatusUpdateListener(this);
    serverStateModel.removeOnStatusLoadedListener(this);
    serverStateModel.removeOnRosErrorsLoadedListener(this);
  }

  @Override
  public void onStartRecordingProgress(float progress) {

  }

  @Override
  public void onRecordingStarted(String filename, String recordingName) {
    updateStatusDisplays();
  }

  @Override
  public void onRecordingStopped() {
    updateStatusDisplays();
  }


  private void updateStatusDisplays() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "System state fragment couldn't handle status update because it was not associated with an activity.");
      return;
    }
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (getView() != null) {
          boolean isNotRecording = serverStateModel.getRecordingState() != ServerStateModel.RecordingState.RECORDING;
          ArrayList<FlowState> flowStates = serverStateModel.getFlowStates();
          for (int i = 0; i < WARNING_TEXTS.length; i++) {
            if (IGNORED_BITS.contains(i)) {
              continue;
            }

            // check the bit
            FlowState s = FlowState.GOOD;
            if (i < flowStates.size()) {
              s = flowStates.get(i);

              // Don't show warnings for values if no packets are arriving on a topic
              if (i % 2 == 1) {
                if (flowStates.get(i - 1) == FlowState.FAILED && s  == FlowState.FAILED) {
                  s = FlowState.GOOD;
                }
              }
            }
            // Don't show scan only warnings while we are not recording
            if (SCAN_ONLY_WARNINGS[i] && isNotRecording && s == FlowState.FAILED) {
              s = FlowState.GOOD;
            }
            if (textViews[i] != null) {
              textViews[i].setVisibility(s == FlowState.GOOD ? View.GONE : View.VISIBLE);
            }
            if (imageViews[i] != null) {
              imageViews[i].setVisibility(s == FlowState.GOOD ? View.GONE : View.VISIBLE);
              if (s == FlowState.FAILED) {
                imageViews[i].setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorWarning)));
              } else if (s == FlowState.HAD_FAILED) {
                imageViews[i].setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorWarningGone)));
              }
            }
          }

          rosErrorImage.setVisibility(serverStateModel.getHasRosError() ? View.VISIBLE : View.GONE);
          rosErrorText.setVisibility(serverStateModel.getHasRosError() ? View.VISIBLE : View.GONE);

          stateGpsLock.setState(serverStateModel.getGpsHasFix() ? StateView.State.POSITIVE : StateView.State.NEGATIVE);
        }
      }
    });
  }

  @Override
  public void onStatusUpdate(final ServerStateModel model) {
    updateStatusDisplays();
  }

  @Override
  public void onStatusLoaded() {
    if (serverStateModel.isRecording()) {
      onRecordingStarted(serverStateModel.getRecordingFilename(), serverStateModel.getRecordingName());
    }
  }

  private void showRosErrors() {
    if (rosErrorsAlertDialog != null) {
      return;
    }

    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "System state fragment couldn't handle ros errors because it was not associated with an activity.");
      return;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    ProgressBar p = new ProgressBar(activity);
    p.setIndeterminate(true);
    builder.setView(p);
    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        rosErrorsAlertDialog = null;
      }
    });
    // builder.setPositiveButton(R.string.ok, null);
    rosErrorsAlertDialog = builder.create();

    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        rosErrorsAlertDialog.show();
        serverStateModel.queryForRosErrors();
      }
    });
  }

  @Override
  public void onRosErrorsLoaded(final String errors[]) {
    Log.d(getClass().getSimpleName(), "Got ros errors.");
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "System state fragment couldn't handle ros errors because it was not associated with an activity.");
      return;
    }
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (rosErrorsAlertDialog == null) {
          Log.w(getClass().getSimpleName(), "Ros errors where loaded but no dialog to display them has been opened.");
          return;
        }
        ScrollView scrollView = new ScrollView(getActivity());
        scrollView.setPadding(10, 10, 10, 10);
        TextView textView = new TextView(getActivity());

        StringBuilder textBuilder = new StringBuilder();
        if (errors.length == 0) {
          textBuilder.append("No errors have been logged on the sensor.");
        } else {
          for (String error : errors) {
            textBuilder.append(error);
            textBuilder.append("\n");
          }
        }

        textView.setText(textBuilder.toString());
        scrollView.addView(textView);
        rosErrorsAlertDialog.setContentView(scrollView);
      }
    });

  }
}
