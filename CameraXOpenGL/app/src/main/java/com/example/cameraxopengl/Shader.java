package com.example.cameraxopengl;

import android.opengl.GLES20;

import org.jetbrains.annotations.NotNull;

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
        String vertexShaderCode = "attribute vec4 aPosition;" +
                "attribute vec2 aTexPosition;" +
                "varying vec2 vTexPosition;" +
                "void main() {" +
                "  gl_Position = aPosition;" +
                "  vTexPosition = aTexPosition;" +
                "}";
        GLES20.glShaderSource(vertexShader, vertexShaderCode);
        GLES20.glCompileShader(vertexShader);

        int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        String fragmentShaderCode = "precision mediump float;" +
                "uniform sampler2D uTexture;" +
                "varying vec2 vTexPosition;" +
                "void main() {" +
                "  gl_FragColor = texture2D(uTexture, vTexPosition);" +
                "}";
        GLES20.glShaderSource(fragmentShader, fragmentShaderCode);
        GLES20.glCompileShader(fragmentShader);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);

        GLES20.glLinkProgram(program);

        // Create shader program for rendering polygons
        int vertexShaderGeometry = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        String vertexShaderGeometryCode = "attribute vec4 aPosition;" +
                "void main() {" +
                "  gl_Position = aPosition;" +
                "}";
        GLES20.glShaderSource(vertexShaderGeometry, vertexShaderGeometryCode);
        GLES20.glCompileShader(vertexShaderGeometry);

        int fragmentShaderGeometry = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        String fragmentShaderGeometryCode = "precision mediump float;" +
                "void main() {" +
                "  gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);" +
                "}";
        GLES20.glShaderSource(fragmentShaderGeometry, fragmentShaderGeometryCode);
        GLES20.glCompileShader(fragmentShaderGeometry);

        programGeometry = GLES20.glCreateProgram();
        GLES20.glAttachShader(programGeometry, vertexShaderGeometry);
        GLES20.glAttachShader(programGeometry, fragmentShaderGeometry);

        GLES20.glLinkProgram(programGeometry);

        GLES20.glLineWidth(3f);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    // Draw preview onto texture unit "texture"
    void draw(int texture) {
        GLES20.glUseProgram(program);

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

    // Draw geometry without running glClear (draw on top of whatever is on-screen)
    void drawMarkerGL(float[][] markerVertices){
        GLES20.glUseProgram(programGeometry);

        // Draw lines around the markers
        for (float[] markerVertex : markerVertices) {
            ByteBuffer buff = ByteBuffer.allocateDirect(markerVertex.length * Float.BYTES);
            buff.order(ByteOrder.nativeOrder());
            FloatBuffer markerVerticesBuffer = buff.asFloatBuffer();
            markerVerticesBuffer.put(markerVertex);
            markerVerticesBuffer.position(0);

            int positionHandle = GLES20.glGetAttribLocation(programGeometry, "aPosition");
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, markerVerticesBuffer);

            // GL_LINE_LOOP draws a line from the last vertex to the first one to complete the loop
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4);
        }
    }

    void drawThinLine(@NotNull float[] _start, @NotNull float[] _end){
        GLES20.glUseProgram(programGeometry);

        float[] vertexArrayLine = {
                _start[0],
                _start[1],
                _end[0],
                _end[1],
        };

        ByteBuffer buff = ByteBuffer.allocateDirect(vertexArrayLine.length * Float.BYTES);
        buff.order(ByteOrder.nativeOrder());
        FloatBuffer lineVerticesBuffer = buff.asFloatBuffer();
        lineVerticesBuffer.put(vertexArrayLine);
        lineVerticesBuffer.position(0);

        int positionHandle = GLES20.glGetAttribLocation(programGeometry, "aPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, lineVerticesBuffer);

        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 2);
    }

    // Draw a line from the two measure points using triangles
    void drawLine(float[] _start, float[] _end, @NotNull float[] depth){
        //  Generate rectangle from line
        // Line from start to end
        vec2 start = new vec2(_start);
        vec2 end = new vec2(_end);
        vec2 line = vec2.subVec2(end, start);

        // A new line perpendicular to the first one with a length that depends on the depth at the
        // starting point
        @SuppressWarnings("SuspiciousNameCombination") vec2 perpendicularLineStart = new vec2(line.y, -line.x);
        perpendicularLineStart.normalizeVec();
        perpendicularLineStart.scale((float) 1024.0 /(depth[0]*depth[0]));

        // Create two vertices each a bit apart from the center and widen the line
        vec2[] startVertices = {
                vec2.subVec2(start, perpendicularLineStart),
                vec2.addVec2(start, perpendicularLineStart)
        };

        // A new line perpendicular to the first one with a length that depends on the depth at the
        // end point
        @SuppressWarnings("SuspiciousNameCombination") vec2 perpendicularLineEnd = new vec2(line.y, -line.x);
        perpendicularLineEnd.normalizeVec();
        perpendicularLineEnd.scale((float) 1024.0 /(depth[1]*depth[1]));

        // Widen the line at the end point by perpendicularLineEnd in each direction
        vec2[] endVertices = {
                vec2.subVec2(end, perpendicularLineEnd),
                vec2.addVec2(end, perpendicularLineEnd)
        };

        // Construct the vertex array from the four new points in counter-clockwise order
        float[] vertexArrayLine = new float[]{
                startVertices[0].x, startVertices[0].y,
                startVertices[1].x, startVertices[1].y,
                endVertices[0].x, endVertices[0].y,
                endVertices[1].x, endVertices[1].y
        };

        GLES20.glUseProgram(programGeometry);

        ByteBuffer buff = ByteBuffer.allocateDirect(vertexArrayLine.length * Float.BYTES);
        buff.order(ByteOrder.nativeOrder());
        FloatBuffer lineVerticesBuffer = buff.asFloatBuffer();
        lineVerticesBuffer.put(vertexArrayLine);
        lineVerticesBuffer.position(0);

        int positionHandle = GLES20.glGetAttribLocation(programGeometry, "aPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, lineVerticesBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }
}
