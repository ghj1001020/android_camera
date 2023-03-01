package com.ghj.camera.camera;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private final String TAG = "CameraSurfaceView";


    Camera mCamera;
    Camera.Size mPreviewSize;
    SurfaceHolder mSurfaceHolder;


    public CameraSurfaceView(Context context, Camera camera) {
        super(context);
        this.mCamera = camera;
        this.mSurfaceHolder = getHolder();
        this.mSurfaceHolder.addCallback(this);
        this.mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
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
        Log.d(TAG, "[CameraSurfaceView] surfaceChanged");
        if( mSurfaceHolder.getSurface() == null ) return;
        try {
            mCamera.stopPreview();

            Camera.Parameters parameters = mCamera.getParameters();
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            mPreviewSize = getOptimalPreviewSize(sizes, width, height);
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            mCamera.setParameters(parameters);

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
        if(mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    // 프리뷰 사이즈구하기
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int width, int height) {
        Log.d(TAG, "[getOptimalPreviewSize]1 " + width + " , " + height);

        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) width / height;
        if(sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = height;

        for(Camera.Size size : sizes) {
            Log.d(TAG, "[getOptimalPreviewSize]2 " + size.width + " , " + size.height);

            double ratio = (double) size.height / size.width;
            if(Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if(Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if(optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for(Camera.Size size : sizes) {
                if(Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        return optimalSize;
    }
}
