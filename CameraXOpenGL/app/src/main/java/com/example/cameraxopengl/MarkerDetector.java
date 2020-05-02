package com.example.cameraxopengl;

import android.graphics.Bitmap;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.aruco.DetectorParameters;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.aruco.Aruco.DICT_6X6_50;
import static org.opencv.aruco.Aruco.detectMarkers;
import static org.opencv.aruco.Aruco.estimatePoseSingleMarkers;
import static org.opencv.aruco.Aruco.getPredefinedDictionary;
import static org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR;
import static org.opencv.imgproc.Imgproc.cvtColor;

// MarkerDetector handles ArUco marker detection. It takes a MarkerContainer and a Bitmap as input.
// If it finds markers, their corners are stored in the MarkerContainer.
// MarkerDetector implements the Runnable interface which allows it to be run asynchronously on
// a new thread or submitted as a task to be performed using ExecutorService.

class MarkerDetector implements Runnable {
    private Mat imageMat;
    private MarkerContainer markerContainer;
    private float markerLength;
    private float cx;
    private float cy;
    private float fx;
    private float fy;

    MarkerDetector(Mat _mat, MarkerContainer _container, float _length){
        // Receives a reference to a copy of a preview frame
        imageMat = _mat;
        markerContainer = _container;
        markerLength = _length;

        //Calculate fx, fy, cx, and cy based on image resolution
        cx = imageMat.width() * 0.49904564092f;
        cy = imageMat.height() * 0.49937073486f;
        fx = imageMat.width() * 0.67352064836f;
        fy = imageMat.height() * 1.19671093141f;
    }

    @Override
    public void run() {
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
    }
}
