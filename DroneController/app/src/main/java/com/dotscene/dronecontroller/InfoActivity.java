package com.dotscene.dronecontroller;

import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.dotscene.dronecontroller.BuildConfig;
import java.lang.reflect.Array;
import java.util.ArrayList;

public class InfoActivity extends AppCompatActivity {

  private class InfoItem {
    String key;
    String value;

    InfoItem() {}

    InfoItem(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  private class InfoAdapter implements ListAdapter {

    private ArrayList<DataSetObserver> observers = new ArrayList<>();
    private ArrayList<InfoItem> items = new ArrayList<>();

    LayoutInflater inflater;

    public InfoAdapter(LayoutInflater inflater) {
      this.inflater = inflater;
    }

    public void addItem(InfoItem item) {
      items.add(item);
      onChanged();
    }

    public void removeItem(InfoItem item) {
      items.remove(item);
      onChanged();
    }

    public void removeItem(int position) {
      items.remove(position);
      onChanged();
    }

    public void clear() {
      items.clear();
      onChanged();
    }

    private void onChanged() {
      for (DataSetObserver o : observers) {
        o.onChanged();
      }
    }

    @Override
    public boolean areAllItemsEnabled() {
      // The adapter does not support separator elements, so all elements are always enabled
      return true;
    }

    @Override
    public boolean isEnabled(int position) {
      // The adapter does not support separator elements, so all elements are always enabled
      return true;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
      observers.add(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
      observers.remove(observer);
    }

    @Override
    public int getCount() {
      return items.size();
    }

    @Override
    public Object getItem(int position) {
      return items.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      InfoItem info = items.get(position);

      // Try to reuse an old view
      View root = convertView;
      if (root == null) {
        root = inflater.inflate(R.layout.info_item, null);
      }

      TextView keyView = root.findViewById(R.id.infoItemLine1);
      TextView valueView = root.findViewById(R.id.infoItemLine2);
      keyView.setText(info.key);
      valueView.setText(info.value);
      return root;
    }

    @Override
    public int getItemViewType(int position) {
      return 0;
    }

    @Override
    public int getViewTypeCount() {
      return 1;
    }

    @Override
    public boolean isEmpty() {
      return items.isEmpty();
    }

  }

  ServerStateModel serverState;
  InfoAdapter adapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_info);

    if (!BuildConfig.MOCK_NETWORK) {
      // Use the global instance
      serverState = ServerStateModel.SINGLETON;
    } else {
      serverState = new ServerStateModelMock();
    }

    adapter = new InfoAdapter(getLayoutInflater());
    updateInfo();

    ListView infoList = findViewById(R.id.infoList);
    infoList.setAdapter(adapter);
  }

  private void updateInfo() {
    ServerStateModel.RemoteSystemInfo info = serverState.getRemoteSystemInfo();

    String version = info.versionMajor + "." + info.versionMinor + "." + info.versionPatch;

    adapter.clear();
    adapter.addItem(new InfoItem(getResources().getString(R.string.infoName), info.name));
    adapter.addItem(new InfoItem(getResources().getString(R.string.infoAppVersion), BuildConfig.VERSION_NAME));
    adapter.addItem(new InfoItem(getResources().getString(R.string.infoDotcubeOSVersion), version));
    adapter.addItem(new InfoItem(getResources().getString(R.string.infoHardwareVersion), info.hardwareVersion));
    adapter.addItem(new InfoItem(getResources().getString(R.string.infoNetworkProtocolVersion), "" + ServerStateModel.NETWORK_PROTOCOL_VERSION));
    adapter.addItem(new InfoItem(getResources().getString(R.string.infoNetworkIP), info.usbIpInfo));
    adapter.addItem(new InfoItem(getResources().getString(R.string.infoNetworkMAC), info.usbMacInfo));
  }
}
