package com.dotscene.dronecontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

public class TabNetworkingFragment extends Fragment implements
        ServerStateModel.OnNetUsbDhcpResponseListener,
        CompoundButton.OnCheckedChangeListener,
        ServerStateModel.OnNetProxyResponseListener,
        ServerStateModel.OnNetPingResponseListener {


  final static String SOCKS5_PREFIX = "socks5://";

  ServerStateModel serverStateModel = null;

  Timer toggleDhcpTimer = null;
  Timer statusPollTimer = null;
  LinearLayout toggleDhcpGroup;
  TextView noSuitableInterface;
  Switch toggleDhcp = null;
  boolean lastPollWasError = false;

  EditText proxyUrlEdit;
  Button proxyUrlSetButton;
  TextView proxyUrlGetErrorView;

  EditText pingUrlEdit;
  Button pingButton;

  AlertDialog pingDialog = null;
  Timer pingTimer = null;
  Timer pingProgressTimer = null;
  ProgressBar pingProgress = null;
  ReentrantLock pingLock = new ReentrantLock();


  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.fragment_tab_networking, container, false);
    toggleDhcpGroup = v.findViewById(R.id.networkToggleDhcpGroup);
    noSuitableInterface = v.findViewById(R.id.networkingNoSuitableInterface);

    toggleDhcp = v.findViewById(R.id.networkToggleDhcp);
    toggleDhcp.setOnCheckedChangeListener(this);

    proxyUrlEdit = v.findViewById(R.id.networkProxyUrl);
    proxyUrlSetButton = v.findViewById(R.id.networkSetProxyUrl);
    proxyUrlSetButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (serverStateModel != null) {
          String url = proxyUrlEdit.getText().toString();
          if (!url.startsWith(SOCKS5_PREFIX)) {
            url = SOCKS5_PREFIX + url;
          }
          serverStateModel.setNetProxyUrl(url);
        } else {
          Activity a = getActivity();
          if (a != null) {
            Toast.makeText(a, R.string.noServerStateModelError, Toast.LENGTH_LONG).show();
          }
          Log.e(getClass().getSimpleName(), "The serverStateModel is null.");
        }
      }
    });
    proxyUrlGetErrorView = v.findViewById(R.id.networkingGetProxyError);

    pingUrlEdit = v.findViewById(R.id.networkPingUrl);
    pingButton = v.findViewById(R.id.networkDoPing);
    pingButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (serverStateModel != null) {
          serverStateModel.pingFromSensor(pingUrlEdit.getText().toString());

          final Activity a = getActivity();
          if (a != null) {
            a.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                AlertDialog.Builder b = new AlertDialog.Builder(a);
                pingProgress = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
                pingProgress.setIndeterminate(false);
                pingProgress.setMax(60);
                pingProgress.setProgress(0);
                b.setView(pingProgress);
                b.setMessage(R.string.networkingPinging);
                pingDialog = b.create();
                pingDialog.show();
                if (pingTimer != null) {
                  pingTimer.cancel();
                }
                pingTimer = new Timer();
                pingTimer.schedule(new TimerTask() {
                  @Override
                  public void run() {
                    a.runOnUiThread(new Runnable() {
                      @Override
                      public void run() {
                        try {
                          pingLock.lock();
                          if (pingDialog != null) {
                            pingDialog.hide();
                            pingDialog = null;
                            Toast.makeText(a, R.string.networkingPingResponseTimeout, Toast.LENGTH_LONG).show();
                          }
                        } finally {
                          pingLock.unlock();
                        }
                      }
                    });
                  }
                }, 60000);
                pingProgressTimer = new Timer();
                pingProgressTimer.schedule(new TimerTask() {
                  @Override
                  public void run() {
                    try {
                      pingLock.lock();
                      if (pingProgress != null) {
                        pingProgress.incrementProgressBy(1);
                      } else {
                        pingProgressTimer.cancel();
                        pingProgressTimer = null;
                      }
                    } finally {
                      pingLock.unlock();
                    }
                  }
                }, 1000);
              }
            });
          }
        } else {
          Activity a = getActivity();
          if (a != null) {
            Toast.makeText(a, R.string.noServerStateModelError, Toast.LENGTH_LONG).show();
          }
          Log.e(getClass().getSimpleName(), "The serverStateModel is null.");
        }
      }
    });

    return v;
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(getClass().getSimpleName(), "On resume");
    toggleDhcp.setEnabled(true);
    Activity a = getActivity();
    if (a != null) {
      serverStateModel = ((ServerStateModel.ServerStateProvider) a).getServerStateModel();
      serverStateModel.addOnNetUsbDhcpResponseListener(this);
      serverStateModel.addOnNetProxyResponseListener(this);
      serverStateModel.addOnNetPingResponseListener(this);
      if (statusPollTimer != null) {
        statusPollTimer.cancel();
        statusPollTimer = null;
      }
      statusPollTimer = new Timer();
      statusPollTimer.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
          Log.d(getClass().getSimpleName(), "Query the server dhcp status");
          serverStateModel.queryNetUsbDhcpServerState();
        }
      }, 0, 1000);
      serverStateModel.queryNetUsbDhcpServerState();
      serverStateModel.loadNetProxyUrl();
      Log.d(getClass().getSimpleName(), "started the timer");
    } else {
      Log.e(getClass().getSimpleName(), "On resume: Activity is null");
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    serverStateModel.removeOnNetUsbDhcpResponseListener(this);
    serverStateModel.removeOnNetProxyResponseListener(this);
    serverStateModel.removeOnNetPingResponseListener(this);
    if (statusPollTimer != null) {
      statusPollTimer.cancel();
      statusPollTimer = null;
    }

    try {
      pingLock.lock();
      if (pingTimer != null) {
        pingTimer.cancel();
        pingTimer = null;
      }
      if (pingDialog != null) {
        pingDialog.hide();
        pingDialog = null;
      }
    } finally {
      pingLock.unlock();
    }
  }


  @Override
  public void onNetUsbDhcpServerStatus(final ServerStatus status) {
    Log.d(getClass().getSimpleName(), "Server dhcp status: " + status.name());
    try {
      getActivity().runOnUiThread(new Runnable() {

        @Override
        public void run() {
          switch (status) {
            case RUNNING:
              toggleDhcp.setOnCheckedChangeListener(null);
              if (toggleDhcpTimer == null) {
                toggleDhcp.setChecked(true);
              }
              noSuitableInterface.setVisibility(View.GONE);
              toggleDhcpGroup.setVisibility(View.VISIBLE);
              toggleDhcp.setOnCheckedChangeListener(TabNetworkingFragment.this);
              lastPollWasError = false;
              break;
            case NOT_RUNNING:
              toggleDhcp.setOnCheckedChangeListener(null);
              noSuitableInterface.setVisibility(View.GONE);
              toggleDhcpGroup.setVisibility(View.VISIBLE);
              if (toggleDhcpTimer == null) {
                toggleDhcp.setChecked(false);
              }
              toggleDhcp.setOnCheckedChangeListener(TabNetworkingFragment.this);
              lastPollWasError = false;
              break;
            case SYSTEMD_ERROR:
              if (!lastPollWasError) {
                Toast.makeText(getContext(), R.string.networkingDhcpPollSystemdError, Toast.LENGTH_LONG).show();
              }
              lastPollWasError = true;
              break;
            case NO_NET_USB_INTERFACES:
              lastPollWasError = false;
              noSuitableInterface.setVisibility(View.VISIBLE);
              toggleDhcpGroup.setVisibility(View.GONE);
              break;
          }
        }
      });
    } catch (NullPointerException e) {
      Log.e(getClass().getSimpleName(), "Error when creating a toast: nullptr", e);
    }
  }

  @Override
  public void onNetUsbDhcpServerStarted(final boolean startSuccessful) {
    if (!startSuccessful) {
      Log.i(getClass().getSimpleName(), "Error when starting the dhcp server");
    } else {
      Log.i(getClass().getSimpleName(), "Dhcp client started");
    }
    try {
      getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          if (startSuccessful) {
            Toast.makeText(getContext(), R.string.networkingDhcpServerStarted, Toast.LENGTH_LONG).show();
          } else {
            Toast.makeText(getContext(), R.string.networkingDhcpStatusChangeFailed, Toast.LENGTH_LONG).show();
          }
        }
      });
    } catch (NullPointerException e) {
      Log.e(getClass().getSimpleName(), "Error when creating a toast: nullptr", e);
    }
  }

  @Override
  public void onNetUsbDhcpClientStarted(final boolean startSuccessful) {
    if (!startSuccessful) {
      Log.i(getClass().getSimpleName(), "Error when starting the dhcp client");
    } else {
      Log.i(getClass().getSimpleName(), "Dhcp client started");
    }
    try {
      getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          if (startSuccessful) {
            Toast.makeText(getContext(), R.string.networkingDhcpClientStarted, Toast.LENGTH_LONG).show();
          } else {
            Toast.makeText(getContext(), R.string.networkingDhcpStatusChangeFailed, Toast.LENGTH_LONG).show();
          }
        }
      });
    } catch (NullPointerException e) {
      Log.e(getClass().getSimpleName(), "Error when creating a toast: nullptr", e);
    }
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    toggleDhcp.setEnabled(false);
    if (toggleDhcpTimer != null) {
      toggleDhcpTimer.cancel();
    }
    toggleDhcpTimer = new Timer();
    toggleDhcpTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            toggleDhcp.setEnabled(true);
            if (toggleDhcpTimer != null) {
              toggleDhcpTimer.cancel();
              toggleDhcpTimer = null;
            }
          }
        });
      }
    }, 5000);

    try {
      if (isChecked) {
        serverStateModel.netUsbDhcpStartServer();
      } else {
        serverStateModel.netUsbDhcpStopServer();
      }
    } catch (Exception e) {
      Toast.makeText(getContext(), R.string.networkingDhcpStatusChangeFailedClient, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onNetProxyUrlReceived(final boolean success, final String proxyUrl) {
    final Activity a = getActivity();
    if (a != null) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          if (success) {
            String url = proxyUrl;
            if (url.startsWith(SOCKS5_PREFIX)) {
              url = url.substring(SOCKS5_PREFIX.length());
            }
            proxyUrlEdit.setText(url);
            proxyUrlGetErrorView.setVisibility(View.GONE);
          } else {
            proxyUrlEdit.setText("");
            proxyUrlGetErrorView.setVisibility(View.VISIBLE);
          }
        }
      });
    } else {
      Log.e(getClass().getSimpleName(), "No activity available when the proxy url was set.");
    }
  }

  @Override
  public void onNetProxyUrlSet(final boolean success) {
    final Activity a = getActivity();
    if (a != null) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          if (success) {
            if (a != null) {
              AlertDialog.Builder b = new AlertDialog.Builder(a);
              b.setMessage(R.string.networkingSetProxySuccess);
              b.setPositiveButton(R.string.ok, null);
              b.setNegativeButton(R.string.rebootNow, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  serverStateModel.rebootServer();
                }
              });
              b.create().show();
            }
            Log.i(getClass().getSimpleName(), "Successfully set the server proxy url.");
          } else {
            if (a != null) {
              AlertDialog.Builder b = new AlertDialog.Builder(a);
              b.setMessage(R.string.networkingSetProxyError);
              b.setPositiveButton(R.string.ok, null);
              b.create().show();
            }
            Log.e(getClass().getSimpleName(), "Unable to set the proxy url.");
          }
        }
      });
    } else {
      Log.e(getClass().getSimpleName(), "No activity available when the proxy url was set.");
    }
  }

  @Override
  public void onPingResponseReceived(final boolean pingSuccessful) {
    final Activity a = getActivity();
    if (a != null) {
      a.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          try {
            pingLock.lock();
            if (pingTimer != null) {
              pingTimer.cancel();
              pingTimer = null;
            }
            if (pingDialog != null) {
              pingDialog.hide();
              pingDialog = null;
            }
          } finally {
            pingLock.unlock();
          }
          AlertDialog.Builder b = new AlertDialog.Builder(a);
          if (pingSuccessful) {
            b.setMessage(R.string.networkingPingSuccess);
          } else {
            b.setMessage(R.string.networkingPingFailure);
          }
          b.setPositiveButton(R.string.ok, null);
          b.create().show();
        }
      });
    } else {
      Log.e(getClass().getSimpleName(), "No activity available when a ping finished.");
    }
  }
}
