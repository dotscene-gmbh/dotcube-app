package com.dotscene.dronecontroller;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Created by Florian Kramer on 8/17/17.
 *
 * This class can be used to upload files to the sensor
 */
public class FileUploader {

  private TCPClient client;

  public boolean connect() {
    try {
      client = new TCPClient("", 0);
    } catch (Exception e) {
      Log.e(getClass().getSimpleName(), "Error while connecting to server: " + e.getMessage());
      return false;
    }
    return true;
  }

  public void disconnect() {
    client.disconnect();
    client = null;
  }

  public boolean sendFile(File localFile, String remoteFile) {
    if (!localFile.exists() || ! localFile.isFile()) {
      return false;
    }
    // TODO(florian): if this is still a feature we want reimplement the actual upload using
    // the new tcp based messaging
    return true;
  }

}
