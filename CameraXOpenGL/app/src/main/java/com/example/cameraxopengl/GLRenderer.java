package com.example.cameraxopengl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static org.opencv.aruco.Aruco.DICT_6X6_50;
import static org.opencv.aruco.Aruco.detectMarkers;
import static org.opencv.aruco.Aruco.getPredefinedDictionary;
import static org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR;
import static org.opencv.imgproc.Imgproc.cvtColor;

// The class GLRenderer implements a custom GLSurfaceView.Renderer to handle rendering to a
// GLSurfaceView. It also provides an ImageAnalysis.Analyzer to perform image analysis on each
// frame. A texture is generated and the rendering is performed  in an OpenGL shader which is called
// on each frame.

class GLRenderer implements GLSurfaceView.Renderer, ImageAnalysis.Analyzer {
    private GLSurfaceView glSurfaceView;
    private int[] textures = {0};
    private Bitmap image;
    private Bitmap imageCopyForExecutor;
    private Shader shader;
    private MarkerContainer markerContainer = new MarkerContainer();
    private ExecutorService executor;

    // Constructor that sets up the
    GLRenderer(GLSurfaceView view){
        glSurfaceView = view;

        // Create an empty bitmap to place in the glSurfaceView until the first frame is rendered
        image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);

        // Set up the executor to handle Marker Detection in the background
        executor = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
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
        }
    }

    @Override
    public void analyze(@NonNull ImageProxy proxy) {
        Bitmap bitmap = Bitmap.createBitmap(toBitmap(proxy), 0, 0, proxy.getWidth(), proxy.getHeight());

        // USE EITHER ASYNCHRONOUS OR SYNCHRONOUS MARKER DETECTION
        markerDetectionAsynchronized(bitmap);
//        markerDetectionSynchronized(bitmap);

        setImage(bitmap);

        glSurfaceView.requestRender();

        proxy.close();
    }

    // Asynchronously detect ArUco markers by executing an instance of MarkerDetector in the background
    // Camera preview is rendered regardless if marker coordinates are found.
    // If markers are found, they will be rendered the next time onDrawFrame() is called
    private void markerDetectionAsynchronized(Bitmap bitmap) {
        // The executor needs to work on a copy of the bitmap to prevent it from being recycled
        imageCopyForExecutor = bitmap.copy(bitmap.getConfig(), false);
        executor.execute(new MarkerDetector(imageCopyForExecutor, markerContainer));
    }

    // Detect markers on the same thread
    // Camera preview is not rendered until we know the marker coordinates
    private void markerDetectionSynchronized(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        // Create a Mat and copy the input Bitmap into it
        Mat originalImage = new Mat(height, width, CvType.CV_8UC3);
        Utils.bitmapToMat(bitmap, originalImage);

        // Create a new Mat that's the same as the one above but with three color channels instead of four
        // This is a must since ArUco need the image to have three color channels in order to work
        Mat image = new Mat();
        cvtColor(originalImage, image, COLOR_BGRA2BGR);
        originalImage.release();

        List<Mat> corners = new ArrayList<>();  // Create a list of Mats to store the corners of the markers
        Mat ids = new Mat();                    // Create a Mat to store all the ids of the markers

        // Detect the markers in the image and store their corners and ids in the corresponding variables
        detectMarkers(image, getPredefinedDictionary(DICT_6X6_50), corners, ids);

        if(corners.size()>0){
            // Each marker has 4 corners with 2 coordinates each -> 8 floats per corner
            float[][] markerCorners2D = new float[corners.size()][8];

            for(int i = 0; i<corners.size(); ++i){
                // i is the index of the marker
                Mat marker = corners.get(i);

                // Put corners in the order OpenGL expects them. Note that the Y-axis is flipped
                // Corner 1 - Bottom Left
                markerCorners2D[i][0] = (float) (marker.get(0,0)[0] * 2.0 / width - 1);
                markerCorners2D[i][1] = (float) -(marker.get(0,0)[1] * 2.0 / height - 1);
                // Corner 2 - Bottom Right
                markerCorners2D[i][2] = (float) (marker.get(0,1)[0] * 2.0 / width - 1);
                markerCorners2D[i][3] = (float) -(marker.get(0,1)[1] * 2.0 / height - 1);
                // Corner 3 - Top Left
                markerCorners2D[i][4] = (float) (marker.get(0,2)[0] * 2.0 / width - 1);
                markerCorners2D[i][5] = (float) -(marker.get(0,2)[1] * 2.0 / height - 1);
                // Corner 4 - Top Right
                markerCorners2D[i][6] = (float) (marker.get(0,3)[0] * 2.0 / width - 1);
                markerCorners2D[i][7] = (float) -(marker.get(0,3)[1] * 2.0 / height - 1);
            }

            markerContainer.setMarkerCorners(markerCorners2D);
        }
        else{
            markerContainer.makeEmpty();
        }
    }

    private synchronized void setImage (Bitmap frame){
        image.recycle();
        image = frame;
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

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, image, 0);
    }

    private Bitmap toBitmap(ImageProxy image) {
        assert image != null;
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);
        yuvImage = null;

        byte[] imageBytes = out.toByteArray();
        out = null;
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
}
