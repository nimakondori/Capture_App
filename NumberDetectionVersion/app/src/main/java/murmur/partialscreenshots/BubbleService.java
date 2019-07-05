package murmur.partialscreenshots;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import murmur.partialscreenshots.databinding.BubbleLayoutBinding;
import murmur.partialscreenshots.databinding.ClipLayoutBinding;
import murmur.partialscreenshots.databinding.LayoutBottomSheetBinding;
import murmur.partialscreenshots.databinding.TrashLayoutBinding;
import murmur.partialscreenshots.tflite.Classifier;
import murmur.partialscreenshots.tflite.MnistModelConfig;
import murmur.partialscreenshots.Detection.Classification;

import static android.os.Environment.DIRECTORY_PICTURES;
import static murmur.partialscreenshots.MainActivity.sMediaProjection;

class GLOBAL
{
    static boolean stop = true;
    static boolean hasBeenRunning = false;
    static int count = 0;
}

public class BubbleService extends Service{
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
    private Results results = new Results();  private Handler handler;
    private HandlerThread handlerThread;
    private Detection classifier;
    List<Classification> TF_Results = null;
    String TF_Results_string;


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
        loadMnistClassifier();
        initial();

// ======================================================================= From TF Lite app to enable background image processing ========================================================
        handlerThread = new HandlerThread("Inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
// =======================================================================================================================================================================================

        return super.onStartCommand(intent, flags, startId);

    }

