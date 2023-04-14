package com.dotscene.dronecontroller;

import android.util.Log;

import com.dotscene.dronecontroller.ServerStateModel.OnFilesLoadedListener;

import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

/**
 * Created by Florian Kramer on 4/16/17.
 */

public class RemoteFilemanager implements OnFilesLoadedListener {

  class RemoteFile {
    String name = "";
    String raw_name = "";
    boolean isDir = false;
    String humanReadableSize = "";
    long size_bytes;
    String creationDate = "";
    boolean isBroken = false;
    ArrayList<String> original_raw_names = new ArrayList<>();
  }

  interface FileDisplayer {
    void onFilesChanged(RemoteFile[] remoteFiles);

    void displayFileDetails(String filepath);
  }

  private String rootPath = "/";

  private ServerStateModel serverStateModel;

  // The directory currently being displayed
  private String workingDirectory = "";
  private String idMappedWorkingDirectory = "";

  private FileDisplayer fileDisplayer;

  private ArrayList<RemoteFile> fileSelection;

  // Used to block the click listener for at least a second after the user requested entering a
  // subdirectory
  private long lastReloadRequestTime = 0;
  private static final long NEW_ROOT_CLICK_DISABLE_TIME = 1000;

  RemoteFile[] filesInDir = {};

  private Lock fileInDirLock = new ReentrantLock();

  public RemoteFilemanager(ServerStateModel serverStateModel) {
    this.serverStateModel = serverStateModel;
    fileSelection = new ArrayList<>();

  }

  public void setWorkingDirectory(String path) {
    if (!path.endsWith("/")) {
      path = path + '/';
    }
    workingDirectory = path;
    if (serverStateModel != null) {
      lastReloadRequestTime = System.currentTimeMillis();
      serverStateModel.loadFilesInDir(workingDirectory, this);
    }
  }

  RemoteFile[] getFilesInCurrentDir() {
    return filesInDir;
  }

  public void setRootPath(String newRootPath) {
    if (!rootPath.equals(newRootPath)) {
      if (!newRootPath.endsWith("/")) {
        newRootPath = newRootPath + "/";
      }
      rootPath = newRootPath;
      setWorkingDirectory(rootPath);
    }
  }

  public void changeDirectory(String dirname) {
    setWorkingDirectory(workingDirectory + dirname);
    idMappedWorkingDirectory += "/" + dirname;
    idMappedWorkingDirectory = idMappedWorkingDirectory.replaceAll("//", "/");
  }

  public void onFilePressed(RemoteFile f) {
    if (System.currentTimeMillis() - lastReloadRequestTime > NEW_ROOT_CLICK_DISABLE_TIME) {
      if (f.isDir) {
        setWorkingDirectory(workingDirectory + f.raw_name);
        idMappedWorkingDirectory += "/" + f.name;
        idMappedWorkingDirectory = idMappedWorkingDirectory.replaceAll("//", "/");
      }
    }
  }

  public void reload() {
    setWorkingDirectory(workingDirectory);
  }

  public String formatSize(long bytes) {
    return Long.toString(bytes / (1 << 20)) + "MiB";
  }

  @Override
  public void onFilesLoaded(String files[], String raw_names[], long sizes[], boolean is_dir[]) {
    lastReloadRequestTime = 0;
    // this can be called concurrently, leading to the number of lines in fileList and subsequently
    // the size onf filesInDir to potentially shrink, which can cause ArrayOutOfBounds Exceptions
    try {
      fileInDirLock.lock();
      // clear now invalid selection
      fileSelection.clear();

      ArrayList<RemoteFile> newFiles = new ArrayList<>();
      for (int i = 0; i < files.length; i += 1) {
        Log.d(getClass().getSimpleName(), "Got File: " + files[i]);
        if (ignoreFile(files[i], sizes[i], is_dir[i])) {
          Log.d(getClass().getSimpleName(), "Ignoring that file");
          continue;
        }
        RemoteFile f = new RemoteFile();
        f.size_bytes = sizes[i];
        f.humanReadableSize = formatSize(f.size_bytes);
        f.name = files[i];
        f.raw_name = raw_names[i];
        f.original_raw_names.add(f.raw_name);
        if (f.name.endsWith(".bag")) {
          // remove the .bag
          f.name = f.name.substring(0, f.name.length() - 4);
          // Deal with files marked as BROKEN
          if (f.name.endsWith(".BROKEN")) {
            f.isBroken = true;
            f.name = f.name.substring(0, f.name.length() - 7);
          } else {
            f.isBroken = false;
          }
          // extract the date
          String datePart = f.name.substring(0, "yyyy_MM_dd_HH-mm-ss".length());
          f.name = f.name.substring("yyyy_MM_dd_HH-mm-ss_".length());

          DateFormat format = new SimpleDateFormat("yyyy_MM_dd_HH-mm-ss");
          DateFormat dispayFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
          try {
            Date d = format.parse(datePart);
            f.creationDate = dispayFormat.format(d);
          } catch (ParseException e) {
            e.printStackTrace();
          }
        } else if (f.name.endsWith(".bag.active")) {
          // remove the .bag
          f.name = f.name.substring(0, f.name.length() - 11);
          // extract the date
          String datePart = f.name.substring(0, "yyyy_MM_dd_HH-mm-ss".length());
          f.name = f.name.substring("yyyy_MM_dd_HH-mm-ss_".length());
          f.isBroken = true;

          DateFormat format = new SimpleDateFormat("yyyy_MM_dd_HH-mm-ss");
          DateFormat dispayFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
          try {
            Date d = format.parse(datePart);
            f.creationDate = dispayFormat.format(d);
          } catch (ParseException e) {
            e.printStackTrace();
          }
        }
        f.isDir = is_dir[i];
        newFiles.add(f);
      }
      mergeBagFileParts(newFiles);
      filesInDir = new RemoteFile[newFiles.size()];
      filesInDir = newFiles.toArray(filesInDir);
      // Sort the files lexicographically
      Arrays.sort(filesInDir, new Comparator<RemoteFile>() {
        @Override
        public int compare(RemoteFile o1, RemoteFile o2) {
          return o1.name.compareTo(o2.name);
        }
      });
      if (fileDisplayer != null) {
        fileDisplayer.onFilesChanged(filesInDir);
      }
    } finally {
      fileInDirLock.unlock();
    }
  }

