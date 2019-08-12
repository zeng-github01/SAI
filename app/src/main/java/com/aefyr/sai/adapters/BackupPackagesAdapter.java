package com.aefyr.sai.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aefyr.sai.R;
import com.aefyr.sai.model.backup.PackageMeta;

import java.util.List;

public class BackupPackagesAdapter extends RecyclerView.Adapter<BackupPackagesAdapter.ViewHolder> {

    private LayoutInflater mInflater;
    private List<PackageMeta> mPackages;

    private OnItemInteractionListener mListener;


    public BackupPackagesAdapter(Context c) {
        mInflater = LayoutInflater.from(c);
        setHasStableIds(true);
    }

    public void setData(List<PackageMeta> packages) {
        mPackages = packages;
        notifyDataSetChanged();
    }

    public void setInteractionListener(OnItemInteractionListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(mInflater.inflate(R.layout.item_backup_package, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PackageMeta packageMeta = mPackages.get(position);

        holder.appName.setText(packageMeta.label);
        holder.appPackage.setText(packageMeta.packageName);
    }

    @Override
    public int getItemCount() {
        return mPackages == null ? 0 : mPackages.size();
    }

    @Override
    public long getItemId(int position) {
        return mPackages.get(position).packageName.hashCode();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        TextView appName;
        TextView appPackage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            appName = itemView.findViewById(R.id.tv_app_name);
            appPackage = itemView.findViewById(R.id.tv_app_package);

            itemView.findViewById(R.id.ib_backup).setOnClickListener((v) -> {
                int adapterPosition = getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION)
                    return;

                if (mListener != null)
                    mListener.onBackupButtonClicked(mPackages.get(adapterPosition));
            });
        }
    }

    public interface OnItemInteractionListener {
        void onBackupButtonClicked(PackageMeta packageMeta);
    }

}
