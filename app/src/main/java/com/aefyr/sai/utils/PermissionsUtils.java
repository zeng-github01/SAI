package com.aefyr.sai.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

public class PermissionsUtils {
    public static final int REQUEST_CODE_STORAGE_PERMISSIONS = 322;
    public static final int REQUEST_CODE_SHIZUKU = 1337;

    public static boolean checkAndRequestStoragePermissions(Activity a) {
        return checkAndRequestPermissions(a, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_STORAGE_PERMISSIONS);
    }

    public static boolean checkAndRequestStoragePermissions(Fragment f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) 及以上版本
            if (Environment.isExternalStorageManager()) {
                return true;
            } else {
                // 跳转到系统特权设置页面
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + f.getContext().getPackageName()));
                f.startActivityForResult(intent, REQUEST_CODE_STORAGE_PERMISSIONS);
                return false;
            }
        } else {
            // Android 10 及以下版本，保留原有的运行时权限请求
            return checkAndRequestPermissions(f,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_STORAGE_PERMISSIONS);
        }
    }

    public static boolean checkAndRequestShizukuPermissions(Activity a) {
        return checkAndRequestPermissions(a, new String[]{"moe.shizuku.manager.permission.API_V23"}, REQUEST_CODE_SHIZUKU);
    }

    public static boolean checkAndRequestShizukuPermissions(Fragment f) {
        return checkAndRequestPermissions(f, new String[]{"moe.shizuku.manager.permission.API_V23"}, REQUEST_CODE_SHIZUKU);
    }

    private static boolean checkAndRequestPermissions(Activity a, String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT < 23)
            return true;

        for (String permission : permissions) {
            if ((ActivityCompat.checkSelfPermission(a, permission)) == PackageManager.PERMISSION_DENIED) {
                a.requestPermissions(permissions, requestCode);
                return false;
            }
        }
        return true;
    }

    private static boolean checkAndRequestPermissions(Fragment f, String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT < 23)
            return true;

        for (String permission : permissions) {
            if ((ActivityCompat.checkSelfPermission(f.requireContext(), permission)) == PackageManager.PERMISSION_DENIED) {
                f.requestPermissions(permissions, requestCode);
                return false;
            }
        }
        return true;
    }

}
