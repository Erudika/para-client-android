/*
 * Copyright 2013-2019 Erudika. https://erudika.com
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
package com.erudika.para.client;

import android.content.Context;
import static com.android.volley.Request.Method.*;
import com.android.volley.RequestQueue;
import static com.android.volley.Response.*;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.NoCache;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.erudika.para.client.utils.OkHttp3Stack;
import com.erudika.para.client.utils.Pager;
import com.erudika.para.client.utils.Signer;
import com.erudika.para.client.utils.ClientUtils;
import com.erudika.para.core.Constraint;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Java REST client for communicating with a Para API server.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SuppressWarnings("unchecked")
public final class ParaClient {

    private static final Logger logger = LoggerFactory.getLogger(ParaClient.class);
    private static final String DEFAULT_ENDPOINT = "https://paraio.com";
    private static final String DEFAULT_PATH = "/v1/";
    private static final String JWT_PATH = "/jwt_auth";
    private static final String SEPARATOR = ":";
    private String endpoint;
    private String path;
    private String accessKey;
    private String secretKey;
    private String tokenKey;
    private Long tokenKeyExpires;
    private Long tokenKeyNextRefresh;
    private final Signer signer = new Signer();
    private Context ctx;
    private String trustedHostname;
    private int requestTimeout;

    private RequestQueue requestQueue;

    public ParaClient(String accessKey, String secretKey, Context ctx) {
        this.ctx = ctx;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.requestQueue = getRequestQueue();
        this.tokenKey = ClientUtils.loadPref("tokenKey", ctx);
        String tke = ClientUtils.loadPref("tokenKeyExpires", ctx);
        String tknr = ClientUtils.loadPref("tokenKeyNextRefresh", ctx);
        this.tokenKeyExpires = (tke != null) ? Long.parseLong(tke) : null;
        this.tokenKeyNextRefresh = (tknr != null) ? Long.parseLong(tknr) : null;
        this.requestTimeout = NumberUtils.toInt(System.getProperty("para.client.timeout", "30"));
        if (StringUtils.isBlank(secretKey)) {
            logger.warn("Secret key not provided. Make sure you call 'signIn()' first.");
        }
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            if (ctx == null) {
                requestQueue = new RequestQueue(new NoCache(), new BasicNetwork(new OkHttp3Stack()));
            } else {
                if (StringUtils.isBlank(trustedHostname)) {
                    requestQueue = Volley.newRequestQueue(ctx.getApplicationContext(),
                            new OkHttp3Stack());
                } else {
                    requestQueue = Volley.newRequestQueue(ctx.getApplicationContext(),
                        new OkHttp3Stack(ClientUtils.newCustomSocketFactory(trustedHostname)));
                }
            }
        }
        return requestQueue;
    }

    /**
     * Disables the verification of TLS certificates for a given
     * hostname. Allows self-signed certificates. Use with caution.
     * @param trustedHostname a hostname to trust, e.g. 'example.com'
     */
    public void trustHostnameCertificates(String trustedHostname) {
        this.trustedHostname = trustedHostname;
    }

    /**
     * Returns the endpoint URL
     * @return the endpoint
     */
    public String getEndpoint() {
        if (StringUtils.isBlank(endpoint)) {
            return DEFAULT_ENDPOINT;
        } else {
            return endpoint;
        }
    }

    /**
     * Sets the host URL of the Para server.
     * @param endpoint the Para server location
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Sets the API request path
     * @param path a new path
     */
    public void setApiPath(String path) {
        this.path = path;
    }

    /**
     * Returns the API request path
     * @return the request path without parameters
     */
    public String getApiPath() {
        if (StringUtils.isBlank(path)) {
            return DEFAULT_PATH;
        } else {
            if (!path.endsWith("/")) {
                path += "/";
            }
            return path;
        }
    }

    /**
     * Returns the version of Para server.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void getServerVersion(final Listener<String> callback, ErrorListener... error) {
        invokeGet("", null, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                if (res == null || StringUtils.isBlank((String) res.get("version"))) {
                    callback.onResponse("unknown");
                } else {
                    callback.onResponse((String) res.get("version"));
                }
            }
        }, error);
    }

    /**
     * @return the version of Para server
     */
    public String getServerVersionSync() {
        Map<String, Object> res = invokeSyncGet("", null, Map.class);
        if (res == null || StringUtils.isBlank((String) res.get("version"))) {
            return "unknown";
        } else {
            return (String) res.get("version");
        }
    }

    /**
     * @return the JWT access token, or null if not signed in
     */
    public String getAccessToken() {
        return tokenKey;
    }

    /**
     * Sets the JWT access token.
     * @param token a valid token
     */
    public void setAccessToken(String token) {
        if (!StringUtils.isBlank(token)) {
            try {
                String payload = ClientUtils.base64dec(StringUtils.substringBetween(token, ".", "."));
                Map<String, Object> decoded = ClientUtils.getJsonMapper().readValue(payload, Map.class);
                if (decoded != null && decoded.containsKey("exp")) {
                    this.tokenKeyExpires = (Long) decoded.get("exp");
                    this.tokenKeyNextRefresh = (Long) decoded.get("refresh");
                }
            } catch (Exception ex) {
                this.tokenKeyExpires = null;
                this.tokenKeyNextRefresh = null;
            }
        }
        this.tokenKey = token;
    }

    /**
     * Clears the JWT token from memory, if such exists.
     */
    private void clearAccessToken() {
        tokenKey = null;
        tokenKeyExpires = null;
        tokenKeyNextRefresh = null;
        ClientUtils.clearPref("tokenKey", ctx);
        ClientUtils.clearPref("tokenKeyExpires", ctx);
        ClientUtils.clearPref("tokenKeyNextRefresh", ctx);
    }

    private void saveAccessToken(Map<?, ?> jwtData) {
        if (jwtData != null) {
            tokenKey = (String) jwtData.get("access_token");
            ClientUtils.savePref("tokenKey", tokenKey, ctx);

            tokenKeyExpires = (Long) jwtData.get("expires");
            ClientUtils.savePref("tokenKeyExpires", tokenKeyExpires != null ?
                    tokenKeyExpires.toString() : null, ctx);

            tokenKeyNextRefresh = (Long) jwtData.get("refresh");
            ClientUtils.savePref("tokenKeyNextRefresh", tokenKeyNextRefresh != null ?
                    tokenKeyNextRefresh.toString() : null, ctx);
        }
    }

    private String key(boolean refresh) {
        if (tokenKey != null) {
            if (refresh) {
                refreshToken(null);
            }
            return "Bearer " + tokenKey;
        }
        return secretKey;
    }

    private <T> void fail(Listener<T> callback, Object returnValue) {
        if (callback != null) {
            callback.onResponse((T) returnValue);
        }
    }

    private ErrorListener onError(ErrorListener... error) {
        if (error != null && error.length > 0) {
            return error[0];
        } else {
            return new ErrorListener() {
                public void onErrorResponse(VolleyError err) {
                    byte[] data = err.networkResponse != null ? err.networkResponse.data : null;
                    String msg = err.getMessage();
                    String errorType = err.getClass().getSimpleName();
                    Map<String, Object> error = data != null ? readEntity(Map.class, data) : null;
                    if (error != null && error.containsKey("code")) {
                        msg = error.containsKey("message") ? (String) error.get("message") : msg;
                        logger.error("{}:" + msg + " - {}", errorType, error.get("code"));
                    } else {
                        logger.error("{} - {} {}", err.networkResponse.statusCode, msg, errorType);
                    }
                }
            };
        }
    }

    /**
     * Deserializes a Response object to POJO of some type.
     * @param clazz type
     * @param data input stream
     * @param <T> type
     * @return ParaObject
     */
    public <T> T readEntity(Class<T> clazz, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return ClientUtils.getJsonReader(clazz).readValue(data);
        } catch (IOException e) {
            logger.error(null, e);
            return null;
        }
    }

    /**
     * @param resourcePath API subpath
     * @return the full resource path, e.g. "/v1/path"
     */
    protected String getFullPath(String resourcePath) {
        if (StringUtils.startsWith(resourcePath, JWT_PATH)) {
            return resourcePath;
        }
        if (resourcePath == null) {
            resourcePath = "";
        } else if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        return getApiPath() + resourcePath;
    }

    protected <T> T invokeSignedSyncRequest(int method, String resourcePath,
                                          Map<String, String> headers,
                                          Map<String, Object> params,
                                          Object entity, Class<T> returnType) {
        RequestFuture<T> future = RequestFuture.newFuture();
        ErrorListener error = onError();
        boolean refreshJWT = !(method == GET && JWT_PATH.equals(resourcePath));
        getRequestQueue().add(signer.invokeSignedRequest(accessKey, key(refreshJWT),
                method, getEndpoint(), getFullPath(resourcePath), headers, params,
                entity, returnType, future, future));
        try {
            return future.get(requestTimeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            error.onErrorResponse(new VolleyError(e));
        }
        return null;
    }

    protected <T> void invokeSignedRequest(int method, String resourcePath,
                                          Map<String, String> headers,
                                          Map<String, Object> params,
                                          Object entity, Class<T> returnType,
                                          Listener<?> success, ErrorListener... error) {
        boolean refreshJWT = !(method == GET && JWT_PATH.equals(resourcePath));
        getRequestQueue().add(signer.invokeSignedRequest(accessKey, key(refreshJWT),
                method, getEndpoint(), getFullPath(resourcePath), headers, params,
                entity, returnType, success, onError(error)));
    }

    /**
     * Invoke a GET request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param params query parameters
     * @param returnType type
     * @param success callback
     * @param error callback
     */
    public void invokeGet(String resourcePath, Map<String, Object> params, Class<?> returnType,
                           Listener<?> success, ErrorListener... error) {
        getRequestQueue().add(signer.invokeSignedRequest(accessKey, key(!JWT_PATH.equals(resourcePath)), GET,
                getEndpoint(), getFullPath(resourcePath), null, params, null, returnType,
                success, onError(error)));
    }

    /**
     * Invoke a POST request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param entity request body
     * @param returnType type
     * @param success callback
     * @param error callback
     */
    public void invokePost(String resourcePath, Object entity, Class<?> returnType,
                            Listener<?> success, ErrorListener... error) {
        getRequestQueue().add(signer.invokeSignedRequest(accessKey, key(false), POST,
                getEndpoint(), getFullPath(resourcePath), null, null, entity, returnType,
                success, onError(error)));
    }

    /**
     * Invoke a PUT request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param entity request body
     * @param returnType type
     * @param success callback
     * @param error callback
     */
    public void invokePut(String resourcePath, Object entity, Class<?> returnType,
                           Listener<?> success, ErrorListener... error) {
        getRequestQueue().add(signer.invokeSignedRequest(accessKey, key(false), PUT,
                getEndpoint(), getFullPath(resourcePath), null, null, entity, returnType,
                success, onError(error)));
    }

    /**
     * Invoke a PATCH request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param entity request body
     * @param returnType type
     * @param success callback
     * @param error callback
     */
    public void invokePatch(String resourcePath, Object entity, Class<?> returnType,
                             Listener<?> success, ErrorListener... error) {
        getRequestQueue().add(signer.invokeSignedRequest(accessKey, key(false), PATCH,
                getEndpoint(), getFullPath(resourcePath), null, null, entity, returnType,
                success, onError(error)));
    }

    /**
     * Invoke a DELETE request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param params query parameters
     * @param returnType type
     * @param success callback
     * @param error callback
     */
    public void invokeDelete(String resourcePath, Map<String, Object> params, Class<?> returnType,
                              Listener<?> success, ErrorListener... error) {
        getRequestQueue().add(signer.invokeSignedRequest(accessKey, key(false), DELETE,
                getEndpoint(), getFullPath(resourcePath), null, params, null, returnType,
                success, onError(error)));
    }

    /**
     * Invoke a GET request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param params query parameters
     * @param returnType type
     * @param <T> type
     * @return response
     */
    public <T> T invokeSyncGet(String resourcePath, Map<String, Object> params, Class<T> returnType) {
        return invokeSignedSyncRequest(GET, resourcePath, null, params, null, returnType);
    }

    /**
     * Invoke a POST request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param entity request body
     * @param returnType type
     * @param <T> type
     * @return response
     */
    public <T> T invokeSyncPost(String resourcePath, Object entity, Class<T> returnType) {
        return invokeSignedSyncRequest(POST, resourcePath, null, null, entity, returnType);
    }

    /**
     * Invoke a PUT request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param entity request body
     * @param returnType type
     * @param <T> type
     * @return response
     */
    public <T> T invokeSyncPut(String resourcePath, Object entity, Class<T> returnType) {
        return invokeSignedSyncRequest(PUT, resourcePath, null, null, entity, returnType);
    }

    /**
     * Invoke a PATCH request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param entity request body
     * @param returnType type
     * @param <T> type
     * @return response
     */
    public <T> T invokeSyncPatch(String resourcePath, Object entity, Class<T> returnType) {
        return invokeSignedSyncRequest(PATCH, resourcePath, null, null, entity, returnType);
    }

    /**
     * Invoke a DELETE request to the Para API.
     * @param resourcePath the subpath after '/v1/', should not start with '/'
     * @param params query parameters
     * @param returnType type
     * @param <T> type
     * @return response
     */
    public <T> T invokeSyncDelete(String resourcePath, Map<String, Object> params, Class<T> returnType) {
        return invokeSignedSyncRequest(DELETE, resourcePath, null, params, null, returnType);
    }

    /**
     * Converts a {@link Pager} object to query parameters.
     * @param pager a Pager
     * @return list of query parameters
     */
    public Map<String, Object> pagerToParams(Pager... pager) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (pager != null && pager.length > 0) {
            Pager p = pager[0];
            if (p != null) {
                map.put("page", Collections.singletonList(Long.toString(p.getPage())));
                map.put("desc", Collections.singletonList(Boolean.toString(p.isDesc())));
                map.put("limit", Collections.singletonList(Integer.toString(p.getLimit())));
                if (p.getLastKey() != null) {
                    map.put("lastKey", Collections.singletonList(p.getLastKey()));
                }
                if (p.getSortby() != null) {
                    map.put("sort", Collections.singletonList(p.getSortby()));
                }
            }
        }
        return map;
    }

    /**
     * Deserializes ParaObjects from a JSON array (the "items:[]" field in search results).
     * @param result a list of deserialized maps
     * @return a list of ParaObjects
     */
    public List<ParaObject> getItemsFromList(List<Map<String, Object>> result) {
        if (result != null && !result.isEmpty()) {
            // this isn't very efficient but there's no way to know what type of objects we're reading
            List<ParaObject> objects = new ArrayList<ParaObject>(result.size());
            for (Map<String, Object> map : result) {
                ParaObject p = ClientUtils.setFields(Sysprop.class, map);
                if (p != null) {
                    objects.add(p);
                }
            }
            return objects;
        }
        return Collections.emptyList();
    }

    /**
     * Converts a list of Maps to a List of ParaObjects, at a given path within the JSON tree structure.
     * @param <P> type
     * @param at the path (field) where the array of objects is located
     * @param result the response body for an API request
     * @param pager a {@link Pager} object
     * @return a list of ParaObjects
     */
    public <P extends ParaObject> List<P> getItems(String at, Map<String, Object> result, Pager... pager) {
        if (result != null && !result.isEmpty() && !StringUtils.isBlank(at) && result.containsKey(at)) {
            if (pager != null && pager.length > 0 && pager[0] != null) {
                if (result.containsKey("totalHits")) {
                    pager[0].setCount(((Integer) result.get("totalHits")).longValue());
                }
                if (result.containsKey("lastKey")) {
                    pager[0].setLastKey((String) result.get("lastKey"));
                }
            }
            return (List<P>) getItemsFromList((List<Map<String, Object>>) result.get(at));
        }
        return Collections.emptyList();
    }

    public <P extends ParaObject> List<P> getItems(Map<String, Object> result, Pager... pager) {
        return getItems("items", result, pager);
    }

    /////////////////////////////////////////////
    //				 PERSISTENCE
    /////////////////////////////////////////////

    /**
     * Persists an object to the data store. If the object's type and id are given,
     * then the request will be a {@code PUT} request and any existing object will be
     * overwritten.
     * @param obj the domain object
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void create(final ParaObject obj, Listener<? extends ParaObject> callback,
                       ErrorListener... error) {
        if (obj == null) {
            fail(callback, null);
            return;
        }
        if (StringUtils.isBlank(obj.getId()) || StringUtils.isBlank(obj.getType())) {
            invokePost(obj.getType(), obj, null, callback, error);
        } else {
            invokePut(obj.getType().concat("/").concat(obj.getId()), obj, null, callback, error);
        }
    }

    /**
     * Persists an object to the data store. If the object's type and id are given,
     * then the request will be a {@code PUT} request and any existing object will be
     * overwritten.
     * @param <P> the type of object
     * @param obj the domain object
     * @return the same object with assigned id or null if not created.
     */
    public <P extends ParaObject> P createSync(P obj) {
        if (obj == null) {
            return null;
        }
        if (StringUtils.isBlank(obj.getId()) || StringUtils.isBlank(obj.getType())) {
            return invokeSyncPost(obj.getType(), obj, null);
        } else {
            return invokeSyncPut(obj.getType().concat("/").concat(obj.getId()), obj, null);
        }
    }

    /**
     * Retrieves an object from the data store.
     * @param type the type of the object
     * @param id the id of the object
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void read(Class<? extends ParaObject> type, String id,
                     Listener<? extends ParaObject> callback,
                     ErrorListener... error) {
        if (type == null || StringUtils.isBlank(id)) {
            fail(callback, null);
            return;
        }
        invokeGet(type.getSimpleName().toLowerCase().concat("/").concat(id),
                null, type, callback, error);
    }

    /**
     * Retrieves an object from the data store.
     * @param <P> the type of object
     * @param type the type of the object
     * @param id the id of the object
     * @return the retrieved object or null if not found
     */
    public <P extends ParaObject> P readSync(Class<P> type, String id) {
        if (type == null || StringUtils.isBlank(id)) {
            return null;
        }
        return invokeSyncGet(type.getSimpleName().toLowerCase().concat("/").concat(id), null, type);
    }

    /**
     * Retrieves an object from the data store.
     * @param id the id of the object
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void read(String id, Listener<? extends ParaObject> callback,
                     ErrorListener... error) {
        if (StringUtils.isBlank(id)) {
            fail(callback, null);
            return;
        }
        invokeGet("_id/".concat(id), null, Sysprop.class, callback, error);
    }

    /**
     * Retrieves an object from the data store.
     * @param <P> the type of object
     * @param id the id of the object
     * @return the retrieved object or null if not found
     */
    public <P extends ParaObject> P readSync(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        return (P) invokeSyncGet("_id/".concat(id), null, Sysprop.class);
    }

    /**
     * Updates an object permanently. Supports partial updates.
     * @param obj the object to update
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void update(ParaObject obj, Listener<? extends ParaObject> callback,
                       ErrorListener... error) {
        if (obj == null) {
            fail(callback, null);
            return;
        }
        invokePatch(obj.getObjectURI(), obj, null, callback, error);
    }

    /**
     * Updates an object permanently. Supports partial updates.
     * @param <P> the type of object
     * @param obj the object to update
     * @return the updated object
     */
    public <P extends ParaObject> P updateSync(P obj) {
        if (obj == null) {
            return null;
        }
        return invokeSyncPatch(obj.getObjectURI(), obj, null);
    }

    /**
     * Deletes an object permanently.
     * @param obj the object
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void delete(ParaObject obj, Listener<? extends ParaObject> callback,
                       ErrorListener... error) {
        if (obj == null) {
            fail(callback, null);
            return;
        }
        invokeDelete(obj.getObjectURI(), null, obj.getClass(), callback, error);
    }

    /**
     * Deletes an object permanently.
     * @param <P> the type of object
     * @param obj the object
     */
    public <P extends ParaObject> void deleteSync(P obj) {
        if (obj == null) {
            return;
        }
        invokeSyncDelete(obj.getObjectURI(), null, obj.getClass());
    }

    /**
     * Saves multiple objects to the data store.
     * @param objects the list of objects to save
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void createAll(List<ParaObject> objects, final Listener<List<ParaObject>> callback,
                          ErrorListener... error) {
        if (objects == null || objects.isEmpty() || objects.get(0) == null) {
            fail(callback, Collections.emptyList());
            return;
        }
        invokePost("_batch", objects, null, new Listener<List<Map<String, Object>>>() {
            public void onResponse(List<Map<String, Object>> res) {
                callback.onResponse(getItemsFromList(res));
            }
        }, error);
    }

    /**
     * Saves multiple objects to the data store.
     * @param <P> the type of object
     * @param objects the list of objects to save
     * @return a list of objects
     */
    public <P extends ParaObject> List<P> createAllSync(List<P> objects) {
        if (objects == null || objects.isEmpty() || objects.get(0) == null) {
            return Collections.emptyList();
        }
        return getItemsFromList(invokeSyncPost("_batch", objects, List.class));
    }

    /**
     * Retrieves multiple objects from the data store.
     * @param keys a list of object ids
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void readAll(List<String> keys, final Listener<List<ParaObject>> callback,
                        ErrorListener... error) {
        if (keys == null || keys.isEmpty()) {
            fail(callback, Collections.emptyList());
            return;
        }
        Map<String, Object> ids = new HashMap<String, Object>();
        ids.put("ids", keys);
        invokeGet("_batch", ids, List.class, new Listener<List<Map<String, Object>>>() {
            public void onResponse(List<Map<String, Object>> res) {
                callback.onResponse(getItemsFromList(res));
            }
        }, error);
    }

    /**
     * Retrieves multiple objects from the data store.
     * @param <P> the type of object
     * @param keys a list of object ids
     * @return a list of objects
     */
    public <P extends ParaObject> List<P> readAllSync(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> ids = new HashMap<String, Object>();
        ids.put("ids", keys);
        return getItemsFromList(invokeSyncGet("_batch", ids, List.class));
    }

    /**
     * Updates multiple objects.
     * @param objects the objects to update
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void updateAll(List<ParaObject> objects, final Listener<List<ParaObject>> callback,
                          ErrorListener... error) {
        if (objects == null || objects.isEmpty()) {
            fail(callback, Collections.emptyList());
            return;
        }
        invokePatch("_batch", objects, null, new Listener<List<Map<String, Object>>>() {
            public void onResponse(List<Map<String, Object>> res) {
                callback.onResponse(getItemsFromList(res));
            }
        }, error);
    }

    /**
     * Updates multiple objects.
     * @param <P> the type of object
     * @param objects the objects to update
     * @return a list of objects
     */
    public <P extends ParaObject> List<P> updateAllSync(List<P> objects) {
        if (objects == null || objects.isEmpty()) {
            return Collections.emptyList();
        }
        return getItemsFromList(invokeSyncPatch("_batch", objects, List.class));
    }

    /**
     * Deletes multiple objects.
     * @param keys the ids of the objects to delete
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void deleteAll(List<String> keys, Listener<List<ParaObject>> callback,
                          ErrorListener... error) {
        if (keys == null || keys.isEmpty()) {
            fail(callback, null);
            return;
        }
        Map<String, Object> ids = new HashMap<String, Object>();
        ids.put("ids", keys);
        invokeDelete("_batch", ids, Map.class, callback, error);
    }

    /**
     * Deletes multiple objects.
     * @param keys the ids of the objects to delete
     */
    public void deleteAllSync(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        Map<String, Object> ids = new HashMap<String, Object>();
        ids.put("ids", keys);
        invokeSyncDelete("_batch", ids, null);
    }

    /**
     * Returns a list all objects found for the given type.
     * The result is paginated so only one page of items is returned, at a time.
     * @param type the type of objects to search for
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void list(String type, final Pager pager, final Listener<List<ParaObject>> callback,
                     ErrorListener... error) {
        if (StringUtils.isBlank(type)) {
            fail(callback, Collections.emptyList());
            return;
        }
        invokeGet(type, pagerToParams(pager), Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Returns a list all objects found for the given type.
     * The result is paginated so only one page of items is returned, at a time.
     * @param <P> the type of object
     * @param type the type of objects to search for
     * @param pager a {@link Pager}
     * @return a list of objects
     */
    public <P extends ParaObject> List<P> listSync(String type, Pager... pager) {
        if (StringUtils.isBlank(type)) {
            return Collections.emptyList();
        }
        return getItems(invokeSyncGet(type, pagerToParams(pager), Map.class), pager);
    }

    /////////////////////////////////////////////
    //				 SEARCH
    /////////////////////////////////////////////

    /**
     * Simple id search.
     * @param id the id
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findById(String id, final Listener<ParaObject> callback,
                         ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        find("id", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                List<ParaObject> list = getItems(res);
                callback.onResponse(list.isEmpty() ? null : list.get(0));
            }
        }, error);

    }

    /**
     * Simple id search.
     * @param <P> type of the object
     * @param id the id
     * @return the object if found or null
     */
    public <P extends ParaObject> P findByIdSync(String id) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("id", id);
        List<P> list = getItems(findSync("id", params));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Simple multi id search.
     * @param ids a list of ids to search for
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findByIds(List<String> ids, final Listener<List<ParaObject>> callback,
                          ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ids", ids);
        find("ids", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res));
            }
        }, error);
    }

    /**
     * Simple multi id search.
     * @param <P> type of the object
     * @param ids a list of ids to search for
     * @return a list of object found
     */
    public <P extends ParaObject> List<P> findByIdsSync(List<String> ids) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ids", ids);
        return getItems(findSync("ids", params));
    }

    /**
     * Search for Address objects in a radius of X km from a given point.
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param query the query string
     * @param radius the radius of the search circle
     * @param lat latitude
     * @param lng longitude
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findNearby(String type, String query, int radius, double lat, double lng,
                           final Pager pager, final Listener<List<ParaObject>> callback,
                           ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("latlng", lat + "," + lng);
        params.put("radius", Integer.toString(radius));
        params.put("q", query);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        find("nearby", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Search for Address objects in a radius of X km from a given point.
     * @param <P> type of the object
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param query the query string
     * @param radius the radius of the search circle
     * @param lat latitude
     * @param lng longitude
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findNearbySync(String type, String query, int radius,
                                                     double lat, double lng, Pager... pager) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("latlng", lat + "," + lng);
        params.put("radius", Integer.toString(radius));
        params.put("q", query);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        return getItems(findSync("nearby", params), pager);
    }

    /**
     * Searches for objects that have a property which value starts with a given prefix.
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param field the property name of an object
     * @param prefix the prefix
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findPrefix(String type, String field, String prefix, final Pager pager,
                           final Listener<List<ParaObject>> callback,
                           ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("field", field);
        params.put("prefix", prefix);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        find("prefix", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Searches for objects that have a property which value starts with a given prefix.
     * @param <P> type of the object
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param field the property name of an object
     * @param prefix the prefix
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findPrefixSync(String type, String field,
                                                     String prefix, Pager... pager) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("field", field);
        params.put("prefix", prefix);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        return getItems(findSync("prefix", params), pager);
    }

    /**
     * Simple query string search. This is the basic search method.
     * @param type the type of object to search for. See {@link Sysprop#getType()}
     * @param query the query string
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findQuery(String type, String query, final Pager pager,
                          final Listener<List<ParaObject>> callback,
                          ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("q", query);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        find("", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Simple query string search. This is the basic search method.
     * @param <P> type of the object
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param query the query string
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findQuerySync(String type, String query, Pager... pager) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("q", query);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        return getItems(findSync("", params), pager);
    }

    /**
     * Searches within a nested field. The objects of the given type must contain a nested field "nstd".
     * @param type the type of object to search for. See {@link Sysprop#getType()}
     * @param field field the name of the field to target (within a nested field "nstd")
     * @param query the query string
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findNestedQuery(String type, String field, String query, final Pager pager,
                          final Listener<List<ParaObject>> callback,
                          ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("q", query);
        params.put("field", field);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        find("nested", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Searches within a nested field. The objects of the given type must contain a nested field "nstd".
     * @param <P> type of the object
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param field field the name of the field to target (within a nested field "nstd")
     * @param query the query string
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findNestedQuerySync(String type, String field,
                                                        String query, Pager... pager) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("q", query);
        params.put("field", field);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        return getItems(findSync("nested", params), pager);
    }

    /**
     * Searches for objects that have similar property values to a given text.
     * A "find like this" query.
     * @param type the type of object to search for. See {@link Sysprop#getType()}
     * @param filterKey exclude an object with this key from the results (optional)
     * @param fields a list of property names
     * @param liketext text to compare to
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findSimilar(String type, String filterKey, String[] fields, String liketext,
                            final Pager pager, final Listener<List<ParaObject>> callback,
                            ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("fields", fields == null ? null : Arrays.asList(fields));
        params.put("filterid", filterKey);
        params.put("like", liketext);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        find("similar", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Searches for objects that have similar property values to a given text.
     * A "find like this" query.
     * @param <P> type of the object
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param filterKey exclude an object with this key from the results (optional)
     * @param fields a list of property names
     * @param liketext text to compare to
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findSimilarSync(String type, String filterKey, String[] fields,
                                                      String liketext, Pager... pager) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("fields", fields == null ? null : Arrays.asList(fields));
        params.put("filterid", filterKey);
        params.put("like", liketext);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        return getItems(findSync("similar", params), pager);
    }

    /**
     * Searches for objects tagged with one or more tags.
     * @param type the type of object to search for. See {@link Sysprop#getType()}
     * @param tags the list of tags
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findTagged(String type, String[] tags, final Pager pager,
                           final Listener<List<ParaObject>> callback,
                           ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("tags", tags == null ? null : Arrays.asList(tags));
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        find("tagged", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Searches for objects tagged with one or more tags.
     * @param <P> type of the object
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param tags the list of tags
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findTaggedSync(String type, String[] tags, Pager... pager) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("tags", tags == null ? null : Arrays.asList(tags));
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        return getItems(findSync("tagged", params), pager);
    }

    /**
     * Searches for Tag objects.
     * This method might be deprecated in the future.
     * @param keyword the tag keyword to search for
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findTags(String keyword, Pager pager, Listener<List<ParaObject>> callback,
                         ErrorListener... error) {
        keyword = (keyword == null) ? "*" : keyword.concat("*");
        findWildcard("tag", "tag", keyword, pager, callback, error);
    }

    /**
     * Searches for Tag objects.
     * This method might be deprecated in the future.
     * @param <P> type of the object
     * @param keyword the tag keyword to search for
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findTagsSync(String keyword, Pager... pager) {
        keyword = (keyword == null) ? "*" : keyword.concat("*");
        return findWildcardSync("tag", "tag", keyword, pager);
    }

    /**
     * Searches for objects having a property value that is in list of possible values.
     * @param type the type of object to search for. See {@link Sysprop#getType()}
     * @param field the property name of an object
     * @param terms a list of terms (property values)
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findTermInList(String type, String field, List<String> terms,
                               final Pager pager, final Listener<List<ParaObject>> callback,
                               ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("field", field);
        params.put("terms", terms);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        find("in", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Searches for objects having a property value that is in list of possible values.
     * @param <P> type of the object
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param field the property name of an object
     * @param terms a list of terms (property values)
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findTermInListSync(String type, String field,
                                                             List<String> terms, Pager... pager) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("field", field);
        params.put("terms", terms);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        return getItems(findSync("in", params), pager);
    }

    /**
     * Searches for objects that have properties matching some given values. A terms query.
     * @param type the type of object to search for. See {@link Sysprop#getType()}
     * @param terms a map of fields (property names) to terms (property values)
     * @param matchAll match all terms. If true - AND search, if false - OR search
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findTerms(String type, Map<String, ?> terms, boolean matchAll,
                          final Pager pager, final Listener<List<ParaObject>> callback,
                          ErrorListener... error) {
        if (terms == null) {
            fail(callback, Collections.emptyList());
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("matchall", Boolean.toString(matchAll));
        LinkedList<String> list = new LinkedList<String>();
        for (Map.Entry<String, ?> term : terms.entrySet()) {
            String key = term.getKey();
            Object value = term.getValue();
            if (value != null) {
                list.add(key.concat(SEPARATOR).concat(value.toString()));
            }
        }
        if (!terms.isEmpty()) {
            params.put("terms", list);
        }
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        find("terms", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Searches for objects that have properties matching some given values. A terms query.
     * @param <P> type of the object
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param terms a map of fields (property names) to terms (property values)
     * @param matchAll match all terms. If true - AND search, if false - OR search
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findTermsSync(String type, Map<String, ?> terms,
                                                        boolean matchAll, Pager... pager) {
        if (terms == null) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("matchall", Boolean.toString(matchAll));
        LinkedList<String> list = new LinkedList<String>();
        for (Map.Entry<String, ?> term : terms.entrySet()) {
            String key = term.getKey();
            Object value = term.getValue();
            if (value != null) {
                list.add(key.concat(SEPARATOR).concat(value.toString()));
            }
        }
        if (!terms.isEmpty()) {
            params.put("terms", list);
        }
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        return getItems(findSync("terms", params), pager);
    }

    /**
     * Searches for objects that have a property with a value matching a wildcard query.
     * @param type the type of object to search for. See {@link Sysprop#getType()}
     * @param field the property name of an object
     * @param wildcard wildcard query string. For example "cat*".
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findWildcard(String type, String field, String wildcard,
                             final Pager pager, final Listener<List<ParaObject>> callback,
                             ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("field", field);
        params.put("q", wildcard);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        find("wildcard", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Searches for objects that have a property with a value matching a wildcard query.
     * @param <P> type of the object
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param field the property name of an object
     * @param wildcard wildcard query string. For example "cat*".
     * @param pager a {@link Pager}
     * @return a list of objects found
     */
    public <P extends ParaObject> List<P> findWildcardSync(String type, String field,
                                                           String wildcard, Pager... pager) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("field", field);
        params.put("q", wildcard);
        params.put("type", type);
        params.putAll(pagerToParams(pager));
        return getItems(findSync("wildcard", params), pager);
    }

    /**
     * Counts indexed objects.
     * @param type the type of object to search for. See {@link Sysprop#getType()}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void getCount(String type, final Listener<Long> callback,
                         ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("type", type);
        final Pager pager = new Pager();
        find("count", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                getItems(res, pager);
                callback.onResponse(pager.getCount());
            }
        }, error);
    }

    /**
     * Counts indexed objects.
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @return the number of results found
     */
    public Long getCountSync(String type) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("type", type);
        Pager pager = new Pager();
        getItems(findSync("count", params), pager);
        return pager.getCount();
    }

    /**
     * Counts indexed objects matching a set of terms/values.
     * @param type the type of object to search for. See {@link Sysprop#getType()}
     * @param terms a list of terms (property values)
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void getCount(String type, Map<String, ?> terms, final Listener<Long> callback,
                         ErrorListener... error) {
        if (terms == null) {
            fail(callback, 0L);
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        LinkedList<String> list = new LinkedList<String>();
        for (Map.Entry<String, ?> term : terms.entrySet()) {
            String key = term.getKey();
            Object value = term.getValue();
            if (value != null) {
                list.add(key.concat(SEPARATOR).concat(value.toString()));
            }
        }
        if (!terms.isEmpty()) {
            params.put("terms", list);
        }
        params.put("type", type);
        params.put("count", "true");
        final Pager pager = new Pager();
        find("terms", params, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                getItems(res, pager);
                callback.onResponse(pager.getCount());
            }
        }, error);
    }

    /**
     * Counts indexed objects matching a set of terms/values.
     * @param type the type of object to search for. See {@link ParaObject#getType()}
     * @param terms a list of terms (property values)
     * @return the number of results found
     */
    public Long getCountSync(String type, Map<String, ?> terms) {
        if (terms == null) {
            return 0L;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        LinkedList<String> list = new LinkedList<String>();
        for (Map.Entry<String, ?> term : terms.entrySet()) {
            String key = term.getKey();
            Object value = term.getValue();
            if (value != null) {
                list.add(key.concat(SEPARATOR).concat(value.toString()));
            }
        }
        if (!terms.isEmpty()) {
            params.put("terms", list);
        }
        params.put("type", type);
        params.put("count", "true");
        Pager pager = new Pager();
        getItems(findSync("terms", params), pager);
        return pager.getCount();
    }

    private void find(String queryType, Map<String, Object> params,
                      Listener<Map<String, Object>> callback, ErrorListener... error) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (params != null && !params.isEmpty()) {
            String qType = StringUtils.isBlank(queryType) ? "/default" : "/".concat(queryType);
            if (!params.containsKey("type") || StringUtils.isBlank((String) params.get("type"))) {
                invokeGet("search".concat(qType), params, Map.class, callback, error);
            } else {
                invokeGet(params.get("type") + "/search" + qType, params, Map.class, callback, error);
            }
            return;
        } else {
            map.put("items", Collections.emptyList());
            map.put("totalHits", 0);
        }
        callback.onResponse(map);
    }

    private Map<String, Object> findSync(String queryType, Map<String, Object> params) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (params != null && !params.isEmpty()) {
            String qType = StringUtils.isBlank(queryType) ? "/default" : "/".concat(queryType);
            if (!params.containsKey("type") || StringUtils.isBlank((String) params.get("type"))) {
                return invokeSyncGet("search".concat(qType), params, Map.class);
            } else {
                return invokeSyncGet(params.get("type") + "/search" + qType, params, Map.class);
            }
        } else {
            map.put("items", Collections.emptyList());
            map.put("totalHits", 0);
        }
        return map;
    }

    /////////////////////////////////////////////
    //				 LINKS
    /////////////////////////////////////////////

    /**
     * Count the total number of links between this object and another type of object.
     * @param type2 the other type of object
     * @param obj the object to execute this method on
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void countLinks(ParaObject obj, String type2, final Listener<Long> callback,
                           ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null) {
            fail(callback, 0L);
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("count", "true");
        final Pager pager = new Pager();
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeGet(url, params, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> map) {
                getItems(map, pager);
                callback.onResponse(pager.getCount());
            }
        }, error);
    }

    /**
     * Count the total number of links between this object and another type of object.
     * @param type2 the other type of object
     * @param obj the object to execute this method on
     * @return the number of links for the given object
     */
    public Long countLinksSync(ParaObject obj, String type2) {
        if (obj == null || obj.getId() == null || type2 == null) {
            return 0L;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("count", "true");
        Pager pager = new Pager();
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        getItems(invokeSyncGet(url, params, Map.class), pager);
        return pager.getCount();
    }

    /**
     * Returns all objects linked to the given one.
     * Only applicable to many-to-many relationships.
     * @param type2 type of linked objects to search for
     * @param obj the object to execute this method on
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void getLinkedObjects(ParaObject obj, String type2, final Pager pager,
                                final Listener<List<ParaObject>> callback,
                                ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null) {
            fail(callback, Collections.emptyList());
            return;
        }
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeGet(url, pagerToParams(pager), Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Returns all objects linked to the given one. Only applicable to many-to-many relationships.
     * @param <P> type of linked objects
     * @param type2 type of linked objects to search for
     * @param obj the object to execute this method on
     * @param pager a {@link Pager}
     * @return a list of linked objects
     */
    public <P extends ParaObject> List<P> getLinkedObjectsSync(ParaObject obj, String type2,
                                                               Pager... pager) {
        if (obj == null || obj.getId() == null || type2 == null) {
            return Collections.emptyList();
        }
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        return getItems(invokeSyncGet(url, pagerToParams(pager), Map.class), pager);
    }

    /**
     * Searches through all linked objects in many-to-many relationships.
     * @param obj the object to execute this method on
     * @param type2 type of linked objects to search for
     * @param field field the name of the field to target (within a nested field "nstd")
     * @param query the query string
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findLinkedObjects(ParaObject obj, String type2, String field, String query,
                                  final Pager pager, final Listener<List<ParaObject>> callback,
                                  ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null) {
            fail(callback, Collections.emptyList());
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("field", field);
        params.put("q", (query == null) ? "*" : query);
        params.putAll(pagerToParams(pager));
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeGet(url, params, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Searches through all linked objects in many-to-many relationships.
     * @param <P> type of linked objects
     * @param obj the object to execute this method on
     * @param type2 type of linked objects to search for
     * @param field field the name of the field to target (within a nested field "nstd")
     * @param query the query string
     * @param pager a {@link Pager}
     * @return a list of linked objects
     */
    public <P extends ParaObject> List<P> findLinkedObjectsSync(ParaObject obj, String type2,
                                                                String field, String query,
                                                                Pager... pager) {
        if (obj == null || obj.getId() == null || type2 == null) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("field", field);
        params.put("q", (query == null) ? "*" : query);
        params.putAll(pagerToParams(pager));
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        return getItems(invokeSyncGet(url, params, Map.class), pager);
    }

    /**
     * Checks if this object is linked to another.
     * @param type2 the other type
     * @param id2 the other id
     * @param obj the object to execute this method on
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void isLinked(ParaObject obj, String type2, String id2,
                            final Listener<Boolean> callback, ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null || id2 == null) {
            fail(callback, false);
            return;
        }
        String url = ClientUtils.formatMessage("{0}/links/{1}/{2}", obj.getObjectURI(), type2, id2);
        invokeGet(url, null, String.class, new Listener<String>() {
            public void onResponse(String res) {
                callback.onResponse(res == null ? false : Boolean.parseBoolean(res));
            }
        }, error);
    }

    /**
     * Checks if this object is linked to another.
     * @param type2 the other type
     * @param id2 the other id
     * @param obj the object to execute this method on
     * @return true if the two are linked
     */
    public boolean isLinkedSync(ParaObject obj, String type2, String id2) {
        if (obj == null || obj.getId() == null || type2 == null || id2 == null) {
            return false;
        }
        String url = ClientUtils.formatMessage("{0}/links/{1}/{2}", obj.getObjectURI(), type2, id2);
        return Boolean.parseBoolean(invokeSyncGet(url, null, String.class));
    }

    /**
     * Checks if a given object is linked to this one.
     * @param toObj the other object
     * @param obj the object to execute this method on
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void isLinked(ParaObject obj, Sysprop toObj, Listener<Boolean> callback,
                         ErrorListener... error) {
        if (obj == null || obj.getId() == null || toObj == null || toObj.getId() == null) {
            fail(callback, false);
            return;
        }
        isLinked(obj, toObj.getType(), toObj.getId(), callback, error);
    }

    /**
     * Checks if a given object is linked to this one.
     * @param toObj the other object
     * @param obj the object to execute this method on
     * @return true if linked
     */
    public boolean isLinkedSync(ParaObject obj, ParaObject toObj) {
        if (obj == null || obj.getId() == null || toObj == null || toObj.getId() == null) {
            return false;
        }
        return isLinkedSync(obj, toObj.getType(), toObj.getId());
    }

    /**
     * Links an object to this one in a many-to-many relationship.
     * Only a link is created. Objects are left untouched.
     * The type of the second object is automatically determined on read.
     * @param id2 link to the object with this id
     * @param obj the object to execute this method on
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void link(ParaObject obj, String id2, Listener<String> callback,
                     ErrorListener... error) {
        if (obj == null || obj.getId() == null || id2 == null) {
            fail(callback, null);
            return;
        }
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), id2);
        invokePost(url, null, String.class, callback, error);
    }

    /**
     * Links an object to this one in a many-to-many relationship.
     * Only a link is created. Objects are left untouched.
     * The type of the second object is automatically determined on read.
     * @param id2 link to the object with this id
     * @param obj the object to execute this method on
     * @return the id of the Linker object that is created
     */
    public String linkSync(ParaObject obj, String id2) {
        if (obj == null || obj.getId() == null || id2 == null) {
            return null;
        }
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), id2);
        return invokeSyncPost(url, null, String.class);
    }

    /**
     * Unlinks an object from this one.
     * Only a link is deleted. Objects are left untouched.
     * @param type2 the other type
     * @param obj the object to execute this method on
     * @param id2 the other id
     * @param error ErrorListener called on error
     */
    public void unlink(ParaObject obj, String type2, String id2, Listener<Map> callback,
                       ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null || id2 == null) {
            fail(callback, null);
            return;
        }
        String url = ClientUtils.formatMessage("{0}/links/{1}/{2}", obj.getObjectURI(), type2, id2);
        invokeDelete(url, null, Map.class, callback, error);
    }

    /**
     * Unlinks an object from this one.
     * Only a link is deleted. Objects are left untouched.
     * @param type2 the other type
     * @param obj the object to execute this method on
     * @param id2 the other id
     */
    public void unlinkSync(ParaObject obj, String type2, String id2) {
        if (obj == null || obj.getId() == null || type2 == null || id2 == null) {
            return;
        }
        String url = ClientUtils.formatMessage("{0}/links/{1}/{2}", obj.getObjectURI(), type2, id2);
        invokeSyncDelete(url, null, Map.class);
    }

    /**
     * Unlinks all objects that are linked to this one.
     * @param obj the object to execute this method on
     * Only Linker objects are deleted.
     * {@link ParaObject}s are left untouched.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void unlinkAll(ParaObject obj, Listener<Map> callback, ErrorListener... error) {
        if (obj == null || obj.getId() == null) {
            fail(callback, null);
            return;
        }
        String url = ClientUtils.formatMessage("{0}/links", obj.getObjectURI());
        invokeDelete(url, null, Map.class, callback, error);
    }

    /**
     * Unlinks all objects that are linked to this one.
     * @param obj the object to execute this method on
     * Only Linker objects are deleted.
     * {@link com.erudika.para.core.ParaObject}s are left untouched.
     */
    public void unlinkAllSync(ParaObject obj) {
        if (obj == null || obj.getId() == null) {
            return;
        }
        String url = ClientUtils.formatMessage("{0}/links", obj.getObjectURI());
        invokeSyncDelete(url, null, Map.class);
    }

    /**
     * Count the total number of child objects for this object.
     * @param type2 the type of the other object
     * @param obj the object to execute this method on
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void countChildren(ParaObject obj, String type2, final Listener<Long> callback,
                              ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null) {
            fail(callback, 0L);
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("count", "true");
        params.put("childrenonly", "true");
        final Pager pager = new Pager();
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeGet(url, params, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                getItems(res, pager);
                callback.onResponse(pager.getCount());
            }
        }, error);
    }

    /**
     * Count the total number of child objects for this object.
     * @param type2 the type of the other object
     * @param obj the object to execute this method on
     * @return the number of links
     */
    public Long countChildrenSync(ParaObject obj, String type2) {
        if (obj == null || obj.getId() == null || type2 == null) {
            return 0L;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("count", "true");
        params.put("childrenonly", "true");
        Pager pager = new Pager();
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        getItems(invokeSyncGet(url, params, Map.class), pager);
        return pager.getCount();
    }

    /**
     * Returns all child objects linked to this object.
     * @param type2 the type of children to look for
     * @param obj the object to execute this method on
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void getChildren(ParaObject obj, String type2, final Pager pager,
                            final Listener<List<ParaObject>> callback,
                            ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null) {
            fail(callback, Collections.emptyList());
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("childrenonly", "true");
        params.putAll(pagerToParams(pager));
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeGet(url, params, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Returns all child objects linked to this object.
     * @param <P> the type of children
     * @param type2 the type of children to look for
     * @param obj the object to execute this method on
     * @param pager a {@link Pager}
     * @return a list of {@link ParaObject} in a one-to-many relationship with this object
     */
    public <P extends ParaObject> List<P> getChildrenSync(ParaObject obj, String type2, Pager... pager) {
        if (obj == null || obj.getId() == null || type2 == null) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("childrenonly", "true");
        params.putAll(pagerToParams(pager));
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        return getItems(invokeSyncGet(url, params, Map.class), pager);
    }

    /**
     * Returns all child objects linked to this object.
     * @param type2 the type of children to look for
     * @param field the field name to use as filter
     * @param term the field value to use as filter
     * @param obj the object to execute this method on
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void getChildren(ParaObject obj, String type2, String field, String term,
                            final Pager pager, final Listener<List<ParaObject>> callback,
                            ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null) {
            fail(callback, Collections.emptyList());
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("childrenonly", "true");
        params.put("field", field);
        params.put("term", term);
        params.putAll(pagerToParams(pager));
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeGet(url, params, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Returns all child objects linked to this object.
     * @param <P> the type of children
     * @param type2 the type of children to look for
     * @param field the field name to use as filter
     * @param term the field value to use as filter
     * @param obj the object to execute this method on
     * @param pager a {@link Pager}
     * @return a list of {@link ParaObject} in a one-to-many relationship with this object
     */
    public <P extends ParaObject> List<P> getChildrenSync(ParaObject obj, String type2, String field,
                                                          String term, Pager... pager) {
        if (obj == null || obj.getId() == null || type2 == null) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("childrenonly", "true");
        params.put("field", field);
        params.put("term", term);
        params.putAll(pagerToParams(pager));
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        return getItems(invokeSyncGet(url, params, Map.class), pager);
    }

    /**
     * Search through all child objects. Only searches child objects directly
     * connected to this parent via the {@code parentid} field.
     * @param type2 the type of children to look for
     * @param query a query string
     * @param obj the object to execute this method on
     * @param pager a {@link Pager}
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void findChildren(ParaObject obj, String type2, String query, final Pager pager,
                             final Listener<List<ParaObject>> callback,
                             ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null) {
            fail(callback, Collections.emptyList());
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("childrenonly", "true");
        params.put("q", (query == null) ? "*" : query);
        params.putAll(pagerToParams(pager));
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeGet(url, params, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
    }

    /**
     * Search through all child objects. Only searches child objects directly
     * connected to this parent via the {@code parentid} field.
     * @param <P> the type of children
     * @param type2 the type of children to look for
     * @param query a query string
     * @param obj the object to execute this method on
     * @param pager a {@link Pager}
     * @return a list of {@link ParaObject} in a one-to-many relationship with this object
     */
    public <P extends ParaObject> List<P> findChildrenSync(ParaObject obj, String type2,
                                                           String query, Pager... pager) {
        if (obj == null || obj.getId() == null || type2 == null) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("childrenonly", "true");
        params.put("q", (query == null) ? "*" : query);
        params.putAll(pagerToParams(pager));
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        return getItems(invokeSyncGet(url, params, Map.class), pager);
    }

    /**
     * Deletes all child objects permanently.
     * @param obj the object to execute this method on
     * @param type2 the children's type.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void deleteChildren(ParaObject obj, String type2, Listener<Map> callback,
                               ErrorListener... error) {
        if (obj == null || obj.getId() == null || type2 == null) {
            fail(callback, null);
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("childrenonly", "true");
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeDelete(url, params, Map.class, callback, error);
    }

    /**
     * Deletes all child objects permanently.
     * @param obj the object to execute this method on
     * @param type2 the children's type.
     */
    public void deleteChildrenSync(ParaObject obj, String type2) {
        if (obj == null || obj.getId() == null || type2 == null) {
            return;
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("childrenonly", "true");
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeSyncDelete(url, params, Map.class);
    }

    /////////////////////////////////////////////
    //				 UTILS
    /////////////////////////////////////////////

    /**
     * Generates a new unique id.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void newId(final Listener<String> callback, ErrorListener... error) {
        invokeGet("utils/newid", null, String.class, new Listener<String>() {
            public void onResponse(String res) {
                callback.onResponse(res != null ? res : "");
            }
        }, error);
    }

    /**
     * Generates a new unique id.
     * @return a new id
     */
    public String newIdSync() {
        String res = invokeSyncGet("utils/newid", null, String.class);
        return res != null ? res : "";
    }

    /**
     * Returns the current timestamp.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void getTimestamp(final Listener<Long> callback, ErrorListener... error) {
        invokeGet("utils/timestamp", null, String.class, new Listener<String>() {
            public void onResponse(String res) {
                callback.onResponse(res != null ? Long.decode(res) : 0L);
            }
        }, error);
    }

    /**
     * Returns the current timestamp.
     * @return a long number
     */
    public long getTimestampSync() {
        Long res = Long.decode(invokeSyncGet("utils/timestamp", null, String.class));
        return res != null ? res : 0L;
    }

    /**
     * Formats a date in a specific format.
     * @param format the date format
     * @param loc the locale instance
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void formatDate(String format, Locale loc, Listener<String> callback,
                           ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("format", format);
        params.put("locale", loc == null ? null : loc.toString());
        invokeGet("utils/formatdate", params, String.class, callback, error);
    }

    /**
     * Formats a date in a specific format.
     * @param format the date format
     * @param loc the locale instance
     * @return a formatted date
     */
    public String formatDateSync(String format, Locale loc) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("format", format);
        params.put("locale", loc == null ? null : loc.toString());
        return invokeSyncGet("utils/formatdate", params, String.class);
    }

    /**
     * Converts spaces to dashes.
     * @param str a string with spaces
     * @param replaceWith a string to replace spaces with
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void noSpaces(String str, String replaceWith, Listener<String> callback,
                         ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("string", str);
        params.put("replacement", replaceWith);
        invokeGet("utils/nospaces", params, String.class, callback, error);
    }

    /**
     * Converts spaces to dashes.
     * @param str a string with spaces
     * @param replaceWith a string to replace spaces with
     * @return a string with dashes
     */
    public String noSpacesSync(String str, String replaceWith) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("string", str);
        params.put("replacement", replaceWith);
        return invokeSyncGet("utils/nospaces", params, String.class);
    }

    /**
     * Strips all symbols, punctuation, whitespace and control chars from a string.
     * @param str a dirty string
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void stripAndTrim(String str, Listener<String> callback,
                             ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("string", str);
        invokeGet("utils/nosymbols", params, String.class, callback, error);
    }

    /**
     * Strips all symbols, punctuation, whitespace and control chars from a string.
     * @param str a dirty string
     * @return a clean string
     */
    public String stripAndTrimSync(String str) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("string", str);
        return invokeSyncGet("utils/nosymbols", params, String.class);
    }

    /**
     * Converts Markdown to HTML
     * @param markdownString Markdown
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void markdownToHtml(String markdownString, Listener<String> callback,
                               ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("md", markdownString);
        invokeGet("utils/md2html", params, String.class, callback, error);
    }

    /**
     * Converts Markdown to HTML
     * @param markdownString Markdown
     * @return HTML
     */
    public String markdownToHtmlSync(String markdownString) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("md", markdownString);
        return invokeSyncGet("utils/md2html", params, String.class);
    }

    /**
     * Returns the number of minutes, hours, months elapsed for a time delta (milliseconds).
     * @param delta the time delta between two events, in milliseconds
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void approximately(long delta, Listener<String> callback,
                              ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("delta", Long.toString(delta));
        invokeGet("utils/timeago", params, String.class, callback, error);
    }

    /**
     * Returns the number of minutes, hours, months elapsed for a time delta (milliseconds).
     * @param delta the time delta between two events, in milliseconds
     * @return a string like "5m", "1h"
     */
    public String approximatelySync(long delta) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("delta", Long.toString(delta));
        return invokeSyncGet("utils/timeago", params, String.class);
    }

    /////////////////////////////////////////////
    //				 MISC
    /////////////////////////////////////////////

    /**
     * Generates a new set of access/secret keys.
     * Old keys are discarded and invalid after this.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void newKeys(final Listener<Map<String, String>> callback,
                        ErrorListener... error) {
        final ParaClient that = this;
        invokePost("_newkeys", new HashMap<String, String>(), null,
                new Listener<Map<String, String>>() {
            public void onResponse(Map<String, String> keys) {
                if (keys != null && keys.containsKey("secretKey")) {
                    that.secretKey = keys.get("secretKey");
                    callback.onResponse(keys);
                }
            }
        }, error);
    }

    /**
     * Generates a new set of access/secret keys.
     * Old keys are discarded and invalid after this.
     * @return a map of new credentials
     */
    public Map<String, String> newKeysSync() {
        Map<String, String> keys = invokeSyncPost("_newkeys", null, Map.class);
        if (keys != null && keys.containsKey("secretKey")) {
            this.secretKey = keys.get("secretKey");
        }
        return keys;
    }

    /**
     * Returns all registered types for this App.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void types(Listener<Map<String, String>> callback, ErrorListener... error) {
        invokeGet("_types", null, Map.class, callback, error);
    }

    /**
     * Returns all registered types for this App.
     * @return a map of plural-singular form of all the registered types.
     */
    public Map<String, String> typesSync() {
        return invokeSyncGet("_types", null, Map.class);
    }

    /**
     * Returns a User or an
     * App that is currently authenticated.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void me(Listener<? extends ParaObject> callback, ErrorListener... error) {
        invokeGet("_me", null, Sysprop.class, callback, error);
    }

    /**
     * Returns a User or an App that is currently authenticated.
     * @param <P> an App or User
     * @return a User or an App
     */
    public <P extends ParaObject> P meSync() {
        return (P) invokeSyncGet("_me", null, Sysprop.class);
    }

    /**
     * Verifies a given JWT and returns the authenticated subject.
     * This request will not remember the JWT in memory.
     * @param accessToken a valid JWT access token
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void me(String accessToken, Listener<? extends ParaObject> callback, ErrorListener... error) {
        if (!StringUtils.isBlank(accessToken)) {
            String auth = accessToken.startsWith("Bearer") ? accessToken : "Bearer " + accessToken;
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", auth);
            getRequestQueue().add(signer.invokeSignedRequest(accessKey, key(true), GET,
                    getEndpoint(), getFullPath("_me"), headers, null, null, Sysprop.class,
                    callback, onError(error)));
        } else {
            invokeGet("_me", null, Sysprop.class, callback, error);
        }
    }

    /**
     * Verifies a given JWT and returns the authenticated subject.
     * This request will not remember the JWT in memory.
     * @param <P> an App or User
     * @param accessToken a valid JWT access token
     * @return a User or an App
     */
    public <P extends ParaObject> P meSync(String accessToken) {
        if (!StringUtils.isBlank(accessToken)) {
            String auth = accessToken.startsWith("Bearer") ? accessToken : "Bearer " + accessToken;
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", auth);
            return (P) invokeSignedSyncRequest(GET, getFullPath("_me"), headers, null, null, Sysprop.class);
        }
        return meSync();
    }

    /**
     * Upvote an object and register the vote in DB.
     * @param obj the object to receive +1 votes
     * @param voterid the userid of the voter
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void voteUp(ParaObject obj, String voterid, final Listener<Boolean> callback,
                          ErrorListener... error) {
        vote(obj, voterid, true, callback, error);
    }

    /**
     * Upvote an object and register the vote in DB.
     * @param obj the object to receive +1 votes
     * @param voterid the userid of the voter
     * @return true if vote was successful
     */
    public boolean voteUpSync(ParaObject obj, String voterid) {
        return voteSync(obj, voterid, true);
    }

    /**
     * Downvote an object and register the vote in DB.
     * @param obj the object to receive +1 votes
     * @param voterid the userid of the voter
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void voteDown(ParaObject obj, String voterid, final Listener<Boolean> callback,
                       ErrorListener... error) {
        vote(obj, voterid, false, callback, error);
    }

    /**
     * Downvote an object and register the vote in DB.
     * @param obj the object to receive -1 votes
     * @param voterid the userid of the voter
     * @return true if vote was successful
     */
    public boolean voteDownSync(ParaObject obj, String voterid) {
        return voteSync(obj, voterid, false);
    }

    private void vote(ParaObject obj, String voterid, boolean isUpvote,
                      final Listener<Boolean> callback, ErrorListener... error) {
        if (obj == null || StringUtils.isBlank(voterid)) {
            fail(callback, false);
        }
        String val = isUpvote ? "_voteup" : "_votedown";
        invokePatch(obj.getType().concat("/").concat(obj.getId()),
                Collections.singletonMap(val, voterid), String.class, new Listener<String>() {
                    public void onResponse(String res) {
                        callback.onResponse(res == null ? false : Boolean.parseBoolean(res));
                    }
                }, error);
    }

    private boolean voteSync(ParaObject obj, String voterid, boolean isUpvote) {
        if (obj == null || StringUtils.isBlank(voterid)) {
            return false;
        }
        String val = isUpvote ? "_voteup" : "_votedown";
        return invokeSyncPatch(obj.getType().concat("/").concat(obj.getId()),
                Collections.singletonMap(val, voterid), Boolean.class);
    }

    /**
     * Rebuilds the entire search index.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     * @return a response object with properties "tookMillis" and "reindexed"
     */
    public void rebuildIndex(Listener<Map<String, String>> callback, ErrorListener... error) {
        invokePost("_reindex", null, Map.class, callback, error);
    }

    /**
     * Rebuilds the entire search index.
     * @return a response object with properties "tookMillis" and "reindexed"
     */
    public Map<String, Object> rebuildIndexSync() {
        return invokeSyncPost("_reindex", null, Map.class);
    }

    /**
     * Rebuilds the entire search index.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     * @param destinationIndex an existing index as destination
     * @return a response object with properties "tookMillis" and "reindexed"
     */
    public void rebuildIndex(String destinationIndex, Listener<Map<String, String>> callback,
                             ErrorListener... error) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("destinationIndex", destinationIndex);
        invokeSignedRequest(POST, getFullPath("_reindex"),
                null, params, new byte[0], Map.class, callback, error);
    }

    /**
     * Rebuilds the entire search index.
     * @param destinationIndex an existing index as destination
     * @return a response object with properties "tookMillis" and "reindexed"
     */
    public Map<String, Object> rebuildIndexSync(String destinationIndex) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("destinationIndex", destinationIndex);
        return invokeSignedSyncRequest(POST, getFullPath("_reindex"),
                null, params, new byte[0], Map.class);
    }

    /////////////////////////////////////////////
    //			Validation Constraints
    /////////////////////////////////////////////

    /**
     * Returns the validation constraints map.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void validationConstraints(Listener<Map<String, Map<String, Map<String,
            Map<String, ?>>>>> callback, ErrorListener... error) {
        invokeGet("_constraints", null, Map.class, callback, error);
    }

    /**
     * Returns the validation constraints map.
     * @return a map containing all validation constraints.
     */
    public Map<String, Map<String, Map<String, Map<String, ?>>>> validationConstraintsSync() {
        return invokeSyncGet("_constraints", null, Map.class);
    }

    /**
     * Returns the validation constraints map.
     * @param type a type
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void validationConstraints(String type, Listener<Map<String, Map<String,
            Map<String, Map<String, ?>>>>> callback, ErrorListener... error) {
        invokeGet(ClientUtils.formatMessage("_constraints/{0}", type),
                null, Map.class, callback, error);
    }

    /**
     * Returns the validation constraints map.
     * @param type a type
     * @return a map containing all validation constraints for this type.
     */
    public Map<String, Map<String, Map<String, Map<String, ?>>>> validationConstraintsSync(String type) {
        return invokeSyncGet(ClientUtils.formatMessage("_constraints/{0}", type), null, Map.class);
    }

    /**
     * Add a new constraint for a given field.
     * @param type a type
     * @param field a field name
     * @param c the constraint
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void addValidationConstraint(String type, String field, Constraint c,
            Listener<Map<String, Map<String, Map<String, Map<String, ?>>>>> callback,
                                        ErrorListener... error) {
        if (StringUtils.isBlank(type) || StringUtils.isBlank(field) || c == null) {
            fail(callback, Collections.emptyMap());
            return;
        }
        invokePut(ClientUtils.formatMessage("_constraints/{0}/{1}/{2}", type, field, c.getName()),
                c.getPayload(), Map.class, callback, error);
    }

    /**
     * Add a new constraint for a given field.
     * @param type a type
     * @param field a field name
     * @param c the constraint
     * @return a map containing all validation constraints for this type.
     */
    public Map<String, Map<String, Map<String, Map<String, ?>>>> addValidationConstraintSync(
            String type, String field, Constraint c) {
        if (StringUtils.isBlank(type) || StringUtils.isBlank(field) || c == null) {
            return Collections.emptyMap();
        }
        return invokeSyncPut(ClientUtils.formatMessage("_constraints/{0}/{1}/{2}", type,
                field, c.getName()), c.getPayload(), Map.class);
    }

    /**
     * Removes a validation constraint for a given field.
     * @param type a type
     * @param field a field name
     * @param constraintName the name of the constraint to remove
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void removeValidationConstraint(String type, String field, String constraintName,
               Listener<Map<String, Map<String, Map<String, Map<String, ?>>>>> callback,
                                           ErrorListener... error) {
        if (StringUtils.isBlank(type) || StringUtils.isBlank(field) ||
                StringUtils.isBlank(constraintName)) {
            fail(callback, Collections.emptyMap());
            return;
        }
        invokeDelete(ClientUtils.formatMessage("_constraints/{0}/{1}/{2}", type,
                field, constraintName), null, Map.class, callback, error);
    }

    /**
     * Removes a validation constraint for a given field.
     * @param type a type
     * @param field a field name
     * @param constraintName the name of the constraint to remove
     * @return a map containing all validation constraints for this type.
     */
    public Map<String, Map<String, Map<String, Map<String, ?>>>> removeValidationConstraintSync(
            String type, String field, String constraintName) {
        if (StringUtils.isBlank(type) || StringUtils.isBlank(field) ||
                StringUtils.isBlank(constraintName)) {
            return Collections.emptyMap();
        }
        return invokeSyncDelete(ClientUtils.formatMessage("_constraints/{0}/{1}/{2}", type,
                field, constraintName), null, Map.class);
    }

    /////////////////////////////////////////////
    //			Resource Permissions
    /////////////////////////////////////////////

    /**
     * Returns the permissions for all subjects and resources for current app.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void resourcePermissions(Listener<Map<String, Map<String, List<String>>>> callback,
                                    ErrorListener... error) {
        invokeGet("_permissions", null, Map.class, callback, error);
    }

    /**
     * Returns the permissions for all subjects and resources for current app.
     * @return a map of subject ids to resource names to a list of allowed methods
     */
    public Map<String, Map<String, List<String>>> resourcePermissionsSync() {
        return invokeSyncGet("_permissions", null, Map.class);
    }

    /**
     * Returns only the permissions for a given subject (user) of the current app.
     * @param subjectid the subject id (user id)
     * @param callback Listener called with response object
     */
    public void resourcePermissions(String subjectid,
                                    Listener<Map<String, Map<String, List<String>>>> callback,
                                    ErrorListener... error) {
        invokeGet(ClientUtils.formatMessage("_permissions/{0}", subjectid),
                null, Map.class, callback, error);
    }

    /**
     * Returns only the permissions for a given subject (user) of the current app.
     * @param subjectid the subject id (user id)
     * @return a map of subject ids to resource names to a list of allowed methods
     */
    public Map<String, Map<String, List<String>>> resourcePermissionsSync(String subjectid) {
        return invokeSyncGet(ClientUtils.formatMessage("_permissions/{0}", subjectid), null, Map.class);
    }

    /**
     * Grants a permission to a subject that allows them to
     * call the specified HTTP methods on a given resource.
     * @param subjectid subject id (user id)
     * @param resourcePath resource path or object type
     * @param permission an array of allowed HTTP methods
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void grantResourcePermission(String subjectid, String resourcePath, String[] permission,
                                        Listener<Map<String, Map<String, List<String>>>> callback,
                                        ErrorListener... error) {
        grantResourcePermission(subjectid, resourcePath, permission, false, callback, error);
    }

    /**
     * Grants a permission to a subject that allows them to
     * call the specified HTTP methods on a given resource.
     * @param subjectid subject id (user id)
     * @param resourcePath resource path or object type
     * @param permission an array of allowed HTTP methods
     * @param allowGuestAccess if true - all unauthenticated requests will go through, 'false' by default.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void grantResourcePermission(String subjectid, String resourcePath, String[] permission,
                                        boolean allowGuestAccess,
                                        Listener<Map<String, Map<String, List<String>>>> callback,
                                        ErrorListener... error) {
        if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath) || permission == null) {
            fail(callback, Collections.emptyMap());
            return;
        }
        if (allowGuestAccess && ClientUtils.ALLOW_ALL.equals(subjectid)) {
            permission = Arrays.copyOf(permission, permission.length + 1);
            permission[permission.length - 1] = ClientUtils.GUEST;
        }
        resourcePath = ClientUtils.urlEncode(resourcePath);
        invokePut(ClientUtils.formatMessage("_permissions/{0}/{1}", subjectid, resourcePath),
                permission, Map.class, callback, error);
    }

    /**
     * Grants a permission to a subject that allows them to call the
     * specified HTTP methods on a given resource.
     * @param subjectid subject id (user id)
     * @param resourcePath resource path or object type
     * @param permission a set of HTTP methods
     * @return a map of the permissions for this subject id
     */
    public Map<String, Map<String, List<String>>> grantResourcePermissionSync(String subjectid,
                      String resourcePath, String[] permission) {
        return grantResourcePermissionSync(subjectid, resourcePath, permission, false);
    }

    /**
     * Grants a permission to a subject that allows them to call the
     * specified HTTP methods on a given resource.
     * @param subjectid subject id (user id)
     * @param resourcePath resource path or object type
     * @param permission a set of HTTP methods
     * @param allowGuestAccess if true - all unauthenticated requests will go through, 'false' by default.
     * @return a map of the permissions for this subject id
     */
    public Map<String, Map<String, List<String>>> grantResourcePermissionSync(String subjectid,
                      String resourcePath, String[] permission, boolean allowGuestAccess) {
        if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath) || permission == null) {
            return Collections.emptyMap();
        }
        if (allowGuestAccess && ClientUtils.ALLOW_ALL.equals(subjectid)) {
            permission = Arrays.copyOf(permission, permission.length + 1);
            permission[permission.length - 1] = ClientUtils.GUEST;
        }
        resourcePath = ClientUtils.urlEncode(resourcePath);
        return invokeSyncPut(ClientUtils.formatMessage("_permissions/{0}/{1}", subjectid, resourcePath),
                permission, Map.class);
    }

    /**
     * Revokes a permission for a subject, meaning they
     * no longer will be able to access the given resource.
     * @param subjectid subject id (user id)
     * @param resourcePath resource path or object type
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void revokeResourcePermission(String subjectid, String resourcePath,
                                     Listener<Map<String, Map<String, List<String>>>> callback,
                                         ErrorListener... error) {
        if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath)) {
            fail(callback, Collections.emptyMap());
            return;
        }
        resourcePath = ClientUtils.urlEncode(resourcePath);
        invokeDelete(ClientUtils.formatMessage("_permissions/{0}/{1}", subjectid, resourcePath),
            null, Map.class, callback, error);
    }

    /**
     * Revokes a permission for a subject, meaning they no longer
     * will be able to access the given resource.
     * @param subjectid subject id (user id)
     * @param resourcePath resource path or object type
     * @return a map of the permissions for this subject id
     */
    public Map<String, Map<String, List<String>>> revokeResourcePermissionSync(String subjectid,
                                                                               String resourcePath) {
        if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath)) {
            return Collections.emptyMap();
        }
        resourcePath = ClientUtils.urlEncode(resourcePath);
        return invokeSyncDelete(ClientUtils.formatMessage("_permissions/{0}/{1}", subjectid, resourcePath),
                null, Map.class);
    }

    /**
     * Revokes all permission for a subject.
     * @param subjectid subject id (user id)
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void revokeAllResourcePermissions(String subjectid,
                                         Listener<Map<String, Map<String, List<String>>>> callback,
                                         ErrorListener... error) {
        if (StringUtils.isBlank(subjectid)) {
            fail(callback, Collections.emptyMap());
            return;
        }
        invokeDelete(ClientUtils.formatMessage("_permissions/{0}", subjectid),
                null, Map.class, callback, error);
    }

    /**
     * Revokes all permission for a subject.
     * @param subjectid subject id (user id)
     * @return a map of the permissions for this subject id
     */
    public Map<String, Map<String, List<String>>> revokeAllResourcePermissionsSync(String subjectid) {
        if (StringUtils.isBlank(subjectid)) {
            return Collections.emptyMap();
        }
        return invokeSyncDelete(ClientUtils.formatMessage("_permissions/{0}", subjectid),
                null, Map.class);
    }

    /**
     * Checks if a subject is allowed to call method X on resource Y.
     * @param subjectid subject id
     * @param resourcePath resource path or object type
     * @param httpMethod HTTP method name
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void isAllowedTo(String subjectid, String resourcePath, String httpMethod,
                               final Listener<Boolean> callback, ErrorListener... error) {
        if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath) ||
                StringUtils.isBlank(httpMethod)) {
            fail(callback, false);
            return;
        }
        resourcePath = ClientUtils.urlEncode(resourcePath);
        String url = ClientUtils.formatMessage("_permissions/{0}/{1}/{2}",
                subjectid, resourcePath, httpMethod);
        invokeGet(url, null, String.class, new Listener<String>() {
            public void onResponse(String res) {
                callback.onResponse(res == null ? false : Boolean.parseBoolean(res));
            }
        }, error);
    }

    /**
     * Checks if a subject is allowed to call method X on resource Y.
     * @param subjectid subject id
     * @param resourcePath resource path or object type (URL encoded)
     * @param httpMethod HTTP method name
     * @return true if allowed
     */
    public boolean isAllowedToSync(String subjectid, String resourcePath, String httpMethod) {
        if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourcePath) ||
                StringUtils.isBlank(httpMethod)) {
            return false;
        }
        String url = ClientUtils.formatMessage("_permissions/{0}/{1}/{2}",
                subjectid, resourcePath, httpMethod);
        return Boolean.parseBoolean(invokeSyncGet(url, null, String.class));
    }

    /////////////////////////////////////////////
    //				App Settings
    /////////////////////////////////////////////

    /**
     * Returns the map containing app-specific settings.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void appSettings(final Listener<Map<String, Object>> callback, ErrorListener... error) {
        invokeGet("_settings", null, Map.class, callback, error);
    }

    /**
     * Returns the map containing app-specific settings.
     * @return a map
     */
    public Map<String, Object> appSettingsSync() {
        return invokeSyncGet("_settings", null, Map.class);
    }

    /**
     * Returns the value of a specific app setting (property).
     * @param key a key
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void appSettings(String key, final Listener<Map<String, Object>> callback,
                                           ErrorListener... error) {
        if (StringUtils.isBlank(key)) {
            appSettings(callback, error);
        }
        invokeGet(ClientUtils.formatMessage("_settings/{0}", key), null, Map.class, callback, error);
    }

    /**
     * Returns the value of a specific app setting (property).
     * @param key a key
     * @return a map containing one element {"value": "the_value"} or an empty map.
     */
    public Map<String, Object> appSettingsSync(String key) {
        if (StringUtils.isBlank(key)) {
            return appSettingsSync();
        }
        return invokeSyncGet(ClientUtils.formatMessage("_settings/{0}", key), null, Map.class);
    }

    /**
     * Adds or overwrites an app-specific setting.
     * @param key a key
     * @param value a value
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void addAppSetting(String key, Object value, final Listener<Map<String, Object>> callback,
                                  ErrorListener... error) {
        if (!StringUtils.isBlank(key) && value != null) {
            invokePut(ClientUtils.formatMessage("_settings/{0}", key),
                    Collections.singletonMap("value", value), Map.class, callback, error);
        } else {
            fail(callback, Collections.emptyMap());
        }
    }

    /**
     * Adds or overwrites an app-specific setting.
     * @param key a key
     * @param value a value
     */
    public void addAppSettingSync(String key, Object value) {
        if (!StringUtils.isBlank(key) && value != null) {
            invokeSyncPut(ClientUtils.formatMessage("_settings/{0}", key),
                    Collections.singletonMap("value", value), Map.class);
        }
    }

    /**
     * Overwrites all app-specific settings.
     * @param settings a key-value map of properties
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void setAppSettings(Map<String, Object> settings, final Listener<Map<String, Object>> callback,
                              ErrorListener... error) {
        if (settings != null) {
            invokePut("_settings", settings, Map.class, callback, error);
        } else {
            fail(callback, Collections.emptyMap());
        }
    }

    /**
     * Overwrites all app-specific settings.
     * @param settings a key-value map of properties
     */
    public void setAppSettingsSync(Map<String, Object> settings) {
        if (settings != null) {
            invokeSyncPut("_settings", settings, Map.class);
        }
    }

    /**
     * Removes an app-specific setting.
     * @param key a key
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void removeAppSetting(String key, final Listener<Map<String, Object>> callback,
                                 ErrorListener... error) {
        if (!StringUtils.isBlank(key)) {
            invokeDelete(ClientUtils.formatMessage("_settings/{0}", key),
                    null, Map.class, callback, error);
        } else {
            fail(callback, Collections.emptyMap());
        }
    }

    /**
     * Removes an app-specific setting.
     * @param key a key
     */
    public void removeAppSettingSync(String key) {
        if (!StringUtils.isBlank(key)) {
            invokeSyncDelete(ClientUtils.formatMessage("_settings/{0}", key), null, Map.class);
        }
    }
    /////////////////////////////////////////////
    //				Access Tokens
    /////////////////////////////////////////////

    /**
     * Takes an identity provider access token and fetches the user data from that provider.
     * A new User object is created if that user doesn't exist.
     * Access tokens are returned upon successful authentication using one of the SDKs from
     * Facebook, Google, Twitter, etc.
     * <b>Note:</b> Twitter uses OAuth 1 and gives you a token and a token secret.
     * <b>You must concatenate them like this: <code>{oauth_token}:{oauth_token_secret}</code> and
     * use that as the provider access token.</b>
     * @param provider identity provider, e.g. 'facebook', 'google'...
     * @param providerToken access token from a provider like Facebook, Google, Twitter
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void signIn(String provider, String providerToken, boolean rememberJWT,
                       final Listener<Sysprop> callback, final ErrorListener... error) {
        if (!StringUtils.isBlank(provider) && !StringUtils.isBlank(providerToken)) {
            Map<String, String> credentials = new HashMap<String, String>();
            credentials.put("appid", accessKey);
            credentials.put("provider", provider);
            credentials.put("token", providerToken);
            invokePost(JWT_PATH, credentials, null, new Listener<Map>() {
                public void onResponse(Map result) {
                    if (result != null && result.containsKey("user") && result.containsKey("jwt")) {
                        Map<?, ?> jwtData = (Map<?, ?>) result.get("jwt");
                        Map<String, Object> userData = (Map<String, Object>) result.get("user");
                        saveAccessToken(jwtData);
                        if (callback != null) {
                            callback.onResponse(ClientUtils.setFields(Sysprop.class, userData));
                        }
                    } else {
                        clearAccessToken();
                        if (callback != null) {
                            callback.onResponse(null);
                        }
                    }
                }
            }, new ErrorListener() {
                public void onErrorResponse(VolleyError volleyError) {
                    clearAccessToken();
                    onError(error);
                }
            });
        } else {
            if (callback != null) {
                callback.onResponse(null);
            }
        }
    }

    /**
     * @see #signIn(String, String, boolean, Listener, ErrorListener...)
     * @param provider identity provider, e.g. 'facebook', 'google'...
     * @param providerToken access token from a provider like Facebook, Google, Twitter
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void signIn(String provider, String providerToken, final Listener<Sysprop> callback,
                       final ErrorListener... error) {
        signIn(provider, providerToken, true, callback, error);
    }

    /**
     * Takes an identity provider access token and fetches the user data from that provider.
     * A new User object is created if that user doesn't exist.
     * Access tokens are returned upon successful authentication using one of the SDKs from
     * Facebook, Google, Twitter, etc.
     * <b>Note:</b> Twitter uses OAuth 1 and gives you a token and a token secret.
     * <b>You must concatenate them like this: <code>{oauth_token}:{oauth_token_secret}</code> and
     * use that as the provider access token.</b>
     * @param provider identity provider, e.g. 'facebook', 'google'...
     * @param providerToken access token from a provider like Facebook, Google, Twitter
     * @return a User object or null if something failed
     */
    public Sysprop signInSync(String provider, String providerToken, boolean rememberJWT) {
        if (!StringUtils.isBlank(provider) && !StringUtils.isBlank(providerToken)) {
            Map<String, String> credentials = new HashMap<String, String>();
            credentials.put("appid", accessKey);
            credentials.put("provider", provider);
            credentials.put("token", providerToken);
            Map<String, Object> result = invokeSyncPost(JWT_PATH, credentials, Map.class);
            if (result != null && result.containsKey("user") && result.containsKey("jwt")) {
                Map<?, ?> jwtData = (Map<?, ?>) result.get("jwt");
                if (rememberJWT) {
                    saveAccessToken(jwtData);
                }
                return ClientUtils.setFields(Sysprop.class, (Map<String, Object>) result.get("user"));
            } else {
                clearAccessToken();
            }
        }
        return null;
    }

    /**
     * @see #signInSync(String, String, boolean)
     * @param provider identity provider, e.g. 'facebook', 'google'...
     * @param providerToken access token from a provider like Facebook, Google, Twitter
     * @return a User object or null if something failed
     */
    public Sysprop signInSync(String provider, String providerToken) {
        return signInSync(provider, providerToken, true);
    }

    /**
     * Clears the JWT access token but token is not revoked.
     * Tokens can be revoked globally per user with revokeAllTokens().
     */
    public void signOut() {
        clearAccessToken();
    }

    /**
     * Refreshes the JWT access token. This requires a valid existing token.
     * Call signIn() first.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    protected void refreshToken(final Listener<Boolean> callback, final ErrorListener... error) {
        long now = System.currentTimeMillis();
        boolean notExpired = tokenKeyExpires != null && tokenKeyExpires > now;
        boolean canRefresh = tokenKeyNextRefresh != null &&
        (tokenKeyNextRefresh < now || tokenKeyNextRefresh > tokenKeyExpires);
        // token present and NOT expired
        if (tokenKey != null && notExpired && canRefresh) {
            invokeGet(JWT_PATH, null, Map.class, new Listener<Map>() {
                public void onResponse(Map result) {
                    if (result != null && result.containsKey("user") && result.containsKey("jwt")) {
                        Map<?, ?> jwtData = (Map<?, ?>) result.get("jwt");
                        saveAccessToken(jwtData);
                        if (callback != null) {
                            callback.onResponse(true);
                        }
                    } else {
                        clearAccessToken();
                        if (callback != null) {
                            callback.onResponse(false);
                        }
                    }
                }
            }, new ErrorListener() {
                public void onErrorResponse(VolleyError volleyError) {
                    clearAccessToken();
                    onError(error);
                }
            });
        } else {
            if (callback != null) {
                callback.onResponse(false);
            }
        }
    }

    /**
     * Refreshes the JWT access token. This requires a valid existing token.
     *	Call signIn() first.
     * @return true if token was refreshed
     */
    protected boolean refreshTokenSync() {
        long now = System.currentTimeMillis();
        boolean notExpired = tokenKeyExpires != null && tokenKeyExpires > now;
        boolean canRefresh = tokenKeyNextRefresh != null &&
                (tokenKeyNextRefresh < now || tokenKeyNextRefresh > tokenKeyExpires);
        // token present and NOT expired
        if (tokenKey != null && notExpired && canRefresh) {
            Map<String, Object> result = invokeSyncGet(JWT_PATH, null, Map.class);
            if (result != null && result.containsKey("user") && result.containsKey("jwt")) {
                Map<?, ?> jwtData = (Map<?, ?>) result.get("jwt");
                saveAccessToken(jwtData);
                return true;
            } else {
                clearAccessToken();
            }
        }
        return false;
    }

    /**
     * Revokes all user tokens for a given user id.
     * This would be equivalent to "logout everywhere".
     * <b>Note:</b> Generating a new API secret on the server will also invalidate all client tokens.
     * Requires a valid existing token.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void revokeAllTokens(final Listener<Boolean> callback, ErrorListener... error) {
        invokeDelete(JWT_PATH, null, Map.class, new Listener<Map>() {
            public void onResponse(Map response) {
                if (callback != null) {
                    callback.onResponse(response != null);
                }
            }
        }, error);
    }

    /**
     * Revokes all user tokens for a given user id.
     * This would be equivalent to "logout everywhere".
     * <b>Note:</b> Generating a new API secret on the server will also invalidate all client tokens.
     * Requires a valid existing token.
     * @return true if successful
     */
    public boolean revokeAllTokensSync() {
        return invokeSyncDelete(JWT_PATH, null, Map.class) != null;
    }

}
