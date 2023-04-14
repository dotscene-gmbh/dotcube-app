package com.dotscene.dronecontroller;

import android.util.Log;
/**
 * Created by Florian Kramer on 9/20/17.
 */

public class ServerResetThread extends Thread {

  public interface OnServerResetListener {
    void onServerReset();
    void onError(String message);
  }

  private OnServerResetListener listener;

  public ServerResetThread(OnServerResetListener listener) {
    this.listener = listener;
  }

  public void run() {
    listener.onServerReset();
  }
}
