package com.example.cameraxopengl;

import android.Manifest;
import android.content.ContentProviderClient;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MotionEventCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

// This class creates a CameraX session and a glSurfaceView container to put the camera preview in.
// It also creates a GLRenderer object which provides a custom GLSurfaceView.Renderer for the
// glSurfaceView and an ImageAnalysis.Analyzer to analyze each camera frame.

public class MainActivity extends AppCompatActivity implements NumberPicker.OnValueChangeListener {
    private GLRenderer renderer;
    //private int cameraPreviewWidth = 720;
    //private int cameraPreviewHeight = 1280;
    private int cameraPreviewWidth = 1080;
    private int cameraPreviewHeight = 1920;
    boolean userPoint = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // "glSurfaceView" is the layout container for the camera preview
        GLSurfaceView glSurfaceView = findViewById(R.id.glsurfaceview);

        // "renderer" is an instance of the custom class GLRenderer which implements a
        // GLSurfaceView.Renderer and an ImageAnalysis.Analyzer
        renderer = new GLRenderer(glSurfaceView, cameraPreviewWidth, cameraPreviewHeight);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setEGLContextClientVersion(3);

        // Use the implemented GLSurfaceView.Renderer functions in the GLRender object "renderer"
        glSurfaceView.setRenderer(renderer);

        // Only perform a render when data in the glSurfaceView has updated
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // Create a text field to enter marker size
        NumberPicker markerSizeInput = findViewById(R.id.marker_size_input);
        markerSizeInput.setMinValue(1);
        markerSizeInput.setMaxValue(1000);
        markerSizeInput.setValue(50);
        markerSizeInput.setOnValueChangedListener(this);
        renderer.setMarkerSize(markerSizeInput.getValue());

        // Get user interaction coordinates
        glSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (MotionEventCompat.getActionMasked(event) == event.ACTION_UP) {
                    int point_x = (int) event.getX();
                    int point_y = (int) event.getY();
                    Log.d("MotionEvent", "[ " + point_x + ", " + point_y + " ]");
                    renderer.pxToWC(point_x, point_y);
                    return true;
                }

                return true;
            }
        });


        final Button placePointButton = findViewById(R.id.placePoint);
        placePointButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // TODO: I mån av tid, lås hjulet
                renderer.placePoint();
            }
        });

        final Button showInputButton = findViewById(R.id.infoButton);
        final LinearLayout markerSizeBox = findViewById(R.id.markerSizeBox);

        showInputButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (markerSizeBox.getVisibility() == View.VISIBLE) {
                    markerSizeBox.setVisibility(View.INVISIBLE);
                    showInputButton.setText("Set Marker Size");
                }
                else {
                    markerSizeBox.setVisibility(View.VISIBLE);
                    showInputButton.setText("Close");
                }
            }
        });

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
                //.setTargetResolution(new Size(720, 1280))
                .setTargetResolution(new Size(cameraPreviewWidth, cameraPreviewHeight))
                .build();
        // Run analysis on a new executor thread. The object "renderer" is an instance of the custom
        // class GLRenderer which implements an image analyzer function
        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), renderer);

        cameraProviderFuture.addListener(() -> {
            try {
                // The lifecycle of the camera is tied to the lifecycle of the object calling "startCamera()"
                // which in this case is the container for the preview "glSurfaceView"
                cameraProviderFuture.get().bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis);
            }
            catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        renderer.setMarkerSize((float) newVal);
    }
}
