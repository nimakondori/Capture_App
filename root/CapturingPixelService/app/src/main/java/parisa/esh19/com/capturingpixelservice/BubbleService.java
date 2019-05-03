package parisa.esh19.com.capturingpixelservice;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import  parisa.esh19.com.capturingpixelservice.databinding.BubbleLayoutBinding;
import  parisa.esh19.com.capturingpixelservice.databinding.RectangularLayoutBinding;
import  parisa.esh19.com.capturingpixelservice.databinding.TrashLayoutBinding;
import static parisa.esh19.com.capturingpixelservice.MainActivity.sMediaProjection;


/**
 * Created by Parisa Eshraghi on 30/06/2018
 *
 * Based on "https://github.com/murmurmuk/PartialScreenshots"
 *
 *
 **/

public class BubbleService extends Service {
    private WindowManager mWindowManager;
    private BubbleLayoutBinding mBubbleLayoutBinding;
    private WindowManager.LayoutParams mBubbleLayoutParams;
    private TrashLayoutBinding mTrashLayoutBinding;
    private WindowManager.LayoutParams mTrashLayoutParams;
    private RectangularLayoutBinding mRectangularLayoutBinding;
    private int[] closeRegion = null;//left, top, right, bottom
    private boolean isRecMode;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("kanna", "onStart");
        if (sMediaProjection != null) {
            Log.d("kanna", "mediaProjection alive");
        }
        initial();
        return super.onStartCommand(intent, flags, startId);
    }

    private void initial() {
        Log.d("kanna", "initial");
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        mTrashLayoutBinding = TrashLayoutBinding.inflate(layoutInflater);
        if (mTrashLayoutParams == null) {
            mTrashLayoutParams = buildLayoutParamsForBubble(0, 0);
            mTrashLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        getWindowManager().addView(mTrashLayoutBinding.getRoot(), mTrashLayoutParams);
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);

        mBubbleLayoutBinding = BubbleLayoutBinding.inflate(layoutInflater);
        if (mBubbleLayoutParams == null) {
            mBubbleLayoutParams = buildLayoutParamsForBubble(60, 60);
        }
        mBubbleLayoutBinding.setHandler(new BubbleHandler(this));
        getWindowManager().addView(mBubbleLayoutBinding.getRoot(), mBubbleLayoutParams);
    }

    private WindowManager getWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        return mWindowManager;
    }

    public void checkInCloseRegion(float x, float y) {
        if (closeRegion == null) {
            int location[] = new int[2];
            View v = mTrashLayoutBinding.getRoot();
            v.getLocationOnScreen(location);
            closeRegion = new int[]{location[0], location[1],
                    location[0] + v.getWidth(), location[1] + v.getHeight()};
        }

        if (Float.compare(x, closeRegion[0]) >= 0 &&
                Float.compare(y, closeRegion[1]) >= 0 &&
                Float.compare(x, closeRegion[2]) <= 0 &&
                Float.compare(3, closeRegion[3]) <= 0) {
            stopSelf();
        } else {
            mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
        }
    }

    public void updateViewLayout(View view, WindowManager.LayoutParams params) {
        mTrashLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        getWindowManager().updateViewLayout(view, params);
    }

    public void startRecMode() {
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
        isRecMode = true;
        if (mRectangularLayoutBinding == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(this);
            mRectangularLayoutBinding = mRectangularLayoutBinding.inflate(layoutInflater);

        }

        mBubbleLayoutBinding.getRoot().setVisibility(View.GONE);
        getWindowManager().addView(mRectangularLayoutBinding.getRoot(), params);
        Toast.makeText(this, "Start clip mode.", Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (isRecMode) {
            isRecMode = false;
            getWindowManager().removeView(mRectangularLayoutBinding.getRoot());
            Toast.makeText(this, "Configuration changed, stop Rec mode.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onDestroy() {
        if (mWindowManager != null) {
            if (mBubbleLayoutBinding != null) {
                mWindowManager.removeView(mBubbleLayoutBinding.getRoot());
            }
            if (mTrashLayoutBinding != null) {
                mWindowManager.removeView(mTrashLayoutBinding.getRoot());
            }
            if (mRectangularLayoutBinding != null) {
                if (isRecMode) {
                    mWindowManager.removeView(mRectangularLayoutBinding.getRoot());
                }
            }
            mWindowManager = null;
        }
        if (sMediaProjection != null) {
            sMediaProjection.stop();
            sMediaProjection = null;
        }
        super.onDestroy();
    }

    private WindowManager.LayoutParams buildLayoutParamsForBubble(int x, int y) {
        WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= 26) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        } else if (Build.VERSION.SDK_INT >= 23) {
            //noinspection deprecation
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        } else {
            //noinspection deprecation
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_TOAST,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        }
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        return params;
    }

    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            //WindowManager.LayoutParams.TYPE_INPUT_METHOD |
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,// | WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);

}