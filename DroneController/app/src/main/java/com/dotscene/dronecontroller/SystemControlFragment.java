package com.dotscene.dronecontroller;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.dotscene.dronecontroller.ServerStateModel.OnShutdownListener;
import com.dotscene.dronecontroller.ServerStateModel.OnShutdownResponseListener;
import com.dotscene.dronecontroller.ServerStateModel.ServerStateProvider;
import java.util.ArrayList;

/** Created by Florian Kramer on 1/28/17. */
public class SystemControlFragment extends Fragment
    implements View.OnClickListener, OnShutdownListener, OnShutdownResponseListener {

  ServerStateModel serverStateModel;

  AlertDialog confirmShutdownDialog;

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.fragment_system_control, container, false);
    // setup the listeners for the buttons
    Button connectButton = v.findViewById(R.id.sshDisconnectButton);
    connectButton.setOnClickListener(this);

    Button bShutdown = v.findViewById(R.id.shutdownButton);
    bShutdown.setOnClickListener(this);
    return v;
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
  }

  @Override
  public void onAttach(Context c) {
    super.onAttach(c);
  }

  @Override
  public void onResume() {
    super.onResume();

    serverStateModel = ((ServerStateProvider) getActivity()).getServerStateModel();
    serverStateModel.addOnShutdownResponseListener(this);
    serverStateModel.addOnShutdownListener(this);
  }

  @Override
  public void onPause() {
    super.onPause();
    serverStateModel.removeOnShutdownResponseListener(this);
    serverStateModel.removeOnShutdownListener(this);
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.shutdownButton:
        final SystemControlFragment fragment = this;

        if (serverStateModel.isRecording()) {
          AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
          builder.setMessage(R.string.confirm_shutdown_during_recording);
          builder.setPositiveButton(
              R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  serverStateModel.shutdownServer(false);
                  confirmShutdownDialog.dismiss();
                }
              });
          builder.setNeutralButton(
              R.string.force_shutdown,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  serverStateModel.shutdownServer(true, false);
                  confirmShutdownDialog.dismiss();
                }
              });
          builder.setNegativeButton(
              R.string.cancel,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {}
              });
          confirmShutdownDialog = builder.create();
          confirmShutdownDialog.show();
        } else {
          int okString = R.string.ok;
          int textString = R.string.confirm_shutdown;
          int cancelString = R.string.cancel;
          AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
          builder.setMessage(textString);
          builder.setPositiveButton(
              okString,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  serverStateModel.shutdownServer(false);
                  confirmShutdownDialog.dismiss();
                }
              });
          builder.setNegativeButton(
              cancelString,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {}
              });
          confirmShutdownDialog = builder.create();
          confirmShutdownDialog.show();
        }

        break;
      case R.id.sshDisconnectButton:
        serverStateModel.disconnect();
        Intent i = new Intent(getContext(), ConnectActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        startActivity(i);
        break;
    }
  }

  @Override
  public void onShutdownRequested() {
  }

  @Override
  public void onShutdownResponse(int status) {
    final Activity a = getActivity();
    if (a != null) {
      a.runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              AlertDialog.Builder builder = new AlertDialog.Builder(a);
              builder.setView(getLayoutInflater().inflate(R.layout.shutdown_dialog, null));
              builder.create().show();
            }
          });
    }
  }

  @Override
  public void onShutdownBrokenBagfiles(final String[] bagfiles) {
    final Activity a = getActivity();
    if (a != null) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          AlertDialog.Builder builder = new Builder(a);

          String message = getActivity().getResources().getString(R.string.shutdownBrokenBagfiles);
          for (String filename : bagfiles) {
            message += "\n - " + filename;
          }

          builder.setMessage(message);

          builder.setNegativeButton(R.string.cancel, null);
          builder.setPositiveButton(R.string.force_shutdown, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              serverStateModel.shutdownServer(true);
            }
          });
          builder.create().show();
        }
      });
    } else {
      Log.e(getClass().getSimpleName(), "No activity available for displaying the broken bagfiles dialog");
    }
  }
}
