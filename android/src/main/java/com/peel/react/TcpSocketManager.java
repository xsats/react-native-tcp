package com.peel.react;

import androidx.annotation.Nullable;
import android.util.SparseArray;
import android.os.Build;

import com.facebook.react.bridge.Callback;
import com.koushikdutta.async.*;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ListenCallback;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Created by aprock on 12/29/15.
 */
public final class TcpSocketManager {
    private SparseArray<Object> mClients = new SparseArray<Object>();

    private WeakReference<TcpSocketListener> mListener;
    private AsyncServer mServer = AsyncServer.getDefault();

    private int mInstances = 5000;

    static SSLContext defaultSSLContext;
    static SSLContext trustAllSSLContext;
    static TrustManager[] trustAllManagers;
    static HostnameVerifier trustAllVerifier;

    public TcpSocketManager(TcpSocketListener listener) throws IOException {
        mListener = new WeakReference<TcpSocketListener>(listener);
    }

    private void setSocketCallbacks(final Integer cId, final AsyncSocket socket) {
        socket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onClose(cId, ex == null ? null : ex.getMessage());
                }
            }
        });

        socket.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onData(cId, bb.getAllByteArray());
                }
            }
        });

        socket.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    TcpSocketListener listener = mListener.get();
                    if (listener != null) {
                        listener.onError(cId, ex.getMessage());
                    }
                }
                socket.close();
            }
        });
    }

    public void listen(final Integer cId, final String host, final Integer port)
            throws UnknownHostException, IOException {
        // resolve the address
        final InetSocketAddress socketAddress;
        if (host != null) {
            socketAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        } else {
            socketAddress = new InetSocketAddress(port);
        }

        mServer.listen(InetAddress.getByName(host), port, new ListenCallback() {
            @Override
            public void onListening(AsyncServerSocket socket) {
                mClients.put(cId, socket);

                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onConnect(cId, socketAddress);
                }
            }

            @Override
            public void onAccepted(AsyncSocket socket) {
                setSocketCallbacks(mInstances, socket);
                mClients.put(mInstances, socket);

                AsyncNetworkSocket socketConverted = Util.getWrappedSocket(socket, AsyncNetworkSocket.class);
                InetSocketAddress remoteAddress = socketConverted != null ? socketConverted.getRemoteAddress()
                        : socketAddress;

                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onConnection(cId, mInstances, remoteAddress);
                }

                mInstances++;
            }

            @Override
            public void onCompleted(Exception ex) {
                mClients.delete(cId);

                TcpSocketListener listener = mListener.get();
                if (listener != null) {
                    listener.onClose(cId, ex != null ? ex.getMessage() : null);
                }
            }
        });
    }

    public void connect(final Integer cId, final @Nullable String host, final Integer port, final boolean useTls)
            throws UnknownHostException, IOException {
        // resolve the address
        final InetSocketAddress socketAddress;
        if (host != null) {
            socketAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        } else {
            socketAddress = new InetSocketAddress(port);
        }

        mServer.connectSocket(socketAddress, new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (useTls) {
                    try {
                        // critical extension 2.5.29.15 is implemented improperly prior to 4.0.3.
                        // https://code.google.com/p/android/issues/detail?id=9307
                        // https://groups.google.com/forum/?fromgroups=#!topic/netty/UCfqPPk5O4s
                        // certs that use this extension will throw in Cipher.java.
                        // fallback is to use a custom SSLContext, and hack around the x509 extension.
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                            throw new Exception();
                        defaultSSLContext = SSLContext.getInstance("Default");
                    } catch (Exception ex2) {
                        try {
                            defaultSSLContext = SSLContext.getInstance("TLS");
                            TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                    return new X509Certificate[0];
                                }

                                public void checkClientTrusted(java.security.cert.X509Certificate[] certs,
                                        String authType) {
                                }

                                public void checkServerTrusted(java.security.cert.X509Certificate[] certs,
                                        String authType) {
                                    for (X509Certificate cert : certs) {
                                        if (cert != null && cert.getCriticalExtensionOIDs() != null)
                                            cert.getCriticalExtensionOIDs().remove("2.5.29.15");
                                    }
                                }
                            } };
                            defaultSSLContext.init(null, trustAllCerts, null);
                        } catch (Exception ex3) {
                            ex2.printStackTrace();
                            ex3.printStackTrace();
                        }
                    }

                    try {
                        trustAllSSLContext = SSLContext.getInstance("TLS");
                        trustAllManagers = new TrustManager[] { new X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }

                            public void checkClientTrusted(java.security.cert.X509Certificate[] certs,
                                    String authType) {
                            }

                            public void checkServerTrusted(java.security.cert.X509Certificate[] certs,
                                    String authType) {
                            }
                        } };
                        trustAllSSLContext.init(null, trustAllManagers, null);
                        // trustAllVerifier = (hostname, session) -> true;
                    } catch (Exception ex2) {
                        ex2.printStackTrace();
                    }
                    AsyncSSLSocketWrapper.handshake(socket, socketAddress.getHostName(), socketAddress.getPort(),
                            trustAllSSLContext.createSSLEngine(), trustAllManagers, new HostnameVerifier() {
                                @Override
                                public boolean verify(String s, SSLSession sslSession) {
                                    return true;
                                }
                            }, true, new AsyncSSLSocketWrapper.HandshakeCallback() {
                                @Override
                                public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                                    onConnectionCompleted(e, socket, cId, socketAddress);
                                }
                            });
                } else {
                    onConnectionCompleted(ex, socket, cId, socketAddress);
                }
            }
        });
    }

    private void onConnectionCompleted(Exception ex, AsyncSocket socket, Integer cId, InetSocketAddress socketAddress) {
        TcpSocketListener listener = mListener.get();
        mClients.put(cId, socket);
        if (ex == null) {
            setSocketCallbacks(cId, socket);

            if (listener != null) {
                listener.onConnect(cId, socketAddress);
            }
        } else if (listener != null) {
            listener.onError(cId, "unable to open socket: " + ex);
            close(cId);
        } else {
            close(cId);
        }
    }

    public void upgradeToSecure(final Integer cId, String host, Integer port, final Callback callback) {
        Object existingSocket = mClients.get(cId);
        if (existingSocket != null && existingSocket instanceof AsyncSocket) {
            AsyncSSLSocketWrapper.handshake((AsyncSocket) existingSocket, host, port,
                    AsyncSSLSocketWrapper.getDefaultSSLContext().createSSLEngine(), null, null, true,
                    new AsyncSSLSocketWrapper.HandshakeCallback() {
                        @Override
                        public void onHandshakeCompleted(Exception ex, AsyncSSLSocket upgradedSocket) {
                            TcpSocketListener listener = mListener.get();
                            mClients.put(cId, upgradedSocket);
                            if (ex == null) {
                                setSocketCallbacks(cId, upgradedSocket);
                                if (listener != null) {
                                    listener.onSecureConnect(cId);
                                }
                                if (callback != null) {
                                    callback.invoke();
                                }
                            } else if (listener != null) {
                                listener.onError(cId, "unable to upgrade socket to tls: " + ex);
                                close(cId);
                            } else {
                                close(cId);
                            }
                        }
                    });
        }
    }

    public void write(final Integer cId, final byte[] data) {
        Object socket = mClients.get(cId);
        if (socket != null && socket instanceof AsyncSocket) {
            ((AsyncSocket) socket).write(new ByteBufferList(data));
        }
    }

    public void close(final Integer cId) {
        Object socket = mClients.get(cId);
        if (socket != null) {
            if (socket instanceof AsyncSocket) {
                ((AsyncSocket) socket).close();
            } else if (socket instanceof AsyncServerSocket) {
                ((AsyncServerSocket) socket).stop();
            }
        } else {
            TcpSocketListener listener = mListener.get();
            if (listener != null) {
                listener.onError(cId, "unable to find socket");
            }
        }
    }

    public void closeAllSockets() {
        for (int i = 0; i < mClients.size(); i++) {
            close(mClients.keyAt(i));
        }
        mClients.clear();
    }
}
