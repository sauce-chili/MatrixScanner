package com.example.codescannertest;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MainActivity extends BaseActivity {

    private final long delayTimeMillis = 4000;
    private long lastDecodeTimeMillis = 0;
    private CodeScanner mCodeScanner;
    private CodeScannerView camPreview;
    private FloatingActionButton btnGoToReviewPackage;

    private void bindBtnGoToReviewPackage(){
        btnGoToReviewPackage = findViewById(R.id.goToReviewPackage);
        btnGoToReviewPackage.setOnClickListener(v -> {
            startActivity(new Intent(this,ReviewPackages.class)); });
    }

    private void bindCameraPreview(){
        camPreview = findViewById(R.id.scanner_view);
        mCodeScanner = new CodeScanner(this, camPreview,0);

        Log.d("Formats: ", mCodeScanner.getFormats().toString());
        List<BarcodeFormat> format = new ArrayList<BarcodeFormat>();
        format.add(BarcodeFormat.DATA_MATRIX);
        mCodeScanner.setFormats(format);
        mCodeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(@NonNull final Result result) {

                if (System.currentTimeMillis() - lastDecodeTimeMillis > delayTimeMillis)
                {

                    Log.d("Recognize","write in pack");
                    runOnUiThread(()->packManager.writeInWritingPackage(result.getText()));
                    // Измененеие цвета рамки
                    new Thread(){
                        @Override
                        public synchronized void start() {
                            super.start();
                        }

                        @Override
                        public void run() {
                            runOnUiThread(() -> {
                                camPreview.setFrameColor(Color.GREEN);
                            });
                            try {
                                sleep(1500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            runOnUiThread(() -> camPreview.setFrameColor(Color.parseColor("#FFEB3B")));
                            super.run();
                        }
                    }.start();

                    lastDecodeTimeMillis = System.currentTimeMillis();
                    Log.d("Recognize","recognize time in millis: " + lastDecodeTimeMillis);
                }else{
                    Log.d("Recognize","delay time.Start preview");
                }
                runOnUiThread(() -> mCodeScanner.startPreview());
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PermissionManager.getPermissions(this);
        if (!packManager.isAlive()){
            packManager.setCurrentActivity(MainActivity.this);
            packManager.start();
        }
        this.bindCameraPreview();
        this.bindBtnGoToReviewPackage();
    }

    @Override
    protected void onResume() {
        mCodeScanner.startPreview();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mCodeScanner.stopPreview();
        mCodeScanner.releaseResources();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mCodeScanner.stopPreview();
        mCodeScanner.releaseResources();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == RESULT_OK){
            switch (requestCode){
                case PermissionManager.G_ACCOUNT_PERMISSION_CODE:
                    GoogleSignIn.getSignedInAccountFromIntent(data)
                            .addOnSuccessListener(new OnSuccessListener<GoogleSignInAccount>() {
                                @Override
                                public void onSuccess(GoogleSignInAccount googleSignInAccount) {
                                    GoogleAccountCredential credential = GoogleAccountCredential
                                            .usingOAuth2(getApplicationContext(),
                                                    Collections.singleton(DriveScopes.DRIVE_FILE));
                                    credential.setSelectedAccount(googleSignInAccount.getAccount());

                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {

                                }
                            });
                    break;
            }
        }else{

        }

    }
}