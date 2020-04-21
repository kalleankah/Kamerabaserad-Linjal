package com.example.cameraxopengl;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.aruco.DetectorParameters;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.aruco.Aruco.DICT_6X6_50;
import static org.opencv.aruco.Aruco.detectMarkers;
import static org.opencv.aruco.Aruco.getPredefinedDictionary;
import static org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR;
import static org.opencv.imgproc.Imgproc.cvtColor;

// MarkerDetector handles ArUco marker detection. It takes a MarkerContainer and a Bitmap as input.
// If it finds markers, their corners are stored in the MarkerContainer.
// MarkerDetector implements the Runnable interface which allows it to be run asynchronously on
// a new thread or submitted as a task to be performed using ExecutorService.

class MarkerDetector implements Runnable {
    private Bitmap bitmap;
    private int width = 0;
    private int height = 0;
    private MarkerContainer markerContainer;
    private float[][] markerCorners2D;

    public MarkerDetector(Bitmap b, MarkerContainer container){
        // Receives a reference to a copy of a preview frame, needs to recycle when done ??
        bitmap = b;
        width = b.getWidth();
        height = b.getHeight();
        markerContainer = container;
    }

    @Override
    public void run() {
        // Create a Mat and copy the input Bitmap into it
        Mat originalImage = new Mat(height, width, CvType.CV_8UC3);
        Utils.bitmapToMat(bitmap, originalImage);

        // Remove the copied bitmap when we're done with it
        bitmap.recycle();

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
            // Remove the copied bitmap when we're done with it
            bitmap.recycle();
            markerContainer.makeEmpty();
        }
    }
}