    private void initial() {
        Log.d("kanna", "initial");


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
                    location[0] + v.getWidth(), location[1] + v.getHeight()};
        }

        if (Float.compare(x, closeRegion[0]) >= 0 &&
                Float.compare(y, closeRegion[1]) >= 0 &&
                Float.compare(x, closeRegion[2]) <= 0 &&
                Float.compare(3, closeRegion[3]) <= 0) {
            GLOBAL.stop = true;
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
        mClipLayoutBinding.setHandler(null);
    }
    public void screenshot(int[] clipRegion){
        mLayoutBottomSheetBinding.getRoot().setVisibility(View.VISIBLE);
        mLayoutBottomSheetBinding.setHandler(new BottomSheetHandler(this));
        mLayoutBottomSheetBinding.setResults(results);
    //========================================================================================== Sets the results ===================================================================================================

        if (!GLOBAL.stop)
        {
            shotScreen(clipRegion);
        }
//
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
                        try{
                            image = imageReader.acquireLatestImage();
                            if(image == null){
                                Log.d("Nima", "onImageAvailable: Freakout");
                            }
                            else
                            {
                                    Bitmap bitmapCut = createBitmap(image, clipRegion);
                                    double initial_timer = SystemClock.uptimeMillis();
                                    processImage(bitmapCut);
//========================================================================= don't forget to scale the image for now =====================================================================
//                                    String fileName = createFile();
//                                    writeFile(bitmapCut, fileName);
//                                    updateScan(fileName);
                                    GLOBAL.count ++;
                                    Log.e("nima", "Count:" + GLOBAL.count );
                                    double timeSpent = SystemClock.uptimeMillis() - initial_timer;
                                    Log.e("Nima", "Timer: " + timeSpent );

                            }
                        }catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        //imageReader.setOnImageAvailableListener(null, null);

                    }

                };
                    imageReader.setOnImageAvailableListener(mImageListener, null);
    }
    private void updateScan(final String fileName) {
            String[] path = new String[]{fileName};
            MediaScannerConnection.scanFile(this, path, null, (s, uri) -> {
                if (uri == null) {
                    Log.e("Nima", "updateScan: ERROR ");
                }
            });
    }

    private Bitmap createBitmap(Image image, int[] clipRegion) {
        Bitmap bitmap, bitmapCut;
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

    private void writeFile(Bitmap bitmap, String fileName) throws IOException {
        //Log.d("kanna", "check write file: " + Thread.currentThread().toString());
        FileOutputStream fos = new FileOutputStream(fileName);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        bitmap.recycle();
    }

    private String createFile()
    {
            String directory, fileHead, fileName;
            int count = 0;
            File externalFilesDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES);
            if (externalFilesDir != null) {
                directory = Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES)
                        .getAbsolutePath() + "/Screenshots/";
                File storeDirectory = new File(directory);
                if (!storeDirectory.exists()) {
                    boolean success = storeDirectory.mkdirs();
                    if (!success) {
                        Log.e("Nima", "createFile: nonsense");
                    }
                }

                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd",
                        Locale.ENGLISH);
                Calendar c = Calendar.getInstance();
                fileHead = simpleDateFormat.format(c.getTime()) + "_";
                fileName = directory + fileHead + count + ".png";
                File storeFile = new File(fileName);
                while (storeFile.exists()) {
                    count++;
                    fileName = directory + fileHead + count + ".png";
                    storeFile = new File(fileName);
                }
                return fileName;
            }
            return "NONSENSE";
    }
    protected void processImage(Bitmap bitmap){
        final Canvas canvas = new Canvas(bitmap);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run()
                    {
                        if(classifier != null)
                        {
                            final long startTime = SystemClock.uptimeMillis();
                            Bitmap bit_2 = Bitmap.createScaledBitmap(bitmap, 28, 28, false);
                            Bitmap bit = prepareImageForClassification(bit_2);
                            TF_Results = classifier.recognizeImage(bit);
                            TF_Results_string = TF_Results.toString();
                            long lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
// ==================================================================== Used to store the results from tf lite on UI thread ===================================================================
                            results.setRes1(TF_Results_string);
                            Log.e("Nima", "Detect: " + TF_Results_string);
                            Log.e("Nima", "Time Elapsed: " + (lastProcessingTimeMs - startTime) );

                        }
                    }
                }
        );


    }
    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {                                          // handler.post, posts a message to the handler
            handler.post(r);                                            // .post is used to when you want to run some unknown code on UI thread
       }
    }
    private static final ColorMatrix INVERT = new ColorMatrix(
            new float[]{
                    -1, 0, 0, 0, 255,
                    0, -1, 0, 0, 255,
                    0, 0, -1, 0, 255,
                    0, 0, 0, 1, 0
            });

    private static final ColorMatrix BLACKWHITE = new ColorMatrix(
            new float[]{
                    0.5f, 0.5f, 0.5f, 0, 0,
                    0.5f, 0.5f, 0.5f, 0, 0,
                    0.5f, 0.5f, 0.5f, 0, 0,
                    0, 0, 0, 1, 0,
                    -1, -1, -1, 0, 1
            }
    );

    /**
     * Make bitmap appropriate size, greyscale and inverted. MNIST model is originally teached on
     * dataset of images 28x28px with white letter written on black background.
     */
    public static Bitmap prepareImageForClassification(Bitmap bitmap) {
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        colorMatrix.postConcat(BLACKWHITE);
        colorMatrix.postConcat(INVERT);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(colorMatrix);

        Paint paint = new Paint();
        paint.setColorFilter(f);

        Bitmap bmpGrayscale = Bitmap.createScaledBitmap(
                bitmap,
                MnistModelConfig.INPUT_IMG_SIZE_WIDTH,
                MnistModelConfig.INPUT_IMG_SIZE_HEIGHT,
                false);
        Canvas canvas = new Canvas(bmpGrayscale);
        canvas.drawBitmap(bmpGrayscale, 0, 0, paint);
        return bmpGrayscale;
    }
    private void loadMnistClassifier() {
        try {
            classifier = classifier.classifier(getAssets(), MnistModelConfig.MODEL_FILENAME);                                 // model_Name is important. I was loading a wrong model and getting weird results
        } catch (IOException e) {
            Toast.makeText(this, "MNIST model couldn't be loaded. Check logs for details.", Toast.LENGTH_SHORT).show();    // Safety
            e.printStackTrace();
        }
    }
}