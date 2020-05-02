package com.example.cameraxopengl;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import org.jetbrains.annotations.NotNull;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static org.opencv.aruco.Aruco.DICT_6X6_50;
import static org.opencv.aruco.Aruco.detectMarkers;
import static org.opencv.aruco.Aruco.estimatePoseSingleMarkers;
import static org.opencv.aruco.Aruco.getPredefinedDictionary;
import static org.opencv.core.Core.ROTATE_90_CLOCKWISE;
import static org.opencv.core.Core.rotate;
import static org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.putText;

// The class GLRenderer implements a custom GLSurfaceView.Renderer to handle rendering to a
// GLSurfaceView. It also provides an ImageAnalysis.Analyzer to perform image analysis on each
// frame. A texture is generated and the rendering is performed  in an OpenGL shader which is called
// on each frame.

class GLRenderer implements GLSurfaceView.Renderer, ImageAnalysis.Analyzer {
    private GLSurfaceView glSurfaceView;
    private int[] textures = {0};
    private float markerLength;
    private Shader shader;
    private MarkerContainer markerContainer = new MarkerContainer();
    private ExecutorService executor;
    private Bitmap imageBitmap;
    private Mat imageMat;

    // Constructor that sets up the
    GLRenderer(GLSurfaceView view, int cameraPreviewWidth, int cameraPreviewHeight){
        glSurfaceView = view;

        // Create an empty bitmap to place in the glSurfaceView until the first frame is rendered
        imageBitmap = Bitmap.createBitmap(cameraPreviewWidth, cameraPreviewHeight, Bitmap.Config.ARGB_8888);

        // Set up the executor to handle Marker Detection in the background
        int poolsize = 1; int queuesize = 1;
        executor = new ThreadPoolExecutor(poolsize, poolsize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queuesize),
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        // Generate a texture to put the image frame in
        generateTexture();
        // Instantiate shader
        generateShader();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Prepare the OpenGL context
        renderTexture();

        // Run the shader to render the camera preview
        shader.draw(textures[0]);

        // Draw markers (if found) on top of the preview
        if(markerContainer.isNotEmpty()){
            shader.drawMarkerGL(markerContainer.getMarkerCorners());
            Log.d("Distance" , "" + (int) markerContainer.getDistance() + "mm");

            // If two markers are found
            if(markerContainer.getNumMarkers() >= 2){
                shader.drawLine(markerContainer.getMarkerMidpoint(0), markerContainer.getMarkerMidpoint(1), markerContainer.getDepths());
            }
        }
    }

    @Override
    public void analyze(@NonNull ImageProxy proxy) {
        long analyzeTime = System.currentTimeMillis();
        // imageMat gets assigned
        setImageMatFromProxy(proxy);
        proxy.close();

        // USE EITHER ASYNCHRONOUS OR SYNCHRONOUS MARKER DETECTION
//        markerDetectionAsynchronous();
        markerDetectionSynchronized();

        // Draw marker distance
        if(imageMat != null){
            putText(imageMat, (int)markerContainer.getDistance() + "mm" , new Point(0, imageMat.height()), 1, 2, new Scalar(255, 0, 0), 2, 8);
            Bitmap bm = Bitmap.createBitmap(imageMat.cols(), imageMat.rows(), Bitmap.Config.ARGB_8888);
            setImageBitmapFromMat(imageMat);
        }
        else{
            setImageBitmapFromMat();
        }

        glSurfaceView.requestRender();
        Log.d("Analysis time", "" + (int) (System.currentTimeMillis() - analyzeTime) + "ms");
    }

    // Asynchronously detect ArUco markers by executing an instance of MarkerDetector in the background
    // Camera preview is rendered regardless if marker coordinates are found.
    // If markers are found, they will be rendered the next time onDrawFrame() is called
    private void markerDetectionAsynchronous() {
        // The executor needs to work on a copy of the bitmap to prevent it from being recycled
        executor.execute(new MarkerDetector(imageMat.clone(), markerContainer, markerLength));
    }

