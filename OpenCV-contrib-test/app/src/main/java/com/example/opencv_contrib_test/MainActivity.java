package com.example.opencv_contrib_test;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.opencv.aruco.Aruco.DICT_6X6_50;
import static org.opencv.aruco.Aruco.detectMarkers;
import static org.opencv.aruco.Aruco.drawAxis;
import static org.opencv.aruco.Aruco.drawDetectedMarkers;
import static org.opencv.aruco.Aruco.drawMarker;
import static org.opencv.aruco.Aruco.estimatePoseSingleMarkers;
import static org.opencv.aruco.Aruco.getPredefinedDictionary;
import static org.opencv.calib3d.Calib3d.Rodrigues;
import static org.opencv.calib3d.Calib3d.drawFrameAxes;
import static org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class MainActivity extends AppCompatActivity {

    Dictionary dictionary;
    ImageView imgView;
    private static final int PICK_IMAGE = 100;
    Uri imageUri;
    Mat cameraMatrix;
    Mat distCoeff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if openCV has been loaded
        if (!OpenCVLoader.initDebug())
            Log.e("OpenCv", "Unable to load OpenCV");
        else
            Log.d("OpenCv", "OpenCV loaded");

        imgView = (ImageView) findViewById(R.id.imgView);   // Find ImageView component

        dictionary = getPredefinedDictionary(DICT_6X6_50); // Define which dictionary we use

        // Skapa kamera-matrisen
        cameraMatrix = new Mat(3,3, CvType.CV_32F);

        // Följande värden är alternativa värden för kameramatrisen som vi testat,
        // men det är troligen de som är nu som är rätt för våra telefoner
        // 349.3601,0,258.0883,0,349.7267,210.5905,0,0,1
        // 1365.8452f, 0, 957.5208f, 0, 1364.1266f, 539.8947f, 0, 0, 1
        // 455.28177f, 0, 318.84027f, 0, 454.70886f, 239.63158f, 0, 0, 1
        cameraMatrix = new Mat(3,3, CvType.CV_32F);
        float[] intrinsics = {1365.8452f, 0, 957.5208f, 0, 1364.1266f, 539.8947f, 0, 0, 1};
        cameraMatrix.put(0, 0, intrinsics);

        // 0.3363400302339669, -1.095918772105208, 0.001395881531710981, -0.00113269394288377, 1.487878827052818
        // 0.3431819827974362f, -1.193396780240877f, 0.0006321712163795779f, -0.0015330516048675f, 1.744512267748753f
        // Skapa matrisen med distortionskoefficienterna
        distCoeff = new Mat(1,5, CvType.CV_32F);
        float[] distortion = {0, 0, 0, 0, 0};
        distCoeff.put(0, 0, distortion);

        Button buttonLoadImage = (Button) findViewById(R.id.buttonLoadPicture);
        buttonLoadImage.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                selectImage();                              // Select an image from the phone's gallery
            }
        });

    }

    // Function that allows the user to choose an image from the gallery
    private void selectImage() {
        Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(gallery, PICK_IMAGE);    // Sends this over to function onActivityResult
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE){
            imageUri = data.getData();  // Get the filepath the the chosen image

            InputStream imageStream = null;   // Create an imageStream
            try {
                imageStream = getContentResolver().openInputStream(imageUri); // Get the image from the imageURI
                Bitmap yourSelectedImage = BitmapFactory.decodeStream(imageStream);   // Create a bitmap with the image
                assert imageStream != null;   // Make sure that the imageStream is not null, then close it
                imageStream.close();

                try {
                    detectMarker(yourSelectedImage);  //Try detecting markers in the chosen image
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void displayMarker(Mat image) {
        Bitmap result = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);   // Make a bitmap
        Utils.matToBitmap(image, result);   // Make the input Mat image into a bitmap
        imgView.setImageBitmap(result);     // Put the image in the ImageView
    }

    // This function is currently called OnActivityResult() which is called from selectImage()
    private void detectMarker(Bitmap bitmap) throws FileNotFoundException {

        // Create a Mat and copy the input Bitmap into it
        Mat originalImage = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC3);
        Utils.bitmapToMat(bitmap, originalImage);

        // Create a new Mat that's the same as the one above but with three color channels instead of four
        // This is a must since ArUco need the image to have three color channels in order to work
        Mat image = new Mat();
        cvtColor(originalImage, image, COLOR_BGRA2BGR);

        // Set parameters for the detectMarkers function
        // These are needed for the function to work
        DetectorParameters parameters = DetectorParameters.create();
        parameters.set_minDistanceToBorder(0);
        parameters.set_adaptiveThreshWinSizeMax(400);


        List<Mat> corners = new ArrayList<>();  // Create a list of Mats to store the corners of the markers
        Mat ids = new Mat();                    // Create a Mat to store all the ids of the markers

        // Detect the markers in the image and store their corners and ids in the corresponding variables
        detectMarkers(image, dictionary, corners, ids, parameters);

        // Create an outputImage that is at first a copy of the original image
        // This might not be necessary but is maybe a good practice???
        Mat outputImage = image.clone();

        // If any markers have been detected, draw the square around it and store it in outputImage
        if(corners.size() > 0){
            //drawDetectedMarkers(outputImage, corners, ids);

            // The length of one side of a marker in meters in reality.
            // For now this is defined in the code, but the user should
            // be able to write in this value?
            float marker_length_m = 0.04f;

            // Create empty matrices for the rotation vector and the translation vector
            Mat rvecs = new Mat();
            Mat tvecs = new Mat();

            // Estimate pose and get rvecs and tvecs
            estimatePoseSingleMarkers(corners, marker_length_m, cameraMatrix, distCoeff, rvecs, tvecs);

            // If there are two (or more) markers in the image, calculate the distance
            // from middle to middle for the first two markers
            if(corners.size() > 1) {

                Mat tvec1 = new Mat(1,1, CvType.CV_64FC3);
                tvec1.put(0,0,tvecs.get(0,0));

                Mat tvec2 = new Mat(1,1, CvType.CV_64FC3);
                tvec2.put(0,0,tvecs.get(1,0));

                double distance = Math.sqrt(Math.pow(tvec1.get(0, 0)[0] - tvec2.get(0, 0)[0], 2)
                        + Math.pow(tvec1.get(0, 0)[1] - tvec2.get(0, 0)[1], 2)
                        + Math.pow(tvec1.get(0, 0)[2] - tvec2.get(0, 0)[2], 2));

                Toast.makeText(getApplicationContext(),"The distance (tvec) is " + distance,Toast.LENGTH_SHORT).show();
            }
        }

        // Display the output image
        displayMarker(outputImage);
    }
}