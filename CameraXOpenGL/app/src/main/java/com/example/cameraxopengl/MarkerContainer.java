package com.example.cameraxopengl;

import org.opencv.core.Mat;

import java.util.List;

class MarkerContainer {
    private float[][] markerCorners2D;
    private double distance = 0;
    private int numMarkers = 0;
    private float[] depths = new float[2];

    float[][] getMarkerCorners(){
        return markerCorners2D;
    }

    void setMarkerCorners(List<Mat> listOfCorners, int screenWidth, int screenHeight) {
        // Each marker has 4 corners with 2 coordinates each -> 8 floats per corner
        numMarkers = listOfCorners.size();
        markerCorners2D = new float[numMarkers][8];

        for (int i = 0; i < numMarkers; ++i) {
            // i is the index of the marker
            Mat marker = listOfCorners.get(i);

            // Put corners in clockwise order. Note that the Y-axis is flipped
            for (int j = 0; j < 4; ++j) {
                markerCorners2D[i][2 * j] = (float) (marker.get(0, j)[0] * 2.0 / screenWidth - 1);
                markerCorners2D[i][2 * j + 1] = (float) -(marker.get(0, j)[1] * 2.0 / screenHeight - 1);
            }
        }
    }

    int getNumMarkers(){
        return numMarkers;
    }

    boolean isNotEmpty(){
        return numMarkers > 0;
    }

    boolean isEmpty() {
        return numMarkers <= 0;
    }

    void makeEmpty(){
        numMarkers = 0;
        markerCorners2D = null;
        clearDistance();
        clearDepths();
    }

    void setDistance(double d){
        distance = d;
    }

    double getDistance() {
        return distance;
    }

    void clearDistance(){
        distance = 0;
    }

    void setDepths(float v1, float v2) {
        depths[0] = v1;
        depths[1] = v2;
    }

    void clearDepths() {
        depths[0] = 0;
        depths[1] = 0;
    }

    float[] getDepths() {
        return depths;
    }

    float[] getMarkerMidpoint(int markerIndex){
        assert(markerIndex < numMarkers);
        float x0 = markerCorners2D[markerIndex][0];
        float y0 = markerCorners2D[markerIndex][1];
        float x1 = markerCorners2D[markerIndex][4];
        float y1 = markerCorners2D[markerIndex][5];

        return new float[]{ (x0+x1)/2, (y0+y1)/2 };
    }

    float[] getLineCenter(){
        assert(numMarkers >= 2);

        float x = (getMarkerMidpoint(0)[0] + getMarkerMidpoint(1)[0]) / 2;
        float y = (getMarkerMidpoint(0)[1] + getMarkerMidpoint(1)[1]) / 2;

        return new float[] {x, y};
    }
}
