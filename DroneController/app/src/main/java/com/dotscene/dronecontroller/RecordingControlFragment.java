package com.dotscene.dronecontroller;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;

import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.dotscene.dronecontroller.ServerStateModel.OnConnectionStateChangedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnDiskInfoUpdatedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnRecordingStateChangedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnStatusLoadedListener;
import com.dotscene.dronecontroller.ServerStateModel.RecordingState;
import com.dotscene.dronecontroller.ServerStateModel.ServerStateProvider;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Florian Kramer on 4/14/17.
 */

public class RecordingControlFragment extends Fragment implements OnCheckedChangeListener,
        OnConnectionStateChangedListener, OnRecordingStateChangedListener,
        OnStatusLoadedListener,
        OnDiskInfoUpdatedListener,
        ServerStateModel.OnRecordingStartExpectedDuration,
        ServerStateModel.OnImuTestUpdateListener {

  private ServerStateModel serverStateModel;

  // used to update the recording for text view
  private Timer recordingTimeTimer;
  // used to abort the stop recording dialog, which blocks the ui
  private Timer stopRecordingTimer;
  private Timer startRecordingTimer;
  private UpdateRecordingTimerTask updateRecordingTimerTask;

  private boolean recordingTypeSet = false;
  private boolean nameIsValid = false;

  AlertDialog waitForRecordingDialog;
  AlertDialog startRecordingDialog;

  ProgressBar startRecordingProgress = null;
  TextView startRecordingText = null;

  TextView currentRecordingText;

  String projectName;
  String recordingName;
  String recordingType;

  ToggleButton toggleRecordingButton;

  public RecordingControlFragment() {
    recordingTimeTimer = new Timer();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    View view = inflater.inflate(R.layout.fragment_recording_control,
            container, false);
    toggleRecordingButton = view.findViewById(R.id.toggleRecording);
    toggleRecordingButton.setOnCheckedChangeListener(this);

    currentRecordingText = view.findViewById(R.id.recordingTo);

    return view;
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    serverStateModel = ((ServerStateProvider) context).getServerStateModel();

  }

  @Override
  public void onResume() {
    super.onResume();
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't access the server state " +
              "model update because it was not associated with an activity.");
    } else {
      serverStateModel = ((ServerStateProvider) activity).getServerStateModel();
      // register the listener
      serverStateModel.addOnConnectionStateChangedListener(this);
      serverStateModel.addOnRecordingStateChangedListener(this);
      serverStateModel.addOnStatusLoadedListener(this);
      serverStateModel.addOnDiskInfoUpdatedListener(this);
      serverStateModel.addOnRecordingStartExpectedDuration(this);
      serverStateModel.addOnImuTestUpdateListener(this);

      // Init ui based upon the current server model. If the model hasn't
      // loaded yet the view
      // will be reinitialized with valid data once the model has loaded.
      onStatusLoaded();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    // deregister the listeners that are not necessary if the app is paused
    serverStateModel.removeOnConnectionStateChangedListener(this);
    serverStateModel.removeOnStatusLoadedListener(this);
    serverStateModel.removeOnDiskInfoUpdatedListener(this);
    serverStateModel.removeOnRecordingStartExpectedDuration(this);
    serverStateModel.removeOnImuTestUpdateListener(this);
    if (updateRecordingTimerTask != null) {
      updateRecordingTimerTask.cancel();
    }
  }

  @Override
  public void onDestroy() {
    // deregister the remaining listener
    serverStateModel.removeOnRecordingStateChangedListener(this);

    super.onDestroy();
  }

  private void showImuTestInit() {
    if (serverStateModel.getIsFirstRecording()) {
      final Activity activity = getActivity();
      if (activity == null) {
        Log.e(getClass().getSimpleName(), "Couldn't start the recording " +
                "because the fragment was not associated with an activity.");
        return;
      }
      AlertDialog.Builder b = new AlertDialog.Builder(activity);
      b.setMessage(R.string.startRecordingPrepareImuTest);
      b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          startRecordingWithCurrentSettings();
        }
      });
      b.setNegativeButton(R.string.cancel, null);
      b.setOnCancelListener(new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
          toggleRecordingButton.setChecked(false);
        }
      });
      b.create().show();
    } else {
      startRecordingWithCurrentSettings();
    }
  }

  private void showImuTestRunningScreen() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "The activity is null in " +
              "showImuTestRunningScreen");
      return;
    }

    if (waitForRecordingDialog != null) {
      waitForRecordingDialog.hide();
      waitForRecordingDialog = null;
    }

    // block ui until the recording actually started
    AlertDialog.Builder modalBuilder = new AlertDialog.Builder(activity);
    modalBuilder.setMessage(R.string.startRecordingImuTest);

    LinearLayout startRecordingLayout = new LinearLayout(activity);
    startRecordingLayout.setOrientation(LinearLayout.VERTICAL);
    startRecordingProgress = new ProgressBar(getContext(), null,
            android.R.attr.progressBarStyleHorizontal);
    startRecordingProgress.setPadding(50, 0, 50, 0);
    startRecordingLayout.addView(startRecordingProgress);

    startRecordingText = new TextView(activity);
    startRecordingText.setPadding(50, 0, 50, 0);
    startRecordingText.setText(R.string.startRecordingDontMove);
    startRecordingLayout.addView(startRecordingText);

    modalBuilder.setView(startRecordingLayout);
    modalBuilder.setCancelable(false);
    waitForRecordingDialog = modalBuilder.create();
    waitForRecordingDialog.show();
  }

  private boolean isSensorStartingScreenVisible() {
    return waitForRecordingDialog != null && waitForRecordingDialog.isShowing();
  }

  private void showSensorStartingScreen() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "The activity is null in " +
              "showSensorStartingScreen");
      return;
    }

    if (waitForRecordingDialog != null) {
      waitForRecordingDialog.hide();
      waitForRecordingDialog = null;
    }

    // block ui until the recording actually started
    AlertDialog.Builder modalBuilder = new AlertDialog.Builder(activity);
    modalBuilder.setMessage(R.string.startRecordingWait);


    LinearLayout startRecordingLayout = new LinearLayout(activity);
    startRecordingLayout.setOrientation(LinearLayout.VERTICAL);
    startRecordingProgress = new ProgressBar(getContext(), null,
            android.R.attr.progressBarStyleHorizontal);
    startRecordingProgress.setPadding(50, 0, 50, 0);
    startRecordingLayout.addView(startRecordingProgress);

    startRecordingText = new TextView(activity);
    startRecordingText.setPadding(50, 0, 50, 0);
    startRecordingLayout.addView(startRecordingText);

    modalBuilder.setView(startRecordingLayout);
    modalBuilder.setCancelable(false);
    waitForRecordingDialog = modalBuilder.create();
    waitForRecordingDialog.show();
  }

  private void startRecording() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't start the recording because" +
              " the fragment was not associated with an activity.");
      return;
    }
    Resources res = activity.getResources();

    recordingTypeSet = false;
    nameIsValid = false;

    // show a dialog that asks the user for a filename
    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setMessage(R.string.bagname_dialog);

    LinearLayout rootLayout = new LinearLayout(activity);
    rootLayout.setOrientation(LinearLayout.VERTICAL);

    LinearLayout inputLayout = new LinearLayout(activity);

    final EditText bagNameEdit = new EditText(activity);
    bagNameEdit.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    bagNameEdit.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count,
                                    int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before,
                                int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {
        if (ServerStateModel.PATTERN_FILENAME.matcher(s.toString()).find()) {
          bagNameEdit.setError(getResources().getString(R.string.invalidRecordingName));
          nameIsValid = false;
          startRecordingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(nameIsValid && recordingTypeSet);
        } else if (s.length() == 0) {
          nameIsValid = false;
          startRecordingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(nameIsValid && recordingTypeSet);
        } else {
          bagNameEdit.setError(null);
          nameIsValid = true;
          startRecordingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(nameIsValid && recordingTypeSet);
        }
      }
    });

    inputLayout.addView(bagNameEdit,
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
                    , ViewGroup.LayoutParams.WRAP_CONTENT));

    LinearLayout.LayoutParams inputLayoutParams =
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
                    , ViewGroup.LayoutParams.WRAP_CONTENT);
    float pxMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7
            , res.getDisplayMetrics());
    inputLayoutParams.setMargins(0, 0, 0, (int) pxMargin);

    rootLayout.addView(inputLayout, inputLayoutParams);


    RadioGroup typeGroup = new RadioGroup(activity);
    typeGroup.setOrientation(LinearLayout.HORIZONTAL);

    String defaultRecordingType = serverStateModel.getDefaultRecordingType();

    final RadioButton buttonIndoor = new RadioButton(activity);
    buttonIndoor.setText(R.string.recordingTypeIndoor);
    buttonIndoor.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        try {
          recordingTypeSet = true;
          startRecordingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(nameIsValid && recordingTypeSet);
        } catch (NullPointerException e) {
          Log.e(getClass().getSimpleName(), "Error on radio button select: ",
                  e);
        }
      }
    });
    typeGroup.addView(buttonIndoor);

    final RadioButton buttonOutdoor = new RadioButton(activity);
    buttonOutdoor.setText(R.string.recordingTypeOutdoor);
    buttonOutdoor.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        try {
          recordingTypeSet = true;
          startRecordingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(nameIsValid && recordingTypeSet);
        } catch (NullPointerException e) {
          Log.e(getClass().getSimpleName(), "Error on radio button select: ",
                  e);
        }
      }
    });
    typeGroup.addView(buttonOutdoor);

    final RadioButton buttonFlight = new RadioButton(activity);
    buttonFlight.setText(R.string.recordingTypeFlight);
    buttonFlight.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        try {
          recordingTypeSet = true;
          startRecordingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(nameIsValid && recordingTypeSet);
        } catch (NullPointerException e) {
          Log.e(getClass().getSimpleName(), "Error on radio button select: ",
                  e);
        }
      }
    });
    typeGroup.addView(buttonFlight);

    if (defaultRecordingType.equals(ServerStateModel.RECORDING_TYPE_INDOOR)) {
      buttonIndoor.setChecked(true);
      recordingTypeSet = true;
    } else if (defaultRecordingType.equals(ServerStateModel.RECORDING_TYPE_OUTDOOR)) {
      buttonOutdoor.setChecked(true);
      recordingTypeSet = true;
    } else if (defaultRecordingType.equals(ServerStateModel.RECORDING_TYPE_FLIGHT)) {
      buttonFlight.setChecked(true);
      recordingTypeSet = true;
    }

    rootLayout.addView(typeGroup);

    builder.setView(rootLayout);
    builder.setPositiveButton(R.string.ok,
            new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        recordingName = bagNameEdit.getText().toString();

        // update the recording type
        recordingType = "error";
        if (buttonIndoor.isChecked()) {
          recordingType = ServerStateModel.RECORDING_TYPE_INDOOR;
        } else if (buttonOutdoor.isChecked()) {
          recordingType = ServerStateModel.RECORDING_TYPE_OUTDOOR;
        } else if (buttonFlight.isChecked()) {
          recordingType = ServerStateModel.RECORDING_TYPE_FLIGHT;
        }
        showImuTestInit();
      }
    });
    builder.setNegativeButton(R.string.cancel,
            new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        final View view = getView();
        if (view == null) {
          Log.e(getClass().getSimpleName(), "Couldn't start the recording " +
                  "because the fragment's view doesn't exist.");
          return;
        }
        ToggleButton toggleRecording = view.findViewById(R.id.toggleRecording);
        toggleRecording.setChecked(false);
      }
    });
    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        toggleRecordingButton.setChecked(false);
      }
    });


    startRecordingDialog = builder.create();
    startRecordingDialog.show();
    startRecordingDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    bagNameEdit.requestFocus();
    // Ensure the soft keyboard is always visible while the dialog is visible
    try {
      startRecordingDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    } catch (NullPointerException e) {
      Log.e(getClass().getSimpleName(), "Unable to set the soft input state",
              e);
    }
  }

  void startRecordingWithCurrentSettings() {
    startRecordingWithCurrentSettings(false);
  }

  void startRecordingWithCurrentSettings(boolean skipImuCheck) {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "The activity was null.");
      return;
    }

    if (serverStateModel.getIsFirstRecording() && !skipImuCheck) {
      showImuTestRunningScreen();
    } else {
      showSensorStartingScreen();
    }

    initStartRecordingTimer();

    serverStateModel.startRecording(serverStateModel.getProjectName(),
            recordingName, recordingType, getContext(), skipImuCheck);
  }

  void initStartRecordingTimer() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "The activity was null.");
      return;
    }

    // Start a time that aborts the operation if the server didn't respond
    // within 60 seconds.
    if (startRecordingTimer != null) {
      startRecordingTimer.cancel();
      startRecordingTimer = null;
    }
    startRecordingTimer = new Timer();
    startRecordingTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        try {
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (waitForRecordingDialog != null) {
                waitForRecordingDialog.hide();
              }
              Toast.makeText(getContext(), R.string.startRecordingTimeout,
                      Toast.LENGTH_LONG).show();
            }
          });
        } catch (NullPointerException e) {
          e.printStackTrace();
        }
        serverStateModel.disconnect();
      }
    }, 60000);
  }

  @Override
  public void onCheckedChanged(final CompoundButton buttonView,
                               boolean isChecked) {
    switch (buttonView.getId()) {
      case R.id.toggleRecording:
        if (isChecked && !serverStateModel.isRecording()) {
          startRecording();
        } else if (serverStateModel.isRecording()) {
          showStoppingTheRecordingDialog();
          serverStateModel.stopRecording();
        }
        break;
    }
  }

  private void showStoppingTheRecordingDialog() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't show the stopping the " +
              "recording dialog because the fragment was not associated with " +
              "an activity.");
      return;
    }
    if (serverStateModel.getRecordingState() != ServerStateModel.RecordingState.RECORDING &&
            serverStateModel.getRecordingState() != ServerStateModel.RecordingState.STOPPING) {
      Log.w(getClass().getSimpleName(), "Tried to show the stopping the " +
              "recording dialog whithout a recording running or being stopped" +
              ".");
      return;
    }
    // block ui until the recording actually stopped
    AlertDialog.Builder modalBuilder = new AlertDialog.Builder(activity);
    modalBuilder.setMessage(R.string.stopRecordingWait);
    modalBuilder.setCancelable(false);
    waitForRecordingDialog = modalBuilder.create();
    if (stopRecordingTimer != null) {
      stopRecordingTimer.cancel();
      stopRecordingTimer = null;
    }
    stopRecordingTimer = new Timer();
    stopRecordingTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        try {
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (waitForRecordingDialog != null) {
                waitForRecordingDialog.hide();
              }
              Toast.makeText(getContext(), R.string.stopRecordingTimeout,
                      Toast.LENGTH_LONG).show();
            }
          });
        } catch (NullPointerException e) {
          e.printStackTrace();
        }
        serverStateModel.disconnect();
      }
    }, 60000);
    waitForRecordingDialog.show();
  }

  @Override
  public void onConnectionEstablished() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't handle the on connection " +
              "established event because the fragment was not associated with" +
              " an activity.");
      return;
    }
    // enable the recording button
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (getView() != null) {
          updateScanButtonState();
        }
      }
    });
  }


  @Override
  public void onConnectionClosed() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't handle the on connection " +
              "closed event because the fragment was not associated with an " +
              "activity.");
      return;
    }
    // disable the recording button
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (getView() != null) {
          updateScanButtonState();
        }
      }
    });
  }


  @Override
  public void onStartRecordingProgress(final float progress) {
    final Activity a = getActivity();
    if (a != null) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          ensureRecordingButtonIsChecked();
          if (!isSensorStartingScreenVisible()) {
            showSensorStartingScreen();
          }
          if (startRecordingProgress != null) {
            startRecordingProgress.setMax(10000);
            startRecordingProgress.setProgress((int) (progress * 10000));
          }
        }
      });
    } else {
      Log.e(getClass().getSimpleName(), "Unable to display the recording progress: the activity is null");
    }
  }

  @Override
  public void onRecordingStarted(final String filename,
                                 final String recordingName) {
    // Start a Scan warning service
    Intent startScanWarningService = new Intent(getContext(), ScanWarningService.class);
    getContext().startService(startScanWarningService);

    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't hide the wait for recording" +
              " dialog the fragment was not associated with an activity.");
      return;
    }
    if (startRecordingTimer != null) {
      startRecordingTimer.cancel();
      startRecordingTimer = null;
    }
    // begin updating the recording for text view
    updateRecordingTimerTask = new UpdateRecordingTimerTask();
    recordingTimeTimer.scheduleAtFixedRate(updateRecordingTimerTask, 0, 1000);
    if (waitForRecordingDialog != null) {
      getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          ensureRecordingButtonIsChecked();
          if (stopRecordingTimer != null) {
            stopRecordingTimer.cancel();
            stopRecordingTimer = null;
          }
          if (waitForRecordingDialog != null && getView() != null) {
            waitForRecordingDialog.hide();
          }
          waitForRecordingDialog = null;


          // Set the recording to file
          setCurrentRecordingName(recordingName);
        }
      });
    }
  }

  @Override
  public void onRecordingStopped() {
    // Stop the Scan warning service
    Intent stopScanWarningService = new Intent(getContext(), ScanWarningService.class);
    getContext().stopService(stopScanWarningService);

    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't handle the on recording " +
              "stopped event because the fragment was not associated with an " +
              "activity.");
      return;
    }

    // stop updating the recording time
    if (updateRecordingTimerTask != null) {
      updateRecordingTimerTask.cancel();
      updateRecordingTimerTask = null;
    }
    if (recordingTimeTimer != null) {
      recordingTimeTimer.cancel();
      recordingTimeTimer = new Timer();
    }
    if (stopRecordingTimer != null) {
      stopRecordingTimer.cancel();
      stopRecordingTimer = null;
    }
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        View view = getView();
        if (view == null) {
          Log.e(getClass().getSimpleName(), "Unable to reenable the recording" +
                  " button, no view exists.");
          return;
        }

        ensureRecordingButtonIsNotChecked();
        if (waitForRecordingDialog != null) {
          waitForRecordingDialog.hide();
        }
        waitForRecordingDialog = null;

        // Set the recording to file
        currentRecordingText.setText("");
        currentRecordingText.setVisibility(View.GONE);
      }
    });
  }

  @Override
  public void onStatusLoaded() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't handle the on status loaded" +
              " event because the fragment was not associated with an " +
              "activity.");
      return;
    }
    // enable the scan button
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (getView() != null) {
          updateScanButtonState();
          if (serverStateModel.getRecordingState() == ServerStateModel.RecordingState.RECORDING ||
                  serverStateModel.getRecordingState() == ServerStateModel.RecordingState.IMU_TESTING ||
                  serverStateModel.getRecordingState() == ServerStateModel.RecordingState.STARTING_RECORDING) {
            ToggleButton t = getView().findViewById(R.id.toggleRecording);
            // check the scan button
            t.setOnCheckedChangeListener(null);
            t.setChecked(true);
            t.setOnCheckedChangeListener(RecordingControlFragment.this);

            if (serverStateModel.getRecordingState() == ServerStateModel.RecordingState.RECORDING) {
              setCurrentRecordingName(serverStateModel.getRecordingName());
              onRecordingStarted(serverStateModel.getRecordingFilename(),
                      serverStateModel.getRecordingName());
            } else if (serverStateModel.getRecordingState() == ServerStateModel.RecordingState.IMU_TESTING) {
              showImuTestRunningScreen();
              initStartRecordingTimer();
            } else if (serverStateModel.getRecordingState() == ServerStateModel.RecordingState.STARTING_RECORDING) {
              showSensorStartingScreen();
              initStartRecordingTimer();
            }
          } else if (serverStateModel.getRecordingState() == ServerStateModel.RecordingState.STOPPING) {
            showStoppingTheRecordingDialog();
          }
        }
      }
    });
  }


  @Override
  public void onDiskInfoUpdated() {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't handle the on disk info " +
              "updated event because the fragment was not associated with an " +
              "activity.");
      return;
    }
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (getView() != null) {
          updateScanButtonState();
        }
      }
    });
  }

  private void updateScanButtonState() {
    View view = getView();
    if (view == null) {
      Log.e(getClass().getSimpleName(), "Unable to reenable the recording " +
              "button, no view exists.");
      return;
    }
    toggleRecordingButton.setEnabled(
            serverStateModel.getTimeRemaining() > 0 || serverStateModel.isRecording());
  }


  private void setCurrentRecordingName(String name) {
    Spannable span = new SpannableString(name);
    span.setSpan(
            new ForegroundColorSpan(ContextCompat.getColor(getContext(),
                    R.color.colorPositive)),
            0, span.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    currentRecordingText.setText(span);
    currentRecordingText.setVisibility(View.VISIBLE);
  }

  /**
   * This method should be called whenever something happens that indicates that a scan is in
   * progress. It will set the scan button state without triggering any event listeners.
   */
  public void ensureRecordingButtonIsChecked() {
    if (!toggleRecordingButton.isChecked()) {
      toggleRecordingButton.setOnCheckedChangeListener(null);
      toggleRecordingButton.setChecked(true);
      toggleRecordingButton.setOnCheckedChangeListener(this);
    }
  }

  /**
   * This method should be called whenever something happens that indicates that a scan is not in
   * progress. It will set the scan button state without triggering any event listeners.
   */
  public void ensureRecordingButtonIsNotChecked() {
    if (toggleRecordingButton.isChecked()) {
      toggleRecordingButton.setOnCheckedChangeListener(null);
      toggleRecordingButton.setChecked(false);
      toggleRecordingButton.setOnCheckedChangeListener(this);
    }
  }

  @Override
  public void onRecordingStartExpectedDuration(final float expectedSeconds) {
  }

  @Override
  public void onImuTestStarted() {
    // TODO: ensure we cant timeout if the recording never starts after the
    // imu test
    if (startRecordingTimer != null) {
      startRecordingTimer.cancel();
    }
    startRecordingTimer = null;
    final Activity a = getActivity();
    if (a != null) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          ensureRecordingButtonIsChecked();
          if (startRecordingProgress != null) {
            startRecordingProgress.setMax(10000);
            startRecordingProgress.setProgress(0);
          }
          if (startRecordingText != null) {
            startRecordingText.setText(R.string.startRecordingDontMove);
          }
        }
      });
    }
  }

  @Override
  public void onImuTestProgressUpdate(final float progress) {
    final Activity a = getActivity();
    if (a != null) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          ensureRecordingButtonIsChecked();
          if (!isSensorStartingScreenVisible()) {
            showSensorStartingScreen();
          }
          if (startRecordingProgress != null) {
            startRecordingProgress.setMax(10000);
            startRecordingProgress.setProgress((int) (progress * 10000));
          }
          if (startRecordingText != null) {
            startRecordingText.setText(R.string.startRecordingDontMove);
          }
        }
      });
    }
  }

  @Override
  public void onImuTestFinished(final boolean success) {
    final Activity a = getActivity();
    if (a != null) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          ensureRecordingButtonIsChecked();
          if (success) {
            if (startRecordingText != null) {
              startRecordingText.setText(null);
            }
            waitForRecordingDialog.setMessage(a.getResources().getString(R.string.startRecordingWait));
            if (startRecordingProgress != null) {
              startRecordingProgress.setProgress(0);
            }
          } else {
            if (waitForRecordingDialog != null) {
              waitForRecordingDialog.hide();
              waitForRecordingDialog = null;
              startRecordingProgress = null;
              startRecordingText = null;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(a);
            b.setMessage(R.string.imuTestFailed);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                toggleRecordingButton.setChecked(false);
              }
            });
            b.setNeutralButton(R.string.tryAgain,
                    new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                serverStateModel.onUserCanceledAfterImuTestWarning();
                serverStateModel.restartRecording(false);
              }
            });
            b.setNegativeButton(R.string.ignore,
                    new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                serverStateModel.onUserIgnoredFirstImuTestWarning();
                AlertDialog.Builder b2 = new AlertDialog.Builder(a);
                b2.setMessage(R.string.imuTestFailed2);
                b2.setNegativeButton(R.string.cancel, null);
                b2.setPositiveButton(R.string.ignore,
                        new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                    serverStateModel.restartRecording(true);
                  }
                });
                b2.setCancelable(false);
                b2.create().show();
              }
            });
            b.setCancelable(false);
            b.create().show();
          }
        }
      });
    }
  }

  class UpdateRecordingTimerTask extends TimerTask {

    @Override
    public void run() {
      // compute the time this has been running based upon the starting date
      // of the recording
      // as provided by the serverStateModel and the current date. Simply
      // incrementing the
      // recording time every second will result in a wrong time if the app
      // was paused or closed
      // during a recording
      final long timeRun =
              ((new Date()).getTime() - serverStateModel.getRecordingStartTime())
                      / 1000;
      // This check is slightly redundant but reduces the amount of error
      // messages thrown

      // If we are stopping we expect the recording to stop and should stop updating the ui.
      // If the stopping fails we still have the correct time locally though.
      // We also should avoid updating the label after the recording was finalized, but before
      // this timer terminates. To avoid that we also don't update the timer when the sensor is idle
      if (serverStateModel.getRecordingState() != RecordingState.STOPPING
          && serverStateModel.getRecordingState() != RecordingState.IDLE) {
        if (getActivity() != null) {
          try {
            getActivity()
                .runOnUiThread(
                    new Runnable() {
                      @Override
                      public void run() {
                        if (getView() != null) {
                          TextView v = getView().findViewById(R.id.recordingFor);
                          v.setVisibility(View.VISIBLE);
                          v.setText(
                              String.format("%02d", timeRun / 60)
                                  + ":"
                                  + String.format("%02d", timeRun % 60));
                        }
                      }
                    });
          } catch (NullPointerException e) {
            Log.e(getClass().getSimpleName(), "", e);
          }
        }
      }
    }
  }
}
