package com.herokuapp.pushdemoandroid.helper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.herokuapp.pushdemoandroid.DemoActivity;

import org.json.JSONException;
import org.json.JSONObject;

public final class CommonUtilities {
	
	// give your server registration url here
    public static final String SERVER_URL_REGISTER = "https://pushdemo.herokuapp.com/register?";
    public static final String SERVER_PUSH_URL = "https://pushdemo.herokuapp.com/push_to_user?";
    public static final String SERVER_URL_LOGIN = "https://pushdemo.herokuapp.com/client_login?";

    // Google project id
    public static final String SENDER_ID = "388507868439";

    /**
     * Tag used on log messages.
     */
    public static final String TAG = "GCM Demo";

    public static final String DISPLAY_MESSAGE_ACTION =
            "com.herokuapp.pushnotifications.DISPLAY_MESSAGE";

    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    public static final String PROPERTY_REG_USERNAME = "username";
    public static final String PROPERTY_REG_PASSWORD = "password";
    public static final String PROPERTY_REG_EMAIL = "email";
    public static final String PROPERTY_REG_GCMID = "gcm_regid";
    public static final String PROPERTY_REG_ROLE = "role";
    public static final String PROPERTY_APP_VERSION = "appVersion";
    public static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    /**
     * Notifies UI to display a message.
     * <p>
     * This method is defined in the common helper because it's used both by
     * the UI and the background service.
     *
     * @param context application's context.
     * @param message message to be displayed.
     */
    public static void displayMessage(Context context, String message) {
        Intent intent = new Intent(DISPLAY_MESSAGE_ACTION);
        intent.putExtra(EXTRA_MESSAGE, message);
        context.sendBroadcast(intent);
    }

    public static void storeLastPosition(Context context, String username, String current_user, double latitude, double longitude) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(CommonUtilities.TAG, "Saving last position on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("username", username);
            jsonObject.put("latitude", latitude);
            jsonObject.put("longitude", longitude);
            jsonObject.put("current_user", current_user);
        } catch (JSONException e) {

        }

        editor.putString("lastPost", jsonObject.toString());
        editor.commit();
    }

    public static JSONObject getLasPostJSON(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String lastPost = prefs.getString("lastPost", "");
        if (lastPost.isEmpty()) {
            Log.i(CommonUtilities.TAG, "lastPost not found.");
            return null;
        }

        JSONObject lastPos = null;
        try {
            lastPos = new JSONObject(lastPost);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return lastPos;
    }

    public static String getUsername(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String username = prefs.getString(CommonUtilities.PROPERTY_REG_USERNAME, "");
        if (username.isEmpty()) {
            Log.i(CommonUtilities.TAG, "username not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(CommonUtilities.PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(CommonUtilities.TAG, "App version changed.");
            return "";
        }
        return username;
    }

    private static SharedPreferences getGcmPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return context.getSharedPreferences(DemoActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID
     */
    public static void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(CommonUtilities.TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(CommonUtilities.PROPERTY_REG_ID, regId);
        editor.putInt(CommonUtilities.PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    /**
     * Gets the current registration ID for application on GCM service, if there is one.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    public static String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(CommonUtilities.PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(CommonUtilities.TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(CommonUtilities.PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(CommonUtilities.TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }
}
