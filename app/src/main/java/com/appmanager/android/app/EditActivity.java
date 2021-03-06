/*
 * Copyright 2014 Soichiro Kashima
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appmanager.android.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.appmanager.android.R;
import com.appmanager.android.dao.FileEntryDao;
import com.appmanager.android.entity.FileEntry;
import com.appmanager.android.task.InstallTask;
import com.appmanager.android.util.AppDownloader;
import com.appmanager.android.util.AppManagerSchema;
import com.appmanager.android.util.InstallUtils;
import com.appmanager.android.validator.FileEntryValidator;
import com.simplealertdialog.SimpleAlertDialog;
import com.simplealertdialog.SimpleAlertDialogSupportFragment;

/**
 * @author Soichiro Kashima
 */
public class EditActivity extends DetailActivity implements InstallTask.InstallListener,
        SimpleAlertDialog.OnClickListener,
        SimpleAlertDialog.ViewProvider {

    private static final String DEFAULT_URL = "https://github.com/app-manager/AppManager-for-Android/blob/master/tests/apk/dummy.apk?raw=true";
    private static final int DIALOG_REQUEST_CODE_SHOW_PASSWORD = 1;
    private static final int DIALOG_REQUEST_CODE_DELETE = 2;
    private static final int DIALOG_REQUEST_CODE_FINISH = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    protected void init() {
        setContentView(R.layout.activity_edit);
        setupActionBar();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        mFileEntry = null;

        Button deleteButton = (Button) findViewById(R.id.delete);
        Button showPasswordButton = (Button) findViewById(R.id.show_password);
        if (hasFileEntryInIntent()) {
            mFileEntry = getFileEntryFromIntent();
            if (mFileEntry != null) {
                setTitle(R.string.activity_title_edit_app);
                restoreValues(mFileEntry);
            }
            showPasswordButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmShowPassword();
                }
            });
        } else {
            ((TextView) findViewById(R.id.name)).setText(extractNameFromUrl(DEFAULT_URL));
            ((TextView) findViewById(R.id.url)).setText(DEFAULT_URL);
            showPasswordButton.setEnabled(false);
            deleteButton.setEnabled(false);
        }
        findViewById(R.id.install).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                install();
            }
        });
        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save();
            }
        });
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDelete();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (AppManagerSchema.canDecode(intent)) {
            FileEntry fe = AppManagerSchema.decode(intent.getData().toString());
            if (null != fe) {
                restoreValues(fe);
            }
        }
    }

    @Override
    protected void inflateMenu(Menu menu) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            confirmFinish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        confirmFinish();
    }

    @Override
    protected void restoreValues(FileEntry entry) {
        super.restoreValues(entry);
        ((TextView) findViewById(R.id.basicAuthUser)).setText(entry.basicAuthUser);
        ((TextView) findViewById(R.id.basicAuthPassword)).setText(entry.basicAuthPassword);
    }

    @Override
    protected FileEntry getFileEntryFromScreen() {
        FileEntry entry = new FileEntry();
        if (null != mFileEntry) {
            mFileEntry.copyMetaDataTo(entry);
        }
        entry.url = ((TextView) findViewById(R.id.url)).getText().toString();
        entry.name = ((TextView) findViewById(R.id.name)).getText().toString();
        entry.basicAuthUser = ((TextView) findViewById(R.id.basicAuthUser)).getText().toString();
        entry.basicAuthPassword = ((TextView) findViewById(R.id.basicAuthPassword)).getText().toString();
        return entry;
    }

    @Override
    public void onComplete(final AppDownloader.DownloadResponse response) {
        if (response == null) {
            Toast.makeText(this, R.string.error_download, Toast.LENGTH_SHORT).show();
            return;
        } else if (!TextUtils.isEmpty(response.errorMessage)) {
            Toast.makeText(this, response.errorMessage, Toast.LENGTH_SHORT).show();
            return;
        } else if (TextUtils.isEmpty(response.downloadedApkPath)) {
            Toast.makeText(this, R.string.error_download, Toast.LENGTH_SHORT).show();
            return;
        }

        InstallUtils.delegateInstall(this, response.downloadedApkPath);
    }

    @Override
    public void onDialogPositiveButtonClicked(SimpleAlertDialog simpleAlertDialog, int requestCode, View view) {
        switch (requestCode) {
            case DIALOG_REQUEST_CODE_SHOW_PASSWORD:
                checkPasswordAndShow(view);
                break;
            case DIALOG_REQUEST_CODE_DELETE:
                delete();
                break;
            case DIALOG_REQUEST_CODE_FINISH:
                finish();
                break;
        }
    }

    @Override
    public void onDialogNegativeButtonClicked(SimpleAlertDialog simpleAlertDialog, int requestCode, View view) {
    }

    @Override
    public View onCreateView(SimpleAlertDialog simpleAlertDialog, int requestCode) {
        if (requestCode == DIALOG_REQUEST_CODE_SHOW_PASSWORD) {
            return LayoutInflater.from(this).inflate(R.layout.dialog_ask_admin_password, null);
        }
        return null;
    }

    private void save() {
        FileEntry entry = getFileEntryFromScreen();
        FileEntryValidator validator = new FileEntryValidator(this, entry);
        if (!validator.isValid()) {
            Toast.makeText(this, validator.getErrors(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (mFileEntry == null || mFileEntry.id == 0) {
            // Create
            new FileEntryDao(this).create(entry);
        } else {
            // Update
            new FileEntryDao(this).update(entry);
        }
        finish();
    }

    private void confirmShowPassword() {
        if (adminPasswordIsSet()) {
            new SimpleAlertDialogSupportFragment.Builder()
                    .setTitle(R.string.label_permission_required)
                    .setPositiveButton(android.R.string.ok)
                    .setNegativeButton(android.R.string.cancel)
                    .setRequestCode(DIALOG_REQUEST_CODE_SHOW_PASSWORD)
                    .setUseView(true)
                    .create()
                    .show(getSupportFragmentManager(), "dialog");
        } else {
            showPassword();
        }
    }

    private void confirmDelete() {
        new SimpleAlertDialogSupportFragment.Builder()
                .setMessage(R.string.msg_confirm_file_entry_delete)
                .setPositiveButton(android.R.string.ok)
                .setNegativeButton(android.R.string.cancel)
                .setRequestCode(DIALOG_REQUEST_CODE_DELETE)
                .create()
                .show(getSupportFragmentManager(), "dialog");
    }

    private void checkPasswordAndShow(final View view) {
        if (view == null) {
            return;
        }
        String inputPassword = ((EditText) view.findViewById(R.id.admin_password)).getText().toString();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedPassword = prefs.getString(SettingsActivity.PREF_KEY_ADMIN_PASSWORD, null);
        if (!inputPassword.equals(savedPassword)) {
            Toast.makeText(this, R.string.msg_incorrect_password, Toast.LENGTH_SHORT).show();
            return;
        }
        showPassword();
    }

    private void showPassword() {
        ((EditText) findViewById(R.id.basicAuthPassword)).setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    }

    private void delete() {
        FileEntry entry = getFileEntryFromScreen();
        if (mFileEntry == null) {
            return;
        }
        mFileEntry.copyMetaDataTo(entry);
        new FileEntryDao(this).delete(entry);
        finish();
    }

    private void confirmFinish() {
        if (!contentChanged()) {
            finish();
            return;
        }
        new SimpleAlertDialogSupportFragment.Builder()
                .setMessage(R.string.msg_confirm_file_entry_finish)
                .setPositiveButton(android.R.string.ok)
                .setNegativeButton(android.R.string.cancel)
                .setRequestCode(DIALOG_REQUEST_CODE_FINISH)
                .create()
                .show(getSupportFragmentManager(), "dialog");
    }

    private String extractNameFromUrl(String entryUrl) {
        if (TextUtils.isEmpty(entryUrl)) {
            return "";
        }
        String url = entryUrl.contains("?") ? entryUrl.replaceAll("\\?.*$", "") : entryUrl;
        return url.replaceAll("^.*/", "").replaceAll("\\.apk", "");
    }

    private boolean contentChanged() {
        FileEntry before = mFileEntry;
        FileEntry after = getFileEntryFromScreen();
        if (before == null) {
            before = new FileEntry();
        }
        return !before.contentEqualsTo(after);
    }

    private boolean adminPasswordIsSet() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String password = prefs.getString(SettingsActivity.PREF_KEY_ADMIN_PASSWORD, null);
        return !TextUtils.isEmpty(password);
    }
}
