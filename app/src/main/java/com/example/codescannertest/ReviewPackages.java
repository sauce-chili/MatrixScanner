package com.example.codescannertest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.codescannertest.adapter.PackAdapter;
import com.example.codescannertest.model.Pack;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;


public class ReviewPackages extends BaseeActivity {

    private RecyclerView rvPackages;
    private ArrayList<Pack> selectedPack;
    boolean isSelectAll = false;
    boolean deleteSelect = false;
    boolean uploadSelect = false;
    private PackAdapter adapter;
    private FloatingActionButton addBtn;

    // TODO Enum для состояний

    private void activateSelectionMode(){
        findViewById(R.id.toolbar).setVisibility(View.GONE);
        findViewById(R.id.select_toolbar).setVisibility(View.VISIBLE);
        addBtn.setVisibility(View.INVISIBLE);
        ArrayList<Pack> packagesList = adapter.getData();
        Log.d("ON selected mode","current data: " + packagesList);
        if (packagesList == null) return;
        if (selectedPack == null)   selectedPack = new ArrayList<>();
        selectedPack.clear();
        for(Pack p : packagesList){
            p.isVisibleRadioButton = true;
        }
        adapter.notifyDataSetChanged();
    }

    private void deactivateSelectionMode(){
        findViewById(R.id.select_toolbar).setVisibility(View.GONE);
        findViewById(R.id.toolbar).setVisibility(View.VISIBLE);
        ArrayList<Pack> packagesList = adapter.getData();
        Log.d("OFF selected mode","current data: " + packagesList);
        if (packagesList == null || selectedPack == null) return;
        for(Pack p : packagesList){
            p.isVisibleRadioButton = false;
            p.isSelected = false;
        }
        selectedPack.clear();
        deleteSelect = false;
        uploadSelect = false;
        addBtn.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void showDialogForAddPack(){
        View view = LayoutInflater
                .from(getApplicationContext())
                .inflate(R.layout.dialog_create_package, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        EditText title = (EditText) view.findViewById(R.id.input_title);

        builder.setPositiveButton("Ok", (dialog, which) -> {

            Log.d("Create new file",Boolean.toString(title == null));
            String namePack = title.getText().toString();
            ArrayList<String> filesName = new ArrayList<>();
            filesName.add(namePack);
            packManager.createPackage(filesName);
            Toast.makeText(getApplicationContext(),"Пакет " + namePack + " создан", Toast.LENGTH_SHORT).show();
            dialog.dismiss();

        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.create();
        builder.show();
    }

    private void bindToolbar() {
        ImageView icUpload = (ImageView)findViewById(R.id.ic_upload);
        icUpload.setOnClickListener(v -> {

            Log.d("Click","Click on btn upload.Selected mode: " + true +
                    "; deleteSelected: " + deleteSelect + "; uploadSelected: " + uploadSelect);
            uploadSelect = true;
            activateSelectionMode();

        });

        ImageView icDelete = (ImageView)findViewById(R.id.ic_delete);
        icDelete.setOnClickListener(v -> {

            Log.d("Click","Click on btn delete.Selected mode: " + true +
                    "; deleteSelected: " + deleteSelect + "; uploadSelected: " + uploadSelect);
            deleteSelect = true;
            activateSelectionMode();

        });
    }

    private void bindSelectionToolbar(){
        ImageView icAgree = (ImageView)findViewById(R.id.ic_agree);
        icAgree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d("Click","Click on btn agree cancel.Selected mode: " + false);
                if (uploadSelect){
                    Log.d("Permission","Internet: " + PermissionManager.checkInternetPermission(ReviewPackages.this));
                    if (PermissionManager.checkInternetPermission(ReviewPackages.this)) {
                        if (PermissionManager.checkSignInAccount(ReviewPackages.this)) {
                            Log.d("SignInAcc","User is already signed in acc");
                            packManager.uploadPack(selectedPack);
                        }else{
                            PermissionManager.requestSignInGoogleAccount(ReviewPackages.this);
                        }
                    }else{
                        PermissionManager.requestInternetPermission(ReviewPackages.this);
                    }
                    selectedPack.clear();
                    uploadSelect = false;
                    findViewById(R.id.select_toolbar).setVisibility(View.GONE);
                    findViewById(R.id.toolbar).setVisibility(View.VISIBLE);
                    deactivateSelectionMode();
                } else if(deleteSelect){

                    ArrayList<String> filenames = new ArrayList<>();
                    for(Pack pack : selectedPack){
                       filenames.add(pack.name);
                    }
                    packManager.deletePackage(filenames);
                    deactivateSelectionMode();
                }
            }
        });

        ImageView icCancel = (ImageView)findViewById(R.id.ic_cancel);
        icCancel.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Log.d("Click","Click on btn cancel.Selected mode: " + false);
                deactivateSelectionMode();
            }
        });

        ImageView icSelectAll = (ImageView)findViewById(R.id.ic_selectAll);
        icSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<Pack> packagesList = adapter.getData();
                Log.d("Click","Click on btn Select All.Selected mode: " + false);
                isSelectAll = !isSelectAll;

                for(Pack p : packagesList) {
                    p.isSelected = isSelectAll;
                    if (p.isSelected)
                        if (!selectedPack.contains(p))
                            selectedPack.add(p);
                }
                if(!isSelectAll)
                    selectedPack.clear();
                adapter.notifyDataSetChanged();
                Log.d("Click","Selected pack:" + selectedPack.toString());
            }
        });
    }

    private void bindAddButton(){
        addBtn = findViewById(R.id.ic_add);
        addBtn.setOnClickListener(v -> showDialogForAddPack());
    }

    private void createRecyclerView(){

        rvPackages = findViewById(R.id.rv_pck);
        rvPackages.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PackAdapter();
        selectedPack = new ArrayList<Pack>();
        adapter.setOnUpdaterRecyclerView(new PackAdapter.OnUpdaterRecyclerView() {
            @Override
            public void update() {
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            }
        });

        adapter.setItemClickListeners(new PackAdapter.PackViewHolder.onClickListeners() {
            @Override
            public void onClick(View v,Pack pack) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                Log.d("Click",packManager.getDir().getAbsolutePath());
                Uri uri = Uri.parse(
                        packManager.getDir().getAbsolutePath() + File.separator + pack.name + ".csv"
                );
                intent.setDataAndType(uri, "*/*");
                startActivity(intent);
            }

            @Override
            public void onClickInSelectedMode(View v, Pack pack) {
                Log.d("Click","Click in selected mode");
                Log.d("Click",pack.name + " " + pack.isSelected);
                if (pack.isSelected){
                    selectedPack.add(pack);
                }else {
                    if (selectedPack.contains(pack)){
                        selectedPack.remove(pack);
                    }
                }
                Log.d("Click", selectedPack.toString());
            }

            @Override
            public void onLongClick(View v,Pack pack) {
                Log.d("Click","Long click on pack: " + pack.name);
                if(!packManager.getWritingPackageName().equals(pack.name)){
                    packManager.setWritingPackageName(pack.name);
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Этот пакет уже выбран",
                            Toast.LENGTH_SHORT);
                }
            }
        });
        packManager.subscribe(adapter);

        this.bindAddButton();
        this.bindToolbar();
        this.bindSelectionToolbar();

        DividerItemDecoration decorator =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        decorator.setDrawable(getResources().getDrawable(R.drawable.space));
        rvPackages.setLayoutManager(new LinearLayoutManager(this));
        rvPackages.setAdapter(adapter);
        rvPackages.addItemDecoration(decorator);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        permissionManager.getPermissions(this);
        if(packManager.isAlive()){
            packManager.setCurrentActivity(this);
        }
        setContentView(R.layout.activity_rv_package);
        createRecyclerView();
    }

    @Override
    protected void onStop() {
        super.onStop();
        packManager.unsubscribe(adapter);
        selectedPack.clear();
        isSelectAll = false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 102) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Log.d("SignInAcc","call from rvAct.Intent ref: " + data);
            try {
                super.userAcc = GoogleSignIn.getSignedInAccountFromIntent(data)
                        .getResult(ApiException.class);
            } catch (ApiException e) {
                e.printStackTrace();
            }

        }
    }
}