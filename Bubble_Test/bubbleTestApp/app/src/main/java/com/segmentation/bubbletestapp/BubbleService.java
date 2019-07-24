package com.segmentation.bubbletestapp;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.segmentation.bubbletestapp.databinding.BubbleLayoutBinding;
import com.segmentation.bubbletestapp.databinding.ClipLayoutBinding;
import com.segmentation.bubbletestapp.databinding.ScreensheetBinding;
import com.segmentation.bubbletestapp.databinding.TrashLayoutBinding;

import static com.segmentation.bubbletestapp.MainActivity.sMediaProjection;
import static java.lang.Thread.sleep;



public class BubbleService extends Service {
    private WindowManager mWindowManager;
    private BubbleLayoutBinding mBubbleLayoutBinding;
    private WindowManager.LayoutParams mBubbleLayoutParams;
    private ScreensheetBinding mScreenSheetBinding;
    private WindowManager.LayoutParams mScreenSheetBindingParams;
    private TrashLayoutBinding mTrashLayoutBinding;
    private WindowManager.LayoutParams mTrashLayoutParams;
    private ClipLayoutBinding mClipLayoutBinding;
    private int[] closeRegion = null;//left, top, right, bottom
    private boolean isClipMode;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    public static boolean stop = true;
    public static boolean hasBeenRunning = false;

