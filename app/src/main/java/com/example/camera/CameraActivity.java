package com.example.camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;

import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

//import com.google.android.gms.tasks.OnFailureListener;
//import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
//import com.google.mlkit.vision.common.InputImage;
//import com.google.mlkit.vision.text.Text;
//import com.google.mlkit.vision.text.TextRecognition;
//import com.google.mlkit.vision.text.TextRecognizer;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CameraActivity extends AppCompatActivity {
        private PreviewView previewView;
        private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
        private TextView textView;
        private ImageButton scan;
        private ImageView imageview;
        private Preview preview;
        private ProcessCameraProvider cameraProvider;


    private ImageCapture imageCapture;
    private Executor cameraExecutor;
    private CameraControl cameraControl;
    private  MeteringPointFactory  factory;



    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        previewView = findViewById(R.id.previewView);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);


        cameraExecutor = Executors.newSingleThreadExecutor();
        textView = findViewById(R.id.textView);
        scan = findViewById(R.id.scan);
        //focus touch listener
        View.OnTouchListener foc = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                 if(event.getAction() != MotionEvent.ACTION_MOVE){
                     Log.d("touch", "touched down");
                     Log.d("widhte image captured  ", " juste touched") ;
                     MeteringPoint point2 = factory.createPoint(0, 0);
                     MeteringPoint point = factory.createPoint(x, y);
                     FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE)
                             .addPoint(point2, FocusMeteringAction.FLAG_AE) // could have many
                             // auto calling cancelFocusAndMetering in 5 seconds
                             .setAutoCancelDuration(5, TimeUnit.SECONDS)
                             .build();
                     ListenableFuture future = cameraControl.startFocusAndMetering(action);
                     Executor executor = new Executor() {
                         @Override
                         public void execute(Runnable command) {

                         }
                     };
                     future.addListener( () -> {
                         try {
                             FocusMeteringResult result =(FocusMeteringResult) future.get();
                             // process the result
                         } catch (Exception e) {
                         }
                     } , executor);
                 }



                return true;
            }
        };


        previewView.setOnTouchListener(foc);
        //scan
        scan.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                //take picture
                imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
                    public void onCaptureSuccess (ImageProxy image){
                        Bitmap img = imageProxyToBitmap(image);
                        float j = image.getHeight();
                        img = cropImage(img,previewView,textView);
                        InputImage textimg = InputImage.fromBitmap(img, 0);
                        TextRecognizer recognizer = TextRecognition.getClient();
                        Task<Text> result =
                                recognizer.process(textimg)
                                        .addOnSuccessListener(new OnSuccessListener<Text>() {
                                            @Override
                                            public void onSuccess(Text visionText) {
                                                Log.d("mlkit ", "success") ;
                                                String s = visionText.getText();
                                                //show the result
                                                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
                                            }
                                        })
                                        .addOnFailureListener(
                                                new OnFailureListener() {
                                                    @Override
                                                    public void onFailure(@NonNull Exception e) {
                                                        Log.d("mlkit ", "falls") ;
                                                    }
                                                });

                        image.close();
                    }
                    public void onError (ImageCaptureException exception){
                        Log.d("eroor ", " matmchiche") ;
                    }


                        }
                );

            }
        });

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindImageAnalysis(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(this));

        factory = new SurfaceOrientedMeteringPointFactory(previewView.getWidth(),previewView.getHeight());


    }
    private void bindImageAnalysis(@NonNull ProcessCameraProvider cameraProvider) {

        preview = new Preview.Builder().build();

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        Camera camera = cameraProvider.bindToLifecycle(
                ((LifecycleOwner) this),
                cameraSelector,
                preview,
                imageCapture);
        cameraControl =camera.getCameraControl();
        preview.setSurfaceProvider(previewView.createSurfaceProvider());

    }
    private Bitmap imageProxyToBitmap(ImageProxy image)
    {
        ImageProxy.PlaneProxy planeProxy = image.getPlanes()[0];
        ByteBuffer buffer = planeProxy.getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private Bitmap cropImage( Bitmap bitmap,  View frame,  View reference) {
        float heightOriginal = frame.getHeight();
        float widthOriginal = frame.getWidth();
        float heightFrame = reference.getHeight();
        float widthFrame = reference.getWidth();
        float leftFrame = reference.getLeft();
        float topFrame = reference.getTop();
        float heightReal = bitmap.getHeight();
        float widthReal = bitmap.getWidth();
        float widthFinal = widthFrame * widthReal / widthOriginal;
        float heightFinal = heightFrame * heightReal / heightOriginal;
        float leftFinal = leftFrame * widthReal / widthOriginal;
        float topFinal = topFrame * heightReal / heightOriginal;
        int left = (int)leftFinal;
        int top = (int)topFinal;
        int width = (int)widthFinal;
        int height = (int)heightFinal;
        Bitmap bitmapFinal = Bitmap.createBitmap(bitmap,left,top,width,height);
        return  bitmapFinal;
    }


    }



