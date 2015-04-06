/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.herokuapp.pushdemoandroid;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.herokuapp.pushdemoandroid.helper.AlertDialogManager;
import com.herokuapp.pushdemoandroid.helper.ConnectionDetector;
import com.herokuapp.pushdemoandroid.helper.CommonUtilities;
import com.herokuapp.pushdemoandroid.helper.DatabaseManager;
import com.herokuapp.pushdemoandroid.helper.SessionManager;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main UI for the demo app.
 */
public class DemoActivity extends Activity {

    TextView mDisplay;
    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();
    Context context;

    String regid;
    // alert dialog manager
    AlertDialogManager alert = new AlertDialogManager();

    // Internet detector
    ConnectionDetector cd;

    // UI elements
    EditText txtName;
    EditText txtPassword;
    EditText txtEmail;

    // Register button
    Button btnRegister;
    Button btnLinkToLoginScreen;
    AsyncTask<Void,Void,HttpResponse> mRegisterTask;
    private String username;
    private String password;
    private String email;
    private SessionManager session;
    private ProgressDialog pDialog;
    private DatabaseManager db;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_register);
        txtName = (EditText) findViewById(R.id.txtName);
        txtEmail = (EditText) findViewById(R.id.txtEmail);
        btnRegister = (Button) findViewById(R.id.btnRegister);
        txtPassword = (EditText) findViewById(R.id.txtPassword);
        mDisplay = (TextView) findViewById(R.id.tvDisplay);
        btnLinkToLoginScreen = (Button) findViewById(R.id.btnLinkToLoginScreen);

        // Check if Internet present
        cd = new ConnectionDetector(getApplicationContext());
        // SQLite database handler
        db = new DatabaseManager(getApplicationContext());

        if (!cd.isConnectingToInternet()) {
            // Internet Connection is not present
            alert.showAlertDialog(DemoActivity.this,
                    "Internet Connection Error",
                    "Please connect to working Internet connection", false);
            // stop executing code by return
            return;
        }

        context = getApplicationContext();
        session = new SessionManager(getApplicationContext());
        pDialog = new ProgressDialog(this);
        pDialog.setCancelable(false);

        // Check device for Play Services APK. If check succeeds, proceed with GCM registration.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = CommonUtilities.getRegistrationId(context);

            if (regid.isEmpty()) {
                registerInBackground();
            } else if (session.isLoggedIn()) {
                Intent i = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(i);
                finish();
            }
        } else {
            Log.i(CommonUtilities.TAG, "No valid Google Play Services APK found.");
        }



		/*
		 * Click event on Register button
		 * */
        btnRegister.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                username = txtName.getText().toString();
                email = txtEmail.getText().toString();
                password = txtPassword.getText().toString();

                // Check for empty data in the form
                if (email.trim().length() > 0 && password.trim().length() > 0) {
                    // login user
                    sendPostRequest();
                } else {
                    // Prompt user to enter credentials
                    Toast.makeText(getApplicationContext(),
                            "Please enter the credentials!", Toast.LENGTH_LONG)
                            .show();
                }
            }
        });

        /*
		 * Click event on LinkToLogin button
		 * */
        btnLinkToLoginScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(i);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check device for Play Services APK.
        checkPlayServices();
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        CommonUtilities.PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(CommonUtilities.TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }


    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(CommonUtilities.SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.
                    sendRegistrationIdToBackend();

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Persist the regID - no need to register again.
                    CommonUtilities.storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                mDisplay.append(msg + "\n");
            }
        }.execute(null, null, null);
    }

    // Send an upstream message.
    public void onClick(final View view) {

        if (view == findViewById(R.id.send)) {
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    String msg = "";
                    try {
                        Bundle data = new Bundle();
                        data.putString("my_message", "Hello World");
                        data.putString("my_action", "com.google.android.gcm.demo.app.ECHO_NOW");
                        String id = Integer.toString(msgId.incrementAndGet());
                        gcm.send(CommonUtilities.SENDER_ID + "@gcm.googleapis.com", id, data);
                        msg = "Sent message";
                    } catch (IOException ex) {
                        msg = "Error :" + ex.getMessage();
                    }
                    return msg;
                }

                @Override
                protected void onPostExecute(String msg) {
                }
            }.execute(null, null, null);
        } else if (view == findViewById(R.id.clear)) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP or CCS to send
     * messages to your app. Not needed for this demo since the device sends upstream messages
     * to a server that echoes back the message using the 'from' address in the message.
     */
    private void sendRegistrationIdToBackend() {
      // Your implementation here.
    }

    private void sendPostRequest() {
        pDialog.setMessage("Registering ...");
        showDialog();
        final Context context = this;
        mRegisterTask = new AsyncTask<Void, Void, HttpResponse>() {

            @Override
            protected HttpResponse doInBackground(Void... params) {
                // Register to server
                HttpClient httpClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost(CommonUtilities.SERVER_URL_REGISTER);
                List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
                nameValuePair.add(new BasicNameValuePair(CommonUtilities.PROPERTY_REG_USERNAME, username));
                nameValuePair.add(new BasicNameValuePair(CommonUtilities.PROPERTY_REG_PASSWORD, password));
                nameValuePair.add(new BasicNameValuePair(CommonUtilities.PROPERTY_REG_GCMID, regid));
                nameValuePair.add(new BasicNameValuePair(CommonUtilities.PROPERTY_REG_ROLE, "ROLE_USER"));
                nameValuePair.add(new BasicNameValuePair(CommonUtilities.PROPERTY_REG_EMAIL, email));

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
                    return response;
                } catch (ClientProtocolException e) {
                    // Log exception
                    e.printStackTrace();
                } catch (IOException e) {
                    // Log exception
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(HttpResponse result) {
                hideDialog();
                if (result == null) return;
                String jsonBody = "";
                boolean error = false;
                String errorMessage = "";
                String name = "";
                String mail="";
                try {
                    jsonBody = EntityUtils.toString(result.getEntity());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (!jsonBody.isEmpty()) {
                    try {
                        JSONObject data = new JSONObject(jsonBody);
                        error = data.getBoolean("error");
                        if (error) errorMessage = data.getString("error_message");
                        JSONObject user = data.getJSONObject("user");
                        name = user.getString("name");
                        mail = user.getString("email");
                        String createdAt = user.getString("created_at");
                        String uid = user.getString("uid");

                        // Inserting row in users table
                        db.addUser(name, mail, uid, createdAt);

                        Log.i(CommonUtilities.TAG, "JSONObject user= " + user + " name =" + name + "email = "+ mail);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    if (!error) {
                        mRegisterTask = null;
                        Intent i = new Intent(getApplicationContext(), MainActivity.class);
                        startActivity(i);
                        finish();
                    } else {
                        alert.showAlertDialog(DemoActivity.this,
                                "An error has occurred",errorMessage, false);
                    }
                }

            }

        };
        mRegisterTask.execute(null, null, null);
    }

    private void showDialog() {
        if (!pDialog.isShowing())
            pDialog.show();
    }

    private void hideDialog() {
        if (pDialog.isShowing())
            pDialog.dismiss();
    }
}