  void mergeBagFileParts(ArrayList<RemoteFile> files) {
    Log.d(getClass().getSimpleName(), "Considering " + files.size() + " files for merging");
    HashMap<String, RemoteFile> merged = new HashMap<>();
    for (int i = 0; i < files.size(); ++i) {
      RemoteFile f = files.get(i);
      Log.d(getClass().getSimpleName(), "Considering if " + f.raw_name + " is a bagfile.");
      if (!f.isDir && f.raw_name.contains(".bag")) {
        Log.d(getClass().getSimpleName(), "Considering bag " + f.raw_name + " for merging.");
        files.remove(i);
        --i;

        int idx = f.name.length() - 1;
        // find the underscore before the bag index
        int j;
        for (j = idx; j >= 0 && f.name.charAt(j) != '_'; j--);
        if (j < 0) {
          j = 0;
        }
        String name_remainder = f.name.substring(0, j);
        String merged_key = f.creationDate + name_remainder;

        Log.d(getClass().getSimpleName(), "Name remainder: " + name_remainder);
        if (merged.containsKey(merged_key)) {
          RemoteFile m = merged.get(merged_key);
          m.size_bytes += f.size_bytes;
          m.humanReadableSize = formatSize(m.size_bytes);
          m.original_raw_names.add(f.raw_name);
        } else {
          f.name = name_remainder;
          merged.put(merged_key, f);
        }
      }
    }
    for (RemoteFile s : merged.values()) {
      files.add(s);
    }
  }

  boolean ignoreFile(String name, long size, boolean is_dir) {
    if (name.startsWith(".")) {
      return true;
    }
    if (is_dir) {
      return false;
    }
    if (name.contains(".imucheck.bag")) {
      return true;
    }
    if (serverStateModel.recordingState == ServerStateModel.RecordingState.RECORDING) {
      // Hide the current recording
      if (name.contains("_" + serverStateModel.getRecordingName()) && name.contains("_unchecked")) {
        return true;
      }
    }
    return false;
  }

  /**
   * changes the filepath to the parent directory of the directory currently pointed at by
   * filepath
   */
  public void goBack() {
    if (!workingDirectory.equals(rootPath)) {
      workingDirectory = workingDirectory.substring(0, workingDirectory.lastIndexOf('/', workingDirectory.length() - 2) + 1);
      idMappedWorkingDirectory = idMappedWorkingDirectory.substring(0, idMappedWorkingDirectory.lastIndexOf('/', idMappedWorkingDirectory.length() - 2) + 1);
      idMappedWorkingDirectory = idMappedWorkingDirectory.replaceAll("//", "/");
      if (serverStateModel != null) {
        serverStateModel.loadFilesInDir(workingDirectory, this);
      }
    }
  }

  /**
   * @return The current working directory relative to the root path
   */
  public String getRelativeWorkingDirectory() {
    return workingDirectory.substring(rootPath.length() - 1);
  }

  public String getIdMappedRelativeWorkingDirectory() {
    return idMappedWorkingDirectory.substring(rootPath.length() - 1);
  }

  public void setServerStateModel(ServerStateModel s) {
    serverStateModel = s;
    if (serverStateModel != null) {
      reload();
    }
  }

  public void onFileCheckedChanged(RemoteFile file, boolean isChecked) {
    if (isChecked) {
      // check if the file has been added already to ensure that no problems occur while modifying
      // the servers file system
      if (!fileSelection.contains(file)) {
        fileSelection.add(file);
      }
    } else {
      fileSelection.removeAll(Collections.singleton(file));
    }
  }

  public void clearSelection() {
    fileSelection.clear();
  }

  public void deleteSelection() {
    deleteFiles(fileSelection.toArray(new RemoteFile[]{}));
  }

  public void deleteFiles(RemoteFile files[]) {
    ArrayList<String> filelist = new ArrayList<>();
    for (int i = 0; i < files.length; i++) {
      for (String rn : files[i].original_raw_names) {
        filelist.add(workingDirectory + rn);
      }
    }
    serverStateModel.deleteFiles(filelist.toArray(new String[0]));
  }

  public boolean isSelectionEmtpy() {
    return fileSelection.isEmpty();
  }

  public void setFileDisplayer(FileDisplayer fileDisplayer) {
    this.fileDisplayer = fileDisplayer;
  }
}
