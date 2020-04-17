package com.aefyr.sai.ui.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.aefyr.sai.BuildConfig;
import com.aefyr.sai.R;
import com.aefyr.sai.firebase.Firebase;
import com.aefyr.sai.model.common.PackageMeta;
import com.aefyr.sai.shell.SuShell;
import com.aefyr.sai.ui.activities.AboutActivity;
import com.aefyr.sai.ui.activities.DonateActivity;
import com.aefyr.sai.ui.dialogs.DarkLightThemeSelectionDialogFragment;
import com.aefyr.sai.ui.dialogs.FilePickerDialogFragment;
import com.aefyr.sai.ui.dialogs.NameFormatBuilderDialogFragment;
import com.aefyr.sai.ui.dialogs.SimpleAlertDialogFragment;
import com.aefyr.sai.ui.dialogs.SingleChoiceListDialogFragment;
import com.aefyr.sai.ui.dialogs.ThemeSelectionDialogFragment;
import com.aefyr.sai.ui.dialogs.base.BaseBottomSheetDialogFragment;
import com.aefyr.sai.utils.AlertsUtils;
import com.aefyr.sai.utils.BackupNameFormat;
import com.aefyr.sai.utils.PermissionsUtils;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.PreferencesKeys;
import com.aefyr.sai.utils.PreferencesValues;
import com.aefyr.sai.utils.Theme;
import com.aefyr.sai.utils.Utils;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;

import java.io.File;
import java.util.List;
import java.util.Objects;

import moe.shizuku.api.ShizukuClientHelper;

public class PreferencesFragment extends PreferenceFragmentCompat implements FilePickerDialogFragment.OnFilesSelectedListener, SingleChoiceListDialogFragment.OnItemSelectedListener, BaseBottomSheetDialogFragment.OnDismissListener, SharedPreferences.OnSharedPreferenceChangeListener, DarkLightThemeSelectionDialogFragment.OnDarkLightThemesChosenListener {

    private static final int REQUEST_CODE_SELECT_BACKUP_DIR = 1334;

    private PreferencesHelper mHelper;

    private Preference mHomeDirPref;
    private Preference mFilePickerSortPref;
    private Preference mInstallerPref;
    private Preference mBackupNameFormatPref;
    private Preference mBackupDirPref;
    private Preference mThemePref;
    private SwitchPreference mAutoThemeSwitch;
    private Preference mAutoThemePicker;

    private PackageMeta mDemoMeta;

