package com.dotscene.dronecontroller;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dotscene.dronecontroller.RemoteFilemanager.FileDisplayer;
import com.dotscene.dronecontroller.RemoteFilemanager.RemoteFile;
import com.dotscene.dronecontroller.ServerStateModel.OnRecordingStateChangedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnServerFilesystemChangedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnStatusLoadedListener;
import com.dotscene.dronecontroller.ServerStateModel.ServerStateProvider;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.HashSet;

/**
 * Created by Florian Kramer on 4/16/17.
 */

public class FileManagerFragment extends Fragment implements OnStatusLoadedListener, FileDisplayer,
        OnClickListener, OnRecordingStateChangedListener, OnServerFilesystemChangedListener {

  /**
   * A helper class that handles a list of RemoteFiles. This adapter can be used for RecyclerViews.
   */
  public static class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {

    public static class FileViewHolder extends RecyclerView.ViewHolder {
      public LinearLayout rootView;

      public FileViewHolder(LinearLayout v) {
        super(v);
        rootView = v;
      }
    }


    private Uri folderIconResourceURI = null;
    private RemoteFile files[];
    private boolean isSelected[];
    private FileManagerFragment fileManagerFragment = null;

    public FileListAdapter(RemoteFile files[]) {
      this.files = files;
      if (files != null) {
        isSelected = new boolean[files.length];
      }
    }

    public void setFiles(RemoteFile files[]) {
      this.files = files;
      notifyDataSetChanged();
      if (files != null) {
        isSelected = new boolean[files.length];
      }
    }

    public void setFileManager(FileManagerFragment fileManagerFragment) {
      this.fileManagerFragment = fileManagerFragment;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      if (folderIconResourceURI == null) {
        Resources resources = parent.getContext().getResources();
        folderIconResourceURI = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(R.raw.folder_icon))
                .appendPath(resources.getResourceTypeName(R.raw.folder_icon))
                .appendPath(resources.getResourceEntryName(R.raw.folder_icon))
                .build();
      }
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      LinearLayout itemView = (LinearLayout) inflater.inflate(R.layout.file_manager_item, null);
      RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      itemView.setLayoutParams(lp);
      return new FileViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final FileViewHolder fileViewHolder, int position) {
      // Find all the required views
      CheckBox checkBox = fileViewHolder.rootView.findViewById(R.id.fileManagerItemCheckbox);
      ImageView dirView = fileViewHolder.rootView.findViewById(R.id.fileManagerItemImage);
      TextView nameLabel = fileViewHolder.rootView.findViewById(R.id.fileManagerItemName);
      TextView dateLabel = fileViewHolder.rootView.findViewById(R.id.fileManagerItemDate);
      TextView sizeLabel = fileViewHolder.rootView.findViewById(R.id.fileManagerItemSize);

      final RemoteFile file = files[position];

      // Configure the views
      checkBox.setOnCheckedChangeListener(null);
      checkBox.setChecked(isSelected[position]);
      dirView.setVisibility(file.isDir ? View.VISIBLE : View.INVISIBLE);

      SpannableStringBuilder nameBuilder = new SpannableStringBuilder();
      nameBuilder.append(file.name);
      if (file.isBroken) {
        nameBuilder.append(" ");
        String b = "(BROKEN)";
        int s = nameBuilder.length();
        nameBuilder.append(b);
        nameBuilder.setSpan(new ForegroundColorSpan(0xFFFF0000), s, s + b.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      nameLabel.setText(nameBuilder);

      if (!file.isDir && file.humanReadableSize.length() > 0) {
        sizeLabel.setText(file.humanReadableSize);
        sizeLabel.setVisibility(View.VISIBLE);
      } else {
        sizeLabel.setVisibility(View.GONE);
      }
      if (file.creationDate.length() > 0) {
        dateLabel.setText(file.creationDate);
        dateLabel.setVisibility(View.VISIBLE);
      } else {
        dateLabel.setVisibility(View.GONE);
      }

      checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          isSelected[fileViewHolder.getAdapterPosition()] = isChecked;
          if (fileManagerFragment != null) {
            fileManagerFragment.onCheckedStatuschanged(files[fileViewHolder.getAdapterPosition()]);
          }
        }
      });

      nameLabel.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          if (fileManagerFragment != null) {
            fileManagerFragment.onFileClicked(files[fileViewHolder.getAdapterPosition()], fileViewHolder.getAdapterPosition());
          }
        }
      });

      dirView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          if (fileManagerFragment != null) {
            fileManagerFragment.onFileClicked(files[fileViewHolder.getAdapterPosition()], fileViewHolder.getAdapterPosition());
          }
        }
      });
    }

    @Override
    public int getItemCount() {
      return files.length;
    }

    public boolean[] getSelectedFiles() {
      return isSelected;
    }
  }

  private static final String KEY_SHOW_CHECKBOXES = "com.dotscene.dronecontroller.showFilemanagerCheckboxes";
  private static final HashSet<String> hidden_files = new HashSet<>();

  static {
    hidden_files.add("lost+found");
  }


  private ServerStateModel serverStateModel;
  private RemoteFilemanager remoteFilemanager;


  private boolean showCheckboxes = true;

  private Timer updateFilesTimer = null;

  //private LinearLayout filesListLayout;


  private RemoteFile files[];
  private FileListAdapter fileListAdapter = new FileListAdapter(new RemoteFile[]{});

  public FileManagerFragment() {
    super();
    remoteFilemanager = new RemoteFilemanager(null);
  }

  @Override
  public void onAttach(Context c) {
    super.onAttach(c);
    remoteFilemanager.setServerStateModel(((ServerStateProvider) getActivity()).getServerStateModel());
    remoteFilemanager.setFileDisplayer(this);
    remoteFilemanager.setRootPath("/");
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      showCheckboxes = savedInstanceState.getBoolean(KEY_SHOW_CHECKBOXES);
    }


    View v = inflater.inflate(R.layout.fragment_file_manager, container, false);

    // filesListLayout = (LinearLayout) v.findViewById(R.id.internalFilesList);
    RecyclerView filesListView = v.findViewById(R.id.internalFilesList);
    filesListView.setAdapter(fileListAdapter);
    filesListView.setLayoutManager(new LinearLayoutManager(getContext()));
    fileListAdapter.setFileManager(this);

    ImageView backImage = v.findViewById(R.id.internalBackButton);
    backImage.setOnClickListener(this);
    return v;
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(KEY_SHOW_CHECKBOXES, showCheckboxes);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

  }

  @Override
  public void onResume() {
    super.onResume();
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't access the server state model update because it was not associated with an activity.");
    } else {
      serverStateModel = ((ServerStateProvider) activity).getServerStateModel();
      remoteFilemanager.setServerStateModel(serverStateModel);
      serverStateModel.addOnStatusLoadedListener(this);
      serverStateModel.addOnRecordingStateChangedListener(this);
      serverStateModel.addOnServerFilesystemChangedListener(this);
      // Init ui based upon the current server model. If the model hasn't loaded yet the view
      // will be reinitialized with valid data once the model has loaded.
      onStatusLoaded();
      if (isSelectionEmpty()) {
        startUpdateFilesTimer();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    stopUpdateFilesTimer();
    serverStateModel.removeOnStatusLoadedListener(this);
    serverStateModel.removeOnRecordingStateChangedListener(this);
    serverStateModel.removeOnServerFilesystemChangedListener(this);
  }

  private void stopUpdateFilesTimer() {
    if (updateFilesTimer != null) {
      updateFilesTimer.cancel();
      updateFilesTimer = null;
    }
  }

  private void startUpdateFilesTimer() {
    if (updateFilesTimer != null) {
      updateFilesTimer.cancel();
      updateFilesTimer = null;
    }
    updateFilesTimer = new Timer();
    updateFilesTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        remoteFilemanager.reload();
      }
    }, 0, 15000);
  }

  public void reload() {
    remoteFilemanager.reload();
  }

  @Override
  public void onFilesChanged(final RemoteFile[] remoteFiles) {
    final Activity activity = getActivity();
    if (activity == null) {
      Log.e(getClass().getSimpleName(), "Couldn't process the on files changed event because it was not associated with an activity.");
      return;
    }
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (getView() != null) {
          // Filter out hidden files
          ArrayList<RemoteFile> tmp = new ArrayList<>();
          for (RemoteFile f : remoteFiles) {
            if (!f.name.startsWith(".") && !hidden_files.contains(f.name)) {
              tmp.add(f);
            }
          }

          files = new RemoteFile[tmp.size()];
          tmp.toArray(files);
          TextView breadcrumbs = getView().findViewById(R.id.internalBreadcrumbs);
          breadcrumbs.setText(remoteFilemanager.getIdMappedRelativeWorkingDirectory());
          updateFilesList();
        }
      }
    });
  }

  @Override
  public void displayFileDetails(String filepath) {
  }

  @Override
  public void onStatusLoaded() {
    remoteFilemanager.reload();
  }

  @Override
  public void onClick(View v) {
    // the internalBackButton id belongs to the frame layout containing the back button, not
    // the button itself
    if (v.getId() == R.id.internalBackButton) {
      remoteFilemanager.goBack();
    }
  }

  public void onFileClicked(RemoteFile f, int pos) {
    remoteFilemanager.onFilePressed(f);
  }

  public void setCheckboxesVisible(boolean showCheckboxes) {
    this.showCheckboxes = showCheckboxes;
  }

  public void setRootPath(String rootPath) {
    remoteFilemanager.setRootPath(rootPath);
  }

  @Override
  public void onStartRecordingProgress(float progress) {

  }

  @Override
  public void onRecordingStarted(String filename, String recordingName) {

  }

  public void onCheckedStatuschanged(RemoteFile f) {
    if (!isSelectionEmpty()) {
      // Ensure the update files timer does not trigger a reload while files are selected (this would lead to the checkboxes being reset).
      stopUpdateFilesTimer();
    } else {
      startUpdateFilesTimer();
    }
  }

  @Override
  public void onRecordingStopped() {
    // a new file and folder may have been created during the recording
    remoteFilemanager.reload();
  }

  public void deleteSelection() {
    boolean isSelected[] = fileListAdapter.getSelectedFiles();
    ArrayList<RemoteFile> filesToDelete = new ArrayList<>();
    for (int i = 0; i < isSelected.length; i++) {
      if (isSelected[i]) {
        Log.d(getClass().getSimpleName(), "Deleting: " + files[i].name);
        filesToDelete.add(files[i]);
      }
    }
    Log.d(getClass().getSimpleName(), "Deleting " + filesToDelete.size() + " files");
    remoteFilemanager.deleteFiles(filesToDelete.toArray(new RemoteFile[]{}));
  }

  public boolean isSelectionEmpty() {
    boolean isSelected[] = fileListAdapter.getSelectedFiles();
    for (boolean b : isSelected) {
      if (b) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void onServerFilesystemChanged() {
    Log.d(getClass().getSimpleName(), "on Filesystem changed");
    reload();
  }

  private void updateFilesList() {
    fileListAdapter.setFiles(files);
  }
}
