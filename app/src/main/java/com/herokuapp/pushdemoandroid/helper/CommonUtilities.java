package com.herokuapp.pushdemoandroid.helper;

import android.content.Context;
import android.content.Intent;

public final class CommonUtilities {
	
	// give your server registration url here
    public static final String SERVER_URL_REGISTER = "http://pushdemo.herokuapp.com/register?";
    public static final String SERVER_PUSH_URL = "http://pushdemo.herokuapp.com/push_to_user?";
    public static final String SERVER_URL_LOGIN = "http://pushdemo.herokuapp.com/client_login?";

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
}
