package com.platypus.android.server.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.platypus.crw.AsyncVehicleServer;
import com.platypus.crw.data.Twist;

/**
 * A draggable teleoperation interface that controls the thrust and yaw of a Vehicle.
 *
 * @author pkv
 */
public class TeleopView extends View {
    private static final int NUM_RINGS = 3;

    private Paint mPointerPaint = new Paint();
    private Paint mRingEnabledPaint = new Paint();
    private Paint mRingDisabledPaint = new Paint();

    protected AsyncVehicleServer mServer = null;
    protected PointF mFinger = null;
    protected final Object mFingerLock = new Object();

    protected float mWidth;
    protected float mHeight;
    protected RectF[] mRings;

    public TeleopView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPointerPaint.setColor(Color.WHITE);
        mPointerPaint.setStyle(Paint.Style.FILL);

        mRingDisabledPaint.setColor(Color.GRAY);
        mRingDisabledPaint.setStyle(Paint.Style.STROKE);
        mRingDisabledPaint.setStrokeWidth(5.0f);

        mRingEnabledPaint.setColor(Color.YELLOW);
        mRingEnabledPaint.setStyle(Paint.Style.STROKE);
        mRingEnabledPaint.setStrokeWidth(10.0f);

        mRings = new RectF[NUM_RINGS];
        for (int i = 0; i < NUM_RINGS; ++i)
            mRings[i] = new RectF();
    }

    public synchronized void getVehicleServer(AsyncVehicleServer server) {
        synchronized (mFingerLock) {
            mServer = server;
        }
    }

    public void setVehicleServer(AsyncVehicleServer server) {
        synchronized (mFingerLock) {
            mServer = server;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mWidth = w;
        mHeight = h;

        for (int i = 0; i < NUM_RINGS; ++i) {
            mRings[i].set(0, 0, mWidth, mHeight);
            mRings[i].inset(
                    i * mWidth / (2.0f * NUM_RINGS),
                    i * mHeight / (2.0f * NUM_RINGS));
        }
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint mRingPaint;

        // Draw the pointer and set the ring color.
        synchronized (mFingerLock) {
            if (mFinger != null) {
                canvas.drawCircle(mFinger.x, mFinger.y, 30.0f, mPointerPaint);
                mRingPaint = mRingEnabledPaint;
            } else {
                canvas.drawCircle(mWidth / 2.0f, mHeight / 2.0f, 30.0f, mRingDisabledPaint);
                mRingPaint = mRingDisabledPaint;
            }
        }

        // Draw concentric circles to represent the surface.
        for (int i = 0; i < NUM_RINGS; ++i)
            canvas.drawOval(mRings[i], mRingPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Get pointer location and event type from the event.
        int maskedAction = event.getActionMasked();
        float eventX = event.getX();
        float eventY = event.getY();

        // Track the user's finger to generate a teleop twist.
        synchronized (mFingerLock) {
            switch (maskedAction) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mFinger == null) {
                        mFinger = new PointF(eventX, eventY);
                    } else {
                        mFinger.set(eventX, eventY);
                    }
                    new UpdateVelocity().execute(mFinger);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mFinger = null;
                    new UpdateVelocity().execute();
                    break;
                default:
                    return false;
            }
        }

        // If any recognized touch event occurred, invalidate the view.
        invalidate();
        return true;
    }

    protected class UpdateVelocity extends AsyncTask<PointF, Void, Void> {

        @Override
        protected Void doInBackground(PointF... finger) {
            synchronized (mFingerLock) {
                Twist twist = new Twist();

                // If there is no vehicle server, don't do anything.
                if (mServer == null)
                    return null;

                // If no positions, just use the zero-velocity command.
                if (finger.length == 0) {
                    mServer.setVelocity(twist, null);
                    return null;
                }

                // Scale the contact point to a velocity between [-1.0, 1.0].
                double rudder = 2.0f * (finger[0].x / mWidth) - 1.0f;
                double thrust = -2.0f * (finger[0].y / mHeight) + 1.0f;

                // Send the command to the vehicle.
                twist.dx(thrust);
                twist.drz(rudder);
                mServer.setVelocity(twist, null);
                return null;
            }
        }
    }
}
