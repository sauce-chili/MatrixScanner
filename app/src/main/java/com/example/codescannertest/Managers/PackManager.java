package com.example.codescannertest.Managers;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.codescannertest.model.Pack;
import com.example.codescannertest.model.Publisher;
import com.example.codescannertest.model.Subscriber;
import com.google.android.gms.tasks.OnFailureListener;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class PackManager extends Thread implements Publisher<ArrayList<Pack>> {
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

    public void setCurrentActivity(Activity activity) {
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

    public synchronized void writeInWritingPackage(String data){
         /*проверка на специальный DataMatrix символ,
          * байт-код значение которого равно 29.
          */
        Log.d("MatrixWR",data);
        if((byte)data.charAt(0) == 29){
            String nameWritingPack = getWritingPackageName();
            Log.d("MatrixWR","Datamatrix has a special char");
            Log.d("MatrixWR","current wr pack name: " + nameWritingPack);
            if(!nameWritingPack.equals("")){
                Log.d("MatrixWR","valid name pack(!= '')");
                String folder = dir.getAbsolutePath() + File.separator +
                        nameWritingPack + FILE_SUFFIX;
                try(FileWriter fw = new FileWriter(folder,true)) {
                    Log.d("MatrixWR","Read data from selected pack");
                    String dataWritingPack = readFromPackage(nameWritingPack);
                    if(dataWritingPack != null){
                        Log.d("MatrixWR","not null");
                        List<String> d = Arrays.asList(dataWritingPack.split("\n"));
                        Log.d("MatrixWR","data for current wr pack: " + d.toString());
                        if(!d.contains(data)){
                            Toast.makeText(activity.getApplicationContext(),
                                    "Данные записаны в пакет " + nameWritingPack,
                                    Toast.LENGTH_SHORT).show();
                            Log.d("MatrixWR","scanned data isn't contains in current wr pack");
                            fw.write(data + "\n");
                            Log.d("MatrixWR","Data was recorded in current wr pack");
                        }else{
                            Toast.makeText(activity.getApplicationContext(),
                                    "Данный код уже занесён в пакет",
                                    Toast.LENGTH_SHORT).show();
                            Log.d("MatrixWR","Such data also contains");
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
        Log.d("WritingPack","currently set name wr pack: " + name);
    }

    public String getWritingPackageName() {
        SharedPreferences prefs = activity.getSharedPreferences(SHARED_NAME,
                activity.MODE_PRIVATE);
        return prefs.getString(KEY_CURRENT_WRITING_PACK_NAME, "");
    }

    public void uploadPack(ArrayList<Pack> selectedPacks){
        ProgressDialog progressDialog = new ProgressDialog(activity);
        activity.runOnUiThread(() -> {
            progressDialog.setTitle("Обновление данных на GDrive");
            progressDialog.setMessage("Загрузка..");
            progressDialog.show();
        });

        if (googleDriveManager == null)
            googleDriveManager = new GoogleDriveManager(activity,FILE_SUFFIX,NAME_DIR);

        for(Pack pack : selectedPacks){
            googleDriveManager.createPack(pack.name,dir)
            .addOnSuccessListener(s -> {
                activity.runOnUiThread(progressDialog::dismiss);
                Toast.makeText(activity.getApplicationContext(),
                        "Пакет " + pack.name + " выгружен успешно",
                        Toast.LENGTH_SHORT).show();
                Log.d("GDrive upload","Pack " + pack.name + " was uploaded successful. " + s);
            })
            .addOnFailureListener(e -> {
                e.printStackTrace();
                activity.runOnUiThread(progressDialog::dismiss);
                Toast.makeText(activity.getApplicationContext(),
                        "Не удалось выгрузить пакет " + pack.name,
                        Toast.LENGTH_SHORT).show();
                Log.d("GDRive upload","Pack " + pack.name + " was uploaded unsuccessful");
            });
        }
    }

    public void createPackage(ArrayList<String> fileNames) {
        Log.d("Create new files: ", fileNames.toString());
        new Thread(){
            @Override
            public void run() {
                try {
                    for(String fileName : fileNames){
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
            Log.d(TAG, "Dir " + dir.toString() + " is also exists");
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