    public static int[] clipRegionService;

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
            mBubbleLayoutBinding.getRoot().setBackground(getDrawable(R.drawable.video_camera));
//          Can't set the background color of the icon for some reason

        }
        mBubbleLayoutBinding.setHandler(new BubbleHandler(this));
        getWindowManager().addView(mBubbleLayoutBinding.getRoot(), mBubbleLayoutParams);

        mScreenSheetBinding = ScreensheetBinding.inflate(layoutInflater);
        clipRegionService = new int[4];
    }

    private WindowManager getWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        return mWindowManager;
    }

    public void checkInCloseRegion(float x, float y) {
        if (closeRegion == null) {
            int[] location = new int[2];
            View v = mTrashLayoutBinding.getRoot();
            v.getLocationOnScreen(location);
            closeRegion = new int[]{location[0], location[1],
                    location[0] + v.getWidth(), location[1] + v.getHeight()};
        }

        if (Float.compare(x, closeRegion[0]) >= 0 &&
                Float.compare(y, closeRegion[1]) >= 0 &&
                Float.compare(x, closeRegion[2]) <= 0 &&
                Float.compare(3, closeRegion[3]) <= 0) {
            stop = true;
            stopSelf();
        } else {
            mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
        }
    }
    public boolean checkInClipRegion(float x, float y, int[] clipBox) {

        if (Float.compare(x, clipBox[0]) >= 0 &&
                Float.compare(y, clipBox[1]) >= 0 &&
                Float.compare(x, clipBox[0]+clipBox[2]) <= 0 &&
                Float.compare(y, clipBox[1]+clipBox[3]) <= 0) {
            return true;
        }
        else return false;
    }

    public void updateViewLayout(View view, WindowManager.LayoutParams params) {
        mTrashLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        getWindowManager().updateViewLayout(view, params);
    }

    public void removeTrashLayout()
    {
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
    }
    public void startClipMode() {
        stop = true;
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
        isClipMode = true;
        if (mClipLayoutBinding == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(this);
            mClipLayoutBinding = ClipLayoutBinding.inflate(layoutInflater);
            mClipLayoutBinding.setHandler(new ClipHandler(this));
        }
        WindowManager.LayoutParams mClipLayoutParams = buildLayoutParamsForClip();
        ((ClipView) mClipLayoutBinding.getRoot()).updateRegion(0, 0, 0, 0);
        //mBubbleLayoutBinding.getRoot().setVisibility(View.INVISIBLE);    //This is when you are taking the screenshot. You can set the visibility to Gone to get rid of the Bubble
        mBubbleLayoutBinding.getRoot().setBackground(getDrawable(R.drawable.stop_recording));

        if(!hasBeenRunning) {
            getWindowManager().addView(mClipLayoutBinding.getRoot(), mClipLayoutParams);
        }
        else
            mClipLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        hasBeenRunning = true; //This is so that startclipmode does not throw an exception for the add.view method next time
        Toast.makeText(this, "Start clip mode.", Toast.LENGTH_SHORT).show();
    }

    public void finishClipMode(int[] clipRegion) {
        isClipMode = false;
        clipRegionService = clipRegion;
        //getWindowManager().removeView(mClipLayoutBinding.getRoot());    //This is the clip region view where you choose to take the screenshot.
        // By not removing the view the box will stay on the screen indefinitely
        if (clipRegion[2] < 50 || clipRegion[3] < 50) {
            Toast.makeText(this, "Region is too small. Try Again", Toast.LENGTH_SHORT).show();
            mClipLayoutBinding.getRoot().setVisibility(View.GONE);
            mBubbleLayoutBinding.getRoot().setVisibility(View.GONE);
            mScreenSheetBinding.getRoot().setVisibility(View.GONE);
            finalRelease();
            stopSelf();
        } else {
            if (mScreenSheetBindingParams == null) {
                mScreenSheetBindingParams = buildLayoutParamsForSheet(clipRegion[0],clipRegion[1]-getStatusBarHeight(), clipRegion);
                getWindowManager().addView(mScreenSheetBinding.getRoot(), mScreenSheetBindingParams);
            }
            screenshot(clipRegion);
            mClipLayoutBinding.getRoot().setVisibility(View.GONE);

        }
        mBubbleLayoutBinding.getRoot().setBackground(getDrawable(R.drawable.video_camera));
    }

    public void screenshot(int[] clipRegion) {
        Runnable myRunnable = () -> {
            while (stop == false) {
                try {
                    sleep(1000); // Waits for 1 second (1000 milliseconds
                    Log.e("Nima", "screenshot: thread done");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
//                if (sMediaProjection != null) {
//                            shotScreen(clipRegion);
//                            Log.d("Nima", "run: screenshot in runnable");
//                            Log.d("Nima", "stop :" + stop);
//                }
//                        else break;
//            }

        };

//            Thread myThread = new Thread(myRunnable);
//            myThread.start();



                Thread myThread = new Thread(myRunnable);
                myThread.start();
        }
    @SuppressLint("CheckResult")
    private void shotScreen(int[] clipRegion) {
    }

    private void finalRelease() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (isClipMode) {
            isClipMode = false;
            getWindowManager().removeView(mClipLayoutBinding.getRoot());
            Toast.makeText(this,"Configuration changed, stop clip mode.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        if (mWindowManager != null) {
            if (mBubbleLayoutBinding != null) {
                mWindowManager.removeView(mBubbleLayoutBinding.getRoot());
            }
            if (mTrashLayoutBinding != null) {
                mWindowManager.removeView(mTrashLayoutBinding.getRoot());
            }
            if (mClipLayoutBinding != null) {
                if (isClipMode) {
                    mWindowManager.removeView(mClipLayoutBinding.getRoot());
                }
            }
            if (mScreenSheetBinding != null) {
                    mScreenSheetBinding.getRoot().setVisibility(View.GONE);
                    mWindowManager.removeView(mClipLayoutBinding.getRoot());
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
        } else if(Build.VERSION.SDK_INT >= 23) {
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

 private WindowManager.LayoutParams buildLayoutParamsForSheet(int x, int y, int[] clipRegion) {
        WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= 26) {
            params = new WindowManager.LayoutParams(
                    clipRegion[2],
                    clipRegion[3],
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        } else if(Build.VERSION.SDK_INT >= 23) {
            //noinspection deprecation
            params = new WindowManager.LayoutParams(
                    clipRegion[2],
                    clipRegion[3],
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        } else {
            //noinspection deprecation
            params = new WindowManager.LayoutParams(
                    clipRegion[2],
                    clipRegion[3],
                    WindowManager.LayoutParams.TYPE_TOAST,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        }
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        return params;
    }

    private WindowManager.LayoutParams buildLayoutParamsForClip() {
        WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT <= 22) {
            //noinspection deprecation
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_TOAST,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                    PixelFormat.TRANSPARENT);
        } else {
            Display display = getWindowManager().getDefaultDisplay();
            /*
            The real size may be smaller than the physical size of the screen
            when the window manager is emulating a smaller display (using adb shell wm size).
             */
            Point sizeReal = new Point();
            display.getRealSize(sizeReal);
            /*
            If requested from activity
            (either using getWindowManager() or (WindowManager) getSystemService(Context.WINDOW_SERVICE))
            resulting size will correspond to current app window size.
            In this case it can be smaller than physical size in multi-window mode.
             */
            Point size = new Point();
            display.getSize(size);
            int screenWidth, screenHeight, diff;
            if (size.x == sizeReal.x) {
                diff = sizeReal.y - size.y;
            } else {
                diff = sizeReal.x - size.x;
            }
            screenWidth = sizeReal.x + diff;
            screenHeight = sizeReal.y + diff;

            Log.d("kanna", "get screen " + screenWidth + " " + screenHeight
                    + " " + sizeReal.x + " " + size.x
                    + " " + sizeReal.y + " " + size.y);
            if (Build.VERSION.SDK_INT >= 26) {
                params = new WindowManager.LayoutParams(
                        screenWidth,
                        screenHeight,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSPARENT);
            } else {
                //noinspection deprecation
                params = new WindowManager.LayoutParams(
                        screenWidth,
                        screenHeight,
                        WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSPARENT);
            }

        }
        return params;
    }
    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
}