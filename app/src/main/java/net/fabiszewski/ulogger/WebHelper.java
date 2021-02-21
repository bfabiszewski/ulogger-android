/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

import static android.util.Base64.NO_PADDING;
import static android.util.Base64.NO_WRAP;
import static android.util.Base64.URL_SAFE;

/**
 * Web server communication
 *
 */

class WebHelper {
    private static final String TAG = WebSyncService.class.getSimpleName();
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final String MULTIPART_TEXT_TEMPLATE = "Content-Disposition: form-data; name=\"%s\"\r\n\r\n%s";
    private static final String MULTIPART_FILE_TEMPLATE = "Content-Disposition: form-data; name=\"%s\"; filename=\"upload\"\r\n" +
            "Content-Type: %s\r\n" +
            "Content-Transfer-Encoding: binary\r\n\r\n";
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
    static final String PARAM_COMMENT = "comment";
    static final String PARAM_IMAGE = "image";
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
    private final Context context;

    private static boolean tlsSocketInitialized = false;
    // Socket timeout in milliseconds
    static final int SOCKET_TIMEOUT = 30 * 1000;
    private static final Random random = new Random();

    static boolean isAuthorized = false;
    private byte[] delimiter;
    private static final String CRLF = "\r\n";
    private static final String DASH = "--";

