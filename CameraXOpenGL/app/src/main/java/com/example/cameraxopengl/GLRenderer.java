package com.example.cameraxopengl;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import org.jetbrains.annotations.NotNull;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static org.opencv.aruco.Aruco.DICT_6X6_50;
import static org.opencv.aruco.Aruco.detectMarkers;
import static org.opencv.aruco.Aruco.estimatePoseSingleMarkers;
import static org.opencv.aruco.Aruco.getPredefinedDictionary;
import static org.opencv.calib3d.Calib3d.Rodrigues;
import static org.opencv.calib3d.Calib3d.projectPoints;
import static org.opencv.core.Core.ROTATE_90_CLOCKWISE;
import static org.opencv.core.Core.gemm;
import static org.opencv.core.Core.invert;
import static org.opencv.core.Core.rotate;
import static org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR;
import static org.opencv.imgproc.Imgproc.circle;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.putText;

// The class GLRenderer implements a custom GLSurfaceView.Renderer to handle rendering to a
// GLSurfaceView. It also provides an ImageAnalysis.Analyzer to perform image analysis on each
// frame. A texture is generated and the rendering is performed  in an OpenGL shader which is called
// on each frame.

class GLRenderer implements GLSurfaceView.Renderer, ImageAnalysis.Analyzer {
    private GLSurfaceView glSurfaceView;
    private int[] textures = {0};
    private float markerLength;
    private Shader shader;
    private MarkerContainer markerContainer = new MarkerContainer();
    private ExecutorService executor;
    private Bitmap imageBitmap;
    private Mat imageMat;

    // Plane things
    private boolean userPoint = true;
    private Mat tracked_vector_MC;
    private MatOfPoint2f imagePoints;

    // Constructor that sets up the
    GLRenderer(GLSurfaceView view, int cameraPreviewWidth, int cameraPreviewHeight){
        glSurfaceView = view;

        // Create an empty bitmap to place in the glSurfaceView until the first frame is rendered
        imageBitmap = Bitmap.createBitmap(cameraPreviewWidth, cameraPreviewHeight, Bitmap.Config.ARGB_8888);

        // Set up the executor to handle Marker Detection in the background
        int poolsize = 1; int queuesize = 1;
        executor = new ThreadPoolExecutor(poolsize, poolsize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queuesize),
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        // Generate a texture to put the image frame in
        generateTexture();
        // Instantiate shader
        generateShader();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Prepare the OpenGL context
        renderTexture();

        // Run the shader to render the camera preview
        shader.draw(textures[0]);

        // Draw markers (if found) on top of the preview
        if(markerContainer.isNotEmpty()){
            shader.drawMarkerGL(markerContainer.getMarkerCorners());
            Log.d("Distance" , "" + (int) markerContainer.getDistance() + "mm");

            // If two markers are found
            if(markerContainer.getNumMarkers() >= 2){
                shader.drawLine(markerContainer.getMarkerMidpoint(0), markerContainer.getMarkerMidpoint(1), markerContainer.getDepths());
            }
        }
    }

    @Override
    public void analyze(@NonNull ImageProxy proxy) {
        long analyzeTime = System.currentTimeMillis();
        // imageMat gets assigned
        setImageMatFromProxy(proxy);
        proxy.close();

        // USE EITHER ASYNCHRONOUS OR SYNCHRONOUS MARKER DETECTION
//        markerDetectionAsynchronous();
        markerDetectionSynchronized();

        // Draw marker distance
        if(imageMat != null){
            putText(imageMat, (int)markerContainer.getDistance() + "mm" , new Point(0, imageMat.height()), 1, 2, new Scalar(255, 0, 0), 2, 8);

            // Bilden, koordinaten, radie, färg
            circle(imageMat, new Point(imagePoints.get(0, 0)[0], imagePoints.get(0, 0)[1]), 10, new Scalar(255, 0, 0), -1);
            circle(imageMat, new Point(imageMat.width()/2, imageMat.height()/2), 15, new Scalar(0,0,255), 3);

            setImageBitmapFromMat(imageMat);
        }
        else{
            setImageBitmapFromMat();
        }

        glSurfaceView.requestRender();
        Log.d("Analysis time", "" + (int) (System.currentTimeMillis() - analyzeTime) + "ms");
    }

