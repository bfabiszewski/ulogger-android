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
import android.net.SSLCertificateSocketFactory;
import android.net.SSLSessionCache;
import android.util.Log;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * This custom ssl socket factory will be used only with API < 19
 * to solve problem with connecting to SSL-disabled servers.
 */

@SuppressWarnings("deprecation")
class TlsSocketFactory extends SSLSocketFactory {

    private static final String TAG = TlsSocketFactory.class.getSimpleName();
    private static HostnameVerifier hostnameVerifier;
    private static SSLSocketFactory factory;

    TlsSocketFactory(Context context) {
        SSLSessionCache cache = new SSLSessionCache(context);
        factory = SSLCertificateSocketFactory.getDefault(WebHelper.SOCKET_TIMEOUT, cache);
        hostnameVerifier = new org.apache.http.conn.ssl.BrowserCompatHostnameVerifier();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        if (autoClose) { socket.close(); }

        socket = factory.createSocket(InetAddress.getByName(host), port);

        if (socket != null && socket instanceof SSLSocket) {
            if (Logger.DEBUG) { Log.d(TAG, "[Preparing TLS socket]"); }
            SSLSocket sslSocket = (SSLSocket) socket;

            // set all protocols including TLS (disabled by default on older APIs)
            sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
            sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());

            if (host != null && !host.isEmpty()) {
                // set hostname for SNI
                if (Logger.DEBUG) { Log.d(TAG, "[Setting SNI for host " + host + "]"); }
                try {
                    Method setHostnameMethod = sslSocket.getClass().getMethod("setHostname", String.class);
                    setHostnameMethod.invoke(sslSocket, host);
                } catch (Exception e) {
                    if (Logger.DEBUG) { Log.d(TAG, "[Setting SNI failed: " + e.getMessage() + "]"); }
                }
            }
            // verify hostname and certificate
            SSLSession session = sslSocket.getSession();
            if (!hostnameVerifier.verify(host, session)) {
                throw new SSLPeerUnverifiedException("Hostname '" + host + "' was not verified (" + session.getPeerPrincipal() + ")");
            }

            if (Logger.DEBUG) { Log.d(TAG, "Connected to " + session.getPeerHost() + " using " + session.getProtocol() + " (" + session.getCipherSuite() + ")"); }
        }
        return socket;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String[] getSupportedCipherSuites() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort) throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
