package org.bouncycastle.est.http;


import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class DefaultESTClientSSLSocketProvider
    implements ESTClientSSLSocketProvider
{

    private final TLSAcceptedIssuersSource tlsAcceptedIssuersSource;
    private final TLSAuthorizer serverTLSAuthorizer;
    private final KeyManagerFactory keyManagerFactory;
    private final TLSHostNameAuthorizer<SSLSession> hostNameAuthorizer;


    private SSLSocketFactory sslSocketFactory;

    public DefaultESTClientSSLSocketProvider(
        TLSAcceptedIssuersSource tlsAcceptedIssuersSource,
        TLSAuthorizer serverTLSAuthorizer,
        KeyManagerFactory keyManagerFactory,
        TLSHostNameAuthorizer<SSLSession> hostNameAuthorizer)
        throws Exception
    {
        this.tlsAcceptedIssuersSource = tlsAcceptedIssuersSource;
        this.serverTLSAuthorizer = serverTLSAuthorizer;
        this.hostNameAuthorizer = hostNameAuthorizer;
        this.keyManagerFactory = keyManagerFactory;
        sslSocketFactory = createFactory();
    }

    /**
     * Return an ESTClientSSLSocketProvider that uses the the default SSLSocketProvider and a host name verifier.
     *
     * @param hostNameAuthorizer The host name authorizer. (Can be null for no hostname verification.)
     * @return ESTClientSSLSocketProvider
     * @throws Exception
     */
    public static ESTClientSSLSocketProvider getUsingDefaultSSLSocketFactory(TLSHostNameAuthorizer<SSLSession> hostNameAuthorizer)
        throws Exception
    {
        return new DefaultESTClientSSLSocketProvider(null, null, null, hostNameAuthorizer)
        {
            @Override
            public SSLSocketFactory createFactory()
                throws Exception
            {
                return (SSLSocketFactory)SSLSocketFactory.getDefault();
            }
        };
    }

    /**
     * Return an ESTClientSSLSocketProvider that uses the the default SSLSocketProvider and a host name verifier.
     *
     * @param keyManagerFactory  The keymanager factory supplying the client keys.
     * @param hostNameAuthorizer The host name authorizer. (Can be null for no hostname verification.)
     * @return ESTClientSSLSocketProvider
     * @throws Exception
     */
    public static ESTClientSSLSocketProvider getUsingDefaultSSLSocketFactory(KeyManagerFactory keyManagerFactory, TLSHostNameAuthorizer<SSLSession> hostNameAuthorizer)
        throws Exception
    {
        return new DefaultESTClientSSLSocketProvider(null, null, keyManagerFactory, hostNameAuthorizer)
        {
            @Override
            public SSLSocketFactory createFactory()
                throws Exception
            {
                return (SSLSocketFactory)SSLSocketFactory.getDefault();
            }
        };
    }


    /**
     * Creates the SSLSocketFactory.
     *
     * @return A SSLSocketFactory instance.
     * @throws Exception
     */
    public SSLSocketFactory createFactory()
        throws Exception
    {
        SSLContext ctx = SSLContext.getDefault();
        X509TrustManager tm = new X509TrustManager()
        {
            public void checkClientTrusted(X509Certificate[] x509Certificates, String authType)
                throws CertificateException
            {
                // For clients.
            }

            public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
                throws CertificateException
            {
                if (serverTLSAuthorizer == null)
                {
                    throw new CertificateException(
                        "No serverTLSAuthorizer specified, if you wish to have no validation then you must supply an instance that does nothing."
                    );
                }
                serverTLSAuthorizer.authorize(x509Certificates, s);
            }

            public X509Certificate[] getAcceptedIssuers()
            {
                if (tlsAcceptedIssuersSource != null)
                {
                    return tlsAcceptedIssuersSource.anchors();
                }
                return new X509Certificate[0];
            }
        };

        ctx.init((keyManagerFactory != null) ? keyManagerFactory.getKeyManagers() : null, new TrustManager[]{tm}, new SecureRandom());
        return ctx.getSocketFactory();
    }


    public SSLSocket wrapSocket(Socket plainSocket, String host, int port)
        throws Exception
    {
        SSLSocket sock = (SSLSocket)sslSocketFactory.createSocket(plainSocket, host, port, true);
        sock.setUseClientMode(true);
        sock.startHandshake();
        if (hostNameAuthorizer != null && !hostNameAuthorizer.authorise(host, sock.getSession()))
        {
            throw new IOException("Hostname was not verified: " + host);
        }
        return sock;
    }
}
