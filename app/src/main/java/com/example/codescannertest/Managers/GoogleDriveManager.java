package com.example.codescannertest.Managers;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.google.zxing.common.DefaultGridSampler;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class GoogleDriveManager{
    private final Executor mExecutor;
    private  final String FILE_SUFFIX;
    private final String DRIVER_DIR_NAME;
    private final String MEDIA_TYPE = "text/csv";
    private final Activity activity;
    private final String DEBUG = "GDrive";


    private Drive getGoogleDriveService() throws NullPointerException{
        GoogleAccountCredential credential = GoogleAccountCredential
                .usingOAuth2(activity.getBaseContext(),
                        Collections.singleton(DriveScopes.DRIVE));

        GoogleSignInAccount signInAccount = GoogleSignIn.getLastSignedInAccount(activity.getApplicationContext());

        if (signInAccount == null)
            throw new NullPointerException("GoogleSignIn.getLastSignedInAccount() return is null." +
                    "No authorized accounts");

        credential.setSelectedAccount(signInAccount.getAccount());


        return new Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName(DRIVER_DIR_NAME)
                .build();
    }

    private String getIdDriveDir(Drive GDriveService){

        String id = null;
        String pageToken = null;
        do{
            try {
                // запрос для получения id файла,явяляюзегося рабочей директорией
                String query =
                        "name = '" + DRIVER_DIR_NAME + "' " +                      // имя директории
                        "and mimeType = 'application/vnd.google-apps.folder' " +   // файл должен быть директорией
                        "and trashed = false";                                     // файл не должен находится в корзине
                FileList result = GDriveService.files().list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name)")
                        .setPageToken(pageToken)
                        .execute();
                List<File> fileList = result.getFiles();


                // если директория отсутствует
                if (fileList.size() == 0) {
                    Log.d(DEBUG,"GDrive dir is not exists");
                    return null;
                }

                id = fileList.get(0).getId();
                pageToken = result.getNextPageToken();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }while (pageToken != null);

        return id;
    }

    private String createDriveDir(Drive GDriveService) throws IOException {
        Log.d(DEBUG,"create GDrive dir");
        File PathMetadata = new File()
                .setName(DRIVER_DIR_NAME)
                .setFolderColorRgb("#FFEB3B")
                .setMimeType("application/vnd.google-apps.folder");
        File GPath = GDriveService.files()
                .create(PathMetadata)
                .setFields("id")
                .execute();

        return GPath.getId();
    }

    public GoogleDriveManager(Activity activity,String fs,String workingDirName){
        this.activity = activity;
        this.mExecutor = Executors.newSingleThreadExecutor();
        this.FILE_SUFFIX = fs;
        this.DRIVER_DIR_NAME = workingDirName;
    }

    public List<File> getFileList(String fileName){
        Drive mDriveService = this.getGoogleDriveService();
        List<File> result = null;
        String parentId = this.getIdDriveDir(mDriveService);
        String query =
                "'" + parentId + "' in parents " +
                "and name = '" + fileName + "' " +
                "and mimeType != 'application/vnd.google-apps.folder' " +
                "and trashed = false";
        String pageToken = null;
        do{
            try {
                FileList files = mDriveService.files().list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name)")
                        .setPageToken(pageToken)
                        .execute();

                if (!files.getFiles().isEmpty())
                    result  = files.getFiles();
                pageToken = files.getNextPageToken();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }while (pageToken != null);

        return result;
    }

    public boolean isFileExists(String fileName){
        return this.getFileList(fileName) != null;
    }

    public Task<String> createPack(String fileName, java.io.File dir){
        Drive mDriveService = this.getGoogleDriveService();

        return Tasks.call(mExecutor,() ->{

            // id директории на Gdisk,куда выгружаются данные
            String idDirGoogleDrive = this.getIdDriveDir(mDriveService);
            //Log.d("GDrive",idDirGoogleDrive);
            // Если папка отсутсвует на google disk'e,то создаём её
            if (idDirGoogleDrive == null) {
                idDirGoogleDrive = this.createDriveDir(mDriveService);
            }
            if(this.isFileExists(fileName)){
                Log.d(DEBUG,"file with such name already exists");
                for(File file : this.getFileList(fileName)){
                    this.deleteFileById(file.getId());
                    Log.d(DEBUG,"file was delete");
                }
            }
            // создание метаданных для GDrive
            File GFileMetadata = new File()
                    .setName(fileName)
                    .setParents(Collections.singletonList(idDirGoogleDrive));

            java.io.File mFile = new java.io.File(dir.getAbsolutePath() +
                    java.io.File.separator + fileName + FILE_SUFFIX);

            FileContent mediaContent = new FileContent(MEDIA_TYPE, mFile);
            File Gfile = mDriveService.files()
                    .create(GFileMetadata, mediaContent)
                    .setFields("id, parents")
                    .execute();

            Log.d(DEBUG,"Create file on GDrive");

            return Gfile.getId();
        });
    }


    public void deleteFileById(String id){
        Drive mDriveService = this.getGoogleDriveService();

        Tasks.call(mExecutor, () -> {
            try {
                mDriveService.files()
                        .delete(id)
                        .execute();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        });
    }
}
