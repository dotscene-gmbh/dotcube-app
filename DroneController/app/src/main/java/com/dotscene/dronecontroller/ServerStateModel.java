package com.dotscene.dronecontroller;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;

import com.dotscene.dronecontroller.TCPClient.PacketEncoder;
import com.dotscene.dronecontroller.TCPClient.TCPListener;

import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.net.NetworkInterface;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public class ServerStateModel implements TCPListener, LocationProvider.OnLocationAcquiredListener {

  public static final ServerStateModel SINGLETON = new ServerStateModel();

  protected long kbpsWriteRate = 1873; // Kibibyte per second
  // write rate while recording

  public enum RecordingState {
    IDLE,
    RECORDING,
    STOPPING,
    IMU_TESTING,
    STARTING_RECORDING
  }

  public enum FlowState {
    GOOD,
    HAD_FAILED,  // The state has failed since the last clear but is currently ok
    FAILED
  }

  public static final String RECORDING_TYPE_INDOOR = "indoor";
  public static final String RECORDING_TYPE_OUTDOOR = "outdoor/combined";
  public static final String RECORDING_TYPE_FLIGHT = "flight";

  public static final Pattern PATTERN_FILENAME = Pattern.compile("[^a-zA-Z0" + "-9_\\-]");

  // state variables
  boolean isConnected = false;
  protected RecordingState recordingState = RecordingState.IDLE;

  // time used to regularly check how much recording time is left
  protected Timer diskStatusPullTimer;
  // used to store when recording was started and how much time is left
  protected Date timeRecordingStarted;

  protected Timer serverLockTimer;

  // internal disk info
  protected long timeRemaining = 1;
  protected long diskCapacityMb;
  protected long diskFreeMb;

  protected boolean gpsHasFix = false;
  protected boolean hasRosError = false;

  // Ensure the warning states start out as evrything being ok
  protected ArrayList<FlowState> flowStates = new ArrayList<>();

  // stores the last file that something was recorded to
  protected String lastRecordingFile = "";
  protected String lastRecordingName = "";
  protected String currentRecordingFile = "";
  protected String currentRecordingRelativePath = "";
  protected String currentRecordingName = "";

  protected HashMap<Integer, OnFilesLoadedListener> onFilesLoadedListeners = new HashMap<>();
  protected ReentrantLock onFilesLoadedListenerLock = new ReentrantLock();

  // used to give a unique id to all file loading requests
  protected int onFilesLoadedListenerIdCounter = 0;

  // Used by the client to store the name of the current project
  protected String projectName = "unknown";
  protected long projectId = -1;
  protected long projectCustomerId = -1;
  protected long defaultProjectCustomerId = -1;
  protected String defaultRecordingType = "indoor";

  // The network protocol version we accept. Protocol versions start with 1,
  // a value of 0
  // will always be rejected (no matter the version of the app).
  protected static final int NETWORK_PROTOCOL_VERSION = 18;

  protected ReentrantLock lastLocationLock = new ReentrantLock();
  protected Location lastLocation = null;

  protected boolean lastSyncQueryPrintedError = false;

  protected boolean shutdownInProgress = false;

  protected boolean isFirstRecording = false;

  protected boolean shutdownAfterRecordingStopped = false;

  protected RemoteSystemInfo remoteSystemInfo = new RemoteSystemInfo();

  // TCP message types.
  protected enum MessageType {
    PROTOCOL_VERSION,
    SHUTDOWN_REQUEST,
    SHUTDOWN_RESPONSE,
    SHUTDOWN_RESPONSE_BROKEN_BAGFILES,
    START_RECORDING_REQUEST,
    START_RECORDING_RESPONSE,
    STOP_RECORDING_REQUEST,
    STOP_RECORDING_RESPONSE,
    DISK_STATUS_REQUEST,
    DISK_STATUS_RESPONSE,
    LAST_RECORDING_FILE_REQUEST,
    LAST_RECORDING_FILE_RESPONSE,
    SYSTEM_STATE_REQUEST,
    SYSTEM_STATE_RESPONSE,
    LOAD_FILE_LIST_REQUEST,
    LOAD_FILE_LIST_RESPONSE,
    DELETE_FILES_REQUEST,
    DELETE_FILES_RESPONSE,
    TEST_SENSOR_LOCK_REQUEST,
    TEST_SENSOR_LOCK_RESPONSE,
    STATUS_UPDATE_REQUEST,
    STATUS_UPDATE_RESPONSE,
    ROS_MESSAGES_REQUEST,
    ROS_MESSAGES_RESPONSE,
    NET_INTERFACE_LIST_REQUEST,
    NET_INTERFACE_LIST_RESPONSE,
    NET_INTERFACE_ADD_IP_REQUEST,
    NET_INTERFACE_ADD_IP_RESPONSE,
    NET_INTERFACE_SET_DEFAULT_ROUTE_REQUEST,
    NET_INTERFACE_SET_DEFAULT_ROUTE_RESPONSE,
    NET_INTERFACE_DHCP_SERVER_REQUEST,
    NET_INTERFACE_DHCP_SERVER_RESPONSE,
    NET_INTERFACE_DHCP_CLIENT_REQUEST,
    NET_INTERFACE_DHCP_CLIENT_RESPONSE,
    SYNC_STATUS_REQUEST,
    SYNC_STATUS_RESPONSE,
    NET_USB_DHCP_SERVER_STATUS_REQUEST,
    NET_USB_DHCP_SERVER_STATUS_RESPONSE,
    NET_USB_DHCP_START_SERVER_REQUEST,
    NET_USB_DHCP_START_SERVER_RESPONSE,
    NET_USB_DHCP_START_CLIENT_REQUEST,
    NET_USB_DHCP_START_CLIENT_RESPONSE,
    NET_PROXY_GET_URL_REQUEST,
    NET_PROXY_GET_URL_RESPONSE,
    NET_PROXY_SET_URL_REQUEST,
    NET_PROXY_SET_URL_RESPONSE,
    NET_PING_REQUEST,
    NET_PING_RESPONSE,
    REBOOT_REQUEST,
    REBOOT_RESPONSE,
    START_RECORDING_EXPECTED_DURATION,
    IMU_TEST_PROGRESS_RESPONSE,
    IMU_TEST_FINISHED_RESPONSE,
    IMU_TEST_USER_IGNORED_FIRST_WARNING,
    IMU_TEST_USER_CANCELED_AFTER_WARNING,
    START_RECORDING_PROGRESS_RESPONSE,
    SET_LOCATION_REQUEST,
    SYSTEM_INFO_REQUEST,
    SYSTEM_INFO_RESPONSE,
    PERSISTENT_SETTINGS_REQUEST,
    PERSISTENT_SETTINGS_RESPONSE,
    SET_PERSISTENT_SETTINGS_REQUEST,
    KEEPALIVE_REQUEST,
    KEEPALIVE_RESPONSE,
    RESET_TO_FACTORY_REQUEST,
    RESET_TO_FACTORY_RESPONSE,
    RESTART_LAST_RECORDING_REQUEST,
    RESTART_LAST_RECORDING_RESPONSE
  }

  protected MessageType[] messageTypeOrdinals;

  protected TCPClient tcpClient;

  // litst to store lsiteners to various events
  protected ServerErrorDisplay errorDisplay;

  protected ArrayList<OnConnectionStateChangedListener> onConnectionStateChangedListeners =
      new ArrayList<>();
  protected ArrayList<OnRecordingStateChangedListener> onRecordingStateChangedListeners =
      new ArrayList<>();
  protected ArrayList<OnDiskInfoUpdatedListener> onDiskInfoUpdatedListeners = new ArrayList<>();
  protected ArrayList<OnStatusUpdateListener> onStatusUpdateListeners = new ArrayList<>();
  protected ArrayList<OnStatusLoadedListener> onStatusLoadedListeners = new ArrayList<>();
  protected ArrayList<OnExternalStorageStatusChangedListener>
      onExternalStorageStatusChangedListeners = new ArrayList<>();
  protected ArrayList<OnServerFilesystemChangedListener> onServerFilesystemChangedListeners =
      new ArrayList<>();
  protected ArrayList<OnFilesBackedUpListener> onFilesBackedUpListeners = new ArrayList<>();
  protected ArrayList<OnRosErrorsLoadedListener> onRosErrorsLoadedListeners = new ArrayList<>();
  protected ArrayList<OnNetworkInterfacesLoadedListener> onNetworkInterfacesLoadedListeners =
      new ArrayList<>();
  protected ArrayList<OnSyncStatusReceivedListener> onSyncStatusReceivedListeners =
      new ArrayList<>();
  protected ArrayList<OnNetUsbDhcpResponseListener> onNetUsbDhcpResponsesListeners =
      new ArrayList<>();
  protected ArrayList<OnNetProxyResponseListener> onNetProxyResponseListeners = new ArrayList<>();
  protected ArrayList<OnNetPingResponseListener> onNetPingResponseListeners = new ArrayList<>();
  protected ArrayList<OnRecordingStartExpectedDuration> onRecordingStartExpectedDurations =
      new ArrayList<>();
  protected ArrayList<OnShutdownResponseListener> onShutdownResponseListeners = new ArrayList<>();
  protected ArrayList<OnProjectDetailsChangedListener> onProjectDetailsChangedListeners =
      new ArrayList<>();
  protected ArrayList<OnImuTestUpdateListener> onImuTestUpdateListeners = new ArrayList<>();
  protected ArrayList<OnShutdownListener> onShutdownListeners = new ArrayList<>();
  protected ArrayList<OnDefaultRecordingTypeChangedListener> onDefaultRecordingTypeChangedListeners = new ArrayList<>();

  protected ReentrantLock onConnectionStateChangedListenerLock = new ReentrantLock();
  protected ReentrantLock onRecordingStateChangedListenersLock = new ReentrantLock();
  protected ReentrantLock onDiskInfoUpdatedListenersLock = new ReentrantLock();
  protected ReentrantLock onStatusUpdateListenersLock = new ReentrantLock();
  protected ReentrantLock onStatusLoadedListenersLock = new ReentrantLock();
  protected ReentrantLock onExternalStorageStatusChangedListenersLock = new ReentrantLock();
  protected ReentrantLock onServerFilesystemChangedListenersLock = new ReentrantLock();
  protected ReentrantLock onFilesBackedUpListenersLock = new ReentrantLock();
  protected ReentrantLock onRosErrorsLoadedListenersLock = new ReentrantLock();
  protected ReentrantLock onNetworkInterfacesLoadedListenersLock = new ReentrantLock();
  protected ReentrantLock onSyncStatusReceivedListenersLock = new ReentrantLock();
  protected ReentrantLock onNetUsbDhcpResponsesListenersLock = new ReentrantLock();
  protected ReentrantLock onNetProxyResponseListenersLock = new ReentrantLock();
  protected ReentrantLock onNetPingResponseListenersLock = new ReentrantLock();
  protected ReentrantLock onRecordingStartExpectedDurationsLock = new ReentrantLock();
  protected ReentrantLock onShutdownResponseListenersLock = new ReentrantLock();
  protected ReentrantLock onProjectDetailsChangedListenersLock = new ReentrantLock();
  protected ReentrantLock onImutestUpdateListenersLock = new ReentrantLock();
  protected ReentrantLock onShutdownListenersLock = new ReentrantLock();
  protected ReentrantLock onDefaultRecordingTypeChangedListenersLock = new ReentrantLock();

  // reduced by every loading step
  // current loading steps:
  // -- synchronize recording status
  // -- load the persistent settings
  protected final int INITIAL_LOADING_STATUS = 2;
  protected int loadingStatus = 2;

  protected boolean hasSpaceOnInternal = true;

  protected String deviceIdentifier = "none";

  Timer keepaliveTimer;
  long numKeepAliveSend = 0;
  long numKeepAliveReceived = 0;
  protected ReentrantLock keepaliveLock = new ReentrantLock();

  public ServerStateModel() {
    // cache values array of MessageType for ordinal to enum conversion
    messageTypeOrdinals = MessageType.values();
  }

  public void connect(Context ctx, String ipString) {
    Log.d(getClass().getSimpleName(), "Check connected");
    if (!isConnected) {
      Log.d(getClass().getSimpleName(), "Not connected, connecting...");

      if (ipString == null) {
        WifiManager wifiMgr =
            (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        // Set the most significant byte of the ip to 0 and then add 1,
        // effectively arriving
        // at the ip with the last field replaced by a 1
        ip = (ip & 0x00FFFFFF) + (0x01 << 24);
        // Deprecated as it does not support ipv4. Not a problem for us right
        // now.
        ipString = Formatter.formatIpAddress(ip);
        Log.i(
            getClass().getSimpleName(),
            "Server ip should be " + ipString + " port should be " + 25542);
      }
      tcpClient = new TCPClient(ipString, 25542);
      tcpClient.setTCPListener(this);
      tcpClient.connect();
    }
  }

  public void disconnect() {
    isConnected = false;
    tcpClient.disconnect();

    if (keepaliveTimer != null) {
      keepaliveTimer.cancel();
    }

    // inform listeners that the connection was closed
    onConnectionStateChangedListenerLock.lock();
    try {
      for (OnConnectionStateChangedListener l : onConnectionStateChangedListeners) {
        l.onConnectionClosed();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      onConnectionStateChangedListenerLock.unlock();
    }

    if (diskStatusPullTimer != null) {
      diskStatusPullTimer.cancel();
      diskStatusPullTimer = null;
    }
    if (serverLockTimer != null) {
      serverLockTimer.cancel();
      serverLockTimer = null;
    }
  }

  public void reset() {
    if (isConnected) {
      disconnect();
    }
    isConnected = false;
    recordingState = RecordingState.IDLE;

    if (diskStatusPullTimer != null) {
      diskStatusPullTimer.cancel();
      diskStatusPullTimer = null;
    }

    timeRecordingStarted = null;
    if (serverLockTimer != null) {
      serverLockTimer.cancel();
      serverLockTimer = null;
    }
    timeRemaining = 1;
    diskCapacityMb = 0;
    diskFreeMb = 0;

    gpsHasFix = false;
    hasRosError = false;

    flowStates.clear();

    lastRecordingFile = "";
    lastRecordingName = "";
    currentRecordingFile = "";
    currentRecordingRelativePath = "";
    currentRecordingName = "";

    onFilesLoadedListenerIdCounter = 0;

    projectName = "unknown";
    projectId = -1;
    projectCustomerId = -1;
    defaultProjectCustomerId = -1;
    defaultRecordingType = "indoor";

    try {
      lastLocationLock.lock();
      lastLocation = null;
    } finally {
      lastLocationLock.unlock();
    }

    lastSyncQueryPrintedError = false;
    shutdownInProgress = false;
    isFirstRecording = false;
    shutdownAfterRecordingStopped = false;

    remoteSystemInfo = new RemoteSystemInfo();

    messageTypeOrdinals = MessageType.values();

    tcpClient = null;

    loadingStatus = 2;

    hasSpaceOnInternal = true;

    deviceIdentifier = "none";

    try {
      keepaliveLock.lock();
      if (keepaliveTimer != null) {
        keepaliveTimer.cancel();
        keepaliveTimer = null;
        numKeepAliveSend = 0;
        numKeepAliveReceived = 0;
      }
    } finally {
      keepaliveLock.unlock();
    }
  }

  public void clearEventListeners() {
    try {
      onFilesLoadedListenerLock.lock();
      onFilesLoadedListeners.clear();
    } finally {
      onFilesLoadedListenerLock.unlock();
    }

    try {
      onConnectionStateChangedListenerLock.lock();
      onConnectionStateChangedListeners.clear();
    } finally {
      onConnectionStateChangedListenerLock.unlock();
    }

    try {
      onRecordingStateChangedListenersLock.lock();
      onRecordingStateChangedListeners.clear();
    } finally {
      onRecordingStateChangedListenersLock.unlock();
    }

    try {
      onDiskInfoUpdatedListenersLock.lock();
      onDiskInfoUpdatedListeners.clear();
    } finally {
      onDiskInfoUpdatedListenersLock.unlock();
    }

    try {
      onStatusUpdateListenersLock.lock();
      onStatusLoadedListeners.clear();
    } finally {
      onStatusUpdateListenersLock.unlock();
    }

    try {
      onExternalStorageStatusChangedListenersLock.lock();
      onExternalStorageStatusChangedListeners.clear();
    } finally {
      onExternalStorageStatusChangedListenersLock.unlock();
    }

    try {
      onServerFilesystemChangedListenersLock.lock();
      onServerFilesystemChangedListeners.clear();
    } finally {
      onServerFilesystemChangedListenersLock.unlock();
    }

    try {
      onFilesBackedUpListenersLock.lock();
      onFilesBackedUpListeners.clear();
    } finally {
      onFilesBackedUpListenersLock.unlock();
    }

    try {
      onRosErrorsLoadedListenersLock.lock();
      onRosErrorsLoadedListeners.clear();
    } finally {
      onRosErrorsLoadedListenersLock.unlock();
    }

    try {
      onNetworkInterfacesLoadedListenersLock.lock();
      onNetworkInterfacesLoadedListeners.clear();
    } finally {
      onNetworkInterfacesLoadedListenersLock.unlock();
    }

    try {
      onSyncStatusReceivedListenersLock.lock();
      onSyncStatusReceivedListeners.clear();
    } finally {
      onSyncStatusReceivedListenersLock.unlock();
    }

    try {
      onSyncStatusReceivedListenersLock.lock();
      onSyncStatusReceivedListeners.clear();
    } finally {
      onSyncStatusReceivedListenersLock.unlock();
    }

    try {
      onNetUsbDhcpResponsesListenersLock.lock();
      onNetUsbDhcpResponsesListeners.clear();
    } finally {
      onNetUsbDhcpResponsesListenersLock.unlock();
    }

    try {
      onNetProxyResponseListenersLock.lock();
      onNetProxyResponseListeners.clear();
    } finally {
      onNetProxyResponseListenersLock.unlock();
    }

    try {
      onNetPingResponseListenersLock.lock();
      onNetPingResponseListeners.clear();
    } finally {
      onNetPingResponseListenersLock.unlock();
    }

    try {
      onRecordingStartExpectedDurationsLock.lock();
      onRecordingStartExpectedDurations.clear();
    } finally {
      onRecordingStartExpectedDurationsLock.unlock();
    }

    try {
      onShutdownResponseListenersLock.lock();
      onShutdownResponseListeners.clear();
    } finally {
      onShutdownResponseListenersLock.unlock();
    }

    try {
      onProjectDetailsChangedListenersLock.lock();
      onProjectDetailsChangedListeners.clear();
    } finally {
      onProjectDetailsChangedListenersLock.unlock();
    }

    try {
      onImutestUpdateListenersLock.lock();
      onImuTestUpdateListeners.clear();
    } finally {
      onImutestUpdateListenersLock.unlock();
    }

    try {
      onShutdownListenersLock.lock();
      onShutdownListeners.clear();
    } finally {
      onShutdownListenersLock.unlock();
    }

    try {
      onDefaultRecordingTypeChangedListenersLock.lock();
      onDefaultRecordingTypeChangedListeners.clear();
    } finally {
      onDefaultRecordingTypeChangedListenersLock.unlock();
    }

  }

  public void loadStatus() {
    Log.i(getClass().getSimpleName(), "Requesting the current status from the server.");

    loadingStatus = INITIAL_LOADING_STATUS;

    tcpClient.sendMessage(MessageType.SYSTEM_STATE_REQUEST.ordinal(), null);
    tcpClient.sendMessage(MessageType.STATUS_UPDATE_REQUEST.ordinal(), null);
    tcpClient.sendMessage(MessageType.SYSTEM_INFO_REQUEST.ordinal(), null);
    tcpClient.sendMessage(MessageType.PERSISTENT_SETTINGS_REQUEST.ordinal(), null);
  }

  public void loadRosStatus() {
    tcpClient.sendMessage(MessageType.STATUS_UPDATE_REQUEST.ordinal(), null);
  }

  protected void onStatusLoaded() {
    onStatusLoadedListenersLock.lock();
    try {
      for (OnStatusLoadedListener l : onStatusLoadedListeners) {
        l.onStatusLoaded();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      onStatusLoadedListenersLock.unlock();
    }
  }

  /**
   * @return the filename with any invalid characters replaced by an underscore or an empty string
   *     if the <b>filename</b> was empty
   */
  public String creteRecordingPath(String projectname, String filename) {
    Date date = new Date();
    DateFormat format = new SimpleDateFormat("yyyy_MM_dd_HH-mm-ss");
    DateFormat folderFormat = new SimpleDateFormat("yyyy_MM_dd");

    if (wasMissionManuallySet()) {
      projectname = projectname.replaceAll("[^a-zA-Z0-9_]", "_");
      if (projectname.length() == 0) {
        projectname = folderFormat.format(date);
      }
    } else {
      projectname = "" + projectId;
    }
    filename = filename.replaceAll("[^a-zA-Z0-9_]", "_");
    filename = format.format(date) + "_" + filename;

    if (!filename.endsWith(".bag")) {
      filename += ".bag";
    }
    return projectname + "/" + filename;
  }

  boolean wasMissionManuallySet() {
    return projectId < 0;
  }

  public boolean startRecording(
      String projectname, String recordingName, String recordingType, Context context) {
    return startRecording(projectname, recordingName, recordingType, context, false);
  }

  public void restartRecording(boolean skip_imu_check) {
    TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
    encoder.addBoolean(skip_imu_check);
    tcpClient.sendMessage(MessageType.RESTART_LAST_RECORDING_REQUEST.ordinal(), encoder.getBuffer());
  }

  public boolean startRecording(
      String projectname,
      String recordingName,
      String recordingType,
      Context context,
      boolean skipImuTest) {
    if (recordingState == RecordingState.IDLE) {
      lastLocationLock.lock();
      try {
        lastLocation = null;
      } finally {
        lastLocationLock.unlock();
      }
      LocationProvider.acquireLocation(this, context);

      String filepath = creteRecordingPath(projectname, recordingName);
      if (filepath.length() > 0) {
        currentRecordingFile = filepath;
        currentRecordingName = recordingName;
        // Send a start recording packet to the server
        try {
          TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
          encoder.addString(recordingName);
          long time = System.currentTimeMillis() / 1000L;
          encoder.addUInt64(time);
          encoder.addString(recordingType);
          encoder.addString(projectName);
          if (projectId < 0) {
            encoder.addUInt64(0);
          } else {
            encoder.addUInt64(projectId + 1);
          }
          if (projectCustomerId < 0) {
            encoder.addUInt64(0);
          } else {
            encoder.addUInt64(projectCustomerId + 1);
          }
          encoder.addBoolean(skipImuTest);
          tcpClient.sendMessage(MessageType.START_RECORDING_REQUEST.ordinal(), encoder.getBuffer());
        } catch (UnsupportedEncodingException e) {
          errorDisplay.displayServerStateError(R.string.startRecordingError, filepath);
        }
      } else {
        if (errorDisplay != null) {
          errorDisplay.displayServerStateError(R.string.invalidFilename, filepath);
        }
        onRecordingStateChangedListenersLock.lock();
        try {
          for (OnRecordingStateChangedListener l : onRecordingStateChangedListeners) {
            l.onRecordingStopped();
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          onRecordingStateChangedListenersLock.unlock();
        }
        return false;
      }
    }
    return true;
  }

  public void stopRecording() {
    TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
    try {
      encoder.addString(BuildConfig.VERSION_NAME);
      encoder.addUInt64(System.currentTimeMillis() / 1000L);

      boolean hasLocation = false;
      double longitude = 0;
      double latitude = 0;
      lastLocationLock.lock();
      try {
        hasLocation = lastLocation != null;
        if (lastLocation != null) {
          longitude = lastLocation.getLongitude();
          latitude = lastLocation.getLatitude();
        }
      } finally {
        lastLocationLock.unlock();
      }
      int long_int = (int) ((longitude + 180.0d) / 360.0d * (double) (1 << 30));
      long_int = Math.max(0, Math.min(1 << 30, long_int));
      int lat_int = (int) ((latitude + 90.0d) / 180.0d * (double) (1 << 30));
      lat_int = Math.max(0, Math.min(1 << 30, lat_int));
      encoder.addUInt16((short) (hasLocation ? 0 : 1));
      encoder.addUInt64(long_int);
      encoder.addUInt64(lat_int);
      // Send a stop recording packet
      tcpClient.sendMessage(MessageType.STOP_RECORDING_REQUEST.ordinal(), encoder.getBuffer());
      recordingState = RecordingState.STOPPING;
    } catch (UnsupportedEncodingException e) {
      if (errorDisplay != null) {
        errorDisplay.displayImportantServerStateError(R.string.utf8EncodingError, false);
      }
      e.printStackTrace();
    }
  }

  public void shutdownServer(boolean ignoreBrokenBagFiles) {
    shutdownServer(false, ignoreBrokenBagFiles);
  }

  public void shutdownServer(boolean forceShutdown, boolean ignoreBrokenBagFiles) {
    if (recordingState == RecordingState.RECORDING && !forceShutdown) {
      // Stop the recording first
      shutdownAfterRecordingStopped = true;
      stopRecording();
    } else {
      onShutdownListenersLock.lock();
      try {
        for (OnShutdownListener l : onShutdownListeners) {
          l.onShutdownRequested();
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        onShutdownListenersLock.unlock();
      }
      TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
      encoder.addBoolean(ignoreBrokenBagFiles);
      tcpClient.sendMessage(MessageType.SHUTDOWN_REQUEST.ordinal(), encoder.getBuffer());
      shutdownInProgress = true;
    }
  }

  public boolean isServerShutdownInProgress() {
    return shutdownInProgress;
  }

  public void rebootServer() {
    tcpClient.sendMessage(MessageType.REBOOT_REQUEST.ordinal(), null);
  }

  @Override
  public void onTCPConnected() {
    shutdownInProgress = false;
    isConnected = true;
    deviceIdentifier = "" + System.currentTimeMillis();
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        byte[] bytes = interfaces.nextElement().getHardwareAddress();
        if (bytes != null) {
          deviceIdentifier = "";
          for (byte b : bytes) {
            deviceIdentifier += String.format("%02x", b);
            deviceIdentifier += ":";
          }
          deviceIdentifier = deviceIdentifier.substring(0, deviceIdentifier.length() - 1);
        }
      }
    } catch (SocketException e) {
      // do nothing, we will just use the current time as a semi unique
      // identifier
    }
    Log.d(getClass().getSimpleName(), "Device Identifier: <" + deviceIdentifier + ">");

    // The full initialization will only be started once the protocol version
    // of the server was
    // verified. The server should send us a PROTOCOL_VERSION packet on its own.

    numKeepAliveReceived = 0;
    numKeepAliveSend = 0;
    if (keepaliveTimer != null) {
      keepaliveTimer.cancel();
    }
    keepaliveTimer = new Timer();
    keepaliveTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        tcpClient.sendMessage(MessageType.KEEPALIVE_REQUEST.ordinal(), null);
        keepaliveLock.lock();
        try {
          numKeepAliveSend++;
          if (numKeepAliveSend > numKeepAliveReceived + 10) {
            if (errorDisplay != null) {
              errorDisplay.displayServerStateError(R.string.keepaliveTimeout);
            }
            // We haven't heard from the client for 10 seconds, disconnect
            disconnect();
          }
        } finally{
          keepaliveLock.unlock();
        }
      }
    }, 0,2000);
  }

  protected void onNetworkProtocolVersionCheckPassed() {

    // initialize updating of the remaining recording time
    diskStatusPullTimer = new Timer();
    diskStatusPullTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            tcpClient.sendMessage(MessageType.DISK_STATUS_REQUEST.ordinal(), null);
          }
        },
        0,
        1000);

    loadStatus();

    onConnectionStateChangedListenerLock.lock();
    try {
      for (OnConnectionStateChangedListener l : onConnectionStateChangedListeners) {
        l.onConnectionEstablished();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      onConnectionStateChangedListenerLock.unlock();
    }
  }

  @Override
  public void onTCPConnectionError(TCPClient.ConnectionError cause) {
    if (errorDisplay != null) {
      switch (cause) {
        case HOST_UNKNOWN:
          errorDisplay.displayServerStateError(R.string.serverModelTcpUnknownHost);
          break;
        case HOST_UNREACHABLE:
          errorDisplay.displayServerStateError(R.string.serverModelTcpHostUnreachable);
          break;
        case SERVER_NOT_RUNNING:
          errorDisplay.displayServerStateError(R.string.serverModelTcpServerNotRunning);
          break;
        case GENERIC:
          errorDisplay.displayServerStateError(R.string.serverModelTcpGenericError);
          break;
      }
    }

    onConnectionStateChangedListenerLock.lock();
    try {
      for (OnConnectionStateChangedListener l : onConnectionStateChangedListeners) {
        l.onConnectionClosed();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      onConnectionStateChangedListenerLock.unlock();
    }
  }

  @Override
  public void onTCPDisconnected() {
    onConnectionStateChangedListenerLock.lock();
    try {
      for (OnConnectionStateChangedListener l : onConnectionStateChangedListeners) {
        l.onConnectionClosed();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      onConnectionStateChangedListenerLock.unlock();
    }
  }

  @Override
  public void onTcpMessageReceived(byte[] message) {
    int messageType = ((message[0] & 0xFF) << 8) + (message[1] & 0xFF);
    byte data[] = new byte[message.length - 2];
    System.arraycopy(message, 2, data, 0, data.length);
    TCPClient.PacketDecoder decoder = new TCPClient.PacketDecoder(data);

    switch (messageTypeOrdinals[messageType]) {
      case PROTOCOL_VERSION:
        int serverVersion = decoder.getUInt32();
        if (serverVersion != NETWORK_PROTOCOL_VERSION) {
          if (errorDisplay != null) {
            // Show an error message and disconnect.

            errorDisplay.displayImportantServerStateError(
                R.string.wrongProtocolVersion, true, NETWORK_PROTOCOL_VERSION, serverVersion);
          } else {
            // If the error display does not exist simply disconnect
            // immediately.
            disconnect();
          }
        } else {
          onNetworkProtocolVersionCheckPassed();
        }
        break;
      case SYSTEM_STATE_RESPONSE:
        try {
          recordingState = RecordingState.values()[decoder.getUInt16()];
          currentRecordingFile = decoder.getString();
          currentRecordingName = decoder.getString();
          lastRecordingFile = decoder.getString();
          lastRecordingName = decoder.getString();
          timeRecordingStarted = Calendar.getInstance().getTime();
          timeRecordingStarted =
              new Date(timeRecordingStarted.getTime() - (1000 * decoder.getUInt32()));
          long t = decoder.getUInt64();
          defaultProjectCustomerId = t == 0 ? -1 : t - 1;
          isFirstRecording = decoder.getBoolean();
          kbpsWriteRate = decoder.getUInt64() / 1024;
        } catch (UnsupportedEncodingException e) {
          if (errorDisplay != null) {
            errorDisplay.displayImportantServerStateError(R.string.utf8EncodingError, false);
          }
        }

        Log.d(
            getClass().getSimpleName(),
            "onTcpMessageReceived: the "
                + "recording state on the server is "
                + recordingState.name());

        if (loadingStatus > 0) {
          loadingStatus--;
          if (loadingStatus == 0) {
            onStatusLoaded();
          }
        }
        break;
      case START_RECORDING_RESPONSE:
        int status = decoder.getUInt16();
        if (status == 0) {
          isFirstRecording = false;
          timeRecordingStarted = new Date();
          recordingState = RecordingState.RECORDING;
          onRecordingStateChangedListenersLock.lock();
          try {
            for (OnRecordingStateChangedListener l : onRecordingStateChangedListeners) {
              l.onRecordingStarted(currentRecordingFile, currentRecordingName);
            }
          } catch (Exception e) {
            e.printStackTrace();
          } finally {
            onRecordingStateChangedListenersLock.unlock();
          }
        } else if (status == 2) {
          recordingState = RecordingState.IMU_TESTING;
          // a imu test is required
          onImutestUpdateListenersLock.lock();
          try {
            for (OnImuTestUpdateListener l : onImuTestUpdateListeners) {
              l.onImuTestStarted();
            }
          } finally {
            onImutestUpdateListenersLock.unlock();
          }
        } else {
          recordingState = RecordingState.IDLE;
          Log.e(getClass().getSimpleName(), "Error during recording start " + status);
          if (errorDisplay != null) {
            errorDisplay.displayImportantServerStateError(R.string.startRecordingError, false);
          }
          onRecordingStateChangedListenersLock.lock();
          try {
            for (OnRecordingStateChangedListener l : onRecordingStateChangedListeners) {
              l.onRecordingStopped();
            }
          } catch (Exception e) {
            e.printStackTrace();
          } finally {
            onRecordingStateChangedListenersLock.unlock();
          }
        }
        break;
      case STOP_RECORDING_RESPONSE:
        short stopRecordingStatus = decoder.getUInt16();
        recordingState = RecordingState.IDLE;
        try {
          lastRecordingName = decoder.getString();
          lastRecordingFile = decoder.getString();
        } catch (UnsupportedEncodingException e) {
          Log.e(getClass().getSimpleName(), "UTF-8 is not supported by this device", e);
        }
        currentRecordingName = "";
        currentRecordingFile = "";
        onRecordingStateChangedListenersLock.lock();
        try {
          for (OnRecordingStateChangedListener l : onRecordingStateChangedListeners) {
            l.onRecordingStopped();
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          onRecordingStateChangedListenersLock.unlock();
        }
        if (stopRecordingStatus != 0) {
          Log.e(getClass().getSimpleName(), "The recording did not finish " + "properly.");
          if (errorDisplay != null) {
            errorDisplay.displayImportantServerStateError(R.string.recordingFailed, false);
          }
        }
        if (shutdownAfterRecordingStopped) {
          shutdownServer(false);
        }
        break;
      case LOAD_FILE_LIST_RESPONSE:
        int id = decoder.getUInt16();
        Log.d(getClass().getSimpleName(), "received a file list for " + id);
        OnFilesLoadedListener listener;
        onFilesLoadedListenerLock.lock();
        try {
          listener = onFilesLoadedListeners.get(id);
        } finally {
          onFilesLoadedListenerLock.unlock();
        }
        if (listener != null) {
          String fileNames[] = null;
          String rawFileNames[] = null;
          try {
            fileNames = decoder.getStringArray();
            rawFileNames = decoder.getStringArray();
          } catch (UnsupportedEncodingException e) {
            Log.e(getClass().getSimpleName(), "", e);
            if (errorDisplay != null) {
              errorDisplay.displayServerStateError(R.string.utf8EncodingError);
            }
            return;
          }
          Log.d(getClass().getSimpleName(), "Received " + fileNames.length + " files");
          long sizes[] = new long[fileNames.length];
          for (int i = 0; i < sizes.length; i++) {
            sizes[i] = decoder.getUInt64();
          }
          // The booleans are encoded as a bit mask of 16 bit unsigned integers
          boolean is_dir[] = new boolean[fileNames.length];
          int num_ints = (int) Math.ceil(fileNames.length / 16f);
          for (int i = 0; i < num_ints; i++) {
            short raw = decoder.getUInt16();
            int bits = raw & 0xFFFF;
            for (int j = 0; j < 16 && i * 16 + j < is_dir.length; j++) {
              is_dir[i * 16 + j] = (bits & (1 << (15 - j))) > 0;
            }
          }

          listener.onFilesLoaded(fileNames, rawFileNames, sizes, is_dir);
        } else {
          Log.w(getClass().getSimpleName(), "No listener registered for file " + "id " + id);
        }
        Log.d(getClass().getSimpleName(), "Done handling the file list");
        break;
      case NET_INTERFACE_LIST_RESPONSE:
        try {
          String interfaces[] = decoder.getStringArray();
          String infos[] = decoder.getStringArray();
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
        } catch (UnsupportedEncodingException e) {
          Log.e(getClass().getSimpleName(), "", e);
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.utf8EncodingError);
          }
          return;
        }
        break;
      case NET_INTERFACE_ADD_IP_RESPONSE:
        short addIpResponse = decoder.getUInt16();
        if (addIpResponse == 0) {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.networkingSuccess);
          }
          Log.d(getClass().getSimpleName(), "Successfully added the ip");
        } else {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.networkingError);
          }
          Log.d(getClass().getSimpleName(), "Error while adding the ip");
        }
        break;
      case NET_INTERFACE_SET_DEFAULT_ROUTE_RESPONSE:
        short defaultRouteResponse = decoder.getUInt16();
        if (defaultRouteResponse == 0) {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.networkingSuccess);
          }
          Log.d(getClass().getSimpleName(), "Successfully added the ip");
        } else {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.networkingError);
          }
          Log.d(getClass().getSimpleName(), "Error while adding the ip");
        }
        break;
      case NET_INTERFACE_DHCP_CLIENT_RESPONSE:
        short dhcpClientResponse = decoder.getUInt16();
        if (dhcpClientResponse == 0) {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.networkingSuccess);
          }
          Log.d(getClass().getSimpleName(), "Successfully added the ip");
        } else {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.networkingError);
          }
          Log.d(getClass().getSimpleName(), "Error while adding the ip");
        }
        break;
      case NET_INTERFACE_DHCP_SERVER_RESPONSE:
        short dhcpServerResponse = decoder.getUInt16();
        if (dhcpServerResponse == 0) {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.networkingSuccess);
          }
          Log.d(getClass().getSimpleName(), "Successfully added the ip");
        } else {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.networkingError);
          }
          Log.d(getClass().getSimpleName(), "Error while adding the ip");
        }
        break;
      case DISK_STATUS_RESPONSE:
        diskCapacityMb = decoder.getUInt64();
        diskFreeMb = decoder.getUInt64();
        // the sensor does not fill the last GB of data, it should not be shown
        diskCapacityMb -= 1024;
        diskFreeMb -= 1024;
        diskCapacityMb = diskCapacityMb > 0 ? diskCapacityMb : 0;
        diskFreeMb = diskFreeMb > 0 ? diskFreeMb : 0;

        if (diskFreeMb == 0) {
          if (hasSpaceOnInternal) {
            if (errorDisplay != null) {
              errorDisplay.displayImportantServerStateError(R.string.deviceFull, false);
            }
          }
          hasSpaceOnInternal = false;
        } else {
          hasSpaceOnInternal = true;
        }
        timeRemaining = (diskFreeMb * 1024) / kbpsWriteRate;

        // Inform all listeners
        onDiskInfoUpdatedListenersLock.lock();
        try {
          for (OnDiskInfoUpdatedListener l : onDiskInfoUpdatedListeners) {
            l.onDiskInfoUpdated();
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          onDiskInfoUpdatedListenersLock.unlock();
        }
        break;
      case DELETE_FILES_RESPONSE:
        onServerFilesystemChangedListenersLock.lock();
        try {
          for (OnServerFilesystemChangedListener l : onServerFilesystemChangedListeners) {
            l.onServerFilesystemChanged();
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          onServerFilesystemChangedListenersLock.unlock();
        }

        if (decoder.getUInt16() != 0) {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.deleteFilesFailed);
          }
        }
        break;
      case STATUS_UPDATE_RESPONSE:
        long numEntries = decoder.getUInt64();
        flowStates.clear();
        for (long i = 0; i < numEntries; ++i) {
          flowStates.add(FlowState.values()[decoder.getUInt16()]);
        }
        hasRosError = decoder.getBoolean();
        gpsHasFix = false;
        if (flowStates.size() > 1) {
          gpsHasFix = flowStates.get(1) == FlowState.GOOD;
        }

        onStatusUpdateListenersLock.lock();
        try {
          for (OnStatusUpdateListener l : onStatusUpdateListeners) {
            l.onStatusUpdate(this);
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          onStatusUpdateListenersLock.unlock();
        }
        break;
      case ROS_MESSAGES_RESPONSE:
        try {
          String messages[] = decoder.getStringArray();
          onRosErrorsLoadedListenersLock.lock();
          try {
            for (OnRosErrorsLoadedListener l : onRosErrorsLoadedListeners) {
              l.onRosErrorsLoaded(messages);
            }
          } catch (Exception e) {
            e.printStackTrace();
          } finally {
            onRosErrorsLoadedListenersLock.unlock();
          }
        } catch (UnsupportedEncodingException e) {
          Log.e(getClass().getSimpleName(), "", e);
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.utf8EncodingError);
          }
          return;
        }
        break;
      case SYNC_STATUS_RESPONSE:
        short status_query_response = decoder.getUInt16();
        boolean isUploading = decoder.getBoolean();
        boolean isConnected = decoder.getBoolean();
        long uploadEtaMs = decoder.getUInt64();
        long uploadSpeedKbps = decoder.getUInt64();
        short uploadPercentage = decoder.getUInt16();
        try {
          onSyncStatusReceivedListenersLock.lock();
          for (OnSyncStatusReceivedListener l : onSyncStatusReceivedListeners) {
            l.onSyncStatusReceived(
                status_query_response == 0,
                isUploading,
                isConnected,
                uploadEtaMs,
                uploadSpeedKbps,
                uploadPercentage);
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
        break;
      case NET_USB_DHCP_SERVER_STATUS_RESPONSE:
        try {
          OnNetUsbDhcpResponseListener.ServerStatus netUsbServerStatus =
              OnNetUsbDhcpResponseListener.ServerStatus.values()[decoder.getUInt16()];
          onNetUsbDhcpResponsesListenersLock.lock();
          try {
            for (OnNetUsbDhcpResponseListener l : onNetUsbDhcpResponsesListeners) {
              l.onNetUsbDhcpServerStatus(netUsbServerStatus);
            }
          } finally {
            onNetUsbDhcpResponsesListenersLock.unlock();
          }
        } catch (Exception e) {
          Log.e(
              getClass().getSimpleName(),
              "Error while handling a net usb " + "server status response.",
              e);
        }
        break;
      case NET_USB_DHCP_START_CLIENT_RESPONSE:
        try {
          boolean clientStartFailed = decoder.getUInt16() == 1;
          onNetUsbDhcpResponsesListenersLock.lock();
          try {
            for (OnNetUsbDhcpResponseListener l : onNetUsbDhcpResponsesListeners) {
              l.onNetUsbDhcpClientStarted(!clientStartFailed);
            }
          } finally {
            onNetUsbDhcpResponsesListenersLock.unlock();
          }
        } catch (Exception e) {
          Log.e(
              getClass().getSimpleName(),
              "Error while handling a net usb " + "server status response.",
              e);
        }
        break;
      case NET_USB_DHCP_START_SERVER_RESPONSE:
        try {
          boolean serverStartFailed = decoder.getUInt16() == 1;
          onNetUsbDhcpResponsesListenersLock.lock();
          try {
            for (OnNetUsbDhcpResponseListener l : onNetUsbDhcpResponsesListeners) {
              l.onNetUsbDhcpServerStarted(!serverStartFailed);
            }
          } finally {
            onNetUsbDhcpResponsesListenersLock.unlock();
          }
        } catch (Exception e) {
          Log.e(
              getClass().getSimpleName(),
              "Error while handling a net usb " + "server status response.",
              e);
        }
        break;
      case NET_PROXY_GET_URL_RESPONSE:
        try {
          int proxyGetStatus = decoder.getUInt16();
          String proxyUrl = decoder.getString();
          onNetProxyResponseListenersLock.lock();
          try {
            for (OnNetProxyResponseListener l : onNetProxyResponseListeners) {
              l.onNetProxyUrlReceived(proxyGetStatus == 0, proxyUrl);
            }
          } catch (Exception e) {
            e.printStackTrace();
          } finally {
            onNetProxyResponseListenersLock.unlock();
          }
        } catch (UnsupportedEncodingException e) {
          Log.e(getClass().getSimpleName(), "", e);
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.utf8EncodingError);
          }
          return;
        }
        break;
      case NET_PROXY_SET_URL_RESPONSE:
        int proxyGetStatus = decoder.getUInt16();
        if (proxyGetStatus != 0) {
          if (errorDisplay != null) {
            errorDisplay.displayServerStateError(R.string.networkingSetProxyError);
          }
        }
        onNetProxyResponseListenersLock.lock();
        try {
          for (OnNetProxyResponseListener l : onNetProxyResponseListeners) {
            l.onNetProxyUrlSet(proxyGetStatus == 0);
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          onNetProxyResponseListenersLock.unlock();
        }
        break;
      case NET_PING_RESPONSE:
        short pingResponseStatus = decoder.getUInt16();
        try {
          onNetPingResponseListenersLock.lock();
          for (OnNetPingResponseListener l : onNetPingResponseListeners) {
            l.onPingResponseReceived(pingResponseStatus == 0);
          }
        } finally {
          onNetPingResponseListenersLock.unlock();
        }
        break;
      case START_RECORDING_EXPECTED_DURATION:
        long expectedTimeMs = decoder.getUInt64();
        float expectedTimeS = (float) (expectedTimeMs / 1000.0);
        try {
          onRecordingStartExpectedDurationsLock.lock();
          for (OnRecordingStartExpectedDuration l : onRecordingStartExpectedDurations) {
            l.onRecordingStartExpectedDuration(expectedTimeS);
          }
        } finally {
          onRecordingStartExpectedDurationsLock.unlock();
        }
        break;
      case SHUTDOWN_RESPONSE:
        int shutdownStatus = decoder.getUInt32();
        onShutdownResponseListenersLock.lock();
        try {
          for (OnShutdownResponseListener l : onShutdownResponseListeners) {
            l.onShutdownResponse(shutdownStatus);
          }
        } finally {
          onShutdownResponseListenersLock.unlock();
        }
        break;
      case SHUTDOWN_RESPONSE_BROKEN_BAGFILES:
        shutdownInProgress = false;
        try {
          String[] brokenBagFiles = decoder.getStringArray();
          onShutdownResponseListenersLock.lock();
          try {
            for (OnShutdownResponseListener l : onShutdownResponseListeners) {
              l.onShutdownBrokenBagfiles(brokenBagFiles);
            }
          } finally {
            onShutdownResponseListenersLock.unlock();
          }
          break;
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
      case IMU_TEST_PROGRESS_RESPONSE:
        float imuTestProgress = decoder.getUInt32() / 10000.0f;
        onImutestUpdateListenersLock.lock();
        try {
          for (OnImuTestUpdateListener l : onImuTestUpdateListeners) {
            l.onImuTestProgressUpdate(imuTestProgress);
          }
        } finally {
          onImutestUpdateListenersLock.unlock();
        }
        break;
      case IMU_TEST_FINISHED_RESPONSE:
        boolean imuTestSuccess = decoder.getBoolean();
        if (imuTestSuccess) {
          recordingState = RecordingState.STARTING_RECORDING;
        } else {
          recordingState = RecordingState.IDLE;
        }
        onImutestUpdateListenersLock.lock();
        try {
          for (OnImuTestUpdateListener l : onImuTestUpdateListeners) {
            l.onImuTestFinished(imuTestSuccess);
          }
        } finally {
          onImutestUpdateListenersLock.unlock();
        }

        break;
      case START_RECORDING_PROGRESS_RESPONSE:
        float startRecordingProgess = decoder.getUInt32() / 10000.0f;
        onRecordingStateChangedListenersLock.lock();
        try {
          for (OnRecordingStateChangedListener l : onRecordingStateChangedListeners) {
            l.onStartRecordingProgress(startRecordingProgess);
          }
        } finally {
          onRecordingStateChangedListenersLock.unlock();
        }
        break;
      case SYSTEM_INFO_RESPONSE:
        try {
          remoteSystemInfo.name = decoder.getString();
          remoteSystemInfo.versionMajor = decoder.getUInt16();
          remoteSystemInfo.versionMinor = decoder.getUInt16();
          remoteSystemInfo.versionPatch = decoder.getUInt16();
          remoteSystemInfo.hardwareVersion = decoder.getString();
          remoteSystemInfo.usbMacInfo = decoder.getString();
          remoteSystemInfo.usbIpInfo = decoder.getString();
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
        break;
      case PERSISTENT_SETTINGS_RESPONSE:
        try {
          projectName = decoder.getString();
          projectId = decoder.getUInt64() - 1;
          projectCustomerId = decoder.getUInt64();
          defaultRecordingType = decoder.getString();
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
        if (loadingStatus > 0) {
          loadingStatus--;
          if (loadingStatus == 0) {
            onStatusLoaded();
          }
        }
        // Inform the listeners
        onProjectDetailsChangedListenersLock.lock();
        try {
          for (OnProjectDetailsChangedListener l : onProjectDetailsChangedListeners) {
            l.onProjectCustomerIdChanged(projectCustomerId);
            l.onProjectIdChanged(projectId);
            l.onProjectNameChanged(projectName);
          }
        } finally {
          onProjectDetailsChangedListenersLock.unlock();
        }

        onDefaultRecordingTypeChangedListenersLock.lock();
        try {
          for (OnDefaultRecordingTypeChangedListener l : onDefaultRecordingTypeChangedListeners) {
            l.onDefaultRecordingTypeChanged();
          }
        } finally {
          onDefaultRecordingTypeChangedListenersLock.unlock();
        }
        break;
      case KEEPALIVE_RESPONSE:
        keepaliveLock.lock();
        try {
          numKeepAliveReceived++;
        } finally{
          keepaliveLock.unlock();
        }
        break;
      case RESET_TO_FACTORY_RESPONSE:
        int reset_status = decoder.getUInt16();
        if (reset_status == 0) {
          if (errorDisplay != null) {
            errorDisplay.displayImportantServerStateError(R.string.successfullFactoryReset, false);
          }
        } else {
          if (errorDisplay != null) {
            errorDisplay.displayImportantServerStateError(R.string.factoryResetError, false);
          }
          Log.e(getClass().getSimpleName(), "Unable to reset the server to factory settings");
        }
        break;
      case RESTART_LAST_RECORDING_RESPONSE:
        int restart_status = decoder.getUInt16();
        if (restart_status != 0) {
          if (errorDisplay != null) {
            errorDisplay.displayImportantServerStateError(R.string.startRecordingError, false);
          }
        }
        break;
      default:
        Log.w(
            getClass().getSimpleName(),
            "Received a tcp message without a handler for its message type: " + messageType);
    }
  }

  /**
   * Loads the content of path, displaying all bag files and directories. Will call the listener
   * method of the listener once the data has been returned.
   */
  public void loadFilesInDir(String path, OnFilesLoadedListener listener) {
    // only do this if whe are already connected to the server
    if (isConnected) {
      onFilesLoadedListenerLock.lock();
      try {
        onFilesLoadedListeners.put(onFilesLoadedListenerIdCounter, listener);
      } finally {
        onFilesLoadedListenerLock.unlock();
      }
      TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
      try {
        encoder.addUInt16((short) onFilesLoadedListenerIdCounter);
        encoder.addString(path);
        tcpClient.sendMessage(MessageType.LOAD_FILE_LIST_REQUEST.ordinal(), encoder.getBuffer());
        Log.d(
            getClass().getSimpleName(),
            "requested a file list for " + onFilesLoadedListenerIdCounter + " with path " + path);
      } catch (UnsupportedEncodingException e) {
        if (errorDisplay != null) {
          errorDisplay.displayServerStateError(R.string.utf8EncodingError);
        }
      }
      onFilesLoadedListenerIdCounter++;
      onFilesLoadedListenerIdCounter %= (1 << 14);
    }
  }

  /** @param filelist A list of files and directories to delete */
  public void deleteFiles(String filelist[]) {
    TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
    try {
      encoder.addStringArray(filelist);
      tcpClient.sendMessage(MessageType.DELETE_FILES_REQUEST.ordinal(), encoder.getBuffer());
    } catch (UnsupportedEncodingException e) {
      if (errorDisplay != null) {
        errorDisplay.displayServerStateError(R.string.utf8EncodingError);
      }
      e.printStackTrace();
    }
  }

  public void queryForSyncStatus() {
    tcpClient.sendMessage(MessageType.SYNC_STATUS_REQUEST.ordinal(), null);
  }

  /** @brief Requests a list of all network interfaces from the server */
  public void loadNetworkInterfaces() {
    tcpClient.sendMessage(MessageType.NET_INTERFACE_LIST_REQUEST.ordinal(), null);
  }

  public boolean getIsFirstRecording() {
    return isFirstRecording;
  }

  public void serverInterfaceAddIp(String ifname, String ip) {
    TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
    try {
      encoder.addString(ifname);
      encoder.addString(ip);
      tcpClient.sendMessage(
          MessageType.NET_INTERFACE_ADD_IP_REQUEST.ordinal(), encoder.getBuffer());
    } catch (UnsupportedEncodingException e) {
      if (errorDisplay != null) {
        errorDisplay.displayServerStateError(R.string.utf8EncodingError);
      }
      e.printStackTrace();
    }
  }

  public void serverSetDefaultRoute(String ip) {
    TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
    try {
      encoder.addString(ip);
      tcpClient.sendMessage(
          MessageType.NET_INTERFACE_SET_DEFAULT_ROUTE_REQUEST.ordinal(), encoder.getBuffer());
    } catch (UnsupportedEncodingException e) {
      if (errorDisplay != null) {
        errorDisplay.displayServerStateError(R.string.utf8EncodingError);
      }
      e.printStackTrace();
    }
  }

  public void serverInterfaceSetDhcpClientState(String ifname, boolean running) {
    TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
    try {
      encoder.addString(ifname);
      encoder.addBoolean(running);
      tcpClient.sendMessage(
          MessageType.NET_INTERFACE_DHCP_CLIENT_REQUEST.ordinal(), encoder.getBuffer());
    } catch (UnsupportedEncodingException e) {
      if (errorDisplay != null) {
        errorDisplay.displayServerStateError(R.string.utf8EncodingError);
      }
      e.printStackTrace();
    }
  }

  public void serverInterfaceSetDhcpServerState(String ifname, boolean running) {
    TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
    try {
      encoder.addString(ifname);
      encoder.addBoolean(running);
      tcpClient.sendMessage(
          MessageType.NET_INTERFACE_DHCP_SERVER_REQUEST.ordinal(), encoder.getBuffer());
    } catch (UnsupportedEncodingException e) {
      if (errorDisplay != null) {
        errorDisplay.displayServerStateError(R.string.utf8EncodingError);
      }
      e.printStackTrace();
    }
  }

  @Override
  public void OnLocationAcquired(Location location) {
    Log.d(getClass().getSimpleName(), "Acquired a location");
    lastLocationLock.lock();
    try {
      this.lastLocation = location;

      PacketEncoder encoder = new PacketEncoder();
      encoder.addUInt64(location.getTime() / 1000L);

      double longitude = location.getLongitude();
      double latitude = location.getLatitude();
      int long_int = (int) ((longitude + 180.0d) / 360.0d * (double) (1 << 30));
      long_int = Math.max(0, Math.min(1 << 30, long_int));
      int lat_int = (int) ((latitude + 90.0d) / 180.0d * (double) (1 << 30));
      lat_int = Math.max(0, Math.min(1 << 30, lat_int));
      encoder.addUInt32(long_int);
      encoder.addUInt32(lat_int);
      tcpClient.sendMessage(MessageType.SET_LOCATION_REQUEST.ordinal(), encoder.getBuffer());
    } finally {
      lastLocationLock.unlock();
    }
  }

  public void queryNetUsbDhcpServerState() {
    tcpClient.sendMessage(MessageType.NET_USB_DHCP_SERVER_STATUS_REQUEST.ordinal(), null);
  }

  public void netUsbDhcpStartServer() {
    tcpClient.sendMessage(MessageType.NET_USB_DHCP_START_SERVER_REQUEST.ordinal(), null);
  }

  public void netUsbDhcpStopServer() {
    tcpClient.sendMessage(MessageType.NET_USB_DHCP_START_CLIENT_REQUEST.ordinal(), null);
  }

  public void loadNetProxyUrl() {
    tcpClient.sendMessage(MessageType.NET_PROXY_GET_URL_REQUEST.ordinal(), null);
  }

  public void setNetProxyUrl(String newUrl) {
    TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
    try {
      encoder.addString(newUrl);
      tcpClient.sendMessage(MessageType.NET_PROXY_SET_URL_REQUEST.ordinal(), encoder.getBuffer());
    } catch (UnsupportedEncodingException e) {
      if (errorDisplay != null) {
        errorDisplay.displayImportantServerStateError(R.string.utf8EncodingError, false);
      }
      Log.e(
          getClass().getSimpleName(),
          "Unsupported encoding when trying to assemble a " + "NET_PROXY_SET_URL_REQUEST packet.",
          e);
    }
  }

  public void resetServerToFactorySettings() {
    tcpClient.sendMessage(MessageType.RESET_TO_FACTORY_REQUEST.ordinal(), null);
    // reload the persistent settings
    tcpClient.sendMessage(MessageType.PERSISTENT_SETTINGS_REQUEST.ordinal(), null);
  }

  /**
   * @return the relative path to the file a recording that was start during THIS SESSION OF THE
   *     APP, and is still running. The path is not guaranteed to be valid after a disconnect.
   */
  public String getCurrentRecordingRelativePath() {
    return currentRecordingRelativePath;
  }

  public String getRecordingFilename() {
    return currentRecordingFile;
  }

  public String getRecordingName() {
    return currentRecordingName;
  }

  public boolean isRecording() {
    return recordingState != RecordingState.IDLE;
  }

  public RecordingState getRecordingState() {
    return recordingState;
  }

  public long getTimeRemaining() {
    return timeRemaining;
  }

  public long getDiskCapacityMb() {
    return diskCapacityMb;
  }

  public long getDiskFreeMb() {
    return diskFreeMb;
  }

  public boolean getGpsHasFix() {
    return gpsHasFix;
  }

  public boolean getHasRosError() {
    return hasRosError;
  }

  public ArrayList<FlowState> getFlowStates() {
    return flowStates;
  }

  public boolean isConnected() {
    return isConnected;
  }

  public long getKbpsWriteRate() {
    return kbpsWriteRate;
  }

  public long getTimeRecording() {
    Date d = new Date();
    return timeRecordingStarted.getTime() - d.getTime();
  }

  public String getLastRecordingFile() {
    return lastRecordingFile;
  }

  public String getLastRecordingName() {
    return lastRecordingName;
  }

  public long getRecordingStartTime() {
    return timeRecordingStarted == null ? 0 : timeRecordingStarted.getTime();
  }

  public void queryForRosErrors() {
    Log.d(getClass().getSimpleName(), "querying for ros errors");
    tcpClient.sendMessage(MessageType.ROS_MESSAGES_REQUEST.ordinal(), null);
  }

  public void setServerErrorDisplay(ServerErrorDisplay d) {
    errorDisplay = d;
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String name) {
    projectName = name;

    // Inform the listeners
    onProjectDetailsChangedListenersLock.lock();
    try {
      for (OnProjectDetailsChangedListener l : onProjectDetailsChangedListeners) {
        l.onProjectNameChanged(name);
      }
    } finally {
      onProjectDetailsChangedListenersLock.unlock();
    }
    sendPersistentSettingsUpdate();
  }

  public void setProjectId(long id) {
    projectId = id;
    // Inform the listeners
    onProjectDetailsChangedListenersLock.lock();
    try {
      for (OnProjectDetailsChangedListener l : onProjectDetailsChangedListeners) {
        l.onProjectIdChanged(id);
      }
    } finally {
      onProjectDetailsChangedListenersLock.unlock();
    }
    sendPersistentSettingsUpdate();
  }

  public void setProjectCustomerId(long id) {
    projectCustomerId = id;
    // Inform the listeners
    onProjectDetailsChangedListenersLock.lock();
    try {
      for (OnProjectDetailsChangedListener l : onProjectDetailsChangedListeners) {
        l.onProjectCustomerIdChanged(id);
      }
    } finally {
      onProjectDetailsChangedListenersLock.unlock();
    }
    sendPersistentSettingsUpdate();
  }

  public void setDefaultRecordingType(String type) {
    defaultRecordingType = type;

    onDefaultRecordingTypeChangedListenersLock.lock();
    try {
      for (OnDefaultRecordingTypeChangedListener l : onDefaultRecordingTypeChangedListeners) {
        l.onDefaultRecordingTypeChanged();
      }
    } finally {
      onDefaultRecordingTypeChangedListenersLock.unlock();
    }

    sendPersistentSettingsUpdate();
  }

  public String getDefaultRecordingType() {
    return defaultRecordingType;
  }

  public void sendPersistentSettingsUpdate() {
    try {
      PacketEncoder encoder = new PacketEncoder();
      encoder.addString(projectName);
      encoder.addUInt64(projectId + 1);
      encoder.addUInt64(projectCustomerId);
      encoder.addString(defaultRecordingType);

      tcpClient.sendMessage(MessageType.SET_PERSISTENT_SETTINGS_REQUEST.ordinal(), encoder.getBuffer());
    } catch (UnsupportedEncodingException e) {
      errorDisplay.displayServerStateError(R.string.startRecordingError);
    }
  }

  public RemoteSystemInfo getRemoteSystemInfo() {
    return remoteSystemInfo;
  }

  public long getDefaultProjectCustomerId() {
    return defaultProjectCustomerId;
  }

  public void pingFromSensor(String url) {
    TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
    try {
      encoder.addString(url);
      tcpClient.sendMessage(MessageType.NET_PING_REQUEST.ordinal(), encoder.getBuffer());
    } catch (UnsupportedEncodingException e) {
      if (errorDisplay != null) {
        errorDisplay.displayImportantServerStateError(R.string.utf8EncodingError, false);
      }
      Log.e(
          getClass().getSimpleName(),
          "Unsupported encoding when trying to assemble a " + "NET_PING_REQUEST packet.",
          e);
    }
  }

  public void onUserIgnoredFirstImuTestWarning() {
    tcpClient.sendMessage(MessageType.IMU_TEST_USER_IGNORED_FIRST_WARNING.ordinal(), null);
  }

  public void onUserCanceledAfterImuTestWarning() {
    tcpClient.sendMessage(MessageType.IMU_TEST_USER_CANCELED_AFTER_WARNING.ordinal(), null);
  }

  public void addOnConnectionStateChangedListener(OnConnectionStateChangedListener l) {
    onConnectionStateChangedListenerLock.lock();
    try {
      onConnectionStateChangedListeners.add(l);
    } finally {
      onConnectionStateChangedListenerLock.unlock();
    }
  }

  public void removeOnConnectionStateChangedListener(OnConnectionStateChangedListener l) {
    onConnectionStateChangedListenerLock.lock();
    try {
      onConnectionStateChangedListeners.remove(l);
    } finally {
      onConnectionStateChangedListenerLock.unlock();
    }
  }

  public void addOnStatusUpdateListener(OnStatusUpdateListener l) {
    onStatusUpdateListenersLock.lock();
    try {
      onStatusUpdateListeners.add(l);
    } finally {
      onStatusUpdateListenersLock.unlock();
    }
  }

  public void removeOnStatusUpdateListener(OnStatusUpdateListener l) {
    onStatusUpdateListenersLock.lock();
    try {
      onStatusUpdateListeners.remove(l);
    } finally {
      onStatusUpdateListenersLock.unlock();
    }
  }

  public void addOnRecordingStateChangedListener(OnRecordingStateChangedListener l) {
    onRecordingStateChangedListenersLock.lock();
    try {
      onRecordingStateChangedListeners.add(l);
    } finally {
      onRecordingStateChangedListenersLock.unlock();
    }
  }

  public void removeOnRecordingStateChangedListener(OnRecordingStateChangedListener l) {
    onRecordingStateChangedListenersLock.lock();
    try {
      onRecordingStateChangedListeners.remove(l);
    } finally {
      onRecordingStateChangedListenersLock.unlock();
    }
  }

  public void addOnDiskInfoUpdatedListener(OnDiskInfoUpdatedListener l) {
    onDiskInfoUpdatedListenersLock.lock();
    try {
      onDiskInfoUpdatedListeners.add(l);
    } finally {
      onDiskInfoUpdatedListenersLock.unlock();
    }
  }

  public void removeOnDiskInfoUpdatedListener(OnDiskInfoUpdatedListener l) {
    onDiskInfoUpdatedListenersLock.lock();
    try {
      onDiskInfoUpdatedListeners.remove(l);
    } finally {
      onDiskInfoUpdatedListenersLock.unlock();
    }
  }

  public void addOnStatusLoadedListener(OnStatusLoadedListener l) {
    onStatusLoadedListenersLock.lock();
    try {
      onStatusLoadedListeners.add(l);
    } finally {
      onStatusLoadedListenersLock.unlock();
    }
  }

  public void removeOnStatusLoadedListener(OnStatusLoadedListener l) {
    onStatusLoadedListenersLock.lock();
    try {
      onStatusLoadedListeners.remove(l);
    } finally {
      onStatusLoadedListenersLock.unlock();
    }
  }

  public void addOnExternalStorageStatusChangedListener(OnExternalStorageStatusChangedListener l) {
    onExternalStorageStatusChangedListenersLock.lock();
    try {
      onExternalStorageStatusChangedListeners.add(l);
    } finally {
      onExternalStorageStatusChangedListenersLock.unlock();
    }
  }

  public void removeOnExternalStorageStatusChangedListener(
      OnExternalStorageStatusChangedListener l) {
    onExternalStorageStatusChangedListenersLock.lock();
    try {
      onExternalStorageStatusChangedListeners.remove(l);
    } finally {
      onExternalStorageStatusChangedListenersLock.unlock();
    }
  }

  public void addOnServerFilesystemChangedListener(OnServerFilesystemChangedListener l) {
    onServerFilesystemChangedListenersLock.lock();
    try {
      onServerFilesystemChangedListeners.add(l);
    } finally {
      onServerFilesystemChangedListenersLock.unlock();
    }
  }

  public void removeOnServerFilesystemChangedListener(OnServerFilesystemChangedListener l) {
    onServerFilesystemChangedListenersLock.lock();
    try {
      onServerFilesystemChangedListeners.remove(l);
    } finally {
      onServerFilesystemChangedListenersLock.unlock();
    }
  }

  public void addOnFilesBackedUpListener(OnFilesBackedUpListener l) {
    onFilesBackedUpListenersLock.lock();
    try {
      onFilesBackedUpListeners.add(l);
    } finally {
      onFilesBackedUpListenersLock.unlock();
    }
  }

  public void removeOnFilesBackedUpListener(OnFilesBackedUpListener l) {
    onFilesBackedUpListenersLock.lock();
    try {
      onFilesBackedUpListeners.remove(l);
    } finally {
      onFilesBackedUpListenersLock.unlock();
    }
  }

  public void addOnRosErrorsLoadedListener(OnRosErrorsLoadedListener l) {
    onRosErrorsLoadedListenersLock.lock();
    try {
      onRosErrorsLoadedListeners.add(l);
    } finally {
      onRosErrorsLoadedListenersLock.unlock();
    }
  }

  public void removeOnRosErrorsLoadedListener(OnRosErrorsLoadedListener l) {
    onRosErrorsLoadedListenersLock.lock();
    try {
      onRosErrorsLoadedListeners.remove(l);
    } finally {
      onRosErrorsLoadedListenersLock.unlock();
    }
  }

  public void addOnNetworkInterfacesLoadedListener(OnNetworkInterfacesLoadedListener l) {
    onNetworkInterfacesLoadedListenersLock.lock();
    try {
      onNetworkInterfacesLoadedListeners.add(l);
    } finally {
      onNetworkInterfacesLoadedListenersLock.unlock();
    }
  }

  public void removeOnNetworkInterfacesLoadedListener(OnNetworkInterfacesLoadedListener l) {
    onNetworkInterfacesLoadedListenersLock.lock();
    try {
      onNetworkInterfacesLoadedListeners.remove(l);
    } finally {
      onNetworkInterfacesLoadedListenersLock.unlock();
    }
  }

  public void addOnSyncStatusReceivedListener(OnSyncStatusReceivedListener l) {
    onSyncStatusReceivedListenersLock.lock();
    try {
      onSyncStatusReceivedListeners.add(l);
    } finally {
      onSyncStatusReceivedListenersLock.unlock();
    }
  }

  public void removeOnSyncStatusReceivedListener(OnSyncStatusReceivedListener l) {
    onSyncStatusReceivedListenersLock.lock();
    try {
      onSyncStatusReceivedListeners.remove(l);
    } finally {
      onSyncStatusReceivedListenersLock.unlock();
    }
  }

  public void addOnNetUsbDhcpResponseListener(OnNetUsbDhcpResponseListener l) {
    onNetUsbDhcpResponsesListenersLock.lock();
    try {
      onNetUsbDhcpResponsesListeners.add(l);
    } finally {
      onNetUsbDhcpResponsesListenersLock.unlock();
    }
  }

  public void removeOnNetUsbDhcpResponseListener(OnNetUsbDhcpResponseListener l) {
    onNetUsbDhcpResponsesListenersLock.lock();
    try {
      onNetUsbDhcpResponsesListeners.remove(l);
    } finally {
      onNetUsbDhcpResponsesListenersLock.unlock();
    }
  }

  public void addOnNetProxyResponseListener(OnNetProxyResponseListener l) {
    onNetProxyResponseListenersLock.lock();
    try {
      onNetProxyResponseListeners.add(l);
    } finally {
      onNetProxyResponseListenersLock.unlock();
    }
  }

  public void removeOnNetProxyResponseListener(OnNetProxyResponseListener l) {
    onNetProxyResponseListenersLock.lock();
    try {
      onNetProxyResponseListeners.remove(l);
    } finally {
      onNetProxyResponseListenersLock.unlock();
    }
  }

  public void addOnNetPingResponseListener(OnNetPingResponseListener l) {
    onNetPingResponseListenersLock.lock();
    try {
      onNetPingResponseListeners.add(l);
    } finally {
      onNetPingResponseListenersLock.unlock();
    }
  }

  public void removeOnNetPingResponseListener(OnNetPingResponseListener l) {
    onNetPingResponseListenersLock.lock();
    try {
      onNetPingResponseListeners.remove(l);
    } finally {
      onNetPingResponseListenersLock.unlock();
    }
  }

  public void addOnRecordingStartExpectedDuration(OnRecordingStartExpectedDuration l) {
    onRecordingStartExpectedDurationsLock.lock();
    try {
      onRecordingStartExpectedDurations.add(l);
    } finally {
      onRecordingStartExpectedDurationsLock.unlock();
    }
  }

  public void removeOnRecordingStartExpectedDuration(OnRecordingStartExpectedDuration l) {
    onRecordingStartExpectedDurationsLock.lock();
    try {
      onRecordingStartExpectedDurations.remove(l);
    } finally {
      onRecordingStartExpectedDurationsLock.unlock();
    }
  }

  public void addOnShutdownResponseListener(OnShutdownResponseListener l) {
    onShutdownResponseListenersLock.lock();
    try {
      onShutdownResponseListeners.add(l);
    } finally {
      onShutdownResponseListenersLock.unlock();
    }
  }

  public void removeOnShutdownResponseListener(OnShutdownResponseListener l) {
    onShutdownResponseListenersLock.lock();
    try {
      onShutdownResponseListeners.remove(l);
    } finally {
      onShutdownResponseListenersLock.unlock();
    }
  }

  public void addOnProjectDetailsChangedListener(OnProjectDetailsChangedListener l) {
    onProjectDetailsChangedListenersLock.lock();
    try {
      onProjectDetailsChangedListeners.add(l);
    } finally {
      onProjectDetailsChangedListenersLock.unlock();
    }
  }

  public void removeOnProjectDetailsChangedListener(OnProjectDetailsChangedListener l) {
    onProjectDetailsChangedListenersLock.lock();
    try {
      onProjectDetailsChangedListeners.remove(l);
    } finally {
      onProjectDetailsChangedListenersLock.unlock();
    }
  }

  public void addOnImuTestUpdateListener(OnImuTestUpdateListener l) {
    onImutestUpdateListenersLock.lock();
    try {
      onImuTestUpdateListeners.add(l);
    } finally {
      onImutestUpdateListenersLock.unlock();
    }
  }

  public void removeOnImuTestUpdateListener(OnImuTestUpdateListener l) {
    onImutestUpdateListenersLock.lock();
    try {
      onImuTestUpdateListeners.remove(l);
    } finally {
      onImutestUpdateListenersLock.unlock();
    }
  }

  public void addOnShutdownListener(OnShutdownListener l) {
    onShutdownListenersLock.lock();
    try {
      onShutdownListeners.add(l);
    } finally {
      onShutdownListenersLock.unlock();
    }
  }

  public void removeOnShutdownListener(OnShutdownListener l) {
    onShutdownListenersLock.lock();
    try {
      onShutdownListeners.remove(l);
    } finally {
      onShutdownListenersLock.unlock();
    }
  }

  public void addOnDefaultRecordingTypeChangedListener(OnDefaultRecordingTypeChangedListener l) {
    onDefaultRecordingTypeChangedListenersLock.lock();
    try {
      onDefaultRecordingTypeChangedListeners.add(l);
    } finally {
      onDefaultRecordingTypeChangedListenersLock.unlock();
    }
  }

  public void removeOnDefaultRecordingTypeChangedListener(OnDefaultRecordingTypeChangedListener l) {
    onDefaultRecordingTypeChangedListenersLock.lock();
    try {
      onDefaultRecordingTypeChangedListeners.remove(l);
    } finally {
      onDefaultRecordingTypeChangedListenersLock.unlock();
    }
  }

  // interface for activities that provide a server state
  public interface ServerStateProvider {

    ServerStateModel getServerStateModel();
  }

  public interface OnConnectionStateChangedListener {

    void onConnectionEstablished();

    /** called when the tcpClient disconnects or an error aborts the connecting process. */
    void onConnectionClosed();
  }

  // used to forward error messages to the view
  public interface ServerErrorDisplay {

    void displayServerStateError(int stringId, Object... args);

    /**
     * @param messageId the message to display
     * @param fatal if the error lead to a disconnect from the server
     */
    void displayImportantServerStateError(int messageId, boolean fatal, Object... args);
  }

  public interface OnStatusUpdateListener {

    void onStatusUpdate(ServerStateModel model);
  }

  public interface OnRecordingStateChangedListener {

    void onStartRecordingProgress(float progress);

    void onRecordingStarted(String filename, String recordingName);

    void onRecordingStopped();
  }

  public interface OnDiskInfoUpdatedListener {

    /**
     * called whenever the free or used disk space and consequently the time remaining are updated
     */
    void onDiskInfoUpdated();
  }

  public interface OnFilesLoadedListener {

    void onFilesLoaded(String files[], String raw_names[], long sizes[], boolean is_dir[]);
  }

  // is called once all status information about the server has been gathered
  // all information gotten from the model is only valid after this method was
  // called
  // The method is only called once in a normal lifecycle of the model.
  public interface OnStatusLoadedListener {

    void onStatusLoaded();
  }

  public interface OnExternalStorageStatusChangedListener {

    void onExternalStorageMounted();

    void onExternalStorageUnmounted();

    void onExternalStorageMountAborted();
  }

  /** Can be used to react to changes to the filesystem of the server initiated by the app. */
  public interface OnServerFilesystemChangedListener {

    void onServerFilesystemChanged();
  }

  public interface FormatExternalStorageInteractionHandler {

    // call confirmFormatExternalStorage() or cancelFormatExternalStorage()
    // to continue or abort
    // the formatting process. Do not call neither one, as that will lead to
    // the mounting daemon
    // on the server blocking
    void shouldStorageBeFormatted(String storageSize);

    void onDeviceFormatted(String outcome);
  }

  public interface OnFilesBackedUpListener {

    // called whenever a backup finishes
    void onFilesBackedUp(boolean backupSuccessful);
  }

  public interface OnRosErrorsLoadedListener {

    void onRosErrorsLoaded(String errors[]);
  }

  public interface OnNetworkInterfacesLoadedListener {

    void onNetworkInterfacesLoaded(String[] interfaces, String[] interfaceInfos);
  }

  public interface OnSyncStatusReceivedListener {

    void onSyncStatusReceived(
        boolean requestSuccessful,
        boolean isConnected,
        boolean isIndexing,
        long etaMs,
        long uploadSpeedKbps,
        short percentageComplete);
  }

  public interface OnNetUsbDhcpResponseListener {

    enum ServerStatus {
      NOT_RUNNING,
      RUNNING,
      NO_NET_USB_INTERFACES,
      SYSTEMD_ERROR
    }

    void onNetUsbDhcpServerStatus(ServerStatus status);

    void onNetUsbDhcpServerStarted(boolean startSuccessful);

    void onNetUsbDhcpClientStarted(boolean startSuccessful);
  }

  public interface OnNetProxyResponseListener {

    void onNetProxyUrlReceived(boolean success, String proxyUrl);

    void onNetProxyUrlSet(boolean success);
  }

  public interface OnNetPingResponseListener {

    void onPingResponseReceived(boolean pingSuccessful);
  }

  public interface OnRecordingStartExpectedDuration {

    void onRecordingStartExpectedDuration(float expectedSeconds);
  }

  public interface OnShutdownResponseListener {

    void onShutdownResponse(int status);

    void onShutdownBrokenBagfiles(String[] bagfiles);
  }

  public interface OnProjectDetailsChangedListener {

    public void onProjectNameChanged(String newName);

    public void onProjectIdChanged(long newId);

    public void onProjectCustomerIdChanged(long newCustomerId);
  }

  public interface OnImuTestUpdateListener {

    void onImuTestStarted();

    void onImuTestProgressUpdate(float progress);

    void onImuTestFinished(boolean success);
  }

  public interface OnShutdownListener {

    void onShutdownRequested();
  }

  public interface OnDefaultRecordingTypeChangedListener {
    void onDefaultRecordingTypeChanged();
  }

  public class RemoteSystemInfo {
    public String name;
    public int versionMajor;
    public int versionMinor;
    public int versionPatch;
    public String hardwareVersion;
    public String usbMacInfo;
    public String usbIpInfo;
  }
}
