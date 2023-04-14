package com.dotscene.dronecontroller;

import android.content.Context;

import androidx.appcompat.widget.AppCompatToggleButton;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.widget.ToggleButton;

/**
 * Standard toggle button that changes its background color to colorPositive or colorNegative
 * when it is toggled.
 */
public class DotsceneToggle extends AppCompatToggleButton {

  public DotsceneToggle(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  public DotsceneToggle(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public DotsceneToggle(Context context) {
    super(context);
    init();
  }

  private void init() {
    setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorPositive));
  }

  @Override
  public void setChecked(boolean checked) {
    if (checked) {
      setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorNegative));
    } else {
      setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorPositive));
    }
    super.setChecked(checked);
  }
}
