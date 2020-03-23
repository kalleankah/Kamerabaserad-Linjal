package com.example.opencv_contrib_test;

import androidx.appcompat.app.AppCompatActivity;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.aruco.Dictionary;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Vector;

import static org.opencv.aruco.Aruco.DICT_6X6_250;
import static org.opencv.aruco.Aruco.detectMarkers;
import static org.opencv.aruco.Aruco.drawDetectedMarkers;
import static org.opencv.aruco.Aruco.drawMarker;
import static org.opencv.aruco.Aruco.getPredefinedDictionary;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;

public class MainActivity extends AppCompatActivity {

    Dictionary dictionary;
    String imgPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initDebug())
            Log.e("OpenCv", "Unable to load OpenCV");
        else
            Log.d("OpenCv", "OpenCV loaded");

        dictionary = getPredefinedDictionary(DICT_6X6_250);
        Mat image = createMarker();
        displayMarker(image);
//        detectMarker();
    }


    private Mat createMarker() {
        Mat markerImage = new Mat();
        drawMarker(dictionary, 23, 200, markerImage, 1);
        imwrite("marker23.png", markerImage);

        return markerImage;
    }

    private void displayMarker(Mat image) {
        Bitmap result = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, result);
        ImageView imgView = (ImageView) findViewById(R.id.imgView);
        imgView.setImageBitmap(result);
    }


    private void detectMarker() {
        Mat image = imread(imgPath+"/DCIM/100ANDRO/DSC_0001.JPG");
        if(image.empty()){
            Log.e("File access", "Image null");
        }
//        Vector<Integer> ids = new Vector<>();
//        Vector<Vector<Point>> corners = new Vector<>();
        List<Mat> corners = new ArrayList<>();
        Mat ids = new Mat();
        detectMarkers(image, dictionary, corners, ids);

        if(corners.size() > 0){
            drawDetectedMarkers(image, corners, ids);
        }

        displayMarker(image);
    }
}
