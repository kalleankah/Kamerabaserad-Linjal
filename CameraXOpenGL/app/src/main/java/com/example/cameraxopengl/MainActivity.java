package com.example.cameraxopengl;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraXConfig;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.UseCase;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements CameraXConfig.Provider {
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Executor executor = Executors.newSingleThreadExecutor();
    private GLRenderer renderer;
    private GLSurfaceView glSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        glSurfaceView = findViewById(R.id.glsurfaceview);
        renderer = new GLRenderer(glSurfaceView);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        checkCameraPermission();
        loadOpenCV();

        glSurfaceView.post(() -> startCamera());
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 100);
            while(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void loadOpenCV() {
        Log.d("OpenCVManager", "Waiting for OpenCV to load...");
        while(!OpenCVLoader.initDebug()){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.d("OpenCVManager", "OpenCV loaded successfully.");
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(new Size(1920, 1080))
//                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();

        imageAnalysis.setAnalyzer(executor, renderer);

        cameraProviderFuture.addListener(() -> {
            ProcessCameraProvider cameraProvider = null;
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
            assert cameraProvider != null;
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis);
        }, ContextCompat.getMainExecutor(this));
    }

    @NonNull
    @Override
    public CameraXConfig getCameraXConfig() {
        return Camera2Config.defaultConfig();
    }
}
