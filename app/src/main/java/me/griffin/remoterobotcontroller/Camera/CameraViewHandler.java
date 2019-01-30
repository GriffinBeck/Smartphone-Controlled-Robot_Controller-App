package me.griffin.remoterobotcontroller.Camera;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

import me.griffin.remoterobotcontroller.MainActivity;

/**
 * Created by Alvin on 2016-05-20.
 */
public class CameraViewHandler extends Handler {
    private final WeakReference<MainActivity> mActivity;

    public CameraViewHandler(MainActivity activity) {
        mActivity = new WeakReference<MainActivity>(activity);
    }

    @Override
    public void handleMessage(Message msg) {
        MainActivity activity = mActivity.get();
        if (activity != null) {
            try {
                activity.mLastFrame = (Bitmap) msg.obj;
            } catch (Exception e) {
                e.printStackTrace();
            }
            super.handleMessage(msg);
        }
    }
}