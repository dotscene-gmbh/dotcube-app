package com.dotscene.dronecontroller;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Florian Kramer on 4/8/17.
 */

public class ServerStateModelMock extends ServerStateModel {

  Timer statusUpdateTimer = null;
  Random statusUpdateRandom = new Random();

  int lastErrorId = 0;

  String proxyUrl = "socks5://superproxy:1080";

  public ServerStateModelMock() {
    recordingState = RecordingState.IDLE;

    remoteSystemInfo.name = "dotcube-mock";
    remoteSystemInfo.usbIpInfo = "eno0: 192.168.178.46/24";
    remoteSystemInfo.usbMacInfo = "27:33:a4:b1:22:ff";
    remoteSystemInfo.versionMajor = 5;
    remoteSystemInfo.versionMinor = 99;
    remoteSystemInfo.versionPatch = 2;
    remoteSystemInfo.hardwareVersion = "v42.0 mock";
  }

  @Override
  public void connect(Context ctx, String ipAddr) {
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        isConnected = true;

        synchronized (onDiskInfoUpdatedListeners) {
          for (OnDiskInfoUpdatedListener l : onDiskInfoUpdatedListeners) {
            l.onDiskInfoUpdated();
          }
        }

        synchronized (onConnectionStateChangedListeners) {
          for (OnConnectionStateChangedListener l : onConnectionStateChangedListeners) {
            l.onConnectionEstablished();
          }
        }
        loadStatus();
      }
    }, 1000);

    // Ensure the status update callbacks are called regularly
    if (statusUpdateTimer != null) {
      statusUpdateTimer.cancel();
    }
    flowStates = new ArrayList<>();
    for (int i = 0; i < SystemStateFragment.WARNING_TEXTS.length; ++i) {
      flowStates.add(FlowState.GOOD);
    }
    statusUpdateTimer = new Timer();
    statusUpdateTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        if (flowStates.get(lastErrorId) == FlowState.HAD_FAILED) {
          flowStates.set(lastErrorId, FlowState.FAILED);
        } else {
          flowStates.set(lastErrorId, FlowState.GOOD);
          lastErrorId ++;
          lastErrorId %= flowStates.size();
        }
