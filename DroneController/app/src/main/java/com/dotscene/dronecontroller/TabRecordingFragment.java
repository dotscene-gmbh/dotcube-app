package com.dotscene.dronecontroller;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by Florian Kramer on 4/10/17.
 */

public class TabRecordingFragment extends Fragment {

    SystemStateFragment systemStateFragment;
    RecordingControlFragment recordingControlFragment;
    RecordingDetailsFragment recordingDetailsFragment;
    SystemControlFragment systemControlFragment;
    MissionFragment missionFragment;

    ServerStateModel serverStateModel = null;

    public TabRecordingFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_tab_recording, container, false);

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentManager manager = getChildFragmentManager();
        if (savedInstanceState == null) {
            // create the fragments
            systemStateFragment = new SystemStateFragment();
            recordingControlFragment = new RecordingControlFragment();
            recordingDetailsFragment = new RecordingDetailsFragment();
            systemControlFragment = new SystemControlFragment();
            missionFragment = new MissionFragment();

            // add the fragments to the grid view
            manager.beginTransaction()
                    .replace(R.id.systemStateFragment, systemStateFragment).commit();
            manager.beginTransaction()
                    .replace(R.id.recordingControlFragment, recordingControlFragment).commit();
            manager.beginTransaction()
                    .replace(R.id.missionFragment, missionFragment).commit();
            manager.beginTransaction()
                    .replace(R.id.recordingDetailsFragment, recordingDetailsFragment).commit();
            manager.beginTransaction()
                    .replace(R.id.systemControlFragment, systemControlFragment).commit();
        } else {
            systemStateFragment = (SystemStateFragment) manager
                    .findFragmentById(R.id.systemStateFragment);
            recordingControlFragment = (RecordingControlFragment) manager
                    .findFragmentById(R.id.recordingControlFragment);
            recordingDetailsFragment = (RecordingDetailsFragment) manager
                    .findFragmentById(R.id.recordingDetailsFragment);
            systemControlFragment = (SystemControlFragment) manager
                    .findFragmentById(R.id.systemControlFragment);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        serverStateModel = ((ServerStateModel.ServerStateProvider) getActivity()).getServerStateModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
