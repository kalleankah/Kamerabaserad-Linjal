package com.example.cameraxopengl;

import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;

class vec3 {
    double x;
    double y;
    double z;

    // Default constructor
    vec3() {
        x = 0;
        y = 0;
        z = 0;
    }

    // Constructor
    vec3(double _x, double _y, double _z){
        x = _x;
        y = _y;
        z = _z;
    }

    // Mat Constructor
    vec3(Mat m) {
        // Test to make sure that the Mat has the correct size/dimension
        if (m.rows() * m.cols() * m.channels() != 3) {
            throw new java.lang.Error("Mat->vec3 incorrect dimensions");
        }
        // If Mat is 3 x 1 x 1
        if (m.rows() == 3) {
            x = m.get(0,0)[0];
            y = m.get(1,0)[0];
            z = m.get(2,0)[0];
        }

        // If Mat is 1 x 3 x 1
        if (m.cols() == 3) {
            x = m.get(0,0)[0];
            y = m.get(0,1)[0];
            z = m.get(0,2)[0];
        }

        // If Mat is 1 x 1 x 3
        if (m.channels() == 3) {
            x = m.get(0,0)[0];
            y = m.get(0,0)[1];
            z = m.get(0,0)[2];
        }
    }

    float[] toFloatArray() {
        return new float[]{ (float) x, (float) y, (float) z};
    }

    double[] toDoubleArray() {
        return new double[]{ x, y, z};
    }

    MatOfPoint3f toMatOfPoint3f() {
        Point3 p = new Point3(x, y, z);
        return new MatOfPoint3f(p);
    }

    void print() {
        Log.d("vec3", "[ " + x + ", " + y + ", " + z + " ]");
    }
}
