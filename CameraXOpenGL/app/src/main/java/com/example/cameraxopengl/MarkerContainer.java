package com.example.cameraxopengl;

import android.util.Log;

class MarkerContainer {
    private float[][] markerCorners2D;
    private double distance = 0;
    private int numMarkers = 0;
    private long lastDetection = System.nanoTime();

    public float[][] getMarkerCorners(){
        return markerCorners2D;
    }

    public void setMarkerCorners(float[][] m){
        markerCorners2D = m;
        numMarkers = markerCorners2D.length;
        long newDetection = System.nanoTime();
        Log.d("Detection time", "" + (int) ((newDetection - lastDetection)/1000000.0) + "ms");
        lastDetection = newDetection;
    }

    public int getNumMarkers(){
        return numMarkers;
    }

    public boolean isNotEmpty(){
        return numMarkers > 0;
    }

    public void makeEmpty(){
        numMarkers = 0;
        markerCorners2D = null;
        distance = 0;
    }

    public void setDistance(double d){
        distance = d;
    }

    public double getDistance() {
        return distance;
    }

    public void clearDistance(){
        distance = 0;
    }
}
