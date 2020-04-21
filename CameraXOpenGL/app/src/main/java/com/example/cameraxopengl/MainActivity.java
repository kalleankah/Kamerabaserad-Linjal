package com.example.cameraxopengl;

import android.Manifest;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

// This class creates a CameraX session and a glSurfaceView container to put the camera preview in.
// It also creates a GLRenderer object which provides a custom GLSurfaceView.Renderer for the
// glSurfaceView and an ImageAnalysis.Analyzer to analyze each camera frame.

public class MainActivity extends AppCompatActivity {
    private GLRenderer renderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // "glSurfaceView" is the layout container for the camera preview
        GLSurfaceView glSurfaceView = findViewById(R.id.glsurfaceview);
        // "renderer" is an instance of the custom class GLRenderer which implements a
        // GLSurfaceView.Renderer and an ImageAnalysis.Analyzer
        renderer = new GLRenderer(glSurfaceView);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setEGLContextClientVersion(3);
        // Use the implemented GLSurfaceView.Renderer functions in the GLRender object "renderer"
        glSurfaceView.setRenderer(renderer);
        // Only perform a render when data in the glSurfaceView has updated
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        checkCameraPermission();
        loadOpenCV();

        // Start the camera and bind its lifecycle to the glSurfaceView object. The camera is alive
        // as long as the glSurfaceView object is alive
        glSurfaceView.post(this::startCamera);
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
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        // Create an imageAnalysis Use Case for the camera session.
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                //If new camera frames are delivered faster than the analysis is done, skip frames
                // in between and only perform analysis on the most recent frame.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(new Size(1920, 1080))
                .build();

        // Run analysis on a new executor thread. The object "renderer" is an instance of the custom
        // class GLRenderer which implements an image analyzer function
        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), renderer);

        cameraProviderFuture.addListener(() -> {
            ProcessCameraProvider cameraProvider = null;

            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
            assert cameraProvider != null;

            // The lifecycle of the camera is tied to the lifecycle of the object calling "startCamera()"
            // which in this case is the container for the preview "glSurfaceView"
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis);

            }, ContextCompat.getMainExecutor(this));
    }
}
