package com.se491.app.two2er.Services;

import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.se491.app.two2er.HelperObjects.CurrentUser;
import com.se491.app.two2er.HelperObjects.MyGoogleApiClient_Singleton;
import com.se491.app.two2er.HelperObjects.UserObject;
import com.se491.app.two2er.Utilities.ServerApiUtilities;
import com.stormpath.sdk.Stormpath;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Created by pazra on 4/15/2017.
 */

public class LocationRefreshService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private static final String serviceLogTag = "LocationRefreshService";
    private static final String objectName = "Two2er Location Refresh Service"; // Makes this object easy to find when debugging
    private static final int LOCATION_INTERVAL = 60000;
    private static final float LOCATION_DISTANCE = 10f;

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private OkHttpClient okHttpClient;

    private int responseStatus = 0;

    private volatile static double latitude = 0;
    private volatile static double longitude = 0;

    public static double getLatitude() { return latitude; }
    public static double getLongitude() { return longitude; }

    private static boolean isRunning = true;

    public LocationRefreshService() {
        super(objectName);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(serviceLogTag, "Starting Location Refresh Service");

        UserObject curr = CurrentUser.getCurrentUser();
        latitude = curr.dLat;
        longitude = curr.dLong;

        mGoogleApiClient = MyGoogleApiClient_Singleton.getInstance(null).get_GoogleApiClient();
        setupOkHttpClient();

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( LocationRefreshService.this, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( LocationRefreshService.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return super.onStartCommand(intent, flags, startId);
        }

        initLocationRequest();
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

        Log.i("MapStartup", String.format("StartupLocation long: %f lat: %f", longitude, latitude));

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        try {
            while (isRunning) {
                updateLocation();
                Thread.sleep(10000);
            }
        }
        catch (Exception ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(serviceLogTag, "Connected to GoogleApiClient " + bundle);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(serviceLogTag, "Connection suspended to GoogleApiClient");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(serviceLogTag, "Connected to GoogleApiClient");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(serviceLogTag, "lat " + location.getLatitude());
        Log.i(serviceLogTag, "lng " + location.getLongitude());
        latitude = location.getLatitude();
        longitude = location.getLongitude();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        Log.i(serviceLogTag, "onDestroy");
        super.onDestroy();
    }

    private void setupOkHttpClient() {
        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                Stormpath.logger().d(message);
            }
        });
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        this.okHttpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(httpLoggingInterceptor)
                .build();
    }

    private void initLocationRequest() {
        Log.i(serviceLogTag, "initLocationRequest");
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(LOCATION_INTERVAL);
        mLocationRequest.setFastestInterval(LOCATION_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }

    private void stopLocationUpdate() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    private void updateLocation() {
        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( LocationRefreshService.this, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( LocationRefreshService.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        }

        Location loc = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (loc != null) {
            Log.i(serviceLogTag, "UpdateLocation lat " + loc.getLatitude() + " : long " + loc.getLongitude());

            if (latitude != loc.getLatitude() || longitude != loc.getLongitude()) {
                latitude = loc.getLatitude();
                longitude = loc.getLongitude();

                if (CurrentUser.getCurrentUser() != null) {
                    CurrentUser.getCurrentUser().dLong = longitude;
                    CurrentUser.getCurrentUser().dLat = latitude;
                }

                try {
                    sendLocationData(latitude, longitude);
                }
                catch (Exception ex) {
                    Log.e(serviceLogTag, ex.toString());
                }
            }
        }
    }

    private void sendLocationData(double latitude, double longitude) {
            JSONObject obj = createLocationJsonObject();

            RequestBody requestBody = RequestBody
                    .create(MediaType.parse("application/json"), obj.toString());

            Request request = new Request.Builder()
                    .url(getUrl())
                    .headers(ServerApiUtilities.buildStandardHeaders(Stormpath.getAccessToken()))
                    .post(requestBody)
                    .build();


            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    Log.e(serviceLogTag, "Error on post to locations: " + e.toString());
                    responseStatus = 2;
                }

                @Override
                public void onResponse(okhttp3.Call call, Response response) throws IOException {
                    Log.i(serviceLogTag, response.code() + " " + response.message());
                    responseStatus = 1;
                }

            });
            while (responseStatus < 1) { }

            }

    private JSONObject createLocationJsonObject() {
        JSONObject obj = new JSONObject();
        JSONObject embeddedObj = new JSONObject();
        JSONArray coords = new JSONArray();

        try {
            coords.put(longitude);
            coords.put(latitude);

            embeddedObj.put("type", "Point");
            embeddedObj.put("coordinates", coords);

            obj.put("location", embeddedObj);

            Log.i(serviceLogTag, obj.toString());
        }
        catch (Exception ex) {
            Log.e(serviceLogTag, ex.toString());
        }

        return obj;
    }

    private String getUrl() {
        return ServerApiUtilities.GetServerApiUrl() + "/" + ServerApiUtilities.SERVER_API_URL_ROUTE_USERS;
    }
}
