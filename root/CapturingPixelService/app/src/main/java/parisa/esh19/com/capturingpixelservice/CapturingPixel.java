package parisa.esh19.com.capturingpixelservice;

import android.annotation.TargetApi;
import android.app.Activity;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import customview.DrawView;

import parisa.esh19.com.capturingpixelservice.databinding.RectangularLayoutBinding;

/**
 * Created by Parisa Eshraghi on 30/06/2018
 *
 * Author: Parisa Eshraghi
 *
 * This class is used to capture the pixels' values of the region of the interest
 *
 **/


public class   CapturingPixel extends Activity {


    RectangularLayoutBinding binding;
    Button capturebtn, stopbtn;
    int xlow, Width;
    int ylow, Height;
    int rad;
    private Handler mHandler = new Handler();
    int number,pp;


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.rectangular_layout);


        capturebtn = (Button)findViewById(R.id.buttonCapture);
        stopbtn= (Button) findViewById(R.id.buttonStop);

        capturebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mToastRunnale.run();
                Toast.makeText(getBaseContext(), " Capture !", Toast.LENGTH_LONG).show();
                Log.i(" capture ", "capture!!");
            }
        });

        stopbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mHandler.removeCallbacks( mToastRunnale);
                Toast.makeText(getBaseContext(), " stop !", Toast.LENGTH_LONG).show();
                Log.i(" stop ", "stop!!");
            }
        });

    }

    private Runnable mToastRunnale = new Runnable() {
        @Override
        public void run() {


            // create bitmap screen capture
            View v1 = getWindow().getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            Bitmap bmpOne = Bitmap.createBitmap(v1.getDrawingCache());
            v1.setDrawingCacheEnabled(false);

            //get the value after dropping the touch
            rad= DrawView.getrad();
            xlow = DrawView.getxCord()+rad;
            ylow= DrawView.getyCord()+rad;

            Width= DrawView.getwidthOne();
            Height=DrawView.getheightOne();



            int[] pixels = new int[bmpOne.getWidth() * bmpOne.getHeight()];

            int[] sqrPixels = new int[(Width+1)*(Height+1)];

            bmpOne.getPixels(pixels, 0, bmpOne.getWidth(), 0, 0, bmpOne.getWidth(), bmpOne.getHeight());


            number=0;
            pp=0;

            for (int j = 0; j <= Height ; j++){
                for (int i = xlow + (ylow+j)*bmpOne.getWidth(); i <= xlow + (ylow+j)*bmpOne.getWidth() + Width ; i++)
                {
                    sqrPixels[pp]=pixels[i];

                    pp++;

                }

            }
            for (int n = 0; n< sqrPixels.length; n++) {
                int r = Color.red(sqrPixels[n]);
                int g = Color.green(sqrPixels[n]);
                int b = Color.blue(sqrPixels[n]);

                Log.i(" Color ", r + " " + g + " " + b + " " + n);

            }



            // Testing if the coordinates are correct
            Log.i(" Dime ", " x" +xlow +" y" +ylow);
            Log.i(" Dime ",  " w"+Width +"h"+ Height +" " +rad);
            Log.i(" Dime ", " " +(float) xlow/bmpOne.getWidth() +" " + (float) ylow/bmpOne.getHeight());




            //Testing the RGB vaues
            //  int r = Color.red(sqrPixels[10]);
            //  int g = Color.green(sqrPixels[10]);
            //  int b = Color.blue(sqrPixels[10]);



            mHandler.postDelayed(this, 10000);

        }

    };




}
