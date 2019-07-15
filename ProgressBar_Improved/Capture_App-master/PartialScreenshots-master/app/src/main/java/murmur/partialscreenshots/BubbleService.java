package murmur.partialscreenshots;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Vector;

import murmur.partialscreenshots.databinding.BubbleLayoutBinding;
import murmur.partialscreenshots.databinding.ClipLayoutBinding;
import murmur.partialscreenshots.databinding.LayoutBottomSheetBinding;
import murmur.partialscreenshots.databinding.TrashLayoutBinding;
import murmur.partialscreenshots.env.ImageUtils;

import static murmur.partialscreenshots.MainActivity.sMediaProjection;

class GLOBAL
{
    static boolean stop = true;
    static boolean hasBeenRunning = false;
    static int count = 0;
    static boolean isProcessDone = true;
}

public class BubbleService extends Service
        implements QUSEventListener{
    private WindowManager mWindowManager;
    private BubbleLayoutBinding mBubbleLayoutBinding;
    private WindowManager.LayoutParams mBubbleLayoutParams;
    private TrashLayoutBinding mTrashLayoutBinding;
    private WindowManager.LayoutParams mTrashLayoutParams;
    private ClipLayoutBinding mClipLayoutBinding;
    private int[] closeRegion = null;//left, top, right, bottom
    private boolean isClipMode;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private LayoutBottomSheetBinding mLayoutBottomSheetBinding;
    private WindowManager.LayoutParams mLayoutBottomSheetParams;
    private Results display_results = new Results();
    private Handler handler;
    private HandlerThread handlerThread;
    private Classifier QUSRunner;
    private Canvas canvas = new Canvas();
    private Bitmap bitmapCut;
    private Bitmap bitmap;
    private Bitmap bit;
    private GoalProgressBar progressBar;

//====================================================================================== QUS  Variables ===================================================================================
    public static final int NUM_VIEW_CLASSES = 14;
    private static float [] view_ema_arr = new float[NUM_VIEW_CLASSES];
    private static final float alpha = 0.1f;
    private final Object results_mtx = new Object();
    Vector res_mean_vec = new Vector();
    Vector res_std_vec = new Vector();
    private static final int FILTER_LENGTH = 35;
    private static float[] lastResults;


    // ======================================================================================= QUSRunner Variables ==================================================================================
    public static final String[] VIEW_NAMES = { "AP2","AP3","AP4","AP5","PLAX",
        "RVIF","SUBC4","SUBC5","SUBCIVC","PSAXA",
        "PSAXM","PSAXPM","PSAXAPIX","SUPRA", "UNINIT"}; // always add unint at the ned but don't include it in the length
    public static final int AP2_IDX = 0;
    public static final int AP3_IDX = 1;
    public static final int AP4_IDX = 2;
    public static final int PLAX_IDX = 4;
    public static final int SUBC4_IDX = 6;
    public static final int SUBIVC_IDX = 8;
    public static final int PSAXAo_IDX = 9;
    public static final int PSAXM_IDX = 10;
    public static final int PSAXPM_IDX = 11;
    public static final int PSAXAp_IDX = 12;
    //public static final int OTHER_IDX = 14;
    public static final int UNINIT_IDX = 14;

    private static final String CNN_FILENAME = "allCNN_5m_lap";
    private static final String CNN_INPUT_NAME = "input";
    private static final String CNN_QUALITY_NAME = "pred2/Mean";
    private static final String CNN_VIEW_NAME = "pred3/concat";
    private static final int INPUT_WIDTH = 128;
    private static final int INPUT_HEIGHT = 128;
    private static final int NETWORK_FRAMES = 1;
    private static final int NUM_QUAL_CLASSES = 2;
    private static final long [] CNN_INPUT_DIMS = {NETWORK_FRAMES, INPUT_WIDTH, INPUT_HEIGHT};
// ======================================================================================= Timing Analysis Vars ==================================================================================

    private static double initialTime = 0;
    private static double finalTime = 0;

    public static float filt_mean = 0, filt_std = 0;

    public BubbleService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.d("kanna", "onStart");
//        if (sMediaProjection != null) {
//            Log.d("kanna", "mediaProjection alive");
//        }
//Necessary object
        lastResults = new float [2];
        display_results.setBar(progressBar);
        initial();
// ======================================================================= From TF Lite app to enable background image processing ========================================================
        handlerThread = new HandlerThread("Inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
// =======================================================================================================================================================================================
        return super.onStartCommand(intent, flags, startId);
    }

    private void initial() {
//      Log.d("kanna", "initial");
//==================================================================== Inflate all the views and set their proper parameters and Handlers===============================================================================
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        mTrashLayoutBinding = TrashLayoutBinding.inflate(layoutInflater);
        mLayoutBottomSheetBinding = LayoutBottomSheetBinding.inflate(layoutInflater);
        if (mTrashLayoutParams == null) {
            mTrashLayoutParams = buildLayoutParamsForBubble(0, 200);
            mTrashLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        getWindowManager().addView(mTrashLayoutBinding.getRoot(), mTrashLayoutParams);
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
        mBubbleLayoutBinding = BubbleLayoutBinding.inflate(layoutInflater);
        if (mBubbleLayoutParams == null) {
            mBubbleLayoutParams = buildLayoutParamsForBubble(60, 60);
            mBubbleLayoutBinding.getRoot().setBackground(getDrawable(R.drawable.video_camera));
        }
        if (mLayoutBottomSheetParams == null) {
            mLayoutBottomSheetParams = buildLayoutParamsForBottomSheet(0, 0);
        }
        mBubbleLayoutBinding.setHandler(new BubbleHandler(this));
        getWindowManager().addView(mBubbleLayoutBinding.getRoot(), mBubbleLayoutParams);
        getWindowManager().addView(mLayoutBottomSheetBinding.getRoot(), mLayoutBottomSheetParams);
        mLayoutBottomSheetBinding.getRoot().setVisibility(View.GONE);
        mLayoutBottomSheetBinding.setHandler(new BottomSheetHandler(this));
        progressBar = mLayoutBottomSheetBinding.progressBar;
    }

    private WindowManager getWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        return mWindowManager;
    }

//==================================================================== This is the function to decide whether trash layout disappears or not==========================================================================
// It also allows you to stop the Service
    public void checkInCloseRegion(float x, float y) {
        if (closeRegion == null) {
            int[] location = new int[2];
            View v = mTrashLayoutBinding.getRoot();
            v.getLocationOnScreen(location);
            closeRegion = new int[]{location[0], location[1],
                    location[0] + v.getWidth()+200 , location[1] + v.getHeight()+200};
        }

        if (Float.compare(x, closeRegion[0]) >= 0 &&
                Float.compare(y, closeRegion[1]) >= 0 &&
                Float.compare(x, closeRegion[2]) <= 0 &&
                Float.compare(3, closeRegion[3]) <= 0) {
            GLOBAL.stop = true;
            QUSRunner.close();
            stopSelf();
        } else {
            mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
        }
    }
//============================================================================ Makes the trashlayout to show up ======================================================================================================
    public void updateViewLayout(View view, WindowManager.LayoutParams params) {
        mTrashLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        getWindowManager().updateViewLayout(view, params);
    }
    //==================================================================== This is the function to decide whether trash layout disappears or not==========================================================================
    public void startClipMode() {
        GLOBAL.stop = true;
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
        if(QUSRunner != null) {
            QUSRunner.clearLastResult();
            //QUSRunner.close();
        }
        //This is so that startClipMode does not throw an exception for the add.view method next time
        if(!GLOBAL.hasBeenRunning) {
            getWindowManager().addView(mClipLayoutBinding.getRoot(), mClipLayoutParams);
        }
        else
            mClipLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        GLOBAL.hasBeenRunning = true;
        Toast.makeText(this, "Start clip mode.", Toast.LENGTH_SHORT).show();
    }

    public void finishClipMode(int[] clipRegion) {
        isClipMode = false;
        GLOBAL.stop = false;
        // Initialize the QUS runner after the clip layout is chosen
        // This helps when you want to reselect the clip layout
        if(QUSRunner == null)
        {
            QUSRunner =
                    TensorFlowQUSRunner.create(
                            this,
                            getAssets(),
                            CNN_FILENAME,
                            CNN_INPUT_NAME,
                            CNN_VIEW_NAME,
                            CNN_QUALITY_NAME,
                            CNN_INPUT_DIMS,
                            NUM_VIEW_CLASSES,
                            NUM_QUAL_CLASSES);

            if(QUSRunner == null) {
                Log.e("Nima", "Error has occurred while loading QUSRunner, exiting...");
                return;
            }
        }
        //getWindowManager().removeView(mClipLayoutBinding.getRoot());    //This is the clip region view where you choose to take the screenshot.
        // By not removing the view the box will stay on the screen indefinitely
        if (clipRegion[2] < 50 || clipRegion[3] < 50) {
            Toast.makeText(this, "Region is too small. Try Again", Toast.LENGTH_SHORT).show();
            mBubbleLayoutBinding.getRoot().setVisibility(View.VISIBLE);
            mClipLayoutBinding.getRoot().setVisibility(View.GONE);
            mBubbleLayoutBinding.getRoot().setVisibility(View.GONE);
            mLayoutBottomSheetBinding.getRoot().setVisibility(View.GONE);
            return;
//            finalRelease();
//            GLOBAL.stop = true;
//            stopSelf();
        } else {
            screenshot(clipRegion);
            mClipLayoutBinding.getRoot().setVisibility(View.GONE);
        }
        mBubbleLayoutBinding.getRoot().setBackground(getDrawable(R.drawable.video_camera));

    }
    public void screenshot(int[] clipRegion){
        mLayoutBottomSheetBinding.getRoot().setVisibility(View.VISIBLE);
//        mLayoutBottomSheetBinding.setHandler(new BottomSheetHandler(this));
        mLayoutBottomSheetBinding.setResult(display_results);

//        if (!GLOBAL.stop)
//        {
            shotScreen(clipRegion);
//        }
    }
    @SuppressLint("CheckResult")
    private void shotScreen(int[] clipRegion) {
          getScreenShot(clipRegion);
    }

// ============================================================================================ Cleaning Up ==============================================================================================

    private void finalRelease() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if(GLOBAL.stop)
        {
            mLayoutBottomSheetBinding.getRoot().setVisibility(View.GONE);
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
                mWindowManager.removeView(mClipLayoutBinding.getRoot());
            }
            if (mLayoutBottomSheetBinding != null) {
                mWindowManager.removeView(mLayoutBottomSheetBinding.getRoot());
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
    private WindowManager.LayoutParams buildLayoutParamsForBottomSheet(int x, int y) {
        WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= 26) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        } else if(Build.VERSION.SDK_INT >= 23) {
            //noinspection deprecation
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        } else {
            //noinspection deprecation
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_TOAST,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        }
        params.gravity = Gravity.BOTTOM | Gravity.START;
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

    //https://stackoverflow.com/questions/14341041/how-to-get-real-screen-height-and-width
    private void getScreenShot(int[] clipRegion) {
        Log.i("Nima", "getScreenShot: run");
        if (imageReader == null) {
            final Point screenSize = new Point();
            final DisplayMetrics metrics = getResources().getDisplayMetrics();
            Display display = getWindowManager().getDefaultDisplay();
            display.getRealSize(screenSize);
            imageReader = ImageReader.newInstance(screenSize.x, screenSize.y,
                    PixelFormat.RGBA_8888, 2);
            virtualDisplay = sMediaProjection.createVirtualDisplay("cap", screenSize.x, screenSize.y,
                    metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
            ImageReader.OnImageAvailableListener mImageListener =
                    new ImageReader.OnImageAvailableListener() {
                        Image image;

                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            try {
                                finalTime = System.currentTimeMillis() -initialTime;
                                initialTime = System.currentTimeMillis();
                                Log.d("Nima", "onImageAvailable: time between frames = " + finalTime);

                                image = imageReader.acquireLatestImage();
                                if (image == null) {
                                    Log.d("Nima", "No image => Freak out");
                                } else {
                                    bitmapCut = createBitmap(image, clipRegion);
                                    GLOBAL.count++;
                                    Log.e("nima", "Count:" + GLOBAL.count);
                                    // The GLOBAL.stop is checked there to avoid the error when terminating the application
                                    if(GLOBAL.isProcessDone && !GLOBAL.stop)
                                        processImage(bitmapCut);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    };
            imageReader.setOnImageAvailableListener(mImageListener, null);

        }
    }
    private Bitmap createBitmap(Image image, int[] clipRegion) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        // create bitmap
        bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmapCut = Bitmap.createBitmap(bitmap,
                clipRegion[0], clipRegion[1], clipRegion[2], clipRegion[3]);
        bitmap.recycle();
        image.close();
        return bitmapCut;
    }

    protected synchronized void processImage(Bitmap bitmap){
        runInBackground(
                new Runnable() {
                    @Override
                    public void run()
                    {
                        if(QUSRunner != null)
                        {
                            // get proper transformation matrix
                            Matrix prev2cnn = ImageUtils.getTransformationMatrix(bitmap.getWidth(), bitmap.getHeight(), INPUT_WIDTH, INPUT_HEIGHT, 0, false);
                            bit = Bitmap.createBitmap(INPUT_WIDTH, INPUT_HEIGHT, Bitmap.Config.ARGB_8888); // creates a new bitmap to draw the scaled bitmapCut onto.
                            canvas.setBitmap(bit); // Sets the bitmap to canvas to draw onto
                            canvas.drawBitmap(bitmap, prev2cnn, null); // Draws the scaled version of bitmap onto bit
// ======================================================================================================================= Run network ========================================================================================================
                            if(QUSRunner==null){Log.e("Nima","QUALITY RUNNER DELETED!");return;}
                            double initial = System.currentTimeMillis();
                            QUSRunner.scoreImage(bit);
                            GLOBAL.isProcessDone = true;
                            double processTime = System.currentTimeMillis() - initial;
                            Log.i("Nima", "ScoreTime = " + processTime);

// ===================================================================================================================== Sets the results ===================================================================================================
                        }
                    }
                });
                runInBackground(new Runnable() {
                    @Override
                    public void run() {

                        synchronized (results_mtx){

                            if (res_mean_vec.size() >= FILTER_LENGTH) {
                                res_mean_vec.remove(0);
                                res_std_vec.remove(0);
                            }
                            res_mean_vec.addElement(lastResults[0]);
                            res_std_vec.addElement(lastResults[1]);


                            //Log.i(TAG,"res size = "+results_vector.size());
                            for (int i = 0; i < res_mean_vec.size(); i++) {
                                filt_mean = filt_mean + (float) res_mean_vec.elementAt(i);
                                filt_std = filt_std + (float) res_std_vec.elementAt(i);
                            }
                            filt_mean = filt_mean / FILTER_LENGTH;
                            filt_std = filt_std / FILTER_LENGTH;
                            displayResults(filt_mean, filt_std);

                        }


                }
        });
        updateViewLayout(mLayoutBottomSheetBinding.getRoot(), mLayoutBottomSheetParams);
        trashLayoutRemove();
    }
    private void displayResults (float filt_mean, float filt_std){
        runInBackground(new Runnable() {
            @Override
            public void run() {
                progressBar.setUncertainProgress(filt_mean, filt_std);
            }
        });
    }
    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {                                          // handler.post, posts a message to the handler
            handler.post(r);                                            // .post is used to when you want to run some unknown code on UI thread
       }
    }

    /**
     * Make bitmap appropriate size, greyscale and inverted. MNIST model is originally teached on
     * dataset of images 28x28px with white letter written on black background.
     */
    public void trashLayoutRemove()
    {
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
    }

    public synchronized void updateResultEvent(float[] view_probs, float[] qual_results) {
        // Update EMA
        int pv = UNINIT_IDX;
        float max_prob = 0;
        for (int i = 0; i < NUM_VIEW_CLASSES; i++) {
            view_ema_arr[i] = alpha * view_probs[i] + (1 - alpha) * view_ema_arr[i];
            if (view_ema_arr[i] > max_prob) {
                max_prob = view_ema_arr[i];
                pv = i;
            }
            System.arraycopy(qual_results,0,lastResults,0,2);

            //Log.i("Nima","View = "+VIEW_NAMES[pv]+" with a prob of = "+max_prob);
            display_results.setRes1(VIEW_NAMES[pv]);
        }
    }

}