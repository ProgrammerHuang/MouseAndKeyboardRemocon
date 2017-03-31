package com.androidapp.santtu.remotemouseandkeyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

public class SetupActivity extends AppCompatActivity {

    Context context;    //this activity's context
    private static final int REQUEST_CAMERA = 200;

    SurfaceView cameraView; //displays what the camera sees
    EditText ipAddressField;    //user can input the server ip with this
    BarcodeDetector barcodeDetector;    //handles detecting QR codes
    CameraSource cameraSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        context = this;
        ipAddressField = (EditText) findViewById(R.id.ipAddressField);

        //setting fab to handle connecting with manually written IP
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(ipAddressField == null)
                    SingleToast.show(context, "Insert valid IP address", Toast.LENGTH_LONG);
                else
                {
                    SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("serverIp", ipAddressField.getText().toString());
                    editor.putBoolean("firstTimeSetupDone", true);

                    editor.commit();
                    Intent i = new Intent(SetupActivity.this, MainActivity.class);
                    startActivity(i);
                    finish();
                }

            }
        });

        //init camera and QR reading
        cameraView = (SurfaceView) findViewById(R.id.camera_view);

        barcodeDetector =
                new BarcodeDetector.Builder(this)
                        .setBarcodeFormats(Barcode.QR_CODE)
                        .build();

        cameraSource = new CameraSource
                .Builder(this, barcodeDetector)
                .setAutoFocusEnabled(true)
                .setRequestedPreviewSize(640, 480)
                .build();



        cameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    cameraSource.start(cameraView.getHolder()); //doesn't get executed if app had to ask for permission!
                } catch (IOException ie) {
                    Log.e("CAMERA SOURCE", ie.getMessage());
                }
            }



            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder)
            {
                cameraSource.stop();
            }
        });

        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections)
            {
                final SparseArray<Barcode> barcodes = detections.getDetectedItems();

                if (barcodes.size() != 0) {

                    //save the connection (server's) IP here and jump to main activity
                    SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("serverIp", barcodes.valueAt(0).displayValue);
                    editor.putBoolean("firstTimeSetupDone", true);

                    editor.commit();
                    Intent i = new Intent(SetupActivity.this, MainActivity.class);
                    startActivity(i);
                    finish();

                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if(grantResults[0] == PackageManager.PERMISSION_GRANTED)    //if the user accepts camera permission, restart this activity
        {
            Intent i = new Intent(SetupActivity.this, SetupActivity.class);
            startActivity(i);
            finish();
        }

    }

}
