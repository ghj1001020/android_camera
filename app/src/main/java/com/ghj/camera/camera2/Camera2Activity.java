package com.ghj.camera.camera2;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.ghj.camera.R;
import com.ghj.camera.common.Code;
import com.ghj.camera.util.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;


// CameraCharacteristics : cameraId 선택
// CameraManager.openCamera
// CaptureRequest TEMPLATE_PREVIEW 설정
// CameraCaptureSession 생성
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Activity extends AppCompatActivity implements View.OnClickListener {

    private final String TAG = "Camera2Activity";


    private final int PERMISSIONS_REQ_CODE = 10000;
    private String[] permissions = new String[]{
            Manifest.permission.CAMERA
    };

    // 카메라 권한여부
    int mCameraPermission = Code.PERMISSION.PERMISSION_UNKNOWN;

    // 화면 orientation 과 카메라센서 orientation 매핑
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);    // 세로
        ORIENTATIONS.append(Surface.ROTATION_90, 0);    // 가로왼쪽
        ORIENTATIONS.append(Surface.ROTATION_180, 270); // 세로180도로 뒤집힌 경우는 없음
        ORIENTATIONS.append(Surface.ROTATION_270, 180); // 가로오른쪽
    }

    // 카메라2 API 가 보장하는 최대 너비 높이
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    AutoFitTextureView mTextureView;
    ImageButton mPicture;
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    // 카메라 상태
    private int mState = STATE_PREVIEW;
    private static final int STATE_PREVIEW = 0; // 프리뷰 보여주고 있음
    private static final int STATE_WAITING_LOCK = 1;    // 이미지캡쳐 전 화면 고정
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;

    // 카메라
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;  // 프리뷰 사이즈
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;

    private int LENS_FACE = CameraCharacteristics.LENS_FACING_BACK; // LENS_FACING_BACK - 후면, LENS_FACING_FRONT - 전면
    private int mSensorOrientation; // 카메라 센서방향

    // 후레쉬
    private boolean mFlashSupported = false;

    // 화면캡쳐 이미지
    private ImageReader mImageReader;
    private File mImageDir;
    private File mImageFile;

    // 카메라가 닫기전에 앱이 종료되지 않도록 카메라 동기화락
    Semaphore mCameraLock = new Semaphore(1);

    // 설정 > 앱 > 권한부여 결과