    /**
     * Constructor
     * @param ctx Context
     */
    WebHelper(Context ctx) {
        context = ctx;
        loadPreferences(ctx);
        userAgent = context.getString(R.string.app_name_ascii) + "/" + BuildConfig.VERSION_NAME + "; " + System.getProperty("http.agent");

        if (cookieManager == null) {
            cookieManager = new CookieManager();
            CookieHandler.setDefault(cookieManager);
        }

        // On APIs < 20 enable TLSv1.1 and TLSv1.2 protocols, on APIs <= 22 disable SSLv3
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1 && !tlsSocketInitialized) {
            try {
                if (Logger.DEBUG) { Log.d(TAG, "[init TLS socket factory]"); }
                HttpsURLConnection.setDefaultSSLSocketFactory(new TlsSocketFactory());
                tlsSocketInitialized = true;
            } catch (Exception e) {
                if (Logger.DEBUG) { Log.d(TAG, "[TLS socket setup error (ignored): " + e.getMessage() + "]"); }
            }
        }
    }

    /**
     * Send post request
     * application/x-www-form-urlencoded
     * @param params Request parameters
     * @return Server response
     * @throws IOException Connection error
     * @throws WebAuthException Authorization error
     */
    private String postWithParams(Map<String, String> params) throws IOException, WebAuthException {
        return postForm(params, null);
    }

    private byte[] getUrlencodedData(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder dataString = new StringBuilder();
        for (Map.Entry<String, String> p : params.entrySet()) {
            String key = p.getKey();
            String value = p.getValue();
            if (dataString.length() > 0) {
                dataString.append("&");
            }
            dataString.append(URLEncoder.encode(key, "UTF-8"))
                      .append("=")
                      .append(URLEncoder.encode(value, "UTF-8"));
        }
        return dataString.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Send post request
     * When uri is supplied it posts multipart/form-data
     * else application/x-www-form-urlencoded type
     * @param params Request parameters
     * @param uri Optional uri of file
     * @return Server response
     * @throws IOException Connection error
     * @throws WebAuthException Authorization error
     */
    private String postForm(@NonNull Map<String, String> params, @Nullable Uri uri) throws IOException, WebAuthException {
        boolean isMultipart = uri != null;
        URL url = new URL(host + "/" + CLIENT_SCRIPT);
        if (Logger.DEBUG) { Log.d(TAG, "[postForm: " + url + " : " + params + ", image=" + uri + "]"); }
        String response;
        byte[] data;
        final long contentLength;
        final String contentType;
        if (isMultipart) {
            final String boundary = generateBoundary();
            final String d = CRLF + DASH + boundary + CRLF;
            delimiter = d.getBytes(StandardCharsets.UTF_8);
            data = getMultipartTextPart(params);
            contentLength = getMultipartLength(uri, data);
            contentType = "multipart/form-data; boundary=" + boundary;
        } else {
            data = getUrlencodedData(params);
            contentLength = data.length;
            contentType = "application/x-www-form-urlencoded";
        }
        HttpURLConnection connection = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            boolean retry;
            int tries = 5;
            do {
                retry = false;
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(SOCKET_TIMEOUT);
                connection.setReadTimeout(SOCKET_TIMEOUT);
                connection.setUseCaches(false);
                connection.setRequestProperty("Content-Type", contentType);
                connection.setFixedLengthStreamingMode(contentLength);

                out = new BufferedOutputStream(connection.getOutputStream());
                byte[] bytes = data;
                if (isMultipart) {
                    out.write(bytes);
                    writeMultipartFile(out, uri);
                    out.write(delimiter, 0, delimiter.length - 2);
                    String end = DASH + CRLF;
                    bytes = end.getBytes(StandardCharsets.UTF_8);
                }
                out.write(bytes);
                out.flush();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307) {
                    URL base = connection.getURL();
                    String location = connection.getHeaderField("Location");
                    if (Logger.DEBUG) { Log.d(TAG, "[postForm redirect: " + location + "]"); }
                    if (location == null || tries == 0) {
                        throw new IOException(context.getString(R.string.e_illegal_redirect, responseCode));
                    }
                    retry = true;
                    tries--;
                    url = new URL(base, location);
                    String h1 = base.getHost();
                    String h2 = url.getHost();
                    if (h1 != null && !h1.equalsIgnoreCase(h2)) {
                        throw new IOException(context.getString(R.string.e_illegal_redirect, responseCode));
                    }
                    try {
                        out.close();
                        connection.getInputStream().close();
                        connection.disconnect();
                    } catch (final IOException ignored) { }
                }
                else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new WebAuthException(context.getString(R.string.e_auth_failure, responseCode));
                }
                else if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException(context.getString(R.string.e_http_code, responseCode));
                }
            } while (retry);

            in = new BufferedInputStream(connection.getInputStream());

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine);
            }
            response = sb.toString();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (final IOException ignored) { }
        }
        if (Logger.DEBUG) { Log.d(TAG, "[postForm response: " + response + "]"); }
        return response;
    }

    /**
     * Get length of multipart body
     * @param uri File uri
     * @param data Text part data
     * @return Length in bytes
     */
    private int getMultipartLength(@NonNull Uri uri, byte[] data) {
        int length = 0;
        String fileMime = ImageHelper.getFileMime(context, uri);
        if (fileMime != null) {
            // text part size
            length += data.length;
            // file size
            long fileSize = ImageHelper.getFileSize(context, uri);
            if (fileSize > 0) {
                String headers = String.format(MULTIPART_FILE_TEMPLATE, PARAM_IMAGE, fileMime);
                length += headers.getBytes(StandardCharsets.UTF_8).length + delimiter.length;
                length += fileSize;
            }
            // closing delimiter
            length += delimiter.length + 2;
        }
        if (Logger.DEBUG) { Log.d(TAG, "[getMultipartLength: " + length + "]"); }
        return length;
    }

    /**
     * Write uri to output stream.
     * File name and extension is ignored, only MIME type is sent.
     * Errors are not propagated to allow skipping problematic file and sending only position.
     * @param out Output stream
     * @param uri File uri
     */
    private void writeMultipartFile(@NonNull OutputStream out, @NonNull Uri uri) {
        ContentResolver cr = context.getContentResolver();
        String fileMime = ImageHelper.getFileMime(context, uri);
        if (fileMime == null) {
            if (Logger.DEBUG) { Log.d(TAG, "[Skipping file, unknown mime type]"); }
            return;
        }
        long fileSize = ImageHelper.getFileSize(context, uri);
        if (fileSize <= 0) {
            if (Logger.DEBUG) { Log.d(TAG, "[Skipping file, wrong size: " + fileSize + "]"); }
            return;
        }
        try (InputStream fileStream = cr.openInputStream(uri)) {
            if (fileStream == null) {
                throw new IOException("InputStream is null");
            }
            out.write(delimiter);
            String headers = String.format(MULTIPART_FILE_TEMPLATE, PARAM_IMAGE, fileMime);
            out.write(headers.getBytes(StandardCharsets.UTF_8));

            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = fileStream.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (IOException | OutOfMemoryError fileException) {
            if (Logger.DEBUG) { Log.d(TAG, "[Skipping file, error: " + fileException + "]"); }
        }
    }

    /**
     * Get text/plain parameters as part of multipart form
     * @param out Output stream
     * @param params Parameters
     * @return Multipart body for text parameters
     * @throws IOException Exception on failure
     */
    private byte[] getMultipartTextPart(Map<String, String> params) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<String, String> p : params.entrySet()) {
            out.write(delimiter);
            String body = String.format(MULTIPART_TEXT_TEMPLATE, p.getKey(), p.getValue());
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    /**
     * Generate random boundary for multipart form
     * @return Boundary
     */
    private static String generateBoundary() {
        byte[] token = new byte[12];
        random.nextBytes(token);
        String boundary = "----uLoggerBoundary";
        return boundary + Base64.encodeToString(token, NO_PADDING|NO_WRAP|URL_SAFE);
    }

    /**
     * Upload position to server
     * @param params Map of parameters (position properties)
     * @throws IOException Connection error
     * @throws WebAuthException Authorization error
     */
    void postPosition(Map<String, String> params) throws IOException, WebAuthException {
        if (Logger.DEBUG) { Log.d(TAG, "[postPosition]"); }
        params.put(PARAM_ACTION, ACTION_ADDPOS);
        String response;
        Uri uri = null;
        if (params.containsKey(PARAM_IMAGE)) {
            uri = Uri.parse(params.remove(PARAM_IMAGE));
        }
        response = postForm(params, uri);
        boolean error = true;
        try {
            JSONObject json = new JSONObject(response);
            error = json.getBoolean("error");
        } catch (JSONException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[postPosition json failed: " + e + "]"); }
        }
        if (error) {
            throw new IOException(context.getString(R.string.e_server_response));
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
        if (Logger.DEBUG) { Log.d(TAG, "[startTrack: " + name + "]"); }
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_ACTION, ACTION_ADDTRACK);
        params.put(PARAM_TRACK, name);
        try {
            String response = postWithParams(params);
            JSONObject json = new JSONObject(response);
            boolean error = json.getBoolean("error");
            if (error) {
                throw new IOException(context.getString(R.string.e_server_response));
            } else {
                return json.getInt("trackid");
            }
        } catch (JSONException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[startTrack json failed: " + e + "]"); }
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
        if (Logger.DEBUG) { Log.d(TAG, "[authorize]"); }
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_ACTION, ACTION_AUTH);
        params.put(PARAM_USER, user);
        params.put(PARAM_PASS, pass);
        String response = postWithParams(params);
        JSONObject json = new JSONObject(response);
        boolean error = json.getBoolean("error");
        if (error) {
            throw new WebAuthException(context.getString(R.string.e_server_response));
        }
        isAuthorized = true;
    }

    /**
     * Remove authorization by removing session cookie
     */
    static void deauthorize() {
        if (Logger.DEBUG) { Log.d(TAG, "[deauthorize]"); }
        if (cookieManager != null) {
            CookieStore store = cookieManager.getCookieStore();
            store.removeAll();
        }
        isAuthorized = false;
    }

    /**
     * Get settings from shared preferences
     * @param context Context
     */
    private static void loadPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        user = prefs.getString(SettingsActivity.KEY_USERNAME, "NULL");
        pass = prefs.getString(SettingsActivity.KEY_PASS, "NULL");
        host = prefs.getString(SettingsActivity.KEY_HOST, "NULL").replaceAll("/+$", "");
    }

    /**
     * Reload settings from shared preferences.
     * @param context Context
     */
     static void updatePreferences(Context context) {
        loadPreferences(context);
        deauthorize();
    }

    /**
     * Check whether given url is valid.
     * Uses relaxed pattern (@see WebPatterns#WEB_URL_RELAXED)
     * @param url URL
     * @return True if valid, false otherwise
     */
    static boolean isValidURL(String url) {
        return WebPatterns.WEB_URL_RELAXED.matcher(url).matches();
    }

}
