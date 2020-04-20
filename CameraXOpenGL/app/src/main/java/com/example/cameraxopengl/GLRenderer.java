package com.example.cameraxopengl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

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
    private static final float TEMPORARY_RESOLUTION_CONSTANT_W = 1920f;
    private static final float TEMPORARY_RESOLUTION_CONSTANT_H = 1080f;
    private GLSurfaceView glSurfaceView;
    private int[] textures = {0};
    private Bitmap image;
    private Shader shader;
    private float[][] markerCorners2D;

    GLRenderer(GLSurfaceView view){
        glSurfaceView = view;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        // Create an empty bitmap to place in the glSurfaceView until the first frame is rendered
        Bitmap.Config conf = Bitmap.Config.ARGB_8888; // see other conf types
        image = Bitmap.createBitmap(1, 1, conf);
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

        // Draw geometry on top of the preview
        if(markerCorners2D != null){
            shader.drawMarkerGL(markerCorners2D);
        }

//        // DEBUG MARKER COORDS
//        Log.d("CORNERS", "Number of markers: " + markerCorners2D.length/8 + "\n" +
//                "Corner 1: (" + markerCorners2D[0] + ", " + markerCorners2D[1] + ")" + "\n" +
//                "Corner 2: (" + markerCorners2D[2] + ", " + markerCorners2D[3] + ")" + "\n" +
//                "Corner 3: (" + markerCorners2D[4] + ", " + markerCorners2D[5] + ")" + "\n" +
//                "Corner 4: (" + markerCorners2D[6] + ", " + markerCorners2D[7] + ")");
    }

    @Override
    public void analyze(@NonNull ImageProxy proxy) {
        Bitmap bitmap = Bitmap.createBitmap(toBitmap(proxy), 0, 0, proxy.getWidth(), proxy.getHeight());

        markerDetection(bitmap);

        setImage(bitmap);

        glSurfaceView.requestRender();

        proxy.close();
    }

    private void markerDetection(Bitmap bitmap) {
        // Create a Mat and copy the input Bitmap into it
        Mat originalImage = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC3);
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
            markerCorners2D = new float[corners.size()][8];

            for(int i = 0; i<corners.size(); ++i){
                // i is the index of the marker
                Mat marker = corners.get(0);

                // Organize corners for GL_LINE_LOOP draw order
//                // Corner 1 - Bottom Left
//                markerCorners2D[8*i+0] = (float) marker.get(0,0)[0] * 2 / TEMPORARY_RESOLUTION_CONSTANT_W - 1;
//                markerCorners2D[8*i+1] = (float) -(marker.get(0,0)[1] * 2 / TEMPORARY_RESOLUTION_CONSTANT_H - 1);
//                // Corner 2 - Bottom Right
//                markerCorners2D[8*i+2] = (float) marker.get(0,3)[0] * 2 / TEMPORARY_RESOLUTION_CONSTANT_W - 1;
//                markerCorners2D[8*i+3] = (float) -(marker.get(0,3)[1] * 2 / TEMPORARY_RESOLUTION_CONSTANT_H - 1);
//                // Corner 3 - Top Left
//                markerCorners2D[8*i+4] = (float) marker.get(0,2)[0] * 2 / TEMPORARY_RESOLUTION_CONSTANT_W - 1;
//                markerCorners2D[8*i+5] = (float) -(marker.get(0,2)[1] * 2 / TEMPORARY_RESOLUTION_CONSTANT_H - 1);
//                // Corner 4 - Top Right
//                markerCorners2D[8*i+6] = (float) marker.get(0,1)[0] * 2 / TEMPORARY_RESOLUTION_CONSTANT_W - 1;
//                markerCorners2D[8*i+7] = (float) -(marker.get(0,1)[1] * 2 / TEMPORARY_RESOLUTION_CONSTANT_H - 1);

                // Corner 1 - Bottom Left
                markerCorners2D[i][0] = (float) marker.get(0,0)[0] * 2 / TEMPORARY_RESOLUTION_CONSTANT_W - 1;
                markerCorners2D[i][1] = (float) -(marker.get(0,0)[1] * 2 / TEMPORARY_RESOLUTION_CONSTANT_H - 1);
                // Corner 2 - Bottom Right
                markerCorners2D[i][2] = (float) marker.get(0,3)[0] * 2 / TEMPORARY_RESOLUTION_CONSTANT_W - 1;
                markerCorners2D[i][3] = (float) -(marker.get(0,3)[1] * 2 / TEMPORARY_RESOLUTION_CONSTANT_H - 1);
                // Corner 3 - Top Left
                markerCorners2D[i][4] = (float) marker.get(0,2)[0] * 2 / TEMPORARY_RESOLUTION_CONSTANT_W - 1;
                markerCorners2D[i][5] = (float) -(marker.get(0,2)[1] * 2 / TEMPORARY_RESOLUTION_CONSTANT_H - 1);
                // Corner 4 - Top Right
                markerCorners2D[i][6] = (float) marker.get(0,1)[0] * 2 / TEMPORARY_RESOLUTION_CONSTANT_W - 1;
                markerCorners2D[i][7] = (float) -(marker.get(0,1)[1] * 2 / TEMPORARY_RESOLUTION_CONSTANT_H - 1);
            }
        }else{
            markerCorners2D = null;
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