//    ActivityResultLauncher<Intent> permissionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
//        openCamera(mTextureView.getWidth(), mTextureView.getHeight());
//    });


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        // 전체화면
        if (Build.VERSION_CODES.R <= Build.VERSION.SDK_INT) {
            getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        // 이미지 저장 폴더만들기 DCIM > cameraApp
        mImageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "cameraApp");
        if(!mImageDir.exists()) {
            mImageDir.mkdir();
        }

        mTextureView = findViewById(R.id.textureView);
        mPicture = findViewById(R.id.picture);
        mPicture.setOnClickListener(this);

    }

    @Override
    protected void onResume() {
        super.onResume();

        // 카메라 하드웨어 체크
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            error(getString(R.string.no_camera_feature));
            return;
        }

        startBackgroundThread();

        if(mTextureView.isAvailable()) {
            Log.d(TAG, "[onResume] openCamera");
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        }
        else {
            Log.d(TAG, "[onResume] mTextureView.setSurfaceTextureListener");
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.picture:
                if(mCameraDevice != null && mCaptureSession != null)
                    takePicture();
                break;
        }
    }

    // 카메라 이미지 캡쳐
    private void takePicture() {
        String filename = "cameraApp_" + Util.getToday("yyyyMMdd_HHmmss") + ".jpg";
        mImageFile = new File(mImageDir, filename);
        lockFocus();
    }

    // 포커스 고정 후 이미지캡쳐
    private void lockFocus() {
        try {
            // 오토포커스 트리거
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 이미지캡쳐 후 포커스 풀기
    private void unlockFocus() {
        try {
            // 오토포커스 트리거 리셋
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);

            // 카메라 프리뷰 시작
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
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

    // 권한요청 결과
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        int grantCnt = 0;
        for (int grant : grantResults) {
            if (grant == PackageManager.PERMISSION_GRANTED) {
                grantCnt++;
            }
        }

        // 카메라 권한
        if (requestCode == PERMISSIONS_REQ_CODE) {
            // 거부
            if (grantCnt != grantResults.length) {
                mCameraPermission = Code.PERMISSION.PERMISSION_DENIED;
                showPermissionDenied();
            }
            // 허용
            else {
                mCameraPermission = Code.PERMISSION.PERMISSION_GRANTED;
            }
        }
    }

    // 서피스뷰 콜백
    TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "[SurfaceTextureListener] onSurfaceTextureAvailable " + width + " , " + height);
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "[SurfaceTextureListener] onSurfaceTextureSizeChanged " + width + " , " + height);
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            Log.d(TAG, "[TextureView.SurfaceTextureListener] onSurfaceTextureDestroyed");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    // 카메라 상태 콜백
    CameraDevice.StateCallback mDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraLock.release();
            mCameraDevice = camera; // 선택한 ID의 카메라를 조작할 수 있는 Camera Device
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraLock.release();
            closeCameraDevice();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            error("카메라 열기 실패");
        }
    };

    // 화면프리뷰 상태 콜백
    private CameraCaptureSession.StateCallback mCaptureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            try {
                if(mCameraDevice == null) {
                    return;
                }

                mCaptureSession = session;
                // 오토포커스 알고리즘은 지속적으로 초점이 맞는 이미지 스트림을 제공하기 위해 렌즈 위치를 지속적으로 수정합니다.
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 세션에 리퀘스트를 넣어줘서 리퀘스트의 기능 수행
                // 카메라 프리뷰 시작
                mPreviewRequest = mPreviewRequestBuilder.build();
                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mHandler);   // 무한반복 화면 캡쳐 (프리뷰)
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
                error("카메라 프리뷰 실패");
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            error("카메라 프리뷰 실패");
        }
    };

    // 화면캡쳐 콜백
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            switch (mState) {
                // 처음 ~ 프리뷰
                case STATE_PREVIEW:
//                    Log.d(TAG, "[CameraCaptureSession.CaptureCallback] STATE_PREVIEW");
                    break;
                case STATE_WAITING_LOCK:
                    Log.d(TAG, "[CameraCaptureSession.CaptureCallback] STATE_WAITING_LOCK");
                    // 오토포커스 현재 상태
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    Log.d(TAG, "[CameraCaptureSession.CaptureCallback] STATE_WAITING_LOCK, afState=" + afState);

                    if(afState == null) {
                        captureStillPicture();
                    }
                    // 오토포커스 초점 잡은후 잠김 or 오토포커스 초점 잡히지 않은체 잠김
                    else if(afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        // 자동노출 현재 상태
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        // 현재화면에 자동노출 제어값이 있음
                        if(aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        }
                        else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                case STATE_WAITING_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if(aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if(aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {

            process(result);
        }
    };

    // 이미지캡쳐전 사전캡쳐 시퀀스 실행
    private void runPrecaptureSequence() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // 사전캡쳐 시퀀스가 설정될때까지 기다름
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 이미지 캡쳐
    private void captureStillPicture() {
        if(mCameraDevice == null) {
            return;
        }
        try {
            // 이미지 캡쳐
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // 오토포커스 알고리즘은 지속적으로 초점이 맞는 이미지 스트림을 제공하기 위해 렌즈 위치를 지속적으로 수정합니다.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // Orientation
//            int rotation = getResources().getConfiguration().orientation;  // 1=세로, 2=가로
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();

            CameraCaptureSession.CaptureCallback mImageCaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    Log.d(TAG, mImageFile.toString());
                    broadcastImage();
                    unlockFocus();
                }
            };

            shootSound();

            mCaptureSession.capture(captureBuilder.build(), mImageCaptureCallback, null);
        }
        catch ( CameraAccessException e ) {
            e.printStackTrace();
        }
    }

    // 지정된 화면에서 JPEG 방향구하기
    // rotation : 화면 orientation
    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    // 캡쳐이미지 저장 할때 호출
    private ImageReader.OnImageAvailableListener mImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "[OnImageAvailableListener] onImageAvailable");
            mHandler.post(new ImageSaver(reader.acquireNextImage(), mImageFile));
        }
    };

    // 백그라운드 쓰레드 시작
    private void startBackgroundThread() {
        mHandlerThread = new HandlerThread("CAMERA_HANDER_THREAD");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    // 백그라운드 쓰레드 중지
    private void stopBackgroundThread() {
        if(mHandlerThread != null ) {
            try {
                mHandlerThread.quitSafely();
                mHandlerThread.join();
                mHandlerThread = null;
                mHandler = null;
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 카메라 열기
    public void openCamera(int width, int height) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
            return;
        }

        setUpCameraOutputs(width, height);
        configureTransform(width, height);

        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            cm.openCamera(mCameraId, mDeviceCallback, mHandler);
        }
        catch ( CameraAccessException e ) {
            e.printStackTrace();
            error("카메라 열기 실패");
        }
    }

    // 카메라 설정
    private void setUpCameraOutputs(int width, int height) {
        // CameraManager 를 이용한 openCamera
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            for (String _cameraId : cm.getCameraIdList()) {
                CameraCharacteristics characteristics = cm.getCameraCharacteristics(_cameraId);

                // 전면 카메라 사용 안함
                Integer lens = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (lens == null || lens == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                // 카메라 장치가 지원하는 사용가능한 스트림 구성
                StreamConfigurationMap scm = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (scm == null) {
                    continue;
                }

                // JPEG 이미지포맷에 맞는 가장 큰 프리뷰 사이즈 선택
                Size[] previewSizes = scm.getOutputSizes(ImageFormat.JPEG);
                if (previewSizes == null || previewSizes.length == 0) {
                    continue;
                }

                // 사용가능한 가장 큰 캡쳐 이미지 사용
                Size largest = Collections.max(Arrays.asList(previewSizes), new CompareSizes());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 7);
                mImageReader.setOnImageAvailableListener(mImageAvailableListener, mHandler);

                // 화면방향
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION); // 카메라방향
                boolean isSwapped = false;  // 화면, 카메라방향이 다른지 여부
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if(mSensorOrientation == 90 || mSensorOrientation == 270) {
                            isSwapped = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if(mSensorOrientation == 0 || mSensorOrientation == 180) {
                            isSwapped = true;
                        }
                        break;
                }

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

                if(maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }
                if(maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // 너무 큰 preview를 사용하면 가비지 데이터가 캡쳐됨
                mPreviewSize = chooseOptimalSize(scm.getOutputSizes(SurfaceTexture.class), rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);

                // surface 뷰의 크기를 선택한 프리뷰 크기에 맞춤
                int orientation = getResources().getConfiguration().orientation;
                if(orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                }
                else {
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // 후레시 지원하는지 체크
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available != null && available;

                mCameraId = _cameraId;
                return;
            }
        }
        catch( CameraAccessException e ) {
            e.printStackTrace();
            error("카메라 열기 실패");
        }
        catch( Exception e ) {
            e.printStackTrace();
            error("카메라 열기 실패");
        }
    }

    // 카메라 프리뷰, textureView 의 크기가 결정되고 난 후 호출
    private void configureTransform(int viewWidth, int viewHeight) {
        if(mTextureView == null || mPreviewSize == null) {
            return;
        }

        int rotation = getWindowManager().getDefaultDisplay().getRotation();    // 화면 회전상태
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if(Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewHeight / mPreviewSize.getHeight(), (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        else if( Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    // 프리뷰 생성
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            if(texture == null) {
                return;
            }

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);

            // 프리뷰에 이미지을 넣어주는 리퀘스트 빌더 생성
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);  // 이미지를 넣어주는 타겟

            // 세선 : 카메라에서 이미지를 받아오는 흐름
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mCaptureStateCallback, mHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 이미지 캡쳐
    private static class ImageSaver implements Runnable {

        private final Image mImage;
        private final File mFile;

        ImageSaver(Image image, File file) {
            this.mImage = image;
            this.mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(mFile);
                fos.write(bytes);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                mImage.close();
                if(fos != null) {
                    try { fos.close(); } catch( IOException e ) { }
                }
            }
        }
    }

    // 프리뷰 사이즈 비교
    private static class CompareSizes implements Comparator<Size>  {
        @Override
        public int compare(Size o1, Size o2) {
            // 부호반환 양수 1, 0, 음수 -1
            return Long.signum((long) o1.getWidth() * o1.getHeight() - (long) o2.getWidth() * o2.getHeight());
        }
    }

    private Size chooseOptimalSize(Size[] choices, int previewWidth, int previewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        // preview 만큼 충분히 큰 해상도
        List<Size> bigEnough = new ArrayList<>();
        // preview 보다 작은 해상도
        List<Size> notBigEnough = new ArrayList<>();

        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for(Size option : choices) {
            // 최대 가로,세로크기 보다 작으면서, 프리뷰화면의 세로/가로 비율이 맞아야됨
            if(option.getWidth() <= maxWidth && option.getHeight() <= maxHeight && option.getHeight() == option.getWidth() * h / w) {
                // 프리뷰화면의 가로,세로보다 크다
                if(option.getWidth() >= previewWidth && option.getHeight() >= previewHeight) {
                    bigEnough.add(option);
                }
                // 프리뷰화면의 가로,세로보다 작다
                else {
                    notBigEnough.add(option);
                }
            }
        }

        // 충분히 큰것중에 가장 작은것 또는 충분히 크지 않은것중 가장 큰것
        if(bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizes());
        }
        else if(notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizes());
        }
        else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    // 카메라 닫기
    private void closeCamera() {
        try {
            mCameraLock.acquire();

            if( mCaptureSession != null ) {
                mCaptureSession.close();
                mCaptureSession = null;
            }

            closeCameraDevice();

            if( mImageReader != null ) {
                mImageReader.close();
                mImageReader = null;
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        finally {
            mCameraLock.release();
        }
    }

    // 카메라 디바이스 닫기
    private void closeCameraDevice() {
        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    // 에러발생
    private void error(String message) {
        closeCameraDevice();
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

    // 카메라 소리
    private void shootSound() {
        MediaActionSound sound = new MediaActionSound();
        if(sound != null) {
            sound.play(MediaActionSound.SHUTTER_CLICK);
        }
    }

    // 찍은 이미지 브로드캐스트
    private void broadcastImage() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(mImageFile));
        sendBroadcast(intent);
    }
}
