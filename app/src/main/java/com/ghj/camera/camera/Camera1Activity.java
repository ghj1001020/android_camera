package com.ghj.camera.camera;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.ghj.camera.R;
import com.ghj.camera.camera2.Camera2Activity;
import com.ghj.camera.common.Code;
import com.ghj.camera.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Camera1Activity extends AppCompatActivity {

    private final String TAG = "Camera1Activity";


    FrameLayout mPreviewLayout;
    ImageButton mPictureBtn;

    // 권한
    private final int PERMISSIONS_REQ_CODE = 10000;
    private String[] permissions = new String[]{
            Manifest.permission.CAMERA
    };
    int mCameraPermission = Code.PERMISSION.PERMISSION_UNKNOWN; // 카메라 권한여부

    // 카메라
    Camera mCamera;
    Camera.Size mPreviewSize;
    CameraSurfaceView mPreview;
    int mCameraId = 0;

    // 최대 너비 높이
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    // 화면캡쳐 이미지
    private File mImageDir;
    private File mImageFile;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera1);

        mPreviewLayout = findViewById(R.id.cameraPreview);
        mPictureBtn = findViewById(R.id.picture);
        mPictureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        // 이미지 저장 폴더만들기 DCIM > cameraApp
        mImageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "cameraApp");
        if(!mImageDir.exists()) {
            mImageDir.mkdir();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 카메라 하드웨어 체크
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            error(getString(R.string.no_camera_feature));
            return;
        }

        openCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    // 카메라 열기
    public void openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
            return;
        }

        Camera camera = null;
        Camera.Size cameraSize = null;
        // Camera Id 후 Camera 생성
        int count = Camera.getNumberOfCameras();
        for(int i=0; i<count; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);

            if(info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                continue;
            }

            // 화면방향
            int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
            int sensorOrientation = info.orientation;
            Log.d(TAG, displayRotation + " , " + sensorOrientation);
            boolean isSwapped = false;  // 화면, 카메라방향이 다른지 여부
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if(sensorOrientation == 90 || sensorOrientation == 270) {
                        isSwapped = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if(sensorOrientation == 0 || sensorOrientation == 270) {
                        isSwapped = true;
                    }
                    break;
            }

            int width = mPreviewLayout.getWidth();
            int height = mPreviewLayout.getHeight();
            // 화면사이즈
            Point displaySize = new Point();
            getWindowManager().getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if(isSwapped) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            mCameraId = i;

            camera = getCameraInstance(mCameraId);
            if(camera != null) {
                List<Camera.Size> previewSizes = camera.getParameters().getSupportedPreviewSizes();
                for(int pi=0; pi<previewSizes.size(); pi++) {
                    Camera.Size tempSize = previewSizes.get(pi);
                    if(tempSize.width <= maxPreviewWidth && tempSize.height <= maxPreviewHeight) {
                        cameraSize = tempSize;
                        break;
                    }
                }
                break;
            }
        }

        mCamera = camera;
        mPreview = new CameraSurfaceView(this, mCamera, cameraSize);
        mPreviewLayout.addView(mPreview);
    }

    // 카메라 닫기
    public void closeCamera() {
        if(mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    // 권한요청
    private void requestPermission() {
        int perm = Util.checkPermission(this, permissions);
        Log.d(TAG, "[requestPermission] " + perm);
        if(perm == Code.PERMISSION.PERMISSION_RATIONALE) {
            mCameraPermission = Code.PERMISSION.PERMISSION_RATIONALE;
            showPermissionDenied();
        }
        else if(perm == Code.PERMISSION.PERMISSION_DENIED) {
            if(mCameraPermission == Code.PERMISSION.PERMISSION_UNKNOWN) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQ_CODE);
            }
        }
    }

    // 카메라가 사진을 찍은직후
    Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {

        }
    };

    // 촬영한 사진을 바이트 배열로 반환
    Camera.PictureCallback mRawCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

        }
    };

    // jpeg 로 만들어진 아미지를 바이트 배열로 반환
    Camera.PictureCallback mJpegCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

        }
    };

    // 카메라 인스턴스 생성
    public Camera getCameraInstance(int cameraId) {
        Camera camera = null;
        try {
            camera = Camera.open(cameraId);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return camera;
    }

    // 사진촬영
    public void takePicture() {
        if(mCamera != null) {
            mCamera.takePicture(mShutterCallback, mRawCallback, mJpegCallback);
        }
    }

    // 에러발생
    private void error(String message) {
        closeCamera();
        Util.alert(this, "에러", message, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
    }

    // 퍼미션 권한 없음
    private void showPermissionDenied() {
        Util.confirm(this, getString(R.string.permission_denied), (dialog, which) -> {
            mCameraPermission = Code.PERMISSION.PERMISSION_UNKNOWN;
            Util.goToPermissionSetting(this);
        }, (dialog, which) -> {
            finish();
        });
    }

    // 프리뷰 사이즈 비교
    private static class CompareSizes implements Comparator<Camera.Size> {
        @Override
        public int compare(Camera.Size o1, Camera.Size o2) {
            // 부호반환 양수 1, 0, 음수 -1
            return Long.signum((long) o1.width * o1.height - (long) o2.width * o2.height);
        }
    }
}
