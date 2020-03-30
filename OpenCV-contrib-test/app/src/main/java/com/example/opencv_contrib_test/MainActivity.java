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
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.opencv.aruco.Aruco.DICT_6X6_250;
import static org.opencv.aruco.Aruco.detectMarkers;
import static org.opencv.aruco.Aruco.drawDetectedMarkers;
import static org.opencv.aruco.Aruco.drawMarker;
import static org.opencv.aruco.Aruco.getPredefinedDictionary;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;
import static org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class MainActivity extends AppCompatActivity {

    Dictionary dictionary;
    ImageView imgView;
    Button button;
    private static final int PICK_IMAGE = 100;
    private static int RESULT_LOAD_IMAGE = 1;
    Uri imageUri;

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

        dictionary = getPredefinedDictionary(DICT_6X6_250); // Define which dictionary we use

        Button buttonLoadImage = (Button) findViewById(R.id.buttonLoadPicture);
        buttonLoadImage.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                selectImage();                              // Select an image from the phone's gallery
            }
        });


        //Mat image = createMarker();                       // Create a marker
        //displayMarker(image);                             // Display an image
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

    private Mat createMarker() {
        Mat markerImage = new Mat();    // Create a mat
        drawMarker(dictionary, 23, 1000, markerImage, 1);   // Create the marker and put it in markerImage
        //imwrite("marker23.png", markerImage);

        return markerImage;
    }

    private void displayMarker(Mat image) {
        Bitmap result = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);   // Make a bitmap
        Utils.matToBitmap(image, result);   // Make the input Mat image into a bitmap
        imgView.setImageBitmap(result);     // Put the image in the ImageView
    }


    private void detectMarker(Bitmap bitmap) throws FileNotFoundException {

        // Create a Mat and copy the input Bitmap into it
        Mat originalImage = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC3);
        Utils.bitmapToMat(bitmap, originalImage);

        // Create a new Mat that's the same as the one above but with three color channels instead of four
        // This is a must since ArUco need the image to have three color channels in order to work
        Mat image = new Mat();
        cvtColor(originalImage, image, COLOR_BGRA2BGR);

        /* This code below is an alternative to the two lines above, don't know which solution is
        better and are therefor keeping this as well for now. Both work equally well, so the question
        is just if one is more effective than the other.

        Mat image = new Mat();
        Bitmap bmp = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp, image); */

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

        double[] id = ids.get(0,0);

        // If any markers have been detected, draw the square around it and store it in outputImage
        if(corners.size() > 0){
            drawDetectedMarkers(outputImage, corners, ids);

            if(corners.size() == 2) {
                Mat corner1 = corners.get(0);
                double[] xy1 = corner1.get(0,0);
                double[] xy3 = corner1.get(0,1);
                Mat corner2 = corners.get(1);
                double[] xy2 = corner2.get(0,3);

                double pixel_width = Math.sqrt(Math.pow(xy3[0] - xy1[0], 2) + Math.pow(xy3[1] - xy1[1], 2));

                double distance = Math.sqrt(Math.pow(xy2[0] - xy1[0], 2) + Math.pow(xy2[1] - xy1[1], 2));
                double distanceIn_mm = distance*45/pixel_width;
                Toast.makeText(getApplicationContext(),"The distance is " + distanceIn_mm,Toast.LENGTH_SHORT).show();
            }
        }

        // Display the output image
        displayMarker(outputImage);
    }
}