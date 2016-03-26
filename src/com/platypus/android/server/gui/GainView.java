package com.platypus.android.server.gui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import com.platypus.android.server.R;

/**
 * View that allows users to read and set gains for the vehicle server.
 *
 * @author pkv
 */
public class GainView extends VehicleGuiView {
    public GainView(Context context, AttributeSet attrs) {
        super(context, attrs);

        View view = LayoutInflater.from(getContext()).inflate(
                R.layout.view_gain, null);
        artistName = view.findViewById(R.id.artistName);
        this.addView(view);
    }
}
