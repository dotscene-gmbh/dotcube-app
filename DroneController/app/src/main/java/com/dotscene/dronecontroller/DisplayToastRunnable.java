package com.dotscene.dronecontroller;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by Florian Kramer on 2/17/17.
 */

public class DisplayToastRunnable implements Runnable{
    private Context c;
    private String message;

    public DisplayToastRunnable(Context c, String m) {
      this.c = c;
      this.message = m;
    }

    @Override
    public void run() {
      Toast t = Toast.makeText(c, message, Toast.LENGTH_LONG);
      t.show();
    }
}
