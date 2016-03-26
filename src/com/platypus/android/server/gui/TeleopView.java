package com.platypus.android.server.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import com.platypus.crw.FunctionObserver;
import com.platypus.crw.data.Twist;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A draggable teleoperation interface that controls the thrust and yaw of a Vehicle.
 *
 * @author pkv
 */
public class TeleopView extends VehicleGuiView {
    private static final String TAG = VehicleGuiView.class.getSimpleName();
    private static final int NUM_RINGS = 3;
    final Object mFingerLock = new Object();
    Paint mPointerPaint = new Paint();
    Paint mRingEnabledPaint = new Paint();
    Paint mRingDisabledPaint = new Paint();
    PointF mFinger = null;
    float mWidth;
    float mHeight;
    RectF[] mRings;

    ScheduledThreadPoolExecutor mExecutor = new ScheduledThreadPoolExecutor(1);
    ScheduledFuture mUpdateFuture = null;

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

    @Override
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
                        mUpdateFuture = mExecutor.scheduleAtFixedRate(new UpdateVelocityTask(),
                                0, 100, TimeUnit.MILLISECONDS);
                    } else {
                        mFinger.set(eventX, eventY);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mFinger = null;
                    mUpdateFuture.cancel(true);
                    mExecutor.schedule(new StopVelocityTask(),
                            0, TimeUnit.MILLISECONDS);
                    break;
                default:
                    return false;
            }
        }

        // If any recognized touch event occurred, invalidate the view.
        invalidate();
        return true;
    }

    class UpdateVelocityTask implements Runnable {
        @Override
        public void run() {
            // Get the current finger position.
            PointF finger;
            synchronized (mFingerLock) {
                finger = mFinger;
            }

            // If the user is not clicking, just exit.
            if (finger == null)
                return;

            // Scale the contact point to a velocity between [-1.0, 1.0].
            double rudder = -2.0f * (finger.x / mWidth) + 1.0f;
            double thrust = -2.0f * (finger.y / mHeight) + 1.0f;

            Twist twist = new Twist();
            twist.dx(thrust);
            twist.drz(rudder);

            // Send the command to the vehicle.
            synchronized (mServerLock) {
                mServer.setVelocity(twist, null);
            }
        }
    }

    class StopVelocityTask implements Runnable {
        @Override
        public void run() {
            synchronized (mServerLock) {
                Twist twist = new Twist();

                // If there is no vehicle server, don't do anything.
                if (mServer == null)
                    return;

                // Send a zero-velocity command.
                mServer.setVelocity(twist, new FunctionObserver<Void>() {
                    @Override
                    public void completed(Void aVoid) {
                        // Do nothing.
                    }

                    @Override
                    public void failed(FunctionError functionError) {
                        Log.w(TAG, "Failed to stop teleop.");
                    }
                });
            }
        }
    }
}
