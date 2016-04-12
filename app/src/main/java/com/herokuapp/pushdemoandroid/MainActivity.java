package com.herokuapp.pushdemoandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.herokuapp.pushdemoandroid.helper.AlertDialogManager;
import com.herokuapp.pushdemoandroid.helper.ConnectionDetector;
import com.herokuapp.pushdemoandroid.helper.DatabaseManager;
import com.herokuapp.pushdemoandroid.helper.GPSTracker;
import com.herokuapp.pushdemoandroid.helper.JSONParser;
import com.herokuapp.pushdemoandroid.helper.SessionManager;
import com.herokuapp.pushdemoandroid.helper.WakeLocker;
import com.herokuapp.pushdemoandroid.helper.CommonUtilities;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
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
    private GoogleMap googleMap;
    private ProgressDialog pDialog;
    private AdView mAdView;

    private final static int READ_TIMEOUT = 10000;
    private final static int CONN_TIMEOUT = 15000;
    private String POST = "POST";

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
        pDialog = new ProgressDialog(this);
        pDialog.setCancelable(false);

        final double latitude;
        final double longitude;

        // check if GPS enabled
        if(gps.canGetLocation()){

            latitude = gps.getLatitude();
            longitude = gps.getLongitude();
        }else{
            // can't get location
            // GPS or Network is not enabled
            // Ask user to enable GPS/network in settings
            showSettingsAlert();
            latitude =0;
            longitude=0;
            return;
        }

        if (!session.isLoggedIn()) {
            logoutUser();
        }

        // Fetching user details from sqlite
        HashMap<String, String> user = db.getUserDetails();
        name = user.get("name");
        email = user.get("email");
        lblName.setText("Welcome " + name + "\n" + email);

        CommonUtilities.storeLastPosition(getApplicationContext(), "Ryuzaki", name, latitude, longitude);

        lblMessage = (TextView) findViewById(R.id.lblMessage);

        registerReceiver(mHandleMessageReceiver, new IntentFilter(
                CommonUtilities.DISPLAY_MESSAGE_ACTION));

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendPostRequest(latitude, longitude, true, name);
            }
        });
        btnLogout.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                logoutUser();
            }
        });

        try {
            // Loading map
//            initilizeMap();
        } catch (Exception e) {
            e.printStackTrace();
        }

        etSendTo.setText(name);

        // Gets the ad view defined in layout/ad_fragment.xml with ad unit ID set in
        // values/strings.xml.
        mAdView = (AdView) findViewById(R.id.ad_view);

        // Create an ad request. Check logcat output for the hashed device ID to
        // get test ads on a physical device. e.g.
        // "Use AdRequest.Builder.addTestDevice("ABCDEF012345") to get test ads on this device."
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                .build();

        // Start loading the ad in the background.
        mAdView.loadAd(adRequest);
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
                String message = data.getString("message");
                String current_user = data.getString("current_user");

                if (message.equalsIgnoreCase("request")) {
                    sendPostRequest(gps.getLatitude(), gps.getLongitude(), false, current_user);
                } else {
                    CommonUtilities.storeLastPosition(getApplicationContext(), username, current_user,
                            latitude,longitude);
                    updateMap();
                }
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

        if (mAdView != null) {
            mAdView.destroy();
        }
        super.onDestroy();
    }

    /** Called when leaving the activity */
    @Override
    public void onPause() {
        if (mAdView != null) {
            mAdView.pause();
        }
        super.onPause();
    }

    /** Called when returning to the activity */
    @Override
    public void onResume() {
        super.onResume();
        if (mAdView != null) {
            mAdView.resume();
        }
    }

    private void sendPostRequest(final double latitude, final double longitude, final boolean isRequest, final String destUser) {
        // Try to register again, but not in the UI thread.
        // It's also necessary to cancel the thread onDestroy(),
        // hence the use of AsyncTask instead of a raw thread.
        final Context context = this;
        pDialog.setMessage("Please wait ...");
        showDialog();
        final String username = etSendTo.getText().toString();
        final String message = etMessage.getText().toString();
        mRegisterTask = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {


                JSONObject data = new JSONObject();
                try {
                    data.put("username", username);
                    data.put("current_user", name);
                    data.put("latitude", latitude);
                    data.put("longitude", longitude);
                    if (isRequest) {
                        data.put("message", "request");
                    } else {
                        data.put("message", message);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }

//                name = "kadek";
//                email = "k@k.k";
                String password = name;

                // Check if user filled the form
                if(name.trim().length() > 0 && username.trim().length() > 0){
                    // Register to server

                    List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
                    String userName = username;
                    if (!isRequest) userName = destUser;
                    nameValuePair.add(new BasicNameValuePair("username", userName));
                    nameValuePair.add(new BasicNameValuePair("password", password));
                    nameValuePair.add(new BasicNameValuePair("message", data.toString()));
                    nameValuePair.add(new BasicNameValuePair("current_user", name));

                    //Encoding POST data
                    try {
                        URL url = new URL(CommonUtilities.SERVER_PUSH_URL);


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

                    } catch (MalformedURLException e) {
//                        setErrorResponse("Malformed URL");
                    } catch (SocketTimeoutException e) {
//                        setErrorResponse("Could not connect: Timeout");
                    } catch (IOException e) {
//                        setErrorResponse("Could not connect");
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
//                Intent i = new Intent(MainActivity.this, MapActivity.class);
//                startActivity(i);
//                updateMap();
                hideDialog();
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

    /**
     * function to load map. If map is not created it will create it for you
     * */
    private void initilizeMap() {
        if (googleMap == null) {
            googleMap = ((MapFragment) getFragmentManager().findFragmentById(
                    R.id.map)).getMap();

            // check if map is created successfully or not
            if (googleMap == null) {
                Toast.makeText(getApplicationContext(),
                        "Sorry! unable to create maps", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    private void updateMap() {
        JSONObject laspos = CommonUtilities.getLasPostJSON(getApplicationContext());
        try {
            String username = laspos.getString("username");
            double latitude = laspos.getDouble("latitude");
            double longitude = laspos.getDouble("longitude");
            String current_user = laspos.getString("current_user");
            MarkerOptions marker = new MarkerOptions().position(new LatLng(latitude, longitude)).title(current_user);
            googleMap.addMarker(marker);

            CameraPosition cameraPosition = new CameraPosition.Builder().target(
                    new LatLng(latitude, longitude)).zoom(12).build();

            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

            String url = makeURL(gps.getLatitude(), gps.getLongitude(), latitude, longitude);
            Log.d("MainActivity","updateMap url = " + url);
            connectGmapDirectionsAsyncTask task = new connectGmapDirectionsAsyncTask(url);
            task.execute(null,null,null);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showDialog() {
        if (!pDialog.isShowing())
            pDialog.show();
    }

    private void hideDialog() {
        if (pDialog.isShowing())
            pDialog.dismiss();
    }

    //GoogleMap directions
    public String makeURL (double sourcelat, double sourcelog, double destlat, double destlog ){
        StringBuilder urlString = new StringBuilder();
        urlString.append("http://maps.googleapis.com/maps/api/directions/json");
        urlString.append("?origin=");// from
        urlString.append(Double.toString(sourcelat));
        urlString.append(",");
        urlString
                .append(Double.toString( sourcelog));
        urlString.append("&destination=");// to
        urlString
                .append(Double.toString( destlat));
        urlString.append(",");
        urlString.append(Double.toString( destlog));
        urlString.append("&sensor=false&mode=driving&alternatives=true");
        return urlString.toString();
    }

    public void drawPath(String  result) {
        Log.d("MainActivity","drawPath result = " + result);
        try {
            //Tranform the string into a json object
            final JSONObject json = new JSONObject(result);
            JSONArray routeArray = json.getJSONArray("routes");
            JSONObject routes = routeArray.getJSONObject(0);
            JSONObject overviewPolylines = routes.getJSONObject("overview_polyline");
            String encodedString = overviewPolylines.getString("points");
            List<LatLng> list = decodePoly(encodedString);

            for(int z = 0; z<list.size()-1;z++){
                LatLng src= list.get(z);
                LatLng dest= list.get(z+1);
                googleMap.addPolyline(new PolylineOptions()
                        .add(new LatLng(src.latitude, src.longitude), new LatLng(dest.latitude, dest.longitude))
                        .width(2)
                        .color(Color.BLUE).geodesic(true));
            }

        }
        catch (JSONException e) {

        }
    }

    private List<LatLng> decodePoly(String encoded) {

        List<LatLng> poly = new ArrayList<LatLng>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng( (((double) lat / 1E5)),
                    (((double) lng / 1E5) ));
            poly.add(p);
        }

        return poly;
    }

    private class connectGmapDirectionsAsyncTask extends AsyncTask<Void, Void, String>{
        private ProgressDialog progressDialog;
        String url;
        connectGmapDirectionsAsyncTask(String urlPass){
            url = urlPass;
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage("Fetching route, Please wait...");
            progressDialog.setIndeterminate(true);
            progressDialog.show();
        }
        @Override
        protected String doInBackground(Void... params) {
            JSONParser jParser = new JSONParser();
            String json = jParser.getJSONFromUrl(url);
            return json;
        }
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            progressDialog.hide();
            if(result!=null){
                drawPath(result);
            }
        }
    }

    /**
     * Function to show settings alert dialog
     * */
    public void showSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        // Setting Dialog Title
        alertDialog.setTitle("GPS is settings");

        // Setting Dialog Message
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");

        // Setting Icon to Dialog
        //alertDialog.setIcon(R.drawable.delete);

        // On pressing Settings button
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Showing Alert Message
        alertDialog.show();
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
