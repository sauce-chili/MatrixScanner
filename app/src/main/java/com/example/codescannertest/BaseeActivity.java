package com.example.codescannertest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.example.codescannertest.Managers.PackManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.drive.DriveScopes;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.single.CompositePermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;



import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;


public class BaseeActivity extends AppCompatActivity {

    GoogleSignInAccount userAcc;

    protected static class PermissionManager {

        public static boolean requestSignInGoogleAccount(Activity activity) {
            GoogleSignInOptions signInOptions = new GoogleSignInOptions
                    .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(new Scope(DriveScopes.DRIVE))
                    .build();

            AtomicBoolean result = new AtomicBoolean(false);

            GoogleSignInClient client = GoogleSignIn.getClient(activity, signInOptions);
            Log.d("SignInAcc","client: " + client.toString());
            Log.d("SignInAcc","Intent: " + client.getSignInIntent().toString());
            activity.startActivityForResult(client.getSignInIntent(),102);
            GoogleSignIn.getSignedInAccountFromIntent(client.getSignInIntent())
                    .addOnSuccessListener(googleSignInAccount -> {

                        GoogleAccountCredential credential = GoogleAccountCredential
                                .usingOAuth2(activity.getBaseContext(),
                                        Collections.singleton(DriveScopes.DRIVE_FILE));
                        credential.setSelectedAccount(googleSignInAccount.getAccount());
                        result.set(true);
                        Log.d("SignInGoogleAcc","Successful sign in account");
                    })
                    .addOnFailureListener(e -> {

                        Log.e("SignInGoogleAcc",e.getMessage());
                        Toast.makeText(activity.getApplicationContext(),
                                "Не удалось войти в аккаунт",Toast.LENGTH_SHORT).show();
                        result.set(false);

                    });

            return result.get();
        }

        public static void requestCameraPermission(Activity activity, Callable<Boolean> CameraCreator){
             /*LayoutInflater inflater = LayoutInflater.from(activity.getApplicationContext());
             View v = inflater.inflate(R.layout.dialog_setting,null,false);*/

             /*PermissionListener dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                    .withContext(activity)
                    .withTitle(R.string.CameraPermissionTitle)
                    .withMessage(R.string.CameraPermissionText)
                    .withButtonText("Настройки", () -> {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                        intent.setData(uri);
                        activity.startActivity(intent);
                    })
                    .build();*/

            //Log.d("RequestPermissionCamera","request permission.Already activity to transition: " + activity.isActivityTransitionRunning());
            Dexter.withContext(activity)
                    .withPermission(Manifest.permission.CAMERA)
                    .withListener(new CompositePermissionListener(
                            new PermissionListener() {
                                @Override
                                public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                                    try {
                                        CameraCreator.call();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                                    if(permissionDeniedResponse.isPermanentlyDenied()){
                                        showSettingDialog(activity,
                                                activity.getResources()
                                                        .getString(R.string.CameraPermissionTitle),
                                                activity.getResources()
                                                        .getString(R.string.CameraPermissionText));
                                    }
                                }

                                @Override
                                public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest,
                                                                               PermissionToken permissionToken) {
                                    permissionToken.continuePermissionRequest();
                                }
                            }/*,
                            dialogOnDeniedPermissionListener*/
                    )).check();
        }

        public static void requestReadAndWriteStoragePermission(Activity activity,Callable<Boolean> Callback){
            Dexter.withContext(activity)
                    .withPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .withListener(new MultiplePermissionsListener() {
                        @RequiresApi(api = Build.VERSION_CODES.O)
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                            if (multiplePermissionsReport.areAllPermissionsGranted()){
                                try {
                                    Callback.call();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            else if(multiplePermissionsReport.isAnyPermissionPermanentlyDenied()){
                                showSettingDialog(activity,
                                        activity.getResources()
                                                .getString(R.string.ReadWritePermissionTitle),
                                        activity.getResources()
                                                .getString(R.string.ReadWritePermissionText));
                            }
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list,
                                                                       PermissionToken permissionToken) {
                            permissionToken.continuePermissionRequest();
                        }
                    }).check();
        }

        public static void requestInternetPermission(Activity activity){
            Dexter.withContext(activity.getBaseContext())
                    .withPermission(Manifest.permission.INTERNET)
                    .withListener(new PermissionListener(){
                        @Override
                        public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                            requestSignInGoogleAccount(activity);
                        }

                        @Override
                        public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                            showSettingDialog(activity,
                                    activity.getResources()
                                            .getString(R.string.GoogleAccountPermissionTitle),
                                    activity.getResources()
                                            .getString(R.string.GoogleAccountPermissionText));
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest,
                                                                       PermissionToken permissionToken) {
                            permissionToken.continuePermissionRequest();
                        }
                    });
        }

        public static void showSettingDialog(Activity activity, String title, String msg){
            /*Log.d("SettingDialog","running activity: " + activity.isActivityTransitionRunning());
            Log.d("SettingDialog","activity is alive: " + activity.isFinishing());*/
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(msg)
                    .setPositiveButton("Настройки", (dialog, which) -> {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                        intent.setData(uri);
                        dialog.cancel();
                        activity.startActivity(intent);
                    })
                    .setNegativeButton("Продолжить", (dialog, which) -> dialog.cancel())
                    .show();
        }

        public static boolean checkPermission(Activity activity,String permission){
            return ActivityCompat.checkSelfPermission(activity,
                    permission) == PackageManager.PERMISSION_GRANTED;
        }

        public static boolean checkCameraPermission(Activity activity){
            return checkPermission(activity,Manifest.permission.CAMERA);
        }

        public static boolean checkWriteStoragePermission(Activity activity){
            return checkPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        public static boolean checkReadStoragePermission(Activity activity){
            return checkPermission(activity,Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        public static boolean checkInternetPermission(Activity activity){
            Log.d("Permission","Internet: " + checkPermission(activity,Manifest.permission.INTERNET));
            return checkPermission(activity,Manifest.permission.INTERNET);
        }

        public static boolean checkGoogleAccountAccess(Activity activity){
            return checkPermission(activity,Manifest.permission.GET_ACCOUNTS);
        }

        public static boolean checkSignInAccount(Activity activity){
            Log.d("Permission","Permission SignInGAcc:" +
                    (GoogleSignIn.getLastSignedInAccount(activity.getBaseContext()) != null));
            return GoogleSignIn.getLastSignedInAccount(activity.getBaseContext()) != null;
        }
    }


    protected PackManager packManager;

    public BaseeActivity(){
        if (packManager == null) packManager = PackManager.getInstance(BaseeActivity.this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}


