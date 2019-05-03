package parisa.esh19.com.capturingpixelservice;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import parisa.esh19.com.capturingpixelservice.databinding.ActivityMainBinding;

/**
 * Created by Parisa Eshraghi on 30/06/2018
 *
 * Based on "https://github.com/murmurmuk/PartialScreenshots"
 *
 **/

public class MainActivity extends Activity {
    public static MediaProjection sMediaProjection;
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 5566;
    private static final int REQUEST_CODE = 55566;
    private ActivityMainBinding binding;
    private MediaProjectionManager mProjectionManager;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        checkDrawOverlayPermission();
        checkWritePermission();



        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        binding.test.setOnClickListener(view -> {
            if(!checkDrawOverlayPermission()){
                checkDrawOverlayPermission();
                return;
            }
            if(!checkWritePermission()){
                checkWritePermission();
                return;
            }
            startMediaProjection();
        });

    }


    private boolean checkWritePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_CONTACTS);
            return false;
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startMediaProjection() {
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }

    private boolean checkDrawOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("kanna","get write permission");
                }
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            sMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
            if (sMediaProjection == null) {
                Log.d("kanna", "not get permission of media projection");
                Toast.makeText(this, "Need MediaProjection", Toast.LENGTH_LONG).show();
                startMediaProjection();
            } else {
                startBubble();
            }
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (sMediaProjection == null) {
            binding.test.setVisibility(View.VISIBLE);
        }
    }

    private void startBubble() {
        Log.d("kanna","start bubble");
        binding.test.setVisibility(View.GONE);
        Intent intent = new Intent(this, BubbleService.class);
        stopService(intent);
        startService(intent);
    }


}
