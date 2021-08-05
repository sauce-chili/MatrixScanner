package com.example.codescannertest.adapter;

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codescannertest.R;
import com.example.codescannertest.model.Pack;
import com.example.codescannertest.model.Subscriber;

import java.util.ArrayList;


public class PackAdapter extends RecyclerView.Adapter<PackAdapter.PackViewHolder>
        implements Subscriber<ArrayList<Pack>> {

    static private final String TAG = "PackAdapter";


    static public class PackViewHolder extends RecyclerView.ViewHolder{
        private TextView namePack;
        private RadioButton SelectedBtn;
        private ImageView icPack;

        public interface onClickListeners{
            void onClick(View v,Pack pack);
            void onClickInSelectedMode(View v,Pack pack);
            void onLongClick(View v,Pack pack);
        }

        private onClickListeners clickListeners;

        public void setClickListeners(onClickListeners clickListeners){
            this.clickListeners = clickListeners;
        }

        public PackViewHolder(@NonNull View itemView) {
            super(itemView);

            namePack = itemView.findViewById(R.id.name_pack);
            SelectedBtn = itemView.findViewById(R.id.select_btn);
            icPack = itemView.findViewById(R.id.ic_code);
        }

        protected void bind(Pack pack){
            namePack.setText(pack.name);
            if (pack.isWritingPackage){
                itemView.findViewById(R.id.ic_writingPack).setVisibility(View.VISIBLE);
            }
            if(pack.isVisibleRadioButton){
                Log.d("bind","rebinding pack: " + pack.toString());
                SelectedBtn.setVisibility(View.VISIBLE);
                itemView.setOnClickListener(null);
                itemView.setOnClickListener(v -> {

                    pack.isSelected = !pack.isSelected;
                    SelectedBtn.setChecked(pack.isSelected);
                    clickListeners.onClickInSelectedMode(v, pack);

                });
                SelectedBtn.setChecked(pack.isSelected);
                //Log.d("Select", String.valueOf(pack.isSelected));
                itemView.setOnLongClickListener(null);
            }else{
                Log.d("bind","binding ");
                SelectedBtn.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
                itemView.setOnClickListener(v -> clickListeners.onClick(v,pack));
                itemView.setOnLongClickListener(v -> {
                    clickListeners.onLongClick(v,pack);
                    return true;
                });
            }
        }
    }

    private ArrayList<Pack> packageList;

    public PackAdapter(ArrayList<Pack> packageList) {
        this.packageList = packageList;
        Log.d(TAG,packageList.toString());
    }

    public PackAdapter(){
        this.packageList = new ArrayList<>();
    }

    public interface OnUpdaterRecyclerView{
        void update();
    }

    OnUpdaterRecyclerView updaterRecyclerView;

    public void setOnUpdaterRecyclerView(OnUpdaterRecyclerView onUpdateRecyclerView) {
        this.updaterRecyclerView = onUpdateRecyclerView;
    }

    @Override
    public void setData(ArrayList<Pack> data) {
        //Log.d("update","update subscriber: " + data.toString());
        //this.packageList = null;
        this.packageList.clear();
        this.packageList = new ArrayList<>(data);
        Log.d("update","sub date: " + packageList.toString());
        if (updaterRecyclerView != null)
            updaterRecyclerView.update();
        else
            this.notifyDataSetChanged();
    }

    @Override
    public ArrayList<Pack> getData() {
        return new ArrayList<>(this.packageList);
    }

    public void setItemClickListeners(PackViewHolder.onClickListeners clickListeners) {
        this.itemClickListener = clickListeners;
    }

    private PackViewHolder.onClickListeners itemClickListener;

    @NonNull
    @Override
    public PackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.item_rv, parent, false);

        return new PackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PackViewHolder holder, int position) {
        holder.setClickListeners(itemClickListener);
        holder.bind(packageList.get(position));
    }

    @Override
    public int getItemCount() {
        return packageList.size();
    }

}