/*
        // scramble the status
        if (statusUpdateRandom.nextFloat() > 0.2) {
          HashSet<Integer> errors = new HashSet<>();
          int numErrors = 1 + statusUpdateRandom.nextInt(3);
          for (int i = 0; i < numErrors; i++) {
            errors.add(statusUpdateRandom.nextInt(9));
          }

          flowTopVelodyne = true;
          flowRearVelodyne = true;
          flowIMU = true;
          flowGPS = true;
          gpsHasFix = true;
          hasRosError = false;
          rearVelodyneCapOn = false;
          topVelodyneCapOn = false;

          if (errors.contains(0)) {
            flowTopVelodyne = false;
          }
          if (errors.contains(1)) {
            flowRearVelodyne = false;
          }
          if (errors.contains(2)) {
            flowIMU = false;
          }
          if (errors.contains(3)) {
            flowGPS = false;
          }
          if (errors.contains(4)) {
            gpsHasFix = false;
          }
          if (errors.contains(5)) {
            hasRosError = true;
          }
          if (errors.contains(7)) {
            rearVelodyneCapOn = true;
          }
          if (errors.contains(8)) {
            topVelodyneCapOn = true;
          }
        } else {
          flowTopVelodyne = true;
          flowRearVelodyne = true;
          flowIMU = true;
          flowGPS = true;
          gpsHasFix = true;
          hasRosError = false;
          rearVelodyneCapOn = false;
          topVelodyneCapOn = false;
        }*/

        onStatusUpdateListenersLock.lock();
        try {
          for (OnStatusUpdateListener listener : onStatusUpdateListeners) {
            listener.onStatusUpdate(ServerStateModelMock.this);
          }
        } finally {
          onStatusUpdateListenersLock.unlock();
        }
      }
    }, 1000, 2000);
  }

  @Override
  public void disconnect() {
    isConnected = false;
    // inform listeners that the connection was closed
    onConnectionStateChangedListenerLock.lock();
    try {
      for (OnConnectionStateChangedListener l : onConnectionStateChangedListeners) {
        l.onConnectionClosed();
      }
    } finally {
      onConnectionStateChangedListenerLock.unlock();
    }
  }

  @Override
  public void loadStatus() {
    onStatusLoaded();
  }

  @Override
  public void loadRosStatus() {
    // No need to do anything, a new ros status is generated every second
  }

  @Override
  protected void onStatusLoaded() {
    synchronized (onStatusLoadedListeners) {
      for (OnStatusLoadedListener l : onStatusLoadedListeners) {
        l.onStatusLoaded();
      }
    }
  }

  @Override
  public boolean startRecording(
      String projectname,
      String recordingName,
      String recordingType,
      Context context,
      boolean skipImuTest) {
    String filepath = creteRecordingPath(projectname, recordingName);
    if (filepath.length() > 0) {
      currentRecordingFile = filepath;
      currentRecordingName = recordingName;
      recordingState = RecordingState.RECORDING;
      timeRecordingStarted = new Date();
      synchronized (onRecordingStateChangedListeners) {
        for (OnRecordingStateChangedListener l : onRecordingStateChangedListeners) {
          l.onRecordingStarted(filepath, currentRecordingName);
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean startRecording(String projectname, String recordingName, String type, Context context) {
    String filepath = creteRecordingPath(projectname, recordingName);
    if (filepath.length() > 0) {
      currentRecordingFile = filepath;
      currentRecordingName = recordingName;
      recordingState = RecordingState.RECORDING;
      timeRecordingStarted = new Date();
      synchronized (onRecordingStateChangedListeners) {
        for (OnRecordingStateChangedListener l : onRecordingStateChangedListeners) {
          l.onRecordingStarted(filepath, currentRecordingName);
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public void stopRecording() {
    recordingState = RecordingState.STOPPING;
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        recordingState = RecordingState.IDLE;
        onRecordingStateChangedListenersLock.lock();
        lastRecordingFile = currentRecordingFile;
        lastRecordingName = currentRecordingName;
        currentRecordingFile = "";
        currentRecordingName = "";
        try {
          for (OnRecordingStateChangedListener l : onRecordingStateChangedListeners) {
            l.onRecordingStopped();
          }
        } finally {
          onRecordingStateChangedListenersLock.unlock();
        }
      }
    }, 2000);
  }

  @Override
  public void shutdownServer(boolean ignoreBroken) {
    shutdownInProgress = true;
    Timer t = new Timer();
    t.schedule(new TimerTask() {
      @Override
      public void run() {
        disconnect();
      }
    }, 7000);
  }


  @Override
  public void rebootServer() {

  }

  /**
   * @brief Requests a list of all network interfaces from the server
   */
  @Override
  public void loadNetworkInterfaces() {
    String interfaces[] = {"enp4s0", "wlp5s1", "enx10AF901238"};
    String infos[] = {"none", "none", "none"};
    onNetworkInterfacesLoadedListenersLock.lock();
    try {
      for (OnNetworkInterfacesLoadedListener l : onNetworkInterfacesLoadedListeners) {
        l.onNetworkInterfacesLoaded(interfaces, infos);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      onNetworkInterfacesLoadedListenersLock.unlock();
    }
  }

  /**
   * Loads the content of path, displaying all bag files and directories. Will call the
   * listener method of the listener once the data has been returned.
   */
  @Override
  public void loadFilesInDir(String path, OnFilesLoadedListener listener) {
    // only do this if whe are already connected to the server
    if (isConnected) {
      String files[] = {
          "dir1",
          "dir2",
          "dir3",
          "dir4",
          "2019_03_28_15-47-32_test1.bag",
          "2019_03_28_15-47-32_test2.bag",
          "2019_03_28_15-47-32_test3.bag",
          "2019_03_28_15-47-32_test4_0.bag",
          "2019_03_28_15-47-32_test4_1.bag.active",
          "2019_03_28_15-47-32_test5_0.BROKEN.bag"
      };
      long sizes[] = {
          0, 0, 0, 0, 123123, 123213213, 1213213, 123114451, 41231123, 123141123
      };
      boolean is_dir[] = {
          true, true, true, true, false, false, false, false, false, false, false
      };
      listener.onFilesLoaded(files, files, sizes, is_dir);
    }
  }

  /**
   * @param filelist a space separated list of filenames. If the filenames contain spaces or special
   *                 characters surround them with single quotes
   */
  @Override
  public void deleteFiles(String filelist[]) {
    synchronized (onServerFilesystemChangedListeners) {
      for (OnServerFilesystemChangedListener l : onServerFilesystemChangedListeners) {
        l.onServerFilesystemChanged();
      }
    }
  }

  @Override
  public void queryForSyncStatus() {
    Timer t = new Timer();
    t.schedule(new TimerTask() {
      @Override
      public void run() {
        short status_query_response = 0;
        boolean isUploading = true;
        boolean isConnected = true;
        long uploadEtaMs = 12312;
        long uploadSpeedKbps = 1233;
        short uploadPercentage = 42;
        try {
          onSyncStatusReceivedListenersLock.lock();
          for (OnSyncStatusReceivedListener l : onSyncStatusReceivedListeners) {
            l.onSyncStatusReceived(status_query_response == 0, isUploading, isConnected, uploadEtaMs, uploadSpeedKbps, uploadPercentage);
          }
        } finally {
          onSyncStatusReceivedListenersLock.unlock();
        }
        if (status_query_response == 0) {
          lastSyncQueryPrintedError = false;
        } else {
          // avoid printing the error more than once
          if (errorDisplay != null && !lastSyncQueryPrintedError) {
            lastSyncQueryPrintedError = true;
            errorDisplay.displayServerStateError(R.string.querySyncError);
          }
        }
      }
    }, 500);
  }


  /**
   * @return the relative path to the file a recording that was start during THIS SESSION OF THE
   * APP, and is still running. The path is not guaranteed to be valid after a disconnect.
   */
  @Override
  public String getCurrentRecordingRelativePath() {
    return "test_recording_dir/bag1.bag";
  }


  @Override
  public String getRecordingFilename() {
    return recordingState == RecordingState.RECORDING ? "Recording File 1" : "";
  }

  @Override
  public long getTimeRemaining() {
    return 1000;
  }

  @Override
  public long getDiskCapacityMb() {
    return 4242;
  }

  @Override
  public long getDiskFreeMb() {
    return 42;
  }

  @Override
  public long getTimeRecording() {
    return 1000;
  }

  @Override
  public String getLastRecordingFile() {
    return "bag23.bag";
  }

  @Override
  public void queryForRosErrors() {
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        String[] errors = {
          "[ERROR] Error 1",
          "[ERROR] Error 2 (also very severe)"
        };
        onRosErrorsLoadedListenersLock.lock();
        try {
          for (OnRosErrorsLoadedListener l : onRosErrorsLoadedListeners) {
            l.onRosErrorsLoaded(errors);
          }
        } finally {
          onRosErrorsLoadedListenersLock.unlock();
        }
      }
    }, 500);
  }

  @Override
  public void queryNetUsbDhcpServerState() {
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        onNetUsbDhcpResponsesListenersLock.lock();
        try {
          for (OnNetUsbDhcpResponseListener l : onNetUsbDhcpResponsesListeners) {
            l.onNetUsbDhcpServerStatus(OnNetUsbDhcpResponseListener.ServerStatus.NO_NET_USB_INTERFACES);
          }
        } finally {
          onNetUsbDhcpResponsesListenersLock.unlock();
        }
      }
    }, 200);
  }

  @Override
  public void loadNetProxyUrl() {
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        Log.d(getClass().getSimpleName(), "Loaded the proxy url" + proxyUrl);
          onNetProxyResponseListenersLock.lock();
          try {
            boolean success = statusUpdateRandom.nextBoolean();
            for (OnNetProxyResponseListener l : onNetProxyResponseListeners) {
              l.onNetProxyUrlReceived(success, proxyUrl);
            }
          } catch (Exception e) {
            e.printStackTrace();
          } finally {
            onNetProxyResponseListenersLock.unlock();
          }

      }
    }, 500);
  }

  @Override
  public void setNetProxyUrl(final String newUrl) {
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        Log.d(getClass().getSimpleName(), "Setting the proxy url to " + newUrl);
        proxyUrl = newUrl;
        onNetProxyResponseListenersLock.lock();
        try {
          for (OnNetProxyResponseListener l : onNetProxyResponseListeners) {
            l.onNetProxyUrlSet(true);
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          onNetProxyResponseListenersLock.unlock();
        }
      }
    }, 500);
  }

  public void pingFromSensor(String url) {
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        onNetPingResponseListenersLock.lock();
        try {
          for (OnNetPingResponseListener l : onNetPingResponseListeners) {
            l.onPingResponseReceived(true);
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          onNetPingResponseListenersLock.unlock();
        }
      }
    }, 1500);
  }

  @Override
  public void netUsbDhcpStartServer() {
  }

  @Override
  public void netUsbDhcpStopServer() {
  }

  @Override
  public void OnLocationAcquired(Location location) {
    Log.d(getClass().getSimpleName(), "Acquired a location");
    lastLocationLock.lock();
    try {
      this.lastLocation = location;
    } finally {
      lastLocationLock.unlock();
    }
  }

}