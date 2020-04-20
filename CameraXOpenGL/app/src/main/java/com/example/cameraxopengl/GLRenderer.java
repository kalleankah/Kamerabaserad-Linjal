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
import org.opencv.aruco.DetectorParameters;
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
import static org.opencv.aruco.Aruco.drawDetectedMarkers;
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
    private Shader shader;

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
//        shader.drawGeometry(textures[0]);
    }

    @Override
    public void analyze(@NonNull ImageProxy proxy) {
        Bitmap bitmap = Bitmap.createBitmap(toBitmap(proxy), 0, 0, proxy.getWidth(), proxy.getHeight());

        markerDetection(bitmap);

        bitmap.recycle();

//        setImage(bitmap);

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

        /* This code below is an alternative to the two lines above, don't know which solution is
        better and are therefor keeping this as well for now. Both work equally well, so the question
        is just if one is more efficient than the other.

        Mat image = new Mat();
        Bitmap bmp = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp, image); */

        // Set parameters for the detectMarkers function
        // These are needed for the function to work
        DetectorParameters parameters = DetectorParameters.create();
        parameters.set_minDistanceToBorder(0);
        parameters.set_adaptiveThreshWinSizeMax(400);


        List<Mat> corners = new ArrayList<>();  // Create a list of Mats to store the corners of the markers
        Mat ids = new Mat();                    // Create a Mat to store all the ids of the markers

        // Detect the markers in the image and store their corners and ids in the corresponding variables
        detectMarkers(image, getPredefinedDictionary(DICT_6X6_50), corners, ids, parameters);

        double[] id1 = ids.get(0,0);
        double[] id2 = ids.get(3,0);

        // If any markers have been detected, draw the square around it and store it in outputImage
        if(corners.size() > 0){
            drawDetectedMarkers(image, corners, ids);
//            image.release();

            if(corners.size() > 3) {
                Mat corner1 = corners.get(0);
                double[] xy10 = corner1.get(0,0);
                double[] xy11 = corner1.get(0,1);
                double[] xy12 = corner1.get(0,2);
                Mat corner2 = corners.get(3);
                double[] xy21 = corner2.get(0,1);
                double[] xy23 = corner2.get(0,3);

                double[] middle1 = {(xy10[0] + xy12[0])/2, (xy10[1] + xy12[1])/2};
                double[] middle2 = {(xy21[0] + xy23[0])/2, (xy21[1] + xy23[1])/2};

                double pixel_width = Math.sqrt(Math.pow(xy11[0] - xy10[0], 2) + Math.pow(xy11[1] - xy10[1], 2));

                double distance = Math.sqrt(Math.pow(middle2[0] - middle1[0], 2) + Math.pow(middle2[1] - middle1[1], 2));
                double distanceIn_mm = distance*50/pixel_width;
                //Toast.makeText(getApplicationContext(), "1: " + id1[0] + " 2: " + id2[0],Toast.LENGTH_SHORT).show();
//                Toast.makeText(getApplicationContext(),"The distance is " + distanceIn_mm,Toast.LENGTH_SHORT).show();
            }
        }

//        Log.d("MAT DIMENSIONS", "Width: " + outputImage.width() + " Height: " + outputImage.height());

        // Convert to bitmap
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap outBitmap = Bitmap.createBitmap(image.width(), image.height(), conf);
        Utils.matToBitmap(image, outBitmap);
        image.release();

        // Display the output image
        setImage(outBitmap);
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
