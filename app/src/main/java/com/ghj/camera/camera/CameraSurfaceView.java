package com.ghj.camera.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.List;

public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private final String TAG = "CameraSurfaceView";

    Camera mCamera;
    Camera.Size mPreviewSize;
    int mOrientation;
    SurfaceHolder mSurfaceHolder;


    public CameraSurfaceView(Context context, Camera camera, Camera.Size previewSize, int orientation) {
        super(context);
        this.mCamera = camera;
        this.mPreviewSize = previewSize;
        this.mOrientation = orientation;
        this.mSurfaceHolder = getHolder();
        this.mSurfaceHolder.addCallback(this);
        this.mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if(mOrientation == Surface.ROTATION_0) {
            setMeasuredDimension(mPreviewSize.height, mPreviewSize.width);
            mCamera.setDisplayOrientation(90);
        }
        else if(mOrientation == Surface.ROTATION_90) {
            setMeasuredDimension(mPreviewSize.width, mPreviewSize.height);
            mCamera.setDisplayOrientation(0);
        }
        else if(mOrientation == Surface.ROTATION_270) {
            setMeasuredDimension(mPreviewSize.width, mPreviewSize.height);
            mCamera.setDisplayOrientation(180);
        }
    }

    // 서피스뷰 생성
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "[CameraSurfaceView] surfaceCreated");
        try {
            // 프리뷰 시작
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "[CameraSurfaceView] surfaceChanged width=" + width + " , height=" + height);
        if( mSurfaceHolder.getSurface() == null ) return;
        try {
            mCamera.stopPreview();

            // 프리뷰 멈추고 다시 시작
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "[CameraSurfaceView] surfaceDestroyed");
        closeCamera();
    }

    public void closeCamera() {
        if(mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }
}
