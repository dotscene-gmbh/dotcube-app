package com.dotscene.dronecontroller;

import android.content.Context;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;

/**
 * Created by Florian Kramer on 2/26/17.
 */

public class StateView extends AppCompatImageView {

  enum State {
    NEGATIVE,
    NEUTRAL,
    POSITIVE
  }

  private static final int STATE_BASE = R.drawable.state_base;

  State state = State.NEGATIVE;

  public StateView(Context context) {
    super(context);
    init();
  }

  public StateView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public StateView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setImageResource(STATE_BASE);
    if (isEnabled()) {
      setColorFilter(ContextCompat.getColor(getContext(), R.color.colorNegative));
    } else {
      setColorFilter(ContextCompat.getColor(getContext(), R.color.colorDisabled));
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    updateColor();
  }

  public void setState(State s) {
    if (state != s) {
      state = s;
      updateColor();
    }
  }

  private void updateColor() {
    if (isEnabled()) {
      switch (state) {
        case NEGATIVE:
          setColorFilter(ContextCompat.getColor(getContext(), R.color.colorNegative));
          break;
        case NEUTRAL:
          setColorFilter(ContextCompat.getColor(getContext(), R.color.colorNeutral));
          break;
        case POSITIVE:
          setColorFilter(ContextCompat.getColor(getContext(), R.color.colorPositive));
          break;
      }
    } else {
      setColorFilter(ContextCompat.getColor(getContext(), R.color.colorDisabled));
    }
  }
}
