package com.herokuapp.pushdemoandroid;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.herokuapp.pushdemoandroid.helper.AlertDialogManager;
import com.herokuapp.pushdemoandroid.helper.ConnectionDetector;
import com.herokuapp.pushdemoandroid.helper.DatabaseManager;
import com.herokuapp.pushdemoandroid.helper.GPSTracker;
import com.herokuapp.pushdemoandroid.helper.SessionManager;
import com.herokuapp.pushdemoandroid.helper.WakeLocker;
import com.herokuapp.pushdemoandroid.helper.CommonUtilities;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Kadek_P on 3/29/2015.
 */
public class MainActivity extends Activity {
    // label to display gcm messages
    TextView lblMessage;

    // Asyntask
    AsyncTask<Void, Void, Void> mRegisterTask;

    // Alert dialog manager
    AlertDialogManager alert = new AlertDialogManager();

    // Connection detector
    ConnectionDetector cd;

    public static String name;
    public static String email;
    private TextView lblName;
    private TextView lblEmail;
    private EditText etMessage;
    private EditText etSendTo;
    private Button btnSend;
    private Button btnLogout;
    private DatabaseManager db;
    private SessionManager session;
    GPSTracker gps;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        lblName = (TextView) findViewById(R.id.lblName);
        lblEmail = (TextView) findViewById(R.id.lblEmail);
        etMessage = (EditText) findViewById(R.id.etMessage);
        etSendTo = (EditText) findViewById(R.id.etSendTo);
        btnSend = (Button) findViewById(R.id.btnSend);
        btnLogout = (Button) findViewById(R.id.btnLogout);

        cd = new ConnectionDetector(getApplicationContext());

        // Check if Internet present
        if (!cd.isConnectingToInternet()) {
            // Internet Connection is not present
            alert.showAlertDialog(MainActivity.this,
                    "Internet Connection Error",
                    "Please connect to working Internet connection", false);
            // stop executing code by return
            return;
        }

        // SqLite database handler
        db = new DatabaseManager(getApplicationContext());

        // session manager
        session = new SessionManager(getApplicationContext());
        gps = new GPSTracker(getApplicationContext());
        final double latitude;
        final double longitude;

        // check if GPS enabled
        if(gps.canGetLocation()){

            latitude = gps.getLatitude();
            longitude = gps.getLongitude();
            CommonUtilities.storeLastPosition(getApplicationContext(), "Ryuzaki", latitude, longitude);
        }else{
            // can't get location
            // GPS or Network is not enabled
            // Ask user to enable GPS/network in settings
            gps.showSettingsAlert();
            latitude =0;
            longitude=0;
        }

        if (!session.isLoggedIn()) {
            logoutUser();
        }

        // Fetching user details from sqlite
        HashMap<String, String> user = db.getUserDetails();
        name = user.get("name");
        email = user.get("email");
        lblName.setText("Welcome " + name + "\n" + email);


        lblMessage = (TextView) findViewById(R.id.lblMessage);

        registerReceiver(mHandleMessageReceiver, new IntentFilter(
                CommonUtilities.DISPLAY_MESSAGE_ACTION));

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendPostRequest(latitude, longitude);
            }
        });
        btnLogout.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                logoutUser();
            }
        });
    }

    /**
     * Receiving push messages
     * */
    private final BroadcastReceiver mHandleMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String newMessage = intent.getExtras().getString(CommonUtilities.EXTRA_MESSAGE);
            // Waking up mobile if it is sleeping
            WakeLocker.acquire(getApplicationContext());

            /**
             * Take appropriate action on this message
             * depending upon your app requirement
             * For now i am just displaying it on the screen
             * */

            // Showing received message
            lblMessage.append(newMessage + "\n");
            Toast.makeText(getApplicationContext(), "New Message: " + newMessage, Toast.LENGTH_LONG).show();

            try {
                JSONObject data = new JSONObject(newMessage);
                String username = data.getString("username");
                double latitude = data.getDouble("latitude");
                double longitude = data.getDouble("longitude");

                CommonUtilities.storeLastPosition(getApplicationContext(), username,
                        latitude,longitude);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // Releasing wake lock
            WakeLocker.release();
        }


    };

    @Override
    protected void onDestroy() {
        if (mRegisterTask != null) {
            mRegisterTask.cancel(true);
        }
        try {
            unregisterReceiver(mHandleMessageReceiver);
        } catch (Exception e) {
            Log.e("UnRegister Receiver Error", "> " + e.getMessage());
        }

        if (gps != null) {
            gps.stopUsingGPS();
        }
        super.onDestroy();
    }

    private void sendPostRequest(final double latitude, final double longitude) {
        // Try to register again, but not in the UI thread.
        // It's also necessary to cancel the thread onDestroy(),
        // hence the use of AsyncTask instead of a raw thread.
        final Context context = this;
        mRegisterTask = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                String username = etSendTo.getText().toString();
                String message = etMessage.getText().toString();
                JSONObject data = new JSONObject();
                try {
                    data.put("message", message);
                    data.put("username", username);
                    data.put("latitude", latitude);
                    data.put("longitude", longitude);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                name = "kadek";
                email = "k@k.k";
                String password = name;

                // Check if user filled the form
                if(name.trim().length() > 0 && username.trim().length() > 0){
                    // Register to server
                    HttpClient httpClient = new DefaultHttpClient();
                    HttpPost httpPost = new HttpPost(CommonUtilities.SERVER_PUSH_URL);
                    List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
                    nameValuePair.add(new BasicNameValuePair("username", username));
                    nameValuePair.add(new BasicNameValuePair("password", password));
                    nameValuePair.add(new BasicNameValuePair("message", data.toString()));
                    nameValuePair.add(new BasicNameValuePair("current_user", name));

                    //Encoding POST data
                    try {
                        httpPost.setEntity(new UrlEncodedFormEntity(nameValuePair));

                    } catch (UnsupportedEncodingException e)
                    {
                        e.printStackTrace();
                    }

                    try {
                        HttpResponse response = httpClient.execute(httpPost);
                        // write response to log
                        Log.d("Http Post Response:", response.toString());
                    } catch (ClientProtocolException e) {
                        // Log exception
                        e.printStackTrace();
                        return null;
                    } catch (IOException e) {
                        // Log exception
                        e.printStackTrace();
                        return null;
                    }

                }else{
                    // user doen't filled that data
                    // ask him to fill the form
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                mRegisterTask = null;
                Intent i = new Intent(MainActivity.this, MapActivity.class);
                startActivity(i);
            }

        };
        mRegisterTask.execute(null, null, null);
    }

    /**
     * Logging out the user. Will set isLoggedIn flag to false in shared
     * preferences Clears the user data from sqlite users table
     * */
    private void logoutUser() {
        session.setLogin(false);

        db.deleteUsers();

        // Launching the login activity
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
