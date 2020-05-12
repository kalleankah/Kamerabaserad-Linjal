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
import org.opencv.aruco.DetectorParameters;
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

import static org.opencv.aruco.Aruco.CORNER_REFINE_SUBPIX;
import static org.opencv.aruco.Aruco.DICT_6X6_50;
import static org.opencv.aruco.Aruco.detectMarkers;
import static org.opencv.aruco.Aruco.drawAxis;
import static org.opencv.aruco.Aruco.estimatePoseSingleMarkers;
import static org.opencv.aruco.Aruco.getPredefinedDictionary;
import static org.opencv.calib3d.Calib3d.Rodrigues;
import static org.opencv.calib3d.Calib3d.projectPoints;
import static org.opencv.core.Core.ROTATE_90_CLOCKWISE;
import static org.opencv.core.Core.gemm;
import static org.opencv.core.Core.invert;
import static org.opencv.core.Core.rotate;
import static org.opencv.core.Core.transpose;
import static org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR;
import static org.opencv.imgproc.Imgproc.circle;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.line;
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
    private Mat cameraMatrix;
    private Mat distortionCoefficients;

    // Plane things
    private boolean userPoint = false;
    private boolean marker_in_frame = false;
    private Mat tracked_vector_MC;
    private Mat imagePoints;
    private int rowCounter = 0;
    private double userX = 0;
    private double userY = 0;
    private Mat rotMatrix;
    private Mat translation_vector;

    Scalar measureColor = new Scalar(255, 102, 0, 1);
    Scalar white = new Scalar(255, 255, 255);
    Scalar white_tp = new Scalar(255, 255, 255, 0.5);

    // Constructor that sets up the
    GLRenderer(GLSurfaceView view, int cameraPreviewWidth, int cameraPreviewHeight) {
        glSurfaceView = view;

        // Create an empty bitmap to place in the glSurfaceView until the first frame is rendered
        imageBitmap = Bitmap.createBitmap(cameraPreviewWidth, cameraPreviewHeight, Bitmap.Config.ARGB_8888);

        // Set up the executor to handle Marker Detection in the background
        int poolsize = 1;
        int queuesize = 1;
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

        // Generate the camera matrix
        generateIntrinsicsAndDistortion();
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
        if (markerContainer.isNotEmpty()) {
            shader.drawMarkerGL(markerContainer.getMarkerCorners());

            // If two markers are found
            if (markerContainer.getNumMarkers() >= 2) {
                shader.drawLine(markerContainer.getMarkerMidpoint(0), markerContainer.getMarkerMidpoint(1), markerContainer.getDepths());
            }


            if (rowCounter == 2) {
                // Pixel values converted to uv coordinates
                float[] startPoint = {  (float) (imagePoints.get(0,0)[0] * 2.0 / imageMat.width() - 1),
                                        (float) - (imagePoints.get(0,1)[0] * 2.0 / imageMat.height() - 1)};
                float[] endPoint = {(float) (imagePoints.get(1,0)[0] * 2.0 / imageMat.width() - 1 ),
                                    (float) - (imagePoints.get(1,1)[0] * 2.0 / imageMat.height() - 1)};
                float[] pointDepth = {300f, 300f};

                shader.drawLine(startPoint, endPoint, pointDepth);
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
        if (imageMat != null) {
            Point screenCenter = new Point(imageMat.width() / 2, imageMat.height() / 2);

            int ch_size = 15;
            Point crossHair_A1 = new Point(screenCenter.x - ch_size, screenCenter.y);
            Point crossHair_A2 = new Point(screenCenter.x + ch_size, screenCenter.y);
            Point crossHair_B1 = new Point(screenCenter.x, screenCenter.y - ch_size);
            Point crossHair_B2 = new Point(screenCenter.x, screenCenter.y + ch_size);

            //circle(imageMat, screenCenter, 15, new Scalar(255, 255, 255), 2);
            line(imageMat, crossHair_A1, crossHair_A2, white_tp, 4);
            line(imageMat, crossHair_B1, crossHair_B2, white_tp, 4);

            //drawAxis(imageMat, cameraMatrix, distortionCoefficients, rvecs, tvecs, markerLength);
            // Bilden, koordinaten, radie, färg
            for (int i = 0; i < rowCounter; i++) {
                circle(imageMat, new Point(imagePoints.get(i, 0)[0], imagePoints.get(i, 1)[0]), 10, measureColor, -1);
            }

            if (rowCounter == 2) {
                double _x = (imagePoints.get(0, 0)[0] + imagePoints.get(1, 0)[0]) / 2 - 50;
                double _y = (imagePoints.get(0, 1)[0] + imagePoints.get(1, 1)[0]) /2 - 20;
                putText(imageMat, (int) markerContainer.getDistance() + " mm", new Point(_x, _y),
                       2, 1, white, 2, 2);
            }

            setImageBitmapFromMat(imageMat);
        } else {
            setImageBitmapFromMat();
        }

        glSurfaceView.requestRender();
        //Log.d("Analysis time", "" + (int) (System.currentTimeMillis() - analyzeTime) + "ms");
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

        // Create an arraylist of Mats to store the corners of the markers
        ArrayList<Mat> listOfCorners = new ArrayList<>();

        // Create a Mat to store all the ids of the markers
        Mat ids = new Mat();

        DetectorParameters params = DetectorParameters.create();
        params.set_cornerRefinementMethod(CORNER_REFINE_SUBPIX);

        // Detect the markers in the image and store their corners and ids in the corresponding variables
        detectMarkers(imageMat, getPredefinedDictionary(DICT_6X6_50), listOfCorners, ids, params);

        // If there are no markers, do nothing
        if (listOfCorners.size() <= 0) {
            markerContainer.makeEmpty();
            marker_in_frame = false;
            return;
        }
        marker_in_frame = true;

        // Sets the marker corners in (u,v)-coordinates
        markerContainer.setMarkerCorners(listOfCorners, imageMat.width(), imageMat.height());

        // TODO: BOKMÄRKE !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        /* Sammanfattning:
            - Namnbyten, för tydlighet när man läser
            - Uppdelning, kod är flyttad till funktioner
        */

        // Create empty matrices for the rotation vector and the translation vector
        Mat rvecs = new Mat();
        Mat tvecs = new Mat();

        // Estimate pose and get rvecs and tvecs
        estimatePoseSingleMarkers(listOfCorners, markerLength, cameraMatrix, distortionCoefficients, rvecs, tvecs);

        translation_vector = tvecs;

        //drawAxis(imageMat, cameraMatrix, distortionCoefficients, rvecs, tvecs, markerLength/2);
        // TODO: Undersök hur vi korrigerar z-axeln rätt

        // Placerar ut en punkt i mitten på skärmen för just denna frame
        if (userPoint) {
            createPoint(rvecs, tvecs);
        }
        // Om användaren har placerat ut en punkt
        if (rowCounter > 0) {
            toPixelCoordinates(tracked_vector_MC, rvecs, tvecs, cameraMatrix);
            // Skicka List<vec3> till toPixelCoordinates()
        }

        // If exactly two markers are detected, measure the distance between them
        if (rowCounter == 2) {
            // Calculate the distance between marker 1 and marker 2
            double distance = Math.sqrt( Math.pow(tracked_vector_MC.get(0, 0)[0] - tracked_vector_MC.get(1, 0)[0], 2) +
                                         Math.pow(tracked_vector_MC.get(0, 1)[0] - tracked_vector_MC.get(1, 1)[0], 2) +
                                         Math.pow(tracked_vector_MC.get(0, 2)[0] - tracked_vector_MC.get(1, 2)[0], 2)
                                );


            markerContainer.setDistance(distance);

        }

        else {
            markerContainer.clearDistance();
            markerContainer.clearDepths();
        }


    }

    // Funktion som hittar koordinaten för en punkt
    private void createPoint(Mat rvecs, Mat tvecs) {
        if (rowCounter == 2) {
            // Reset the row counter
            rowCounter = 0;
        }
        else {
            // Get the intersection point of the camera line and the marker plane
            double z_intersect = getIntersect(rvecs, tvecs);

            rotMatrix = new Mat(3, 3, CvType.CV_64F);
            Rodrigues(rvecs, rotMatrix);

            // Skapar en matris som beskriver både rotation och translation enl. kameran
            Mat resultMatrix = new Mat(4, 4, CvType.CV_64F);
            double[] resultArray_r1 = {rotMatrix.get(0, 0)[0], rotMatrix.get(0, 1)[0], rotMatrix.get(0, 2)[0], tvecs.get(0, 0)[0]};
            double[] resultArray_r2 = {rotMatrix.get(1, 0)[0], rotMatrix.get(1, 1)[0], rotMatrix.get(1, 2)[0], tvecs.get(0, 0)[1]};
            double[] resultArray_r3 = {rotMatrix.get(2, 0)[0], rotMatrix.get(2, 1)[0], rotMatrix.get(2, 2)[0], tvecs.get(0, 0)[2]};
            double[] resultArray_r4 = {0, 0, 0, 1};

            resultMatrix.put(0, 0, resultArray_r1);
            resultMatrix.put(1, 0, resultArray_r2);
            resultMatrix.put(2, 0, resultArray_r3);
            resultMatrix.put(3, 0, resultArray_r4); // TODO: behöver sista raden vara med?

            // Vi inverterar matrisen så vi kan gå från kamera --> markör
            invert(resultMatrix, resultMatrix); // 4 x 4

            // Vektorn från markörens mitt till skärpunkten i världskoordinater/kamerans koordinater (WC)
            Mat tracked_vector_WC = new Mat(4, 1, CvType.CV_64F);
            tracked_vector_WC.put(0, 0, userX);//- tvec.get(0,0)[0]);
            tracked_vector_WC.put(1, 0, userY);//- tvec.get(0,0)[1]);
            tracked_vector_WC.put(2, 0, z_intersect);// - tvecs.get(0,0)[2]); // TODO: Tänk efter om detta är rimligt egentligen!
            tracked_vector_WC.put(3, 0, 1);

            // Applicera inversmatrisen på track-vektorn så vi får den i markörens koordinatsystem (MC)
            Log.d("ABC123", "När det går bra frå vi att A = " + resultMatrix.type() + " och tvWC = " + tracked_vector_WC.type());
            gemm(resultMatrix, tracked_vector_WC, 1, tracked_vector_WC, 0, tracked_vector_WC);

            if (rowCounter == 0) {
                tracked_vector_MC = new Mat(2, 3, CvType.CV_64F);
            }

            tracked_vector_MC.put(rowCounter, 0, tracked_vector_WC.get(0, 0)[0]);
            tracked_vector_MC.put(rowCounter, 1, tracked_vector_WC.get(1, 0)[0]);
            //tracked_vector_MC.put(rowCounter, 2, tracked_vector_WC.get(2, 0)[0]);
            tracked_vector_MC.put(rowCounter, 2, 0);

            rowCounter++;
        }
        userPoint = false;
    }

    // Funktion för att rita ut punkter
    private void toPixelCoordinates(Mat vector, Mat rvec, Mat tvec, Mat cameraMatrix) {
        // Sätt rows = vector.size()
        imagePoints = new Mat(2,2, CvType.CV_64F);

        // Kolla vector.size()
        // TODO: kolla om punkten hamnar bakom kameran
        // OpenGL cullar bort rasterpunkterna som är utanför frustumet
        // Ta bort punkten om den hamnar bakom kameran => felmeddelande
        // TODO: Undersök om vi kan använda ett bräde/flera markörer istället för endast en
        // Jämför med ARCore-baserad applikation, en markör, en 2x2, en jättestor
        // TODO: (Om tid finns) undersök markörer i två plan

        for(int i = 0; i < rowCounter; i++) {
            // gör till v3?
            Point3 p = new Point3(vector.get(i,0)[0], vector.get(i,1)[0], vector.get(i,2)[0]);
            MatOfPoint3f tracked_point = new MatOfPoint3f(p);

            MatOfPoint2f points2D = new MatOfPoint2f();
            //MatOfDouble test_distortion = new MatOfDouble(0,0,0,0,0 );
            MatOfDouble test_distortion = new MatOfDouble(0.3363400302339669, -1.095918772105208, 0.001395881531710981, -0.00113269394288377, 1.487878827052818 );
            projectPoints(tracked_point, rvec, tvec, cameraMatrix, test_distortion, points2D);

            //Log.d("3D-point:", "" + tracked_point.dump());
            //Log.d("2D-point: ", "" + points2D.dump());


            imagePoints.put(i, 0, points2D.get(0,0)[0]);
            imagePoints.put(i, 1, points2D.get(0,0)[1]);
        };
        //Log.d("", "*************************************************");
    }

    private void generateIntrinsicsAndDistortion() {
        // Get the camera parameters
        float cx = imageBitmap.getWidth() * 0.49904564092f;
        float cy = imageBitmap.getHeight() * 0.49937073486f;
        float fx = imageBitmap.getWidth() * 0.67352064836f;
        float fy = imageBitmap.getHeight() * 1.19671093141f;

        // Construct the camera matrix using fx, fy, cx, cy
        float[] intrinsics = {fx, 0, cx, 0, fy, cy, 0, 0, 1};
        cameraMatrix = new Mat(3, 3, CvType.CV_32F);
        cameraMatrix.put(0, 0, intrinsics);

        // Assign distortion coefficients (currently empty)
        //float[] distortion = {0.3363400302339669f, -1.095918772105208f, 0.001395881531710981f, -0.00113269394288377f, 1.487878827052818f};
        float[] distortion = {0,0,0,0,0};
        distortionCoefficients = new Mat(1, 5, CvType.CV_32F);
        distortionCoefficients.put(0, 0, distortion);
    }

    // Hanna började något konstigt experiment här..
    private Mat getCornerCoordinates() {
        Mat cornersMC = new Mat(4,3, CvType.CV_64F);
        Mat cornersWC = new Mat(4,3, CvType.CV_64F);

        // 0th corner
        cornersMC.put(0,0,-markerLength/2);
        cornersMC.put(0,1,markerLength/2);
        cornersMC.put(0,2,0);

        // 1st corner
        cornersMC.put(1,0,markerLength/2);
        cornersMC.put(1,1,markerLength/2);
        cornersMC.put(1,2,0);

        // 2nd corner
        cornersMC.put(2,0,markerLength/2);
        cornersMC.put(2,1,-markerLength/2);
        cornersMC.put(2,2,0);

        // 3rd corner
        cornersMC.put(3,0,-markerLength/2);
        cornersMC.put(3,1,-markerLength/2);
        cornersMC.put(3,2,0);

        // Get the MC_to_WC
        Mat matrixToWC = new Mat(4,4, CvType.CV_64F);

        gemm(matrixToWC, cornersMC, 1, cornersMC, 0, cornersMC);
        return cornersWC;
    }

    // Funktion som hittar en markörs plan
    private double getIntersect(Mat rvecs, Mat tvecs) {

        List<Mat> camera_points = new ArrayList<>();
        Mat rot_mat = new Mat(3,3,CvType.CV_64F);
        Rodrigues(rvecs, rot_mat);
        rot_mat = rot_mat.t();
        double half_side = markerLength/2;

        ///////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Mat cornersWC = getCornerCoordinates();
        //////////////////////////////////////////////////////////////////////////////////////////////////////////

        // E is the halfway point of one side
        double Ex = rot_mat.get(0,0)[0] * half_side;
        double Ey = rot_mat.get(0,1)[0] * half_side;
        double Ez = rot_mat.get(0,2)[0] * half_side;

        // F is the halfway point of the perpendicular side
        double Fx = rot_mat.get(1,0)[0] * half_side;
        double Fy = rot_mat.get(1,1)[0] * half_side;
        double Fz = rot_mat.get(1,2)[0] * half_side;

        // First corner (corner 0 = övre vänstra)
        double corner_x = -Ex + Fx + tvecs.get(0,0)[0];
        double corner_y = -Ey + Fy + tvecs.get(0,0)[1];
        double corner_z = -Ez + Fz + tvecs.get(0,0)[2];

        // Add to matrix
        double[] corner_coord = {corner_x, corner_y, corner_z};
        Mat camera_points_coords_0 = new Mat(1,3,CvType.CV_64F);
        camera_points_coords_0.put(0, 0, corner_coord);
        camera_points.add(camera_points_coords_0);

        // Second corner (corner 1 = övre högra)
        corner_x = Ex + Fx + tvecs.get(0,0)[0];
        corner_y = Ey + Fy + tvecs.get(0,0)[1];
        corner_z = Ez + Fz + tvecs.get(0,0)[2];

        corner_coord[0] = corner_x; corner_coord[1] = corner_y; corner_coord[2] = corner_z;
        Mat camera_points_coords_1 = new Mat(1,3,CvType.CV_64F);
        camera_points_coords_1.put(0, 0, corner_coord);
        camera_points.add(camera_points_coords_1);

        // Third corner (corner 2 = nedre högra)
        corner_x = Ex - Fx + tvecs.get(0,0)[0];
        corner_y = Ey - Fy + tvecs.get(0,0)[1];
        corner_z = Ez - Fz + tvecs.get(0,0)[2];

        corner_coord[0] = corner_x; corner_coord[1] = corner_y; corner_coord[2] = corner_z;
        Mat camera_points_coords_2 = new Mat(1,3,CvType.CV_64F);
        camera_points_coords_2.put(0, 0, corner_coord);
        camera_points.add(camera_points_coords_2);

        // Fourth corner (corner 3 = nedre vänstra)
        corner_x = -Ex - Fx + tvecs.get(0,0)[0];
        corner_y = -Ey - Fy + tvecs.get(0,0)[1];
        corner_z = -Ez - Fz + tvecs.get(0,0)[2];

        corner_coord[0] = corner_x; corner_coord[1] = corner_y; corner_coord[2] = corner_z;
        Mat camera_points_coords_3 = new Mat(1,3,CvType.CV_64F);
        camera_points_coords_3.put(0, 0, corner_coord);
        camera_points.add(camera_points_coords_3);


        /****************************
         *    FÖR ATT HITTA PLAN    *
         ****************************/

        // Få ut koordinaterna till hörn 1 (övre högra)
        vec3 corner1 = new vec3();
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

        return z_intersect;
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

    void placePoint() {
        userPoint = true;
    }

    void placePoint(int x_px, int y_px) {
        userPoint = true;
    }


    void pxToWC(int _x, int _y) {
        /*
        
        if(!marker_in_frame) return;
        if(rowCounter < 1 || rowCounter > 2) return;
        Log.d("CONV", "ENTERED pxToWC");

        // Convert pixel coordinates to camera coordinates
        // rotMatrix = 3 x 3
        // cameraMatrix = 3 x 3
        // [u,v] = 3 x 1
        // t = 3 x 1


        Mat R_M = new Mat(3, 3, CvType.CV_64F);
        Mat RM_uv = new Mat(3, 1, CvType.CV_64F);
        Mat translation_vector_t = new Mat(3, 1, CvType.CV_64F);
        Mat R_t = new Mat(3, 1, CvType.CV_64F);
        Mat result = new Mat(3, 1, CvType.CV_64F);

        Mat uv = new Mat(3,1, CvType.CV_64F);
        uv.put(0,0, new double[]{_x, _y, 1});

        Mat inv_rotMatrix = new Mat(3, 3, CvType.CV_64F);
        invert(rotMatrix, inv_rotMatrix);

        Mat inv_cameraMatrix = new Mat(3, 3, CvType.CV_64F);
        invert(cameraMatrix, inv_cameraMatrix);

        if (inv_cameraMatrix.type() != inv_rotMatrix.type()) {
            //Log.d("ABC123", "INCOMPATIBLE");
            //Log.d("ABC123", "cM = " + inv_cameraMatrix.type() + " and rM = " + inv_rotMatrix.type());
            //Log.d("ABC123", "cM = " + inv_cameraMatrix.dump());
            //Log.d("ABC123", "rM = " + inv_rotMatrix.dump());
            inv_cameraMatrix.convertTo(inv_cameraMatrix, inv_rotMatrix.type());
        }

        // R^{-1} * M^{-1}
        gemm(inv_rotMatrix, inv_cameraMatrix, 1, new Mat(), 0, R_M);
        //R^{-1} * M^{-1} * uv
        gemm(R_M, uv, 1, new Mat(), 0, RM_uv);

        // R^{-1} * t
        if (translation_vector.type() != inv_rotMatrix.type()) {
            Log.d("ABC123", "tvec innan = " + translation_vector.dump());
            translation_vector_t.put(0,0, translation_vector.get(0,0));
            translation_vector_t.put(1,0, translation_vector.get(0,1));
            translation_vector_t.put(2,0, translation_vector.get(0,2));
            Log.d("ABC123", "tvec efter = " + translation_vector_t.dump());
            //translation_vector.convertTo(translation_vector, inv_rotMatrix.type());
        }
        gemm(inv_rotMatrix, translation_vector_t, 1, new Mat(), 0, R_t);

        userX = _x;
        userY = _y;

        */

    }

}
