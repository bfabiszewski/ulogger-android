/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Web server communication
 *
 */

class WebHelper {
    private static final String TAG = WebSyncService.class.getSimpleName();
    private static CookieManager cookieManager = null;

    private static String host;
    private static String user;
    private static String pass;

    private static final String CLIENT_SCRIPT = "client/index.php";
    private static final String PARAM_ACTION = "action";

    // addpos
    private static final String ACTION_ADDPOS = "addpos";
    static final String PARAM_TIME = "time";
    static final String PARAM_LAT = "lat";
    static final String PARAM_LON = "lon";
    static final String PARAM_ALT = "altitude";
    static final String PARAM_SPEED = "speed";
    static final String PARAM_BEARING = "bearing";
    static final String PARAM_ACCURACY = "accuracy";
    static final String PARAM_PROVIDER = "provider";
    // todo add comments, images
//    static final String PARAM_COMMENT = "comment";
//    static final String PARAM_IMAGEID = "imageid";
    static final String PARAM_TRACKID = "trackid";

    // auth
    private static final String ACTION_AUTH = "auth";
    // todo adduser not implemented (do we need it?)
//    private static final String ACTION_ADDUSER = "adduser";
    private static final String PARAM_USER = "user";
    private static final String PARAM_PASS = "pass";

    // addtrack
    private static final String ACTION_ADDTRACK = "addtrack";
    private static final String PARAM_TRACK = "track";

    private final String userAgent;


    /**
     * Constructor
     * @param context Context
     */
    WebHelper(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        user = prefs.getString("prefUsername", "NULL");
        pass = prefs.getString("prefPass", "NULL");
        host = prefs.getString("prefHost", "NULL");
        userAgent = context.getString(R.string.app_name_ascii) + "; " + System.getProperty("http.agent");

        if (cookieManager == null) {
            cookieManager = new CookieManager();
            CookieHandler.setDefault(cookieManager);
        }
    }

    /**
     * Send post request
     * @param params Request parameters
     * @return Server response
     * @throws IOException Connection error
     * @throws WebAuthException Authorization error
     */
    private String postWithParams(Map<String, String> params) throws IOException, WebAuthException {
        URL url = new URL(host + "/" + CLIENT_SCRIPT);
        Log.d(TAG, "[postWithParams: " + url + " : " + params +"]");
        String response = null;

        String dataString = "";
        for (Map.Entry<String, String> p : params.entrySet()) {
            String key = p.getKey();
            String value = p.getValue();
            if (dataString.length() > 0) {
                dataString += "&";
            }
            dataString += URLEncoder.encode(key, "UTF-8") + "=";
            dataString += URLEncoder.encode(value, "UTF-8");
        }
        byte[] data = dataString.getBytes();

        HttpURLConnection connection = null;
        try {
            boolean redirect;
            int redirectTries = 5;
            do {
                redirect = false;
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("Content-Length", Integer.toString(data.length));
                connection.setRequestProperty("User-agent", userAgent);
                connection.setInstanceFollowRedirects(true);

                OutputStream out = new BufferedOutputStream(connection.getOutputStream());
                out.write(data);
                out.flush();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307) {
                    URL base = connection.getURL();
                    String location = connection.getHeaderField("Location");
                    Log.d(TAG, "[postWithParams redirect: " + location + "]");
                    if (location == null || redirectTries == 0) {
                        throw new IOException("Illegal redirect: " + responseCode);
                    }
                    redirect = true;
                    redirectTries--;
                    url = new URL(base, location);
                    String h1 = base.getHost();
                    String h2 = url.getHost();
                    if (h1 != null && !h1.equalsIgnoreCase(h2)) {
                        throw new IOException("Illegal redirect: " + responseCode);
                    }
                }
                else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new WebAuthException("Authorization failure: " + responseCode);
                }
                else if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode);
                }
            } while (redirect);

            InputStream in = new BufferedInputStream(connection.getInputStream());

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine);
            }
            response = sb.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        Log.d(TAG, "[postWithParams response: " + response + "]");
        return response;
    }

    /**
     * Upload position to server
     * @param params Map of parameters (position properties)
     * @throws IOException Connection error
     * @throws WebAuthException Authorization error
     */
    void postPosition(Map<String, String> params) throws IOException, WebAuthException {
        Log.d(TAG, "[postPosition]");
        params.put(PARAM_ACTION, ACTION_ADDPOS);
        String response = postWithParams(params);
        boolean error = true;
        try {
            JSONObject json = new JSONObject(response);
            error = json.getBoolean("error");
        } catch (JSONException e) {
            Log.d(TAG, "[postPosition json failed: " + e +"]");
        }
        if (error) {
            throw new IOException("response error");
        }
    }

    /**
     * Start new track on server
     * @param name Track name
     * @return Track id
     * @throws IOException Connection error
     * @throws WebAuthException Authorization error
     */
    int startTrack(String name) throws IOException, WebAuthException {
        Log.d(TAG, "[startTrack: " + name +"]");
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_ACTION, ACTION_ADDTRACK);
        params.put(PARAM_TRACK, name);
        try {
            String response = postWithParams(params);
            JSONObject json = new JSONObject(response);
            boolean error = json.getBoolean("error");
            if (error) {
                throw new IOException("Server error");
            } else {
                return json.getInt("trackid");
            }
        } catch (JSONException e) {
            Log.d(TAG, "[startTrack json failed: " + e +"]");
            throw new IOException(e);
        }
    }

    /**
     * Authorize on server
     * @throws IOException Connection error
     * @throws WebAuthException Authorization error
     * @throws JSONException Response parsing error
     */
    void authorize() throws IOException, WebAuthException, JSONException {
        Log.d(TAG, "[authorize]");
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_ACTION, ACTION_AUTH);
        params.put(PARAM_USER, user);
        params.put(PARAM_PASS, pass);
        String response = postWithParams(params);
        JSONObject json = new JSONObject(response);
        boolean error = json.getBoolean("error");
        if (error) {
            throw new WebAuthException("Server error");
        }
    }

}
