package com.herokuapp.pushdemoandroid;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.herokuapp.pushdemoandroid.helper.AlertDialogManager;
import com.herokuapp.pushdemoandroid.helper.CommonUtilities;
import com.herokuapp.pushdemoandroid.helper.DatabaseManager;
import com.herokuapp.pushdemoandroid.helper.SessionManager;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
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
    AsyncTask<Void,Void,JsonObject> mRegisterTask;
    // alert dialog manager
    AlertDialogManager alert = new AlertDialogManager();

    private final static int READ_TIMEOUT = 10000;
    private final static int CONN_TIMEOUT = 15000;
    private String POST = "POST";

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
        mRegisterTask = new AsyncTask<Void, Void, JsonObject>() {

            @Override
            protected JsonObject doInBackground(Void... params) {
                List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
                nameValuePair.add(new BasicNameValuePair("usernameOrEmail", username));
                nameValuePair.add(new BasicNameValuePair("password", password));

                try {
                    URL url = new URL(CommonUtilities.SERVER_URL_LOGIN);

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(READ_TIMEOUT);
                    conn.setConnectTimeout(CONN_TIMEOUT);
                    conn.setRequestMethod(POST);
                    conn.setDoInput(true);
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(os, "UTF-8"));
                    writer.write(getQuery(nameValuePair));
                    writer.flush();
                    writer.close();
                    os.close();

                    conn.connect();

                    JsonParser jp = new JsonParser();
                    JsonElement root = jp.parse(new InputStreamReader((InputStream) conn.getContent()));
                    JsonObject rootobj = root.getAsJsonObject();
                    return rootobj;

                } catch (MalformedURLException e) {
//                        setErrorResponse("Malformed URL");
                } catch (SocketTimeoutException e) {
//                        setErrorResponse("Could not connect: Timeout");
                } catch (IOException e) {
//                        setErrorResponse("Could not connect");
                }
                return null;
            }

            @Override
            protected void onPostExecute(JsonObject result) {
                hideDialog();
                String jsonBody = "";
                boolean error = false;
                String errorMessage = "";
                String name = "";
                String mail="";
                String createdAt = "";
                String uid = "";
                jsonBody = result.getAsString();

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
                        Intent i = new Intent(getApplicationContext(), HomeActivity.class);
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

    private String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (NameValuePair pair : params) {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }

        return result.toString();
    }
}
