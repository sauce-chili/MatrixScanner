package com.example.codescannertest;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.example.codescannertest.model.*;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.xmlpull.v1.sax2.Driver;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class GoogleDriveManager{
    private Executor mExecutor;
    private Drive mDriveService;
    private  final String FILE_SUFFIX;
    private final String WORKING_DIR_NAME;
    private final String MEDIA_TYPE = "text/csv";
    private final Activity activity;
    private final String TOKEN = "68777702676-g5pgckr5tmd0tcdbuu78q37focfbql1l.apps.googleusercontent.com";

    private Drive getGoogleDriverService(){
        GoogleAccountCredential credential = GoogleAccountCredential
                .usingOAuth2(activity.getApplicationContext(),
                        Collections.singleton(DriveScopes.DRIVE_FILE));

        Account acc = GoogleSignIn.getLastSignedInAccount(activity.getApplicationContext()).getAccount();
        credential.setSelectedAccount(acc);

        if(acc == null){
            GoogleSignIn.getSignedInAccountFromIntent(activity.getIntent())
                    .addOnSuccessListener(new OnSuccessListener<GoogleSignInAccount>() {
                        @Override
                        public void onSuccess(GoogleSignInAccount googleSignInAccount) {

                            credential.setSelectedAccount(googleSignInAccount.getAccount());

                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {

                        }
                    });
        }

        return new Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("DataMatrixCodes")
                .build();
    }

    public GoogleDriveManager(Activity activity,String fs,String workingDirName){
        this.activity = activity;
        this.mDriveService = this.getGoogleDriverService();
        this.mExecutor = Executors.newSingleThreadExecutor();
        this.FILE_SUFFIX = fs;
        this.WORKING_DIR_NAME = workingDirName;
    }

    public Task<String> createPack(String fileName,File dir){
        return Tasks.call(mExecutor,() ->{
            String nextToken = null;
            HashMap<String, String> GdirsName = new HashMap<>();
            // Проверяем создана-ли директория
            do{
                try {
                    FileList result = mDriveService.files().list()
                            .setQ("application/vnd.google-apps.folder")
                            .setSpaces("drive")
                            .setFields("nextPageToken, files(id, name)")
                            .setPageToken(null)
                            .execute();

                    for(com.google.api.services.drive.model.File file : result.getFiles()){
                        GdirsName.put(file.getName(),file.getId());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }while (nextToken != null);

            // id директории на Gdisk,куда выгружаются данные
            String idDirGoogleDrive;
            // Если папка отсутсвует на google disk'e,то создаём её
            if(! GdirsName.containsKey(WORKING_DIR_NAME)) {

                com.google.api.services.drive.model.File PathMetadata =
                        new com.google.api.services.drive.model.File();
                PathMetadata.setName(WORKING_DIR_NAME);
                PathMetadata.setMimeType("application/vnd.google-apps.folder");

                com.google.api.services.drive.model.File GPath = mDriveService.files().create(PathMetadata)
                        .setFields("id")
                        .execute();

                idDirGoogleDrive = GPath.getId();
            }else {
                idDirGoogleDrive = GdirsName.get(WORKING_DIR_NAME);
            }

            // Создание файла в директории
            com.google.api.services.drive.model.File GFileMetadata =
                    new com.google.api.services.drive.model.File();
            GFileMetadata.setName(fileName);

            GFileMetadata.setParents(Collections.singletonList(idDirGoogleDrive));
            java.io.File MyFile = new java.io.File(dir.getAbsolutePath() +
                    File.separator + fileName + FILE_SUFFIX);

            FileContent mediaContent = new FileContent(MEDIA_TYPE, MyFile);
            com.google.api.services.drive.model.File Gfile =
                    mDriveService.files().create(GFileMetadata, mediaContent)
                            .setFields("id, parents")
                            .execute();

            return Gfile.getId();
        });
    }
}


class PackManager extends Thread implements Publisher<ArrayList<Pack>> {
    private final static String TAG = "PackManager";
    private final static String SHARED_NAME = "PACK_MANAGER";
    private final static String KEY_CURRENT_WRITING_PACK_NAME = "writing_pack";
    private static PackManager Instance;
    private final String NAME_DIR = "DataMatrixCodes";
    private final String FILE_SUFFIX = ".csv";
    private Activity activity;
    private File dir;
    // For observer
    private ArrayList<Subscriber<ArrayList<Pack>>> subs;
    private ArrayList<Pack> data;
    // For upload selected Pack
    GoogleDriveManager googleDriveManager;

    private PackManager(Activity activity) {
        this.activity = activity;
        subs = new ArrayList<>();
        data = new ArrayList<>();
//        Log.d("update","PackManager is create with data: " + data.toString());
    }

    public static PackManager getInstance(Activity activity) {
        if (Instance == null){
            Instance = new PackManager(activity);
        }
        return Instance;
    }

    public File getDir(){
        return dir;
    }

    @Override
    public ArrayList<Pack> getData() {
        return this.data;
    }

    @Override
    public void setData(ArrayList<Pack> d) {
//        Log.d("update","new Publisher data: " + d.toString());
        this.data = new ArrayList<>(d);
//        Log.d("update","current Publisher date: " + data.toString());
        this.notifyDataChange();
    }

    @Override
    public void notifyDataChange() {
        if (this.subs != null) {
//            Log.d("update","subs list: " + subs.toString());
            if (!subs.isEmpty()){
                for (Subscriber<ArrayList<Pack>> sub : subs) {
                    sub.setData(new ArrayList<>(this.data));
                }
            }
            else{
//                Log.d("update","sub list is empty.");
            }
        }
    }

    @Override
    public void subscribe(Subscriber<ArrayList<Pack>> sub) {
        if (subs != null) {
//            Log.d("update","new subscriber: "  + sub.toString());
//            Log.d("update","subscriber data: " + sub.getData().toString());
//            Log.d("update","Publisher data: " + data.toString());
            if (!this.data.equals(sub.getData())) {
//                Log.d("update","publisher data and sub date isn't equal.New sub data : " + data.toString());
                sub.setData(new ArrayList<>(this.data));
            }
            this.subs.add(sub);
//            Log.d("update","new sub list:" + subs.toString());
        }
    }

    @Override
    public void unsubscribe(Subscriber<ArrayList<Pack>> sub) {
        if (subs != null)
            this.subs.remove(sub);
    }

    protected void setCurrentActivity(Activity activity) {
        this.activity = activity;
    }

    protected synchronized String readFromPackage(String fileName){
        StringBuilder data = new StringBuilder();
        if(!fileName.equals("")){
            String folder = dir.getAbsolutePath() + File.separator + fileName + FILE_SUFFIX;

            try(FileReader fr = new FileReader(folder)) {
                Scanner sc = new Scanner(fr);

                while (sc.hasNextLine()){
                    data.append(sc.nextLine()).append("\n");
                }
                fr.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
        Log.d("MatrixR",data.toString());
        return data.toString();
    }

    protected synchronized void writeInWritingPackage(String data){
         /*проверка на специальный DataMatrix символ,
          * байт-код значение которого равно 29.
          */
        Log.d("MatrixWR",data);
        if((byte)data.charAt(0) == 29){
            String nameWritingPack = getWritingPackageName();
            Log.d("MatrixWR","writing pack name: " + nameWritingPack);
            if(!nameWritingPack.equals("")){
                Log.d("MatrixR","valid");
                String folder = dir.getAbsolutePath() + File.separator +
                        nameWritingPack + FILE_SUFFIX;
                try(FileWriter fw = new FileWriter(folder,true)) {
                    Log.d("MatrixWR","Read from pack");
                    String dataWritingPack = readFromPackage(nameWritingPack);
                    if(dataWritingPack != null){
                        Log.d("MatrixWR","not null");
                        List<String> d = Arrays.asList(dataWritingPack.split("\n"));
                        Log.d("MatrixWR","data for wr pack: " + d.toString());
                        if(!d.contains(data)){
                            Toast.makeText(activity.getApplicationContext(),
                                    "Данные записаны в пакет " + nameWritingPack,
                                    Toast.LENGTH_SHORT).show();
                            Log.d("MatrixWR","data isn't contains");
                            fw.write(data + "\n");
                        }else{
                            Toast.makeText(activity.getApplicationContext(),
                                    "Данный код уже занесён в пакет",
                                    Toast.LENGTH_SHORT).show();
                            Log.d("MatrixWR","data also contains");
                        }
                    }
                    fw.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(activity.getApplicationContext(),
                        "Не выбран пакет для записи",
                        Toast.LENGTH_LONG).show();
            }
        }else {
            Toast.makeText(activity.getApplicationContext(),
                    "Invalid DataMatrix code",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void setWritingPackageName(String name) {
        SharedPreferences.Editor editor = activity.getSharedPreferences(SHARED_NAME, activity.MODE_PRIVATE)
                .edit();
        editor.putString(KEY_CURRENT_WRITING_PACK_NAME, name);
        editor.apply();
    }

    public String getWritingPackageName() {
        SharedPreferences prefs = activity.getSharedPreferences(SHARED_NAME,
                activity.MODE_PRIVATE);
        return prefs.getString(KEY_CURRENT_WRITING_PACK_NAME, "");
    }

    public void uploadPack(ArrayList<Pack> selectedPacks){
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Uploading to GDrive");
        progressDialog.setMessage("Please wait..");
        progressDialog.show();

        if (googleDriveManager == null)
            googleDriveManager = new GoogleDriveManager(activity,FILE_SUFFIX,NAME_DIR);

        for(Pack pack : selectedPacks){
            googleDriveManager.createPack(pack.name,dir)
            .addOnSuccessListener(s -> {
                progressDialog.dismiss();
                Toast.makeText(activity.getApplicationContext(),
                        "Пакет " + pack.name + " выгружен успешно",
                        Toast.LENGTH_SHORT).show();
                Log.d("Successful upload","Пакет " + pack.name + " выгружен успешно " + s);
            })
            .addOnFailureListener(Throwable::printStackTrace);

        }
    }

    public void createPackage(ArrayList<String> fileNames) {
        Log.d("Create new files: ", fileNames.toString());
        new Thread(){
            @Override
            public synchronized void start() {
                super.start();
            }
            @Override
            public void run() {
                try {
                    for (String fileName : fileNames) {
                        new File(dir.getAbsolutePath() + File.separator + fileName + FILE_SUFFIX).createNewFile();
                        Log.d(TAG, "Create file " + dir.getAbsolutePath() + File.separator + fileName + FILE_SUFFIX);
                        sleep(10);
                    }
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                }
                super.run();
            }
        }.start();
    }

    public void deletePackage(@NonNull ArrayList<String> fileNames) {
        Log.d("Delete file","File names: "+ fileNames.toString());
        new Thread(){
            @Override
            public synchronized void start() {
                super.start();
            }

            @Override
            public void run() {
                try {
                    for (String fileName : fileNames) {
                        if(fileName.equals(getWritingPackageName())){
                            setWritingPackageName("");
                        }

                        new File(dir.getAbsolutePath() + File.separator + fileName + FILE_SUFFIX).delete();
                        Log.d(TAG, "Delete file " + dir.getAbsolutePath() + File.separator + fileName + FILE_SUFFIX);
                        sleep(10);
                    }

                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                }
                super.run();
            }

        }.start();
    }

    @Override
    public synchronized void start() {
        //Toast.makeText(activity.getApplicationContext(), "Thread start", Toast.LENGTH_LONG).show();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            dir = new File(activity.getExternalFilesDir(null) + File.separator + NAME_DIR);
        }else {
            dir = new File(Environment.getExternalStorageDirectory() + File.separator + NAME_DIR);
        }
        if (!dir.exists()) {
            Log.d(TAG, "Dir is create");
            dir.mkdir();
        } else {
            Log.d(TAG, "Dir " + dir.toString() + " is exists");
        }
        super.start();
    }

    @Override
    public void run() {
        // хранилище для имеющиеся в данный момент файлов;
        ArrayList<Pack> currentPackages = new ArrayList<>();
        Pack currentWritingPackage = null;
        for (; ; ) {
            try {
                if (dir.isDirectory()) {
                    //Log.d(TAG, Arrays.toString(Objects.requireNonNull(dir.listFiles())));
                    // имеющиеся в данный момент файлы;
                    for (File file : Objects.requireNonNull(dir.listFiles())) {
                        if (file.isFile()) {
                            // имя csv файла
                            String namePack = file.getName().split("\\.")[0];

                            Pack p = new Pack(namePack,
                                    namePack.equals(getWritingPackageName()));

                            if (p.isWritingPackage) {
                                currentWritingPackage = new Pack(p);
                            } else {
                                currentPackages.add(p);
                            }
                        }
                    }
                    if (currentWritingPackage != null) {
                        currentPackages.add(currentWritingPackage);
                    }
                    Collections.reverse(currentPackages);
//                        Log.d("update","Data: " + this.getData().toString());
//                        Log.d("update","Current: " + currentPackages.toString());
                    if (!this.data.equals(currentPackages)) {
                        Log.d("update","current data after valid:" + currentPackages.toString());
                        this.setData(new ArrayList<Pack>(currentPackages));
                    }

                    currentPackages.clear();
                    currentWritingPackage = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}



public class BaseActivity extends AppCompatActivity {

    static public class PermissionManager {
        static public final int CAMERA_PERMISSION_CODE = 100;
        static public final int WRITE_EXTERNAL_STORAGE_PERMISSION_CODE = 101;
        static public final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 102;
        static public final int INTERNET_PERMISSION_CODE = 103;
        static public final int G_ACCOUNT_PERMISSION_CODE = 104;

        private static void requestSignInGoogleAccount(Activity activity) {
            GoogleSignInOptions signInOptions = new GoogleSignInOptions
                    .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                    .build();

            GoogleSignInClient client = GoogleSignIn.getClient(activity, signInOptions);
            activity.startActivityForResult(client.getSignInIntent(), G_ACCOUNT_PERMISSION_CODE);
        }

        static void getPermissions(Activity activity) {
            if (!checkCameraPermission(activity)) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            }

            if (!checkWriteStoragePermission(activity)) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_EXTERNAL_STORAGE_PERMISSION_CODE);
            }

            if (!checkReadStoragePermission(activity)) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        READ_EXTERNAL_STORAGE_PERMISSION_CODE);
            }

            if (!checkInternetPermission(activity)) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.INTERNET}, INTERNET_PERMISSION_CODE);
            }

            if (!checkGAccountsPermission(activity)) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.GET_ACCOUNTS}, G_ACCOUNT_PERMISSION_CODE);
            }
            requestSignInGoogleAccount(activity);
        }

        static boolean checkCameraPermission(Activity activity) {
            return ActivityCompat.checkSelfPermission(activity.getApplicationContext(),
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }

        static boolean checkReadStoragePermission(Activity activity) {
            return ActivityCompat.checkSelfPermission(activity.getApplicationContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        static boolean checkWriteStoragePermission(Activity activity) {
            return ActivityCompat.checkSelfPermission(activity.getApplicationContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        static boolean checkInternetPermission(Activity activity) {
            return ActivityCompat.checkSelfPermission(activity.getApplicationContext(),
                    Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        }

        static boolean checkGAccountsPermission(Activity activity) {
            return ActivityCompat.checkSelfPermission(activity.getApplicationContext(),
                    Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED;
        }

    }

    protected PackManager packManager;
    BaseActivity() {
        if (packManager == null) packManager = PackManager.getInstance(BaseActivity.this);
    }
}