    // Asynchronously detect ArUco markers by executing an instance of MarkerDetector in the background
    // Camera preview is rendered regardless if marker coordinates are found.
    // If markers are found, they will be rendered the next time onDrawFrame() is called
    private void markerDetectionAsynchronous() {
        // The executor needs to work on a copy of the bitmap to prevent it from being recycled
        executor.execute(new MarkerDetector(imageMat.clone(), markerContainer, markerLength));
    }

    // Detect markers on the same thread
    // Camera preview is not rendered until we know the marker coordinates
    private void markerDetectionSynchronized() {
//        long lastDetection = System.currentTimeMillis();
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

            /********************************
             * FÖR ATT HITTA 3D-KOORDINATER *
             ********************************/
            // Definierar kameraparametrar enligt extern kalibrering
            float cx = imageMat.width() * 0.49904564092f;
            float cy = imageMat.height() * 0.49937073486f;
            float fx = imageMat.width() * 0.67352064836f;
            float fy = imageMat.height() * 1.19671093141f;

            // Construct the camera matrix using fx, fy, cx, cy
            float[] intrinsics = {fx, 0, cx, 0, fy, cy, 0, 0, 1};
            Mat cameraMatrix = new Mat(3,3, CvType.CV_32F);
            cameraMatrix.put(0, 0, intrinsics);

            // Assign distortion coefficients (currently empty)
            // Todo: Try using real distortion values
            float[] distortion = {0, 0, 0, 0, 0};
            Mat distortionCoefficients = new Mat(1,5, CvType.CV_32F);
            distortionCoefficients.put(0, 0, distortion);

            // Create empty matrices for the rotation vector and the translation vector
            Mat rvecs = new Mat();
            Mat tvecs = new Mat();

            // Estimate pose and get rvecs and tvecs
            estimatePoseSingleMarkers(corners, markerLength, cameraMatrix, distortionCoefficients, rvecs, tvecs);





            /* ---------------------------- FRÅN "TRACKER" BÖRJAR  ------------------------------ */

            // Camera points är en lista med matriser som ska innehålla hörnpunkternas 3D-koordinater
            List<Mat> camera_points = new ArrayList<>();
            float half_side = markerLength / 2;

            //Create empty matrices to fill
            Mat rvec = new Mat(1,1, CvType.CV_64FC3);
            Mat tvec = new Mat(1,1, CvType.CV_64FC3);
            Mat rot_mat = new Mat(3,3,CvType.CV_64F);

            // When there are than one markers in the image, another row is added to rvecs and
            // tvecs for each marker. Therefore, we need to take out and draw the axes for one
            // marker, or one row, at the time.

            // Omvandlar till 3d, hittar plan och följer punkt, ett varv per markör
            for(int i = 0; i < corners.size(); i++) {
                rvec.put(0,0, rvecs.get(i,0));
                tvec.put(0,0, tvecs.get(i,0));
                Rodrigues(rvec, rot_mat);
                rot_mat = rot_mat.t();

                // E is the halfway point of one side
                double Ex = rot_mat.get(0,0)[0] * half_side;
                double Ey = rot_mat.get(0,1)[0] * half_side;
                double Ez = rot_mat.get(0,2)[0] * half_side;

                // F is the halfway point of the perpendicular side
                double Fx = rot_mat.get(1,0)[0] * half_side;
                double Fy = rot_mat.get(1,1)[0] * half_side;
                double Fz = rot_mat.get(1,2)[0] * half_side;

                // First corner (corner 0 = övre vänstra)
                double corner_x = -Ex + Fx + tvec.get(0,0)[0];
                double corner_y = -Ey + Fy + tvec.get(0,0)[1];
                double corner_z = -Ez + Fz + tvec.get(0,0)[2];

                // Add to matrix
                double[] corner_coord = {corner_x, corner_y, corner_z};
                Mat camera_points_coords_0 = new Mat(1,3,CvType.CV_64F);
                camera_points_coords_0.put(0, 0, corner_coord);
                camera_points.add(camera_points_coords_0);

                // Second corner (corner 1 = övre högra)
                corner_x = Ex + Fx + tvec.get(0,0)[0];
                corner_y = Ey + Fy + tvec.get(0,0)[1];
                corner_z = Ez + Fz + tvec.get(0,0)[2];

                corner_coord[0] = corner_x; corner_coord[1] = corner_y; corner_coord[2] = corner_z;
                Mat camera_points_coords_1 = new Mat(1,3,CvType.CV_64F);
                camera_points_coords_1.put(0, 0, corner_coord);
                camera_points.add(camera_points_coords_1);

                // Third corner (corner 2 = nedre högra)
                corner_x = Ex - Fx + tvec.get(0,0)[0];
                corner_y = Ey - Fy + tvec.get(0,0)[1];
                corner_z = Ez - Fz + tvec.get(0,0)[2];

                corner_coord[0] = corner_x; corner_coord[1] = corner_y; corner_coord[2] = corner_z;
                Mat camera_points_coords_2 = new Mat(1,3,CvType.CV_64F);
                camera_points_coords_2.put(0, 0, corner_coord);
                camera_points.add(camera_points_coords_2);

                // Fourth corner (corner 3 = nedre vänstra)
                corner_x = -Ex - Fx + tvec.get(0,0)[0];
                corner_y = -Ey - Fy + tvec.get(0,0)[1];
                corner_z = -Ez - Fz + tvec.get(0,0)[2];

                corner_coord[0] = corner_x; corner_coord[1] = corner_y; corner_coord[2] = corner_z;
                Mat camera_points_coords_3 = new Mat(1,3,CvType.CV_64F);
                camera_points_coords_3.put(0, 0, corner_coord);
                camera_points.add(camera_points_coords_3);


                /****************************
                 *    FÖR ATT HITTA PLAN    *
                 ****************************/

                // Få ut koordinaterna till hörn 1 (övre högra)
                double[] x_1 = {0};
                double[] y_1 = {0};
                double[] z_1 = {0};
                camera_points_coords_1.get(0,0, x_1);
                camera_points_coords_1.get(0,1, y_1);
                camera_points_coords_1.get(0,2, z_1);

                // Få ut koordinaterna till hörn 2 (nedre högra)
                double[] x_2 = {0};
                double[] y_2 = {0};
                double[] z_2 = {0};
                camera_points_coords_2.get(0,0, x_2);
                camera_points_coords_2.get(0,1, y_2);
                camera_points_coords_2.get(0,2, z_2);

                // Få ut koordinaterna till hörn 3
                double[] x_3 = {0};
                double[] y_3 = {0};
                double[] z_3 = {0};
                camera_points_coords_3.get(0,0, x_3);
                camera_points_coords_3.get(0,1, y_3);
                camera_points_coords_3.get(0,2, z_3);

                // Gör vektorer som är 1->2 samt 2->3
                Mat vector_1_2 = new Mat(1, 3, CvType.CV_64F);
                Mat vector_2_3 = new Mat(1, 3, CvType.CV_64F);

                // Beräkna och sätt in värdena
                vector_1_2.put(0, 0, x_2[0] - x_1[0]);
                vector_1_2.put(0, 1, y_2[0] - y_1[0]);
                vector_1_2.put(0, 2, z_2[0] - z_1[0]);

                vector_2_3.put(0, 0, x_3[0] - x_2[0]);
                vector_2_3.put(0, 1, y_3[0] - y_2[0]);
                vector_2_3.put(0, 2, z_3[0] - z_2[0]);

                // Skapa normalen till planet
                Mat normal = vector_1_2.cross(vector_2_3);

                // Hitta längden på normalen så vi kan normalisera den
                double normal_factor = 1/Math.sqrt( (Math.pow(normal.get(0, 0)[0], 2)) +
                        (Math.pow(normal.get(0, 1)[0], 2)) +
                        (Math.pow(normal.get(0, 2)[0], 2)) );

                // Normalisera normalen
                normal.put(0, 0, normal_factor * normal.get(0,0)[0]);
                normal.put(0, 1, normal_factor * normal.get(0,1)[0]);
                normal.put(0, 2, normal_factor * normal.get(0,2)[0]);

                // Planets ekvation på normalform = Ax + By + Cz + D = 0 => D = - (Ax + By + Cz)
                double plane_D = - (normal.get(0,0)[0]*x_1[0] + normal.get(0,1)[0]*y_1[0] + normal.get(0,2)[0]*z_1[0]);

                // Skapar en stråle som går från kameran ut i världen: (0, 0, -t)
                // TODO: Behöver denna vara i markörkoordinater? Gör om intersection för det nya kanske ...
                // Ax + By + Cz + D = 0, med x = y = 0 => Cz + D = 0 => -t = -D/C => t = D/C
                // Intersection sker i punkten (0,0,-D/C)
                // TODO: Behöver denna översättas?
                double z_intersect = - plane_D / normal.get(0,2)[0];

                /***************************
                 *     FÖLJA EN PUNKT      *
                 **************************/
                // Beräkna längden mellan två hörn i 3D, för att kunna skala om tracked_vector senare
                double markerPixelLength = Math.sqrt( Math.pow(vector_1_2.get(0,0)[0], 2)
                        +Math.pow(vector_1_2.get(0,1)[0], 2)
                        +Math.pow(vector_1_2.get(0,2)[0], 2) );

                // Beräkna längden mellan två hörn i 2D, för att kunna skala om tracked_vector senare
                //Mat corner_0 = corners.get(0);
                //double markerPixelLength = Math.sqrt( Math.pow( corner_0.get(0,0)[0] - corner_0.get(0,1)[0], 2 )
                //+Math.pow( corner_0.get(0,0)[1] - corner_0.get(0,1)[1], 2 ));

                // TODO: låt userPoint bestämmas av ett knapptryck
                // TODO: Lista ut förhållandet mellan markör- och världskoordinater på ett självsäkert sätt (felsök!)
                // Placerar ut en punkt i mitten på skärmen för just denna frame
                if (userPoint) {
                    // Problem: vi får punktens koordinater i kamerans koordinatsystem, vilket gör att den inte är
                    // helt låst i skärmen bl.a. vid rotation.
                    //                         y
                    //                         |__ x
                    //                        / (MARKÖR)
                    //                       z
                    // Lösning:
                    // --> Ta inversen av rot_mat och tvec

                    Mat rotMatrix = new Mat(3,3, CvType.CV_64F);
                    Rodrigues(rvec, rotMatrix);

                    // Skapar en matris som beskriver både rotation och translation enl. kameran
                    Mat resultMatrix = new Mat(4,4, CvType.CV_64F);
                    double[] resultArray_r1 = { rotMatrix.get(0,0)[0], rotMatrix.get(0,1)[0], rotMatrix.get(0,2)[0], tvec.get(0,0)[0]};
                    double[] resultArray_r2 = { rotMatrix.get(1,0)[0], rotMatrix.get(1,1)[0], rotMatrix.get(1,2)[0], tvec.get(0,0)[1]};
                    double[] resultArray_r3 = { rotMatrix.get(2,0)[0], rotMatrix.get(2,1)[0], rotMatrix.get(2,2)[0], tvec.get(0,0)[2]};
                    double[] resultArray_r4 = { 0, 0, 0, 1 };

                    resultMatrix.put(0,0, resultArray_r1);
                    resultMatrix.put(1,0, resultArray_r2);
                    resultMatrix.put(2,0, resultArray_r3);
                    resultMatrix.put(3,0, resultArray_r4); // TODO: behöver sista raden vara med?

                    // Vi inverterar matrisen så vi kan gå från kamera --> markör
                    invert(resultMatrix, resultMatrix); // 4 x 4

                    // Vektorn från markörens mitt till skärpunkten i världskoordinater (WC)
                    Mat tracked_vector_WC = new Mat(4, 1, CvType.CV_64F);
                    tracked_vector_WC.put( 0,0, 0 - tvec.get(0,0)[0]);
                    tracked_vector_WC.put( 1,0, 0 - tvec.get(0,0)[1]);
                    tracked_vector_WC.put( 2,0, z_intersect - tvec.get(0,0)[2]); // TODO: Tänk efter om detta är rimligt egentligen!
                    tracked_vector_WC.put( 3,0, 1);

                    // Applicera inversmatrisen på track-vektorn så vi får den i markörens koordinatsystem (MC)
                    gemm(resultMatrix, tracked_vector_WC, 1, tracked_vector_WC, 0, tracked_vector_WC);

                    // Vektorn mellan markörens mitt och skärpunkten (i markörkoordinater)
                    // TODO: Stämmer detta?
                    tracked_vector_MC = new Mat(1, 3, CvType.CV_64F);
                    tracked_vector_MC.put(0,0, 0);//tracked_vector_WC.get(0,0)[0]*markerPixelLength);
                    tracked_vector_MC.put(0,1, 0.5);//tracked_vector_WC.get(1,0)[0]*markerPixelLength);
                    tracked_vector_MC.put(0,2, 0);//tracked_vector_WC.get(2,0)[0]*markerPixelLength);

                    // Vi skapar en vektor som går mellan en punkt på markören och den utsatta punkten
                    // Utgår från hörn 2
                    /*
                    tracked_vector = new Mat(1, 3, CvType.CV_64F);
                    tracked_vector.put(0,0, -x_2[0]*markerPixelLength);
                    tracked_vector.put(0,1, -y_2[0]*markerPixelLength);
                    tracked_vector.put(0,2, (z_intersect - z_2[0])*markerPixelLength);
                    */
                    // Utgår från mitten på markören
                    /*
                    tracked_vector = new Mat(1, 3, CvType.CV_64F);
                    tracked_vector.put(0,0, -tvec.get(0,0)[0]*markerPixelLength);
                    tracked_vector.put(0,1, -tvec.get(0,0)[1]*markerPixelLength);
                    //tracked_vector.put(0,2, (z_intersect - tvec.get(0,0)[2])*markerPixelLength);
                    tracked_vector.put(0,2, 0);
                    */

                    // Ser till så vi inte kommer in hit nästa varv
                    userPoint = false;
                }

                // Om användaren har placerat ut en punkt
                if (tracked_vector_MC != null) {
                    // Vi ska hitta punkten där tracked_vektorn som börjar i hörn 2 tar slut
                    // Vi måste addera x, y, och z var för sig från punkten och vektorn

                    //Point3 p3 = new Point3(tvec.get(0,0)[0] + tracked_vector.get(0,0)[0]/markerPixelLength,
                    //                       tvec.get(0,0)[1] + tracked_vector.get(0,1)[0]/markerPixelLength,
                    //                      tvec.get(0,0)[2] + tracked_vector.get(0,2)[0]/markerPixelLength);

                    // Punkten är definierad i kamerans koordinatsystem
                    /*
                    Point3 p3 = new Point3(x_2[0] + tracked_vector.get(0,0)[0]/markerPixelLength,
                                        y_2[0] + tracked_vector.get(0,1)[0]/markerPixelLength,
                                        z_2[0] + tracked_vector.get(0,2)[0]/markerPixelLength);
                    */

                    //Point3 p3 = new Point3(x_2[0], y_2[0], z_2[0]);

                    // Status:
                    // Vi har en vektor i MC som beskriver vektorn från mitten på markören till vår punkt
                    // Nu vill vi hitta vart den punkten är i kamerakoordinater ?? Nja
                    // Nu vill vi definiera punkten

                    // Todo: Dubbelkolla att detta är rimligt
                    Point3 p3 = new Point3(tracked_vector_MC.get(0,0)[0], tracked_vector_MC.get(0,1)[0], tracked_vector_MC.get(0,2)[0]);
                    Point3 zeroes = new Point3(0,0,0);
                    MatOfPoint3f tracked_point = new MatOfPoint3f(p3);
                    //tracked_point.put(0,0, x_2[0] + tracked_vector.get(0,0)[0]);
                    //tracked_point.put(0,1, y_2[0] + tracked_vector.get(0,1)[0]);
                    //tracked_point.put(0,2, z_2[0] + tracked_vector.get(0,2)[0]);

                    imagePoints = new MatOfPoint2f();
                    MatOfDouble test_distortion = new MatOfDouble(0,0,0,0,0 );
                    Mat rvec_0 = new Mat(1,1,CvType.CV_64FC3, new Scalar(0));
                    Mat tvec_0 = new Mat(1,1,CvType.CV_64FC3, new Scalar(0));
                    projectPoints(tracked_point, rvec, tvec, cameraMatrix, test_distortion, imagePoints);
                }


                // Låt punkten baseras på dess förhållande mellan hörnen i markören
                // Utgå från mitten eller hörnen
                // z kommer alltid vara noll pga allt är i samma plan
                // ex. 100 steg i x-led från markörens mitt

                Log.d("PLANE", "*********************************");
            }

            // Hämtar hörn genom camera_points.get(i).get(0,0)
            // i = vilket hörn

            // Todo:
            // * Se till så att koden vet vilket hörn som är vilken markör

            /* ---------------------------- FRÅN "TRACKER" SLUT ------------------------------ */




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
//        Log.d("Detection time", "" + (int) (System.currentTimeMillis() - lastDetection) + "ms");
    }

