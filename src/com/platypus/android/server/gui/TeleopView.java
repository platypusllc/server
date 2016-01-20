package com.platypus.android.server.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
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
    private Paint mPointerPaint = new Paint();
    private Paint mRingEnabledPaint = new Paint();
    private Paint mRingDisabledPaint = new Paint();

    protected AsyncVehicleServer mServer = null;
    protected PointF mFinger = null;
    protected final Object mFingerLock = new Object();

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

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        Paint mRingPaint;

        // Draw the pointer and set the ring color.
        synchronized (mFingerLock) {
            if (mFinger != null) {
                canvas.drawCircle(mFinger.x, mFinger.y, 30.0f, mPointerPaint);
                mRingPaint = mRingEnabledPaint;
            } else {
                canvas.drawCircle(w / 2.0f, h / 2.0f, 30.0f, mRingDisabledPaint);
                mRingPaint = mRingDisabledPaint;
            }
        }

        // Draw concentric circles to represent the surface.
        int numRings = 3;
        for (int i = 0; i < numRings; ++i) {
            RectF r = new RectF(0, 0, w, h);
            r.inset(i * w / (2.0f * numRings),
                    i * h / (2.0f * numRings));
            canvas.drawOval(r, mRingPaint);
        }
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
                    sendTeleopCommand(mFinger);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mFinger = null;
                    sendTeleopCommand(null);
                    break;
                default:
                    return false;
            }
        }

        // If any recognized touch event occurred, invalidate the view.
        invalidate();
        return true;
    }

    protected void sendTeleopCommand(PointF pos) {
        synchronized (mFingerLock) {
            Twist twist = new Twist();

            // If there is no vehicle server, don't do anything.
            if (mServer == null)
                return;

            // If position is null, just use the zero-velocity command.
            if (pos == null) {
                mServer.setVelocity(twist, null);
                return;
            }

            // Scale the contact point to a velocity between [-1.0, 1.0].
            double rudder = 2.0f * (pos.x / (float)getWidth()) - 1.0f;
            double thrust = -2.0f * (pos.y / (float)getHeight()) + 1.0f;

            // Send the command to the vehicle.
            twist.dx(thrust);
            twist.drz(rudder);
            mServer.setVelocity(twist, null);
        }
    }
}
