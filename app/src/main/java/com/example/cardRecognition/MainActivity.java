package com.example.cardRecognition;

import static org.opencv.imgproc.Imgproc.boundingRect;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    /*
      Assisted from Github
      https://github.com/pakzan/CardRecog
    */

    CameraBridgeViewBase cameraBridgeViewBase;
    Mat mat;
    BaseLoaderCallback baseloadercallback;
    TextView textView;
    Button popUpButton;

    static {
        if (!OpenCVLoader.initDebug())
            Log.d("ERROR", "Unable to load OpenCV");
        else
            Log.d("SUCCESS", "OpenCV loaded");
    }

    private void checkPermission(String permission_type){
        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                permission_type)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{permission_type},
                0);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        checkPermission(Manifest.permission.CAMERA);
        checkPermission(Manifest.permission.INTERNET);


        textView = findViewById(R.id.text1);
        popUpButton = findViewById(R.id.button);

        cameraBridgeViewBase = (JavaCameraView) findViewById(R.id.myCameraView);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(this);

        baseloadercallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                switch (status){
                    case BaseLoaderCallback.SUCCESS:
                        cameraBridgeViewBase.enableView();
                        break;
                    default:
                        super.onManagerConnected(status);
                        break;
                }
            }
        };

        popUpButton.setOnClickListener(view -> onButtonShowPopUp(view));
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mat = new Mat(width, height, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        mat.release();
    }



    public MatOfPoint getMaxContour(Mat mat){
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(mat,contours,new Mat(),Imgproc.RETR_TREE,Imgproc.CHAIN_APPROX_SIMPLE);

        //find largest contour
        double maxArea = 0;
        MatOfPoint maxContour = null;

        Iterator<MatOfPoint> iterator = contours.iterator();
        while (iterator.hasNext()){
            MatOfPoint contour = iterator.next();
            double area = Imgproc.contourArea(contour);
            if(area > maxArea){
                maxArea = area;
                maxContour = contour;
            }
        }
        return maxContour;
    }

    public String getCardType(Mat currMat, String images_path) throws IOException {
        // Covers Section 4 of the Card methodology
        String[] images_name = getAssets().list(images_path);
        int min_diff_pixel = 4000;
        String min_diff_str = "";
        final Mat[] tmp = new Mat[1];

        for (String image_name : images_name) {
            InputStream inputStream = getAssets().open(images_path + "/" + image_name);
            Mat assetMaT = new Mat();
            Utils.bitmapToMat(BitmapFactory.decodeStream(inputStream), assetMaT);
            Imgproc.cvtColor(assetMaT, assetMaT, Imgproc.COLOR_BGRA2GRAY, 1);

            final Mat sizedMat = new Mat();
            //resize Mat to fit assets images
            Size size = new Size(assetMaT.height(), assetMaT.width());
            Imgproc.resize(currMat, sizedMat, size);
            Core.rotate(sizedMat, sizedMat, Core.ROTATE_90_CLOCKWISE);

            //compare image pixel
            Mat diffMat = new Mat();
            Core.absdiff(assetMaT, sizedMat, diffMat);
            int matchPixel = Core.countNonZero(diffMat);

            //compare image contour
            List<MatOfPoint> assetCompare = new ArrayList<>();
            List<MatOfPoint> sizedCompare = new ArrayList<>();
            Imgproc.findContours(assetMaT,assetCompare,new Mat(),Imgproc.RETR_TREE,Imgproc.CHAIN_APPROX_SIMPLE);
            Imgproc.findContours(sizedMat,sizedCompare,new Mat(),Imgproc.RETR_TREE,Imgproc.CHAIN_APPROX_SIMPLE);

            //get the most similar image in pixel and shape
            if (matchPixel < min_diff_pixel) {
                min_diff_pixel = matchPixel;
                min_diff_str = image_name;
                tmp[0] = sizedMat;
            }
        }

        return min_diff_str.replace(".jpg", "");
    }


    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mat = inputFrame.rgba();

        // 1. Greyscale and blur the image to remove unnecessary details.
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(mat, mat, new Size(1,1), 0);
        Imgproc.threshold(mat, mat, 120, 255, Imgproc.THRESH_BINARY);


        //2. Find the largest white area and identify it as a card
        MatOfPoint max_Contour = getMaxContour(mat);

        //check if max_contour exists
        if(max_Contour != null){
            //draw polynomial around card
            double epsilon = 0.02*Imgproc.arcLength(new MatOfPoint2f(max_Contour.toArray()),true);
            MatOfPoint2f approximate = new MatOfPoint2f();
            Imgproc.approxPolyDP(new MatOfPoint2f(max_Contour.toArray()),approximate,epsilon,true);

            Point points[] = approximate.toArray();

            //if found rectangle shape(card), carry on
            if(points.length == 4) {
                //draw rotated rectangle
                for (int i = 0; i < 4; ++i) {
                    Imgproc.line(mat, points[i], points[(i + 1) % 4], new Scalar(255, 255, 255));
                }

                //adjust the orientation of the card
                double widthBetween01 = Math.sqrt(Math.pow(points[0].x - points[1].x, 2) + Math.pow(points[0].y - points[1].y, 2));
                double heightBetween12 = Math.sqrt(Math.pow(points[2].x - points[1].x, 2) + Math.pow(points[2].y - points[1].y, 2));
                double widthBetween23 = Math.sqrt(Math.pow(points[2].x - points[3].x, 2) + Math.pow(points[2].y - points[3].y, 2));
                double heightBetween30 = Math.sqrt(Math.pow(points[0].x - points[3].x, 2) + Math.pow(points[0].y - points[3].y, 2));

                double total_width;
                double total_height;

                if(Math.abs(heightBetween12 - heightBetween30) > Math.abs(widthBetween01 - widthBetween23)){
                    total_width = (widthBetween01 + widthBetween23) * (Math.max(heightBetween12, heightBetween30)/Math.min(heightBetween12, heightBetween30));
                    total_height = 2 * Math.max(heightBetween12, heightBetween30);
                }else{
                    total_width = 2 * Math.max(widthBetween01, widthBetween23);
                    total_height = (heightBetween12 + heightBetween30) * (Math.max(widthBetween01, widthBetween23)/Math.min(widthBetween01, widthBetween23));
                }

                //rotate card
                if (total_width > total_height) {
                    Point tmpPoint = points[3];
                    points[3] = points[2];
                    points[2] = points[1];
                    points[1] = points[0];
                    points[0] = tmpPoint;
                }

                //input rectangle real size
                MatOfPoint2f distance = new MatOfPoint2f(
                        new Point(0, 0),
                        new Point(0, 719),
                        new Point(809, 719),
                        new Point(809, 0)
                );
                //get transform perspective of polynomial to rectangle
                Mat warpMat = Imgproc.getPerspectiveTransform(new MatOfPoint2f(points), distance);
                //transform polynomial to rectangle
                Imgproc.warpPerspective(mat, mat, warpMat, mat.size());

                //3. Crop out the top left position of the card to obtain its Rank and Suit
                Mat cropMat = new Mat(mat, new Rect(0, 604, 227, 115));
                Core.bitwise_not(cropMat, cropMat);

                //get rank and suit from cropped image
                Mat Rank = new Mat(cropMat, new Rect(0, 0, 141, 115));
                Mat Suit = new Mat(cropMat, new Rect(86, 0, 141, 115));


                //crop rank and suit once more to fit the image
                MatOfPoint tempRank = getMaxContour(Rank);

                if (tempRank != null) {
                    //crop rank
                    Rank = new Mat(Rank, boundingRect(tempRank));
                }

                //crop suit
                MatOfPoint tempSuit = getMaxContour(Suit);
                if (tempSuit != null)
                    Suit = new Mat(Suit, boundingRect(tempSuit));

                //4. Compare the Rank and Suit with the pre-stored images and find the most similar one
                if (!(tempRank == null || tempSuit == null)) {
                    final String SRank;
                    final String SSuit;

                    try {
                        SRank = getCardType(Rank, "Rank");
                        SSuit = getCardType(Suit, "Suit");

                        //5. Display the results
                        Thread thread = new Thread() {
                            @Override
                            public void run() {
                                //run on ui thread
                                runOnUiThread(() -> {
                                    //if no matched card, output empty string, followed by new text
                                    if(TextUtils.isEmpty(SRank) || TextUtils.isEmpty(SSuit)) {
                                        textView.setText("");
                                        new Handler().postDelayed(() -> {
                                            textView.setText("Find new card");

                                        }, 3000);

                                    } else {
                                        textView.setText(SRank + " " + SSuit);

                                    }
                                });
                            }
                        };
                        thread.start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
        return mat;
    }

    public void onButtonShowPopUp(View view){

        /*
            Taken from StackOverflow
            https://stackoverflow.com/a/50188704
         */

        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater)
                getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.handpopup, null);

        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.setElevation(20);
        }

        // dismiss the popup window when touched
        popupView.setOnTouchListener((v, event) -> {
            popupWindow.dismiss();
            return true;
        });
    }



    @Override
    protected void onPause() {
        super.onPause();
        if(cameraBridgeViewBase!=null)
            cameraBridgeViewBase.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!OpenCVLoader.initDebug())
            Toast.makeText(getApplicationContext(),"OpenCV Issue, restart app", Toast.LENGTH_SHORT).show();
        else
            baseloadercallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(cameraBridgeViewBase!=null)
            cameraBridgeViewBase.disableView();
    }

}
