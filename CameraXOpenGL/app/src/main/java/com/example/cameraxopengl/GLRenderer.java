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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

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
        shader.drawGeometry(textures[0]);
    }

    @Override
    public void analyze(@NonNull ImageProxy proxy) {
        setImage(Bitmap.createBitmap(toBitmap(proxy), 0, 0, proxy.getWidth(), proxy.getHeight()));

        glSurfaceView.requestRender();

        proxy.close();
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