    private FilePickerDialogFragment mPendingFilePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        //Inject current auto theme status since it isn't managed by PreferencesKeys.AUTO_THEME key
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        prefs.edit().putBoolean(PreferencesKeys.AUTO_THEME, Theme.getInstance(requireContext()).getThemeMode() == Theme.Mode.AUTO_LIGHT_DARK).apply();
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);

        mHelper = PreferencesHelper.getInstance(requireContext());
        mDemoMeta = Objects.requireNonNull(PackageMeta.forPackage(requireContext(), requireContext().getPackageName()));

        mHomeDirPref = findPreference("home_directory");
        updateHomeDirPrefSummary();
        mHomeDirPref.setOnPreferenceClickListener((p) -> {
            selectHomeDir();
            return true;
        });

        mFilePickerSortPref = findPreference("file_picker_sort");
        updateFilePickerSortSummary();
        mFilePickerSortPref.setOnPreferenceClickListener((p) -> {
            SingleChoiceListDialogFragment.newInstance(getText(R.string.settings_main_file_picker_sort), R.array.file_picker_sort_variants, mHelper.getFilePickerRawSort()).show(getChildFragmentManager(), "sort");
            return true;
        });

        findPreference("about").setOnPreferenceClickListener((p) -> {
            startActivity(new Intent(getContext(), AboutActivity.class));
            return true;
        });
        findPreference("donate").setOnPreferenceClickListener(p -> {
            startActivity(new Intent(requireContext(), DonateActivity.class));
            return true;
        });

        mInstallerPref = findPreference("installer");
        updateInstallerSummary();
        mInstallerPref.setOnPreferenceClickListener((p -> {
            SingleChoiceListDialogFragment.newInstance(getText(R.string.settings_main_installer), R.array.installers, mHelper.getInstaller()).show(getChildFragmentManager(), "installer");
            return true;
        }));

        mBackupNameFormatPref = findPreference("backup_file_name_format");
        updateBackupNameFormatSummary();
        mBackupNameFormatPref.setOnPreferenceClickListener((p) -> {
            NameFormatBuilderDialogFragment.newInstance().show(getChildFragmentManager(), "backup_name_format_builder");
            return true;
        });

        mBackupDirPref = findPreference(PreferencesKeys.BACKUP_DIR);
        updateBackupDirSummary();
        mBackupDirPref.setOnPreferenceClickListener(p -> {
            selectBackupDir();
            return true;
        });

        mThemePref = findPreference(PreferencesKeys.THEME);
        updateThemeSummary();
        mThemePref.setOnPreferenceClickListener(p -> {
            ThemeSelectionDialogFragment.newInstance(requireContext()).show(getChildFragmentManager(), "theme");
            return true;
        });
        if (Theme.getInstance(requireContext()).getThemeMode() != Theme.Mode.CONCRETE) {
            mThemePref.setVisible(false);
        }

        mAutoThemeSwitch = Objects.requireNonNull(findPreference(PreferencesKeys.AUTO_THEME));
        mAutoThemePicker = findPreference(PreferencesKeys.AUTO_THEME_PICKER);
        updateAutoThemePickerSummary();

        mAutoThemeSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean value = (boolean) newValue;
            if (value) {
                if (!Utils.apiIsAtLeast(Build.VERSION_CODES.Q))
                    SimpleAlertDialogFragment.newInstance(requireContext(), R.string.settings_main_auto_theme, R.string.settings_main_auto_theme_pre_q_warning).show(getChildFragmentManager(), null);

                Theme.getInstance(requireContext()).setMode(Theme.Mode.AUTO_LIGHT_DARK);
            } else {
                Theme.getInstance(requireContext()).setMode(Theme.Mode.CONCRETE);
            }

            //Hack to not mess with hiding/showing preferences manually
            requireActivity().recreate();
            return true;
        });

        mAutoThemePicker.setOnPreferenceClickListener(pref -> {
            DarkLightThemeSelectionDialogFragment.newInstance().show(getChildFragmentManager(), null);
            return true;
        });

        if (Theme.getInstance(requireContext()).getThemeMode() != Theme.Mode.AUTO_LIGHT_DARK) {
            mAutoThemePicker.setVisible(false);
        }

        SwitchPreference firebasePref = findPreference(PreferencesKeys.ENABLE_FIREBASE);
        firebasePref.setOnPreferenceChangeListener((preference, newValue) -> {
            Firebase.setDataCollectionEnabled(requireContext(), (boolean) newValue);
            return true;
        });
        if (BuildConfig.IS_FLOSS_BUILD)
            firebasePref.setVisible(false);


        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setDividerHeight(0);
    }

    private void openFilePicker(FilePickerDialogFragment filePicker) {
        if (!PermissionsUtils.checkAndRequestStoragePermissions(this)) {
            mPendingFilePicker = filePicker;
            return;
        }
        filePicker.show(Objects.requireNonNull(getChildFragmentManager()), null);
    }

    private void selectHomeDir() {
        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = DialogConfigs.DIR_SELECT;
        properties.root = Environment.getExternalStorageDirectory();

        openFilePicker(FilePickerDialogFragment.newInstance("home", getString(R.string.settings_main_pick_dir), properties));
    }

    private void selectBackupDir() {
        SingleChoiceListDialogFragment.newInstance(getText(R.string.settings_main_backup_backup_dir_dialog), R.array.backup_dir_selection_methods).show(getChildFragmentManager(), "backup_dir_selection_method");
    }

    private void updateHomeDirPrefSummary() {
        mHomeDirPref.setSummary(getString(R.string.settings_main_home_directory_summary, mHelper.getHomeDirectory()));
    }

    private void updateFilePickerSortSummary() {
        mFilePickerSortPref.setSummary(getString(R.string.settings_main_file_picker_sort_summary, getResources().getStringArray(R.array.file_picker_sort_variants)[mHelper.getFilePickerRawSort()]));
    }

    private void updateInstallerSummary() {
        mInstallerPref.setSummary(getString(R.string.settings_main_installer_summary, getResources().getStringArray(R.array.installers)[mHelper.getInstaller()]));
    }

    private void updateBackupNameFormatSummary() {
        mBackupNameFormatPref.setSummary(getString(R.string.settings_main_backup_file_name_format_summary, BackupNameFormat.format(mHelper.getBackupFileNameFormat(), mDemoMeta)));
    }

    private void updateBackupDirSummary() {
        mBackupDirPref.setSummary(getString(R.string.settings_main_backup_backup_dir_summary, mHelper.getBackupDirUri()));
    }

    private void updateThemeSummary() {
        mThemePref.setSummary(Theme.getInstance(requireContext()).getConcreteTheme().getName(requireContext()));
    }

    private void updateAutoThemePickerSummary() {
        Theme theme = Theme.getInstance(requireContext());
        mAutoThemePicker.setSummary(getString(R.string.settings_main_auto_theme_picker_summary, theme.getLightTheme().getName(requireContext()), theme.getDarkTheme().getName(requireContext())));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionsUtils.REQUEST_CODE_STORAGE_PERMISSIONS) {
            if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED)
                AlertsUtils.showAlert(this, R.string.error, R.string.permissions_required_storage);
            else {
                if (mPendingFilePicker != null) {
                    openFilePicker(mPendingFilePicker);
                    mPendingFilePicker = null;
                }
            }
        }

        if (requestCode == PermissionsUtils.REQUEST_CODE_SHIZUKU) {
            if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED)
                AlertsUtils.showAlert(this, R.string.error, R.string.permissions_required_shizuku);
            else {
                mHelper.setInstaller(PreferencesValues.INSTALLER_SHIZUKU);
                updateInstallerSummary();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SELECT_BACKUP_DIR) {
            if (resultCode != Activity.RESULT_OK)
                return;

            data = Objects.requireNonNull(data);
            Uri backupDirUri = Objects.requireNonNull(data.getData());
            requireContext().getContentResolver().takePersistableUriPermission(backupDirUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            mHelper.setBackupDirUri(backupDirUri.toString());
            updateBackupDirSummary();
        }
    }

    @Override
    public void onFilesSelected(String tag, List<File> files) {
        switch (tag) {
            case "home":
                mHelper.setHomeDirectory(files.get(0).getAbsolutePath());
                updateHomeDirPrefSummary();
                break;
            case "backup_dir":
                mHelper.setBackupDirUri(new Uri.Builder()
                        .scheme("file")
                        .path(files.get(0).getAbsolutePath())
                        .build()
                        .toString());
                updateBackupDirSummary();
                break;
        }
    }

    @Override
    public void onItemSelected(String dialogTag, int selectedItemIndex) {
        switch (dialogTag) {
            case "sort":
                mHelper.setFilePickerRawSort(selectedItemIndex);
                switch (selectedItemIndex) {
                    case 0:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_NAME);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_NORMAL);
                        break;
                    case 1:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_NAME);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_REVERSE);
                        break;
                    case 2:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_LAST_MODIFIED);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_NORMAL);
                        break;
                    case 3:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_LAST_MODIFIED);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_REVERSE);
                        break;
                    case 4:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_SIZE);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_REVERSE);
                        break;
                    case 5:
                        mHelper.setFilePickerSortBy(DialogConfigs.SORT_BY_SIZE);
                        mHelper.setFilePickerSortOrder(DialogConfigs.SORT_ORDER_NORMAL);
                        break;
                }
                updateFilePickerSortSummary();
                break;
            case "installer":
                boolean installerSet = false;
                switch (selectedItemIndex) {
                    case PreferencesValues.INSTALLER_ROOTLESS:
                        installerSet = true;
                        break;
                    case PreferencesValues.INSTALLER_ROOTED:
                        if (!SuShell.getInstance().requestRoot()) {
                            AlertsUtils.showAlert(this, R.string.error, R.string.settings_main_use_root_error);
                            return;
                        }
                        installerSet = true;
                        break;
                    case PreferencesValues.INSTALLER_SHIZUKU:
                        if (!Utils.apiIsAtLeast(Build.VERSION_CODES.M)) {
                            AlertsUtils.showAlert(this, R.string.error, R.string.settings_main_installer_error_shizuku_pre_m);
                            return;
                        }
                        if (!ShizukuClientHelper.isManagerV3Installed(requireContext())) {
                            AlertsUtils.showAlert(this, R.string.error, R.string.settings_main_installer_error_no_shizuku);
                            return;
                        }

                        installerSet = PermissionsUtils.checkAndRequestShizukuPermissions(this);
                        break;
                }
                if (installerSet) {
                    mHelper.setInstaller(selectedItemIndex);
                    updateInstallerSummary();
                }
                break;
            case "backup_dir_selection_method":
                switch (selectedItemIndex) {
                    case 0:
                        DialogProperties properties = new DialogProperties();
                        properties.selection_mode = DialogConfigs.SINGLE_MODE;
                        properties.selection_type = DialogConfigs.DIR_SELECT;
                        properties.root = Environment.getExternalStorageDirectory();

                        openFilePicker(FilePickerDialogFragment.newInstance("backup_dir", getString(R.string.settings_main_pick_dir), properties));
                        break;
                    case 1:
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        startActivityForResult(Intent.createChooser(intent, getString(R.string.installer_pick_apks)), REQUEST_CODE_SELECT_BACKUP_DIR);
                        break;
                }
                break;
        }
    }

    @Override
    public void onDialogDismissed(@NonNull String dialogTag) {
        switch (dialogTag) {
            case "backup_name_format_builder":
                updateBackupNameFormatSummary();
                break;
            case "theme":
                updateThemeSummary();
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(PreferencesKeys.USE_OLD_INSTALLER)) {
            prefs.edit().putBoolean(PreferencesKeys.USE_OLD_INSTALLER, prefs.getBoolean(PreferencesKeys.USE_OLD_INSTALLER, false)).commit();
            Utils.hardRestartApp(requireContext());
        }
    }

    @Override
    public void onThemesChosen(@Nullable String tag, Theme.ThemeDescriptor lightTheme, Theme.ThemeDescriptor darkTheme) {
        Theme theme = Theme.getInstance(requireContext());
        theme.setLightTheme(lightTheme);
        theme.setDarkTheme(darkTheme);
    }
}
