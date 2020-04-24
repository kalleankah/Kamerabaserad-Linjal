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
        // Skapa matrisen med distortionskoefficienterna
        distCoeff = new Mat(1,5, CvType.CV_32F);
        float[] distortion = {0, 0, 0, 0, 0}; // 8.4e-03f, -1.6e-01f ,1.4e-03f, -3.9e-03f, 1.3e-01f
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

                List<Mat> camera_points = new ArrayList<>(); // Matrix to fill with the 3D-coordinates in the camera coordinate system
                float half_side = marker_length_m / 2; // The length of half the size of a marker

                //Create empty matrices to fill
                Mat rvec = new Mat(1,1, CvType.CV_64FC3);
                Mat tvec = new Mat(1,1, CvType.CV_64FC3);
                Mat rot_mat = new Mat(3,3,CvType.CV_64F);

                // When there are than one markers in the image, another row is added to rvecs and
                // tvecs for each marker. Therefore, we need to take out one row (one marker) at the time.
                // The for-loop only goes to two right now so that we only calculate the distance between two markers,
                // once we've figured out how we want to do with the case of more than two markers in an image we
                // might want to change from 2 to corners.size()
                for(int i = 0; i < 2; i++) {
                    // Get rvec and tvec for the current marker from rvecs and tvecs
                    rvec.put(0,0, rvecs.get(i,0));
                    tvec.put(0,0, tvecs.get(i,0));
                    Rodrigues(rvec, rot_mat);       // This must be done on rvec to make it into a 3x3 rotation matrix

                    rot_mat = rot_mat.t();

                    // To understand this, best look at https://stackoverflow.com/questions/46363618/aruco-markers-with-opencv-get-the-3d-corner-coordinates
                    // Width is E and Height is F
                    double Width_1 = rot_mat.get(0,0)[0] * half_side;
                    double Width_2 = rot_mat.get(0,1)[0] * half_side;
                    double Width_3 = rot_mat.get(0,2)[0] * half_side;

                    double Height_1 = rot_mat.get(1,0)[0] * half_side;
                    double Height_2 = rot_mat.get(1,1)[0] * half_side;
                    double Height_3 = rot_mat.get(1,2)[0] * half_side;

                    // First corner
                    double corner_x = -Width_1 + Height_1 + tvec.get(0,0)[0];
                    double corner_y = -Width_2 + Height_2 + tvec.get(0,0)[1];
                    double corner_z = -Width_3 + Height_3 + tvec.get(0,0)[2];

                    // Add to matrix
                    double[] corner_coord = {corner_x, corner_y, corner_z};
                    Mat camera_points_coords_0 = new Mat(1,3,CvType.CV_64F);
                    camera_points_coords_0.put(0, 0, corner_coord);
                    camera_points.add(camera_points_coords_0);

                    // Second corner
                    corner_x = Width_1 + Height_1 + tvec.get(0,0)[0];
                    corner_y = Width_2 + Height_2 + tvec.get(0,0)[1];
                    corner_z = Width_3 + Height_3 + tvec.get(0,0)[2];

                    // Add to camera points matrix
                    corner_coord[0] = corner_x; corner_coord[1] = corner_y; corner_coord[2] = corner_z;
                    Mat camera_points_coords_1 = new Mat(1,3,CvType.CV_64F);
                    camera_points_coords_1.put(0, 0, corner_coord);
                    camera_points.add(camera_points_coords_1);

                    // Third corner
                    corner_x = Width_1 - Height_1 + tvec.get(0,0)[0];
                    corner_y = Width_2 - Height_2 + tvec.get(0,0)[1];
                    corner_z = Width_3 - Height_3 + tvec.get(0,0)[2];

                    // Add to camera points matrix
                    corner_coord[0] = corner_x; corner_coord[1] = corner_y; corner_coord[2] = corner_z;
                    Mat camera_points_coords_2 = new Mat(1,3,CvType.CV_64F);
                    camera_points_coords_2.put(0, 0, corner_coord);
                    camera_points.add(camera_points_coords_2);

                    // Fourth corner
                    corner_x = -Width_1 - Height_1 + tvec.get(0,0)[0];
                    corner_y = -Width_2 - Height_2 + tvec.get(0,0)[1];
                    corner_z = -Width_3 - Height_3 + tvec.get(0,0)[2];

                    // Add to camera points matrix
                    corner_coord[0] = corner_x; corner_coord[1] = corner_y; corner_coord[2] = corner_z;
                    Mat camera_points_coords_3 = new Mat(1,3,CvType.CV_64F);
                    camera_points_coords_3.put(0, 0, corner_coord);
                    camera_points.add(camera_points_coords_3);
                }

                if(camera_points.size() == 8) {
                    calculateDistance(camera_points);
                }

            }
        }

        // Display the output image
        displayMarker(outputImage);
    }

    private void calculateDistance(List<Mat> camera_points) {
        double x1 = (camera_points.get(0).get(0,0)[0] + camera_points.get(1).get(0,0)[0]
                + camera_points.get(2).get(0,0)[0] + camera_points.get(3).get(0,0)[0]) / 4;
        double y1 = (camera_points.get(0).get(0,1)[0] + camera_points.get(1).get(0,1)[0]
                + camera_points.get(2).get(0,1)[0] + camera_points.get(3).get(0,1)[0]) / 4;
        double z1 = (camera_points.get(0).get(0,2)[0] + camera_points.get(1).get(0,2)[0]
                + camera_points.get(2).get(0,2)[0] + camera_points.get(3).get(0,2)[0]) / 4;

        double x2 = (camera_points.get(4).get(0,0)[0] + camera_points.get(5).get(0,0)[0]
                + camera_points.get(6).get(0,0)[0] + camera_points.get(7).get(0,0)[0]) / 4;
        double y2 = (camera_points.get(4).get(0,1)[0] + camera_points.get(5).get(0,1)[0]
                + camera_points.get(6).get(0,1)[0] + camera_points.get(7).get(0,1)[0]) / 4;
        double z2 = (camera_points.get(4).get(0,2)[0] + camera_points.get(5).get(0,2)[0]
                + camera_points.get(6).get(0,2)[0] + camera_points.get(7).get(0,2)[0]) / 4;

        double distance = Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2) + Math.pow(z2-z1, 2));

        Toast.makeText(getApplicationContext(),"The distance is " + distance,Toast.LENGTH_SHORT).show();
    }
}