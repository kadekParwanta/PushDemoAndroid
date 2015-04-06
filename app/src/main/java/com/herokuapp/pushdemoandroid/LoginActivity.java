package com.herokuapp.pushdemoandroid;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.herokuapp.pushdemoandroid.helper.AlertDialogManager;
import com.herokuapp.pushdemoandroid.helper.CommonUtilities;
import com.herokuapp.pushdemoandroid.helper.DatabaseManager;
import com.herokuapp.pushdemoandroid.helper.SessionManager;

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

/**
 * Created by Kadek_P on 4/1/15.
 */
public class LoginActivity extends Activity {
    private static final String TAG = DemoActivity.class.getSimpleName();
    private Button btnLogin;
    private Button btnLinkToRegister;
    private EditText inputEmail;
    private EditText inputPassword;
    private ProgressDialog pDialog;
    private SessionManager session;
    private DatabaseManager db;
    AsyncTask<Void,Void,HttpResponse> mRegisterTask;
    // alert dialog manager
    AlertDialogManager alert = new AlertDialogManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        inputEmail = (EditText) findViewById(R.id.email);
        inputPassword = (EditText) findViewById(R.id.password);
        btnLogin = (Button) findViewById(R.id.btnLogin);
        btnLinkToRegister = (Button) findViewById(R.id.btnLinkToRegisterScreen);

        // Progress dialog
        pDialog = new ProgressDialog(this);
        pDialog.setCancelable(false);

        // Session manager
        session = new SessionManager(getApplicationContext());
        db = new DatabaseManager(getApplicationContext());

        // Check if user is already logged in or not
        if (session.isLoggedIn()) {
            // User is already logged in. Take him to main activity
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = inputEmail.getText().toString();
                String password = inputPassword.getText().toString();

                // Check for empty data in the form
                if (email.trim().length() > 0 && password.trim().length() > 0) {
                    // login user
                    sendPostRequest(email, password);
                } else {
                    // Prompt user to enter credentials
                    Toast.makeText(getApplicationContext(),
                            "Please enter the credentials!", Toast.LENGTH_LONG)
                            .show();
                }
            }
        });

        btnLinkToRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(),
                        DemoActivity.class);
                startActivity(i);
                finish();
            }
        });

    }

    private void showDialog() {
        if (!pDialog.isShowing())
            pDialog.show();
    }

    private void hideDialog() {
        if (pDialog.isShowing())
            pDialog.dismiss();
    }

    private void sendPostRequest(final String username, final String password) {
        pDialog.setMessage("Logging in ...");
        showDialog();
        mRegisterTask = new AsyncTask<Void, Void, HttpResponse>() {

            @Override
            protected HttpResponse doInBackground(Void... params) {
                HttpClient httpClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost(CommonUtilities.SERVER_URL_LOGIN);
                List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
                nameValuePair.add(new BasicNameValuePair("usernameOrEmail", username));
                nameValuePair.add(new BasicNameValuePair("password", password));

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
                String jsonBody = "";
                boolean error = false;
                String errorMessage = "";
                String name = "";
                String mail="";
                String createdAt = "";
                String uid = "";
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
                        createdAt = user.getString("created_at");
                        uid = user.getString("uid");

                        Log.i(CommonUtilities.TAG, "JSONObject user= " + user + " name =" + name + "email = "+ mail);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    if (!error) {
                        session.setLogin(true);
                        mRegisterTask = null;
                        if (db.getRowCount() == 0) db.addUser(name, mail, uid, createdAt);
                        Intent i = new Intent(getApplicationContext(), MainActivity.class);
                        startActivity(i);
                        finish();
                    } else {
                        alert.showAlertDialog(LoginActivity.this,
                                "An error has occurred",errorMessage, false);
                    }
                }

            }

        };
        mRegisterTask.execute(null, null, null);
    }
}
