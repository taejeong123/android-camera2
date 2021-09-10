package com.example.bitflow;


import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

public class MainActivity extends FragmentActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 100;

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (null == savedInstanceState) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CODE);
                finish();
            } else {
                getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container, CameraFragmentActivity.newInstance())
                        .commit();
            }
        }
    }
}