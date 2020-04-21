package com.example.cameraxopengl;

class MarkerContainer {
    private float[][] markerCorners2D;
    private int numMarkers = 0;

    public float[][] getMarkerCorners(){
        return markerCorners2D;
    }

    public void setMarkerCorners(float[][] m){
        markerCorners2D = m;
        numMarkers = markerCorners2D.length;
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
    }
}
