package com.appmanager.android.util;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.appmanager.android.entity.FileEntry;

import java.net.URLEncoder;

/**
 * an original schema for importing from email or hyper-link.
 *
 * Note: need to add intent filter to Activity Section in AndroidManifest.xml
 *
 * <intent-filter>
 *     <action android:name="android.intent.action.VIEW" />
 *     <category android:name="android.intent.category.DEFAULT" />
 *     <category android:name="android.intent.category.BROWSABLE" />
 *     <data android:scheme="http" android:host="import-to-appmanager" />
 * </intent-filter>
 * <intent-filter>
 *     <action android:name="android.intent.action.VIEW" />
 *     <category android:name="android.intent.category.DEFAULT" />
 *     <category android:name="android.intent.category.BROWSABLE" />
 *     <data android:scheme="https" android:host="import-to-appmanager" />
 * </intent-filter>
 * Created by maimuzo on 2014/08/30.
 */
public class AppManagerSchema {
    private static final String TAG = "AppManagerSchema";
    public static final String SPECIAL_HOST = "import-to-appmanager";

    /**
     * "https://{basicAuthUser}:{basicAuthPassword}@import-to-appmanager/github.com/app-manager/AppManager-for-Android/blob/master/tests/apk/dummy.apk?raw=true#{name}"
     * to
     * "https://github.com/app-manager/AppManager-for-Android/blob/master/tests/apk/dummy.apk?raw=true"
     * @param uri
     * @return decoded FileEntry, or null if it was not able to decode uri.
     */
    public static FileEntry decode(String uri) {
        FileEntry entry;
        // validate url
        try {
            Uri encodedUri = Uri.parse(uri);

            String specialHost = encodedUri.getHost();
            if(!SPECIAL_HOST.equals(specialHost)){
                throw new UnsupportedOperationException("host is not '" + SPECIAL_HOST + "'");
            }
            entry = new FileEntry();
            entry.name = encodedUri.getFragment(); // null if not include
            String userInfo = encodedUri.getUserInfo();
            if(null != userInfo){
                String[] parts = userInfo.split(":");
                String basicAuthUser = parts[0];
                String basicAuthPassword = parts[1];
                if(!TextUtils.isEmpty(basicAuthUser) && !TextUtils.isEmpty(basicAuthPassword)){
                    entry.basicAuthUser = basicAuthUser;
                    entry.basicAuthPassword = basicAuthPassword;
                }
            }

            String schema = encodedUri.getScheme();
            String encodedPath = encodedUri.getPath();
            int separatePoint = encodedPath.indexOf("/");
            String host = encodedPath.substring(0, separatePoint);
            String path = encodedPath.substring(separatePoint + 1);
            String query = encodedUri.getQuery();
            if(TextUtils.isEmpty(query)){
                entry.url = schema + "://" + host + path;
            } else {
                entry.url = schema + "://" + host + path + "?" + query;
            }

            return entry;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    /**
     * "https://github.com/app-manager/AppManager-for-Android/blob/master/tests/apk/dummy.apk?raw=true"
     * to
     * "https://{basicAuthUser}:{basicAuthPassword}@import-to-appmanager/github.com/app-manager/AppManager-for-Android/blob/master/tests/apk/dummy.apk?raw=true#{name}"
     * @param url
     * @param name
     * @param basicAuthUser
     * @param basicAuthPassword
     * @return encoded uri, or null if it was not able to encode url.
     */
    public static String encode(String url, String name, String basicAuthUser, String basicAuthPassword){
        // validate url
        try{
            Uri uri = Uri.parse(url);
            StringBuilder sb = new StringBuilder();
            sb.append(uri.getScheme()).append("://");
            if(!TextUtils.isEmpty(basicAuthUser) && !TextUtils.isEmpty(basicAuthPassword)){
                sb.append(basicAuthUser).append(":").append(basicAuthPassword).append("@");
            }
            sb.append(SPECIAL_HOST);
            sb.append("/").append(uri.getHost());
            sb.append(uri.getPath());
            if(!TextUtils.isEmpty(uri.getEncodedQuery())){
                sb.append("?").append(uri.getEncodedQuery());
            }
            if(!TextUtils.isEmpty(name)){
                sb.append("#").append(URLEncoder.encode(name, "UTF-8"));
            }
            return sb.toString();
        } catch (Exception e){
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    public static boolean canDecode(Intent intent){
        if (Intent.ACTION_VIEW.equals(intent.getAction())){
            Uri uri = intent.getData();
            String schema = uri.getScheme();
            if(SPECIAL_HOST.equals(uri.getHost()) && ("http".equals(schema) || "https".equals(schema))){
                return true;
            }
        }
        return false;
    }
}
