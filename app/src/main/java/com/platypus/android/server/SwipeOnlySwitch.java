package com.platypus.android.server;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Switch;

/**
 * Implements a switch that can only be swiped to change state.
 */
public class SwipeOnlySwitch extends Switch {

    public SwipeOnlySwitch(Context context) {
        super(context, null);
    }

    public SwipeOnlySwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwipeOnlySwitch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean performClick() {
        return super.isChecked();
    }
}
