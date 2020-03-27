package com.example.opencv_contrib_test;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;

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
    String imgPath;
    ImageView imgView;
   // Button button;
    private static final int PICK_IMAGE = 100;
    Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initDebug())
            Log.e("OpenCv", "Unable to load OpenCV");
        else
            Log.d("OpenCv", "OpenCV loaded");

        imgView = (ImageView) findViewById(R.id.imgView);

        dictionary = getPredefinedDictionary(DICT_6X6_250);
        selectImage();
        //Mat image = createMarker();
        //displayMarker(image);
    }

    private void selectImage() {
        Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(gallery, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE){
          imageUri = data.getData();
          //imgView.setImageURI(imageUri);

            InputStream imageStream = null;
            try {
                imageStream = getContentResolver().openInputStream(imageUri);
                Bitmap yourSelectedImage = BitmapFactory.decodeStream(imageStream);
                assert imageStream != null;
                imageStream.close();

                try {
                    detectMarker(yourSelectedImage);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private Mat createMarker() {
        Mat markerImage = new Mat();
        drawMarker(dictionary, 23, 1000, markerImage, 1);
        imwrite("marker23.png", markerImage);

        return markerImage;
    }

    private void displayMarker(Mat image) {
        Bitmap result = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, result);
        imgView.setImageBitmap(result);
    }


    private void detectMarker(Bitmap bitmap) throws FileNotFoundException {

        Mat originalImage = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC3);
        Utils.bitmapToMat(bitmap, originalImage);

        Mat image = new Mat();
        cvtColor(originalImage, image, COLOR_BGRA2BGR);

        /* Mat image = new Mat();
        Bitmap bmp = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp, image); */

        DetectorParameters parameters = DetectorParameters.create();
        parameters.set_minDistanceToBorder(0);
        parameters.set_adaptiveThreshWinSizeMax(400);


        List<Mat> corners = new ArrayList<>();
        Mat ids = new Mat();
        detectMarkers(image, dictionary, corners, ids, parameters);

        Mat outputImage = image.clone();

        if(corners.size() > 0){
            drawDetectedMarkers(outputImage, corners, ids);
        }

        displayMarker(outputImage);
    }
}