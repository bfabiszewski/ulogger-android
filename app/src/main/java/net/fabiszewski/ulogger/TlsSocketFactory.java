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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
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
    private static String[] cipherSuites;

    TlsSocketFactory(Context context) {
        SSLSessionCache cache = new SSLSessionCache(context);
        factory = SSLCertificateSocketFactory.getDefault(WebHelper.SOCKET_TIMEOUT, cache);
        hostnameVerifier = new org.apache.http.conn.ssl.BrowserCompatHostnameVerifier();
        cipherSuites = initCipherSuites();
    }

    /**
     * Returns connected secure socket,
     * built on SSLCertificateSocketFactory
     * @param socket Socket
     * @param host Host name
     * @param port Port
     * @param autoClose Auto close
     * @return Socket
     * @throws IOException If setup or host name verification fail
     */
    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        if (autoClose && socket != null && !socket.isClosed()) {
            socket.close();
        }

        socket = factory.createSocket();

        if (socket != null && socket instanceof SSLSocket) {
            if (Logger.DEBUG) { Log.d(TAG, "[Preparing TLS socket]"); }
            SSLSocket sslSocket = (SSLSocket) socket;

            // set all protocols including TLS (disabled by default on older APIs)
            sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
            sslSocket.setEnabledCipherSuites(cipherSuites);

            // set hostname for SNI
            if (Logger.DEBUG) { Log.d(TAG, "[Setting SNI for host " + host + "]"); }
            try {
                Method setHostnameMethod = sslSocket.getClass().getMethod("setHostname", String.class);
                setHostnameMethod.invoke(sslSocket, host);
            } catch (Exception e) {
                if (Logger.DEBUG) { Log.d(TAG, "[Setting SNI failed: " + e.getMessage() + "]"); }
            }

            try {
                InetSocketAddress remoteAddress = new InetSocketAddress(host, port);
                sslSocket.connect(remoteAddress, WebHelper.SOCKET_TIMEOUT);
                sslSocket.setSoTimeout(WebHelper.SOCKET_TIMEOUT);
                // verify hostname and certificate
                verifyHostname(sslSocket, host);
            } catch (Exception e) {
                try { sslSocket.close(); } catch (Exception ex) { /* ignore */ }
                throw e;
            }
        }
        return socket;
    }

    /**
     * Verify hostname against certificate
     * @param sslSocket Socket
     * @param host Host name
     * @throws IOException Exception if host name is not verified
     */
    private void verifyHostname(SSLSocket sslSocket, String host) throws IOException {
        // Make sure we started handshake before verifying
        sslSocket.startHandshake();

        SSLSession session = sslSocket.getSession();
        if (session == null) {
            throw new SSLException("Hostname '" + host + "' was not verified (no session)");
        }
        if (!hostnameVerifier.verify(host, session)) {
            throw new SSLPeerUnverifiedException("Hostname '" + host + "' was not verified (" + session.getPeerPrincipal() + ")");
        }
        if (Logger.DEBUG) { Log.d(TAG, "Connected to " + session.getPeerHost() + " using " + session.getProtocol() + " (" + session.getCipherSuite() + ")"); }
    }

    /**
     * Initialize cipher suites array, based on all supported ciphers without weak ones.
     * @return Array of ciphers
     */
    private String[] initCipherSuites() {
        // remove weakest suites
        ArrayList<String> ciphers = new ArrayList<>(20);
        for (String cipher : factory.getSupportedCipherSuites()) {
            if (cipher.contains("_EXPORT_") ||
                    cipher.contains("_anon_") ||
                    cipher.contains("_NULL_")) {
                continue;
            }
            ciphers.add(cipher);
        }
        return ciphers.toArray(new String[0]);
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
    public Socket createSocket(String host, int port) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Socket createSocket(InetAddress host, int port) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
