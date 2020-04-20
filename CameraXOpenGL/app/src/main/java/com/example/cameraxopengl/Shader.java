package com.example.cameraxopengl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

// This class provides OpenGL shader programs to render the camera preview via texture as well as
// polygon geometry on top of the preview.

class Shader {
    // These are 2D-coordinates
    private final float[] screenVertices = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
    private final float[] textureVertices = {0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f};

    private FloatBuffer screenVerticesBuffer;
    private FloatBuffer textureBuffer;

    private int program = 0;
    private int programGeometry = 1;

    private String vertexShaderCode = "attribute vec4 aPosition;" +
            "attribute vec2 aTexPosition;" +
            "varying vec2 vTexPosition;" +
            "void main() {" +
            "  gl_Position = aPosition;" +
            "  vTexPosition = aTexPosition;" +
            "}";

    private String fragmentShaderCode = "precision mediump float;" +
            "uniform sampler2D uTexture;" +
            "varying vec2 vTexPosition;" +
            "void main() {" +
            "  gl_FragColor = texture2D(uTexture, vTexPosition);" +
            "}";

    private String vertexShaderGeometryCode = "attribute vec4 aPosition;" +
            "void main() {" +
            "  gl_Position = aPosition;" +
            "}";

    private String fragmentShaderGeometryCode = "precision mediump float;" +
            "void main() {" +
            "  gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);" +
            "}";

    // Constructor creates shaders from the code and initializes vertex and texture buffers
    Shader() {
        initializeBuffers();
        initializeProgram();
    }

    // Allocate buffers for the vertices
    private void initializeBuffers() {
        ByteBuffer buff = ByteBuffer.allocateDirect(screenVertices.length * 4);
        buff.order(ByteOrder.nativeOrder());
        screenVerticesBuffer = buff.asFloatBuffer();
        screenVerticesBuffer.put(screenVertices);
        screenVerticesBuffer.position(0);

        buff = ByteBuffer.allocateDirect(textureVertices.length * 4);
        buff.order(ByteOrder.nativeOrder());
        textureBuffer = buff.asFloatBuffer();
        textureBuffer.put(textureVertices);
        textureBuffer.position(0);
    }

    // Initialize shaders with the content in the shader string variables
    private void initializeProgram() {
        // Create shader program for rendering the camera preview
        int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShader, vertexShaderCode);
        GLES20.glCompileShader(vertexShader);

        int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShader, fragmentShaderCode);
        GLES20.glCompileShader(fragmentShader);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);

        GLES20.glLinkProgram(program);

        // Create shader program for rendering polygons
        int vertexShaderGeometry = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShaderGeometry, vertexShaderGeometryCode);
        GLES20.glCompileShader(vertexShaderGeometry);

        int fragmentShaderGeometry = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShaderGeometry, fragmentShaderGeometryCode);
        GLES20.glCompileShader(fragmentShaderGeometry);

        programGeometry = GLES20.glCreateProgram();
        GLES20.glAttachShader(programGeometry, vertexShaderGeometry);
        GLES20.glAttachShader(programGeometry, fragmentShaderGeometry);

        GLES20.glLinkProgram(programGeometry);
    }

    // Draw preview onto texture unit "texture"
    void draw(int texture) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glUseProgram(program);
        GLES20.glDisable(GLES20.GL_BLEND);

        int positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        int textureHandle = GLES20.glGetUniformLocation(program, "uTexture");
        int texturePositionHandle = GLES20.glGetAttribLocation(program, "aTexPosition");

        GLES20.glVertexAttribPointer(texturePositionHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GLES20.glEnableVertexAttribArray(texturePositionHandle);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(textureHandle, 0);

        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, screenVerticesBuffer);
        GLES20.glEnableVertexAttribArray(positionHandle);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

//    // SINGLE MARKER DRAW FUNCTION
//    // Draw geometry
//    void drawMarkerGL(float[][] markerVerticesMatrix){
//        int markerCount = markerVerticesMatrix.length;
//
//        float[] markerVertices = markerVerticesMatrix[0];
//
//        ByteBuffer buff = ByteBuffer.allocateDirect(markerVertices.length * 4);
//        buff.order(ByteOrder.nativeOrder());
//        FloatBuffer markerVerticesBuffer = buff.asFloatBuffer();
//        markerVerticesBuffer.put(markerVertices);
//        markerVerticesBuffer.position(0);
//
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
//        GLES20.glUseProgram(programGeometry);
//        GLES20.glDisable(GLES20.GL_BLEND);
//
//        int positionHandle = GLES20.glGetAttribLocation(programGeometry, "aPosition");
//
//        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, markerVerticesBuffer);
//        GLES20.glEnableVertexAttribArray(positionHandle);
//
//        // Count should probably be a different number than 4
//        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4);
//    }

    // MULTIPLE MARKER DRAW FUNCTION -- DOESN'T WORK
    // Draw geometry without running glClear (draw on top of whatever is on-screen)
    void drawMarkerGL(float[][] markerVertices){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glUseProgram(programGeometry);
        GLES20.glDisable(GLES20.GL_BLEND);

        for (float[] markerVertex : markerVertices) {
            ByteBuffer buff = ByteBuffer.allocateDirect(markerVertex.length * 4);
            buff.order(ByteOrder.nativeOrder());
            FloatBuffer markerVerticesBuffer = buff.asFloatBuffer();
            markerVerticesBuffer.put(markerVertex);
            markerVerticesBuffer.position(0);

            int positionHandle = GLES20.glGetAttribLocation(programGeometry, "aPosition");

            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, markerVerticesBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4);
        }
    }
}
