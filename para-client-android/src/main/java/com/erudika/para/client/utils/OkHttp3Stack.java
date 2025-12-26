/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Circle Internet Financial
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.erudika.para.client.utils;

import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.toolbox.BaseHttpStack;
import com.android.volley.toolbox.HttpResponse;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp backed
 */
public class OkHttp3Stack extends BaseHttpStack {

    private final boolean trustAllCertificates;
    private static OkHttpClient client = new OkHttpClient();

    public OkHttp3Stack() {
        this(false);
    }

    /**
     * @param trustAllCertificates if true all HTTPS certs will be trusted
     */
    public OkHttp3Stack(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    @Override
    public HttpResponse executeRequest(Request<?> request,
                                       Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {

        OkHttpClient.Builder clientBuilder = client.newBuilder();
        int timeoutMs = request.getTimeoutMs();

        clientBuilder.connectTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        clientBuilder.readTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        clientBuilder.writeTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        if (trustAllCertificates && request.getUrl().startsWith("https")) {
            X509TrustManager naiveTrustManager = new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    X509Certificate[] myTrustedAnchors = new X509Certificate[0];
                    return myTrustedAnchors;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            };

            try {
                clientBuilder.sslSocketFactory(SSLContext.getDefault().
                        getSocketFactory(), naiveTrustManager);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        okhttp3.Request.Builder okHttpRequestBuilder = new okhttp3.Request.Builder();
        okHttpRequestBuilder.url(request.getUrl());

        Map<String, String> headers = request.getHeaders();
        for(final String name : headers.keySet()) {
            if (headers.get(name) != null) {
                okHttpRequestBuilder.addHeader(name, headers.get(name));
            }
        }
        for(final String name : additionalHeaders.keySet()) {
            if (additionalHeaders.get(name) != null) {
                okHttpRequestBuilder.addHeader(name, additionalHeaders.get(name));
            }
        }

        setConnectionParametersForRequest(okHttpRequestBuilder, request);

        OkHttpClient client = clientBuilder.build();
        okhttp3.Request okHttpRequest = okHttpRequestBuilder.build();
        Call okHttpCall = client.newCall(okHttpRequest);
        Response okHttpResponse = okHttpCall.execute();

        int code = okHttpResponse.code();
        ResponseBody body = okHttpResponse.body();
        InputStream content = body == null ? null : body.byteStream();
        int contentLength = body == null ? 0 : (int) body.contentLength();

        List<Header> responseHeaders = new ArrayList<Header>(okHttpResponse.headers().size());
        for(int i = 0, len = okHttpResponse.headers().size(); i < len; i++) {
            final String name = okHttpResponse.headers().name(i);
            responseHeaders.add(new Header(name, okHttpResponse.headers().value(i)));
        }

        return new HttpResponse(code, responseHeaders, contentLength, content);
    }

    private static void setConnectionParametersForRequest(okhttp3.Request.Builder builder, Request<?> request)
            throws AuthFailureError {
        switch (request.getMethod()) {
            case Request.Method.DEPRECATED_GET_OR_POST:
                // Ensure backwards compatibility.  Volley assumes a request with a null body is a GET.
                byte[] postBody = request.getBody();
                if (postBody != null) {
                    builder.post(RequestBody.create(postBody, MediaType.parse(request.getBodyContentType())));
                }
                break;
            case Request.Method.GET:
                builder.get();
                break;
            case Request.Method.DELETE:
                builder.delete();
                break;
            case Request.Method.POST:
                builder.post(createRequestBody(request));
                break;
            case Request.Method.PUT:
                builder.put(createRequestBody(request));
                break;
            case Request.Method.HEAD:
                builder.head();
                break;
            case Request.Method.OPTIONS:
                builder.method("OPTIONS", null);
                break;
            case Request.Method.TRACE:
                builder.method("TRACE", null);
                break;
            case Request.Method.PATCH:
                builder.patch(createRequestBody(request));
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    private static RequestBody createRequestBody(Request r) throws AuthFailureError {
        final byte[] body = r.getBody();
        if (body == null) {
            return null;
        }
        return RequestBody.create(body, MediaType.parse(r.getBodyContentType()));
    }
}