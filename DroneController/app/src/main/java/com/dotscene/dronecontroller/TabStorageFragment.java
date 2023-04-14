package com.dotscene.dronecontroller;

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import com.dotscene.dronecontroller.ServerStateModel.OnStatusLoadedListener;
import com.dotscene.dronecontroller.ServerStateModel.ServerStateProvider;

/**
 * Created by Florian Kramer on 4/10/17.
 */


public class TabStorageFragment extends Fragment implements OnCheckedChangeListener,
    OnClickListener, OnStatusLoadedListener {

  StorageDetailsFragment storageDetailsFragment;
  SystemControlFragment systemControlFragment;
  FileManagerFragment internalFileManagerFragment;
  SyncStatusFragment syncStatusFragment;

  ServerStateModel serverStateModel;


  public TabStorageFragment() {

  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.fragment_tab_storage, container, false);

    Button deleteButton = (Button) v.findViewById(R.id.deleteButton);
    deleteButton.setOnClickListener(this);

    return v;
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    FragmentManager manager = getChildFragmentManager();
    if (savedInstanceState == null) {
      // create the fragments
      syncStatusFragment = new SyncStatusFragment();
      storageDetailsFragment = new StorageDetailsFragment();
      systemControlFragment = new SystemControlFragment();
      internalFileManagerFragment = new FileManagerFragment();

      // add the fragments to the grid view
      manager.beginTransaction()
          .replace(R.id.syncStatusFragment, syncStatusFragment).commit();
      manager.beginTransaction()
          .replace(R.id.storageDetailsFragment, storageDetailsFragment).commit();
      manager.beginTransaction()
          .replace(R.id.systemControlFragment, systemControlFragment).commit();
      manager.beginTransaction()
          .replace(R.id.internalFileManagerFragment, internalFileManagerFragment).commit();
    } else {
      storageDetailsFragment = (StorageDetailsFragment) manager
          .findFragmentById(R.id.storageDetailsFragment);
      systemControlFragment = (SystemControlFragment) manager
          .findFragmentById(R.id.systemControlFragment);
      internalFileManagerFragment = (FileManagerFragment) manager
          .findFragmentById(R.id.internalFileManagerFragment);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    serverStateModel = ((ServerStateProvider) getActivity()).getServerStateModel();
    serverStateModel.addOnStatusLoadedListener(this);
  }

  @Override
  public void onPause() {
    super.onPause();
    serverStateModel.removeOnStatusLoadedListener(this);
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.deleteButton:
        if (internalFileManagerFragment.isSelectionEmpty()) {
          Toast.makeText(getContext(), R.string.noFilesSelected, Toast.LENGTH_LONG).show();
        } else {
          AlertDialog.Builder deleteBuilder = new AlertDialog.Builder(getActivity());
          deleteBuilder.setMessage(R.string.confirm_delete);
          deleteBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

              internalFileManagerFragment.deleteSelection();

            }
          });
          deleteBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
          });
          AlertDialog deleteDialog = deleteBuilder.create();
          deleteDialog.show();
        }
        break;

    }
  }


  @Override
  public void onStatusLoaded() {


  }
}