    // Detect markers on the same thread
    // Camera preview is not rendered until we know the marker coordinates
    private void markerDetectionSynchronized() {
//        long lastDetection = System.currentTimeMillis();
        List<Mat> corners = new ArrayList<>();  // Create a list of Mats to store the corners of the markers
        Mat ids = new Mat();                    // Create a Mat to store all the ids of the markers

        // Detect the markers in the image and store their corners and ids in the corresponding variables
        detectMarkers(imageMat, getPredefinedDictionary(DICT_6X6_50), corners, ids);

        int numMarkers = corners.size();
        if(numMarkers > 0){
            // Each marker has 4 corners with 2 coordinates each -> 8 floats per corner
            float[][] markerCorners2D = new float[corners.size()][8];

            for(int i = 0; i < corners.size(); ++i){
                // i is the index of the marker
                Mat marker = corners.get(i);

                // Put corners in clockwise order. Note that the Y-axis is flipped
                for(int j = 0; j < 4; ++j){
                    markerCorners2D[i][2*j] = (float) (marker.get(0,j)[0] * 2.0 / imageMat.width() - 1);
                    markerCorners2D[i][2*j+1] = (float) -(marker.get(0,j)[1] * 2.0 / imageMat.height() - 1);
                }
            }

            markerContainer.setMarkerCorners(markerCorners2D);

            //Calculate fx, fy, cx, and cy based on image resolution
            float cx = imageMat.width() * 0.49904564092f;
            float cy = imageMat.height() * 0.49937073486f;
            float fx = imageMat.width() * 0.67352064836f;
            float fy = imageMat.height() * 1.19671093141f;

            // Construct the camera matrix using fx, fy, cx, cy
            float[] intrinsics = {fx, 0, cx, 0, fy, cy, 0, 0, 1};
            Mat cameraMatrix = new Mat(3,3, CvType.CV_32F);
            cameraMatrix.put(0, 0, intrinsics);

            // Assign distortion coefficients (currently empty)
            float[] distortion = {0, 0, 0, 0, 0};
            Mat distortionCoefficients = new Mat(1,5, CvType.CV_32F);
            distortionCoefficients.put(0, 0, distortion);

            // Create empty matrices for the rotation vector and the translation vector
            Mat rvecs = new Mat();
            Mat tvecs = new Mat();

            // Estimate pose and get rvecs and tvecs
            estimatePoseSingleMarkers(corners, markerLength, cameraMatrix, distortionCoefficients, rvecs, tvecs);

            // If exactly two markers are detected, measure the distance between them
            if(numMarkers == 2){
                double[] marker1Coords = tvecs.get(0,0);
                double[] marker2Coords = tvecs.get(1,0);

                // Calculate the distance between marker 1 and marker 2
                double distance = Math.sqrt(
                        (marker1Coords[0]-marker2Coords[0])*(marker1Coords[0]-marker2Coords[0]) +
                        (marker1Coords[1]-marker2Coords[1])*(marker1Coords[1]-marker2Coords[1]) +
                        (marker1Coords[2]-marker2Coords[2])*(marker1Coords[2]-marker2Coords[2]));

                markerContainer.setDistance(distance);
                markerContainer.setDepths((float) marker1Coords[2], (float) marker2Coords[2]);
            }
            else{
                markerContainer.clearDistance();
                markerContainer.clearDepths();
            }
        }
        else{
            markerContainer.makeEmpty();
        }
//        Log.d("Detection time", "" + (int) (System.currentTimeMillis() - lastDetection) + "ms");
    }

    private void setImageMatFromProxy(@NotNull ImageProxy proxy) {
//        long timer = System.currentTimeMillis();

        // The image data is stored in YUV_420_888 and needs to be converted to RGB to make a bitmap
        assert(proxy.getFormat() == ImageFormat.YUV_420_888);
        // yPlane and yMat contain luminance data
        // uvPlane and uvMat contain color data
        ByteBuffer yPlane = proxy.getPlanes()[0].getBuffer();
        ByteBuffer uvPlane = proxy.getPlanes()[2].getBuffer();
        Mat yMat = new Mat(proxy.getHeight(), proxy.getWidth(), CvType.CV_8UC1, yPlane);
        Mat uvMat = new Mat(proxy.getHeight() / 2, proxy.getWidth() / 2, CvType.CV_8UC2, uvPlane);

        // Use OpenCV image processor to convert from YUV to RGB
        Mat rgbaMat = new Mat();
        Imgproc.cvtColorTwoPlane(yMat, uvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV21);

        // TODO is this necessary??
        // Remove the alpha channel
        cvtColor(rgbaMat, rgbaMat, COLOR_BGRA2BGR);

        // Rotate Mat 90 degrees
        rotate(rgbaMat, rgbaMat, ROTATE_90_CLOCKWISE);

        imageMat = rgbaMat;

//        Log.d("Bitmap conversion time", "" + (int)(System.currentTimeMillis() - timer) + "ms");
    }

    private void setImageBitmapFromMat(){
        // Convert Mat to Bitmap
        Utils.matToBitmap(imageMat, imageBitmap);
    }

    private void setImageBitmapFromMat(Mat _mat){
        // Convert Mat to Bitmap
        Utils.matToBitmap(_mat, imageBitmap);
    }

    private void generateShader() {
        if (shader == null) {
            shader = new Shader();
        }
    }

    private void generateTexture() {
        GLES20.glGenTextures(1, textures, 0);
    }

    private void renderTexture() {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, imageBitmap, 0);
    }

    void setMarkerSize(float v){
        markerLength = v;
    }

}