    private void setImageMatFromProxy(@NotNull ImageProxy proxy) {
//        long timer = System.currentTimeMillis();

        // The image data is stored in YUV_420_888 and needs to be converted to RGB to make a bitmap
        assert(proxy.getFormat() == ImageFormat.YUV_420_888);
        // yPlane and yMat contain luminance data
        // uvPlane and uvMat contain color data
        ByteBuffer yPlane = proxy.getPlanes()[0].getBuffer();
        ByteBuffer uvPlane = proxy.getPlanes()[2].getBuffer();
        Mat yMat = new Mat(proxy.getHeight(), proxy.getWidth(), CvType.CV_8UC1, yPlane);
        Mat uvMat = new Mat(proxy.getHeight() / 2, proxy.getWidth() / 2, CvType.CV_8UC2, uvPlane);

        // Use OpenCV image processor to convert from YUV to RGB
        Mat rgbaMat = new Mat();
        Imgproc.cvtColorTwoPlane(yMat, uvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV21);

        // TODO is this necessary??
        // Remove the alpha channel
        cvtColor(rgbaMat, rgbaMat, COLOR_BGRA2BGR);

        // Rotate Mat 90 degrees
        rotate(rgbaMat, rgbaMat, ROTATE_90_CLOCKWISE);

        imageMat = rgbaMat;

//        Log.d("Bitmap conversion time", "" + (int)(System.currentTimeMillis() - timer) + "ms");
    }

    private void setImageBitmapFromMat(){
        // Convert Mat to Bitmap
        Utils.matToBitmap(imageMat, imageBitmap);
    }

    private void setImageBitmapFromMat(Mat _mat){
        // Convert Mat to Bitmap
        Utils.matToBitmap(_mat, imageBitmap);
    }

    private void generateShader() {
        if (shader == null) {
            shader = new Shader();
        }
    }

    private void generateTexture() {
        GLES20.glGenTextures(1, textures, 0);
    }

    private void renderTexture() {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, imageBitmap, 0);
    }

    void setMarkerSize(float v){
        markerLength = v;
    }

}
