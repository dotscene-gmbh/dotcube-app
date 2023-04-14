package com.dotscene.dronecontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.JsonReader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dotscene.dronecontroller.ServerStateModel.OnDefaultRecordingTypeChangedListener;
import com.dotscene.dronecontroller.ServerStateModel.OnStatusLoadedListener;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.Inet4Address;

public class MissionFragment extends Fragment implements OnStatusLoadedListener, ServerStateModel.OnProjectDetailsChangedListener,
    OnDefaultRecordingTypeChangedListener {

    ServerStateModel serverStateModel = null;

    public static final int QRCODE_REQUEST_CODE = 9147;

    TextView missionNameText;
    Button setMissionButton;


    TextView defaultRecordingTypeText;
    Button defaultRecordingTypeButton;


    private AlertDialog setMissionDialog;
    private AlertDialog setMissionManualDialog;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_mission, container, false);
        missionNameText = v.findViewById(R.id.textMissionName);
        setMissionButton = v.findViewById(R.id.setMissionButton);
        setMissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.setMissionDialog);

                LinearLayout layout = new LinearLayout(getContext());

                Button qrCodeButton = new Button(getContext());
                qrCodeButton.setText(R.string.setMissionQrCode);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layoutParams.weight = 1;
                layout.addView(qrCodeButton, layoutParams);
                qrCodeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setMissionDialog.dismiss();
                        Intent getQrCode = new Intent(getActivity(), QrCodeActivity.class);
                        startActivityForResult(getQrCode, QRCODE_REQUEST_CODE);
                    }
                });

                Button manualButton = new Button(getContext());
                manualButton.setText(R.string.setMissionManual);
                layout.addView(manualButton, layoutParams);
                manualButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setMissionDialog.dismiss();

                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setTitle(R.string.setMissionManual);

                        final EditText mname = new EditText(getContext());
                        mname.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {

                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                if (ServerStateModel.PATTERN_FILENAME.matcher(s.toString()).find()) {
                                    mname.setError(getResources().getString(R.string.setMissionNameInvalid));
                                    setMissionManualDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                                } else if (s.length() == 0) {
                                    setMissionManualDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                                } else {
                                    mname.setError(null);
                                    setMissionManualDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                }
                            }
                        });
                        mname.setHint(R.string.setMissionNameHint);
                        builder.setView(mname);

                        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                serverStateModel.setProjectName(mname.getText().toString());
                                serverStateModel.setProjectCustomerId(serverStateModel.getDefaultProjectCustomerId());
                                serverStateModel.setProjectId(-1);

                                // Update the mission name in the ui
                                missionNameText.setText(mname.getText().toString());
                            }
                        });
                        builder.setNegativeButton(R.string.cancel, null);

                        setMissionManualDialog = builder.create();
                        setMissionManualDialog.show();
                    }
                });

                builder.setView(layout);

                setMissionDialog = builder.create();
                setMissionDialog.show();
            }
        });

        defaultRecordingTypeText = v.findViewById(R.id.textDefaultRecordingType);
        defaultRecordingTypeButton = v.findViewById(R.id.setDefaultRecordingType);
        defaultRecordingTypeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.setDefaultRecordingTypeDialog);

                LinearLayout layout = new LinearLayout(getContext());

                final ArrayAdapter<String> recordingTypeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1);
                recordingTypeAdapter.add("indoor");
                recordingTypeAdapter.add("outdoor/combined");
                recordingTypeAdapter.add("flight");

                final Spinner recordingTypeSpinner = new Spinner(getContext());
                recordingTypeSpinner.setAdapter(recordingTypeAdapter);
                recordingTypeSpinner.setSelection(0);
                String currentDefault = serverStateModel.getDefaultRecordingType();
                if (currentDefault.equals(ServerStateModel.RECORDING_TYPE_INDOOR)) {
                    recordingTypeSpinner.setSelection(0);
                } else if (currentDefault.equals(ServerStateModel.RECORDING_TYPE_OUTDOOR)) {
                    recordingTypeSpinner.setSelection(1);
                } else if (currentDefault.equals(ServerStateModel.RECORDING_TYPE_FLIGHT)) {
                    recordingTypeSpinner.setSelection(2);
                }

                layout.addView(recordingTypeSpinner);

                builder.setView(layout);

                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        serverStateModel.setDefaultRecordingType(recordingTypeAdapter.getItem(recordingTypeSpinner.getSelectedItemPosition()));
                    }
                });

                AlertDialog d = builder.create();
                d.show();
            }
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        final Activity activity = getActivity();
        if (activity == null) {
            Log.e(getClass().getSimpleName(), "Couldn't access the server state model update because it was not associated with an activity.");
        } else {
            serverStateModel = ((ServerStateModel.ServerStateProvider) getActivity()).getServerStateModel();
            // register the listener
            serverStateModel.addOnStatusLoadedListener(this);
            serverStateModel.addOnProjectDetailsChangedListener(this);
            serverStateModel.addOnDefaultRecordingTypeChangedListener(this);
            // Init ui based upon the current server model. If the model hasn't loaded yet the view
            // will be reinitialized with valid data once the model has loaded.
            onStatusLoaded();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // deregister the listener
        serverStateModel.removeOnStatusLoadedListener(this);
        serverStateModel.removeOnProjectDetailsChangedListener(this);
        serverStateModel.removeOnDefaultRecordingTypeChangedListener(this);
    }

    @Override
    public void onStatusLoaded() {
        missionNameText.setText(serverStateModel.getProjectName());
        defaultRecordingTypeText.setText(serverStateModel.getDefaultRecordingType());
    }

    @Override
    public void onProjectNameChanged(final String newName) {
        Activity a = getActivity();
        if ( a != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (missionNameText != null) {
                        missionNameText.setText(newName);
                    }
                }
            });
        }
    }

    @Override
    public void onProjectIdChanged(long newId) {

    }

    @Override
    public void onProjectCustomerIdChanged(long newCustomerId) {

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)  {
        if (requestCode == QRCODE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                onQrCodeRead(resultCode, data);
            }
        }
    }

    public void onQrCodeRead(int resultCode, Intent data) {
        if (data.hasExtra(QrCodeActivity.RES_RESTART_KEY)) {
            Intent getQrCode = new Intent(getActivity(), QrCodeActivity.class);
            startActivityForResult(getQrCode, QRCODE_REQUEST_CODE);
            return;
        }
        if (resultCode != Activity.RESULT_OK || ! data.hasExtra(QrCodeActivity.RES_DATA_KEY)) {
            Log.e(MissionFragment.class.getSimpleName(), "Got a qr code with status " + resultCode + " and existing data: " + data.hasExtra(QrCodeActivity.RES_DATA_KEY));
            return;
        }
        String rawQrCode = data.getStringExtra(QrCodeActivity.RES_DATA_KEY);
        try {
            JSONObject json = new JSONObject(rawQrCode);
            serverStateModel.setProjectName(json.getString("name"));
            if (json.isNull("id")) {
                serverStateModel.setProjectId(-1);
            } else {
                Object id = json.get("id");
                if (id instanceof String) {
                    serverStateModel.setProjectId(Long.parseLong((String)id));
                } else if (id instanceof Integer) {
                    serverStateModel.setProjectId((Integer)id);
                } else if (id instanceof  Long) {
                    serverStateModel.setProjectId((Long)id);
                } else {
                    Log.w(getClass().getSimpleName(), "Unknown type of id");
                    serverStateModel.setProjectId(-1);
                }
            }
            if (json.isNull("customer_id")) {
                serverStateModel.setProjectCustomerId(-1);
            } else {
                Object id = json.get("customer_id");
                if (id instanceof String) {
                    serverStateModel.setProjectCustomerId(Long.parseLong((String)id));
                } else if (id instanceof Integer) {
                    serverStateModel.setProjectCustomerId((Integer)id);
                } else if (id instanceof  Long) {
                    serverStateModel.setProjectCustomerId((Long)id);
                } else {
                    Log.w(getClass().getSimpleName(), "Unknown type of customer id");
                    serverStateModel.setProjectCustomerId(-1);
                }
            }
            Activity a = getActivity();
            if (a != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), R.string.setMissionQrCodeSuccess, Toast.LENGTH_LONG).show();
                    }
                });
            }
        } catch (JSONException e) {
            // TODO inform the user that this was not a properly formatted qr code
            e.printStackTrace();
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity(), R.string.setMissionQrCodeInvalid, Toast.LENGTH_LONG).show();
                }
            });
        }

    }

    @Override
    public void onDefaultRecordingTypeChanged() {
        Activity a = getActivity();
        if ( a != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                if (defaultRecordingTypeText != null) {
                    defaultRecordingTypeText.setText(serverStateModel.getDefaultRecordingType());
                }
                }
            });
        }
    }
}
