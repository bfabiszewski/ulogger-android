/*
 * Copyright (c) 2020 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * This custom ssl socket factory will be used only with APIs 16-22
 * to ensure only TLSv1, TLSv1.1, TLSv1.2 are enabled, which is default for later APIs
 *
 * Protocol     Supported (API Levels) 	Enabled by default (API Levels)
 * SSLv3        1–25                    1–22
 * TLSv1        1+                      1+
 * TLSv1.1      16+                     20+
 * TLSv1.2      16+                     20+
 */

@SuppressWarnings("RedundantThrows")
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
class TlsSocketFactory extends SSLSocketFactory {

    private static final String TAG = TlsSocketFactory.class.getSimpleName();
    private static SSLSocketFactory factory;

    TlsSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, null, null);
        factory = sslContext.getSocketFactory();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        socket = factory.createSocket(socket, host, port, autoClose);

        if (socket instanceof SSLSocket) {
            if (Logger.DEBUG) { Log.d(TAG, "[Preparing TLS socket]"); }
            SSLSocket sslSocket = (SSLSocket) socket;

            // set default protocols of APIs 22+
            sslSocket.setEnabledProtocols(new String[] { "TLSv1.2", "TLSv1.1", "TLSv1" });

            if (host != null && !host.isEmpty()) {
                // set hostname for SNI
                if (Logger.DEBUG) { Log.d(TAG, "[Setting SNI for host " + host + "]"); }
                try {
                    sslSocket.getClass().getMethod("setHostname", String.class).invoke(socket, host);
                } catch (Throwable e) {
                    if (Logger.DEBUG) { Log.d(TAG, "[Setting hostname failed: " + e + "]"); }
                }
            }

            SSLSession session = sslSocket.getSession();
            if (!session.isValid()) {
                if (Logger.DEBUG) { Log.d(TAG, "[Handshake failure]"); }
                throw new SSLHandshakeException("Handshake failure");
            }

            if (Logger.DEBUG) { Log.d(TAG, "[Connected to " + session.getPeerHost() + " using " + session.getProtocol() + " (" + session.getCipherSuite() + ")]"); }
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