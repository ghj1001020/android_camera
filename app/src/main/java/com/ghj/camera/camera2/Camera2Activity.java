package com.ghj.camera.camera2;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ghj.camera.R;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Activity extends AppCompatActivity {

    private final int PERMISSIONS_REQ_CODE = 10000;
    private String[] permissions = new String[] {
            Manifest.permission.CAMERA
    };

    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        // 전체화면
        if(Build.VERSION_CODES.R <= Build.VERSION.SDK_INT) {
            getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
        }
        else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback2() {
            // 크기조정후 화면을 다시 그려야할때
            @Override
            public void surfaceRedrawNeeded(@NonNull SurfaceHolder holder) { }

            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean isGranted = checkPermission();
        if(isGranted) {
            start();
        }
        else {
            requestPermission();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // 권한체크
    private boolean checkPermission() {
        if(permissions == null) {
            return true;
        }

        int grantCnt = 0;
        for(String perm : permissions) {
            if( ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED ) {
                grantCnt++;
            }
        }
        return permissions.length == grantCnt;
    }

    // 권한요청
    private void requestPermission() {
        ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQ_CODE);
    }

    // 권한요청 결과
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 카메라 권한
        if(requestCode == PERMISSIONS_REQ_CODE) {
            int grantCnt = 0;
            for(int grant : grantResults) {
                if(grant == PackageManager.PERMISSION_GRANTED) {
                    grantCnt++;
                }
            }
            // 동의
            if(grantCnt == grantResults.length) {
                start();
            }
            // 거부
            else {
                new AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setMessage("카메라 권한이 필요합니다.")
                        .setPositiveButton("확인", ((dialog, which) -> {
                            finish();
                        }))
                        .show();
            }
        }
    }


    // 카메라 프리뷰
    public void start() {

    }
}
