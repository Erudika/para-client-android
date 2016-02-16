/*
 * Copyright 2013-2016 Erudika. http://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.client.utils;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

/**
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ParaRequest<T> extends Request<T> {

    private static final Logger logger = LoggerFactory.getLogger(ParaRequest.class);

    private final Map<String, String> headers;
    private final Response.Listener<T> listener;
    private final Response.ErrorListener errorListener;
    private final byte[] body;
    private final Class<T> type;
    private String url;

    /**
     * Make an API request and return a parsed object from JSON.
     * @param url URL of the request to make
     * @param headers Map of request headers
     * @param jsonEntity request body
     * @param entityType the type to return when JSON is deserialized
     * @param successListener success listener
     * @param errorListener error listener
     */
    public ParaRequest(int method, String url, Map<String, String> headers,
                       byte[] jsonEntity, Class<T> entityType,
                       Response.Listener<T> successListener, Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.headers = headers;
        this.listener = successListener;
        this.errorListener = errorListener;
        this.body = jsonEntity;
        this.type = entityType;

        this.url = url;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers != null ? headers : super.getHeaders();
    }

    @Override
    protected void deliverResponse(T response) {
        if (listener != null) {
            listener.onResponse(response);
        }
    }

    @Override
    public void deliverError(VolleyError error) {
        if (errorListener != null) {
            errorListener.onErrorResponse(error);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            if (response != null && response.data != null && response.data.length > 0) {
                if (ClientUtils.isBasicType(type)) {
                    return (Response<T>) Response.success(new String(response.data, "UTF-8"),
                            HttpHeaderParser.parseCacheHeaders(response));
                } else {
                    return Response.success((T) ClientUtils.getJsonReader(type).
                            readValue(response.data), HttpHeaderParser.parseCacheHeaders(response));
                }
            } else {
                return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
            }
        } catch (Exception e) {
            logger.error("JSON parsing error", e);
            return Response.error(new ParseError(e));
        }
    }

    @Override
    public String getBodyContentType() {
        return "application/json; charset=utf-8";
    }

    @Override
    public byte[] getBody() {
        return body;
    }
}