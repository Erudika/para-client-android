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
package com.erudika.para.client;

import android.content.Context;

import com.android.volley.Request;
import static com.android.volley.Request.Method.*;
import com.android.volley.RequestQueue;
import static com.android.volley.Response.*;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.NoCache;
import com.android.volley.toolbox.Volley;
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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Java REST client for communicating with a Para API server.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
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

    private RequestQueue requestQueue;

    public ParaClient(String accessKey, String secretKey, Context ctx) {
        this.ctx = ctx;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.requestQueue = getRequestQueue();
        this.tokenKey = ClientUtils.loadPref("tokenKey", ctx);
        this.tokenKeyExpires = Long.getLong(ClientUtils.loadPref("tokenKeyExpires", ctx));
        this.tokenKeyNextRefresh = Long.getLong(ClientUtils.loadPref("tokenKeyNextRefresh", ctx));

        if (StringUtils.length(secretKey) < 6) {
            logger.warn("Secret key appears to be invalid. Make sure you call 'signIn()' first.");
        }
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            if (ctx == null) {
                requestQueue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
            } else {
                requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
            }
        }
        return requestQueue;
    }

    public void enqueue(Request<?> req) {
        getRequestQueue().add(req);
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
     * @return the JWT access token, or null if not signed in
     */
    public String getAccessToken() {
        return tokenKey;
    }

    /**
     * Sets the JWT access token.
     * @param token a valid token
     */
    @SuppressWarnings("unchecked")
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

    private String key(boolean refresh) {
        if (tokenKey != null) {
            if (refresh) {
                refreshToken(null);
            }
            return "Bearer " + tokenKey;
        }
        return secretKey;/**
         * Sets the host URL of the Para server.
         * @param endpoint the Para server location
         */
    }

    @SuppressWarnings("unchecked")
    private <T> void fail(Listener<T> callback, Object returnValue) {
        if (callback != null) {
            callback.onResponse((T) returnValue);
        }
    }

    @SuppressWarnings("unchecked")
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
                        logger.error("{} {}", msg, errorType);
                    }
                }
            };
        }
    }

    private <T> T readEntity(Class<T> clazz, byte[] data) {
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

    private String getFullPath(String resourcePath) {
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

    private void invokeGet(String resourcePath, Map<String, Object> params, Class<?> returnType,
                           Listener<?> success, ErrorListener... error) {
        enqueue(signer.invokeSignedRequest(accessKey, key(!JWT_PATH.equals(resourcePath)), GET,
                getEndpoint(), getFullPath(resourcePath), null, params, null, returnType,
                success, onError(error)));
    }

    private void invokePost(String resourcePath, Object entity, Class<?> returnType,
                            Listener<?> success, ErrorListener... error) {
        enqueue(signer.invokeSignedRequest(accessKey, key(false), POST,
                getEndpoint(), getFullPath(resourcePath), null, null, entity, returnType,
                success, onError(error)));
    }

    private void invokePut(String resourcePath, Object entity, Class<?> returnType,
                           Listener<?> success, ErrorListener... error) {
        enqueue(signer.invokeSignedRequest(accessKey, key(false), PUT,
                getEndpoint(), getFullPath(resourcePath), null, null, entity, returnType,
                success, onError(error)));
    }

    private void invokePatch(String resourcePath, Object entity, Class<?> returnType,
                             Listener<?> success, ErrorListener... error) {
        enqueue(signer.invokeSignedRequest(accessKey, key(false), PATCH,
                getEndpoint(), getFullPath(resourcePath), null, null, entity, returnType,
                success, onError(error)));
    }

    private void invokeDelete(String resourcePath, Map<String, Object> params, Class<?> returnType,
                              Listener<?> success, ErrorListener... error) {
        enqueue(signer.invokeSignedRequest(accessKey, key(false), DELETE,
                getEndpoint(), getFullPath(resourcePath), null, params, null, returnType,
                success, onError(error)));
    }

    private Map<String, Object> pagerToParams(Pager... pager) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (pager != null && pager.length > 0) {
            Pager p = pager[0];
            if (p != null) {
                map.put("page", Collections.singletonList(Long.toString(p.getPage())));
                map.put("desc", Collections.singletonList(Boolean.toString(p.isDesc())));
                map.put("limit", Collections.singletonList(Integer.toString(p.getLimit())));
                if (p.getSortby() != null) {
                    map.put("sort", Collections.singletonList(p.getSortby()));
                }
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<ParaObject> getItemsFromList(List<Map<String, Object>> result) {
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

    @SuppressWarnings("unchecked")
    private <P extends ParaObject> List<P> getItems(Map<String, Object> result, Pager... pager) {
        if (result != null && !result.isEmpty() && result.containsKey("items")) {
            if (pager != null && pager.length > 0 && pager[0] != null && result.containsKey("totalHits")) {
                pager[0].setCount(((Integer) result.get("totalHits")).longValue());
            }
            return (List<P>) getItemsFromList((List<Map<String, Object>>) result.get("items"));
        }
        return Collections.emptyList();
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
        for (Map.Entry<String, ? extends Object> term : terms.entrySet()) {
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
        for (Map.Entry<String, ? extends Object> term : terms.entrySet()) {
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

    private void find(String queryType, Map<String, Object> params,
                      Listener<Map<String, Object>> callback, ErrorListener... error) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (params != null && !params.isEmpty()) {
            String qType = StringUtils.isBlank(queryType) ? "" : "/".concat(queryType);
            invokeGet("search".concat(qType), params, Map.class, callback, error);
            return;
        } else {
            map.put("items", Collections.emptyList());
            map.put("totalHits", 0);
        }
        callback.onResponse(map);
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
        invokeGet(url, null, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
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
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeGet(url, params, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
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
        String url = ClientUtils.formatMessage("{0}/links/{1}", obj.getObjectURI(), type2);
        invokeGet(url, params, Map.class, new Listener<Map<String, Object>>() {
            public void onResponse(Map<String, Object> res) {
                callback.onResponse(getItems(res, pager));
            }
        }, error);
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
     * Returns all registered types for this App.
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void types(Listener<Map<String, String>> callback, ErrorListener... error) {
        invokeGet("_types", null, Map.class, callback, error);
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
     * Grants a permission to a subject that allows them to
     * call the specified HTTP methods on a given resource.
     * @param subjectid subject id (user id)
     * @param resourceName resource name or object type
     * @param permission an array of allowed HTTP methods
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void grantResourcePermission(String subjectid, String resourceName, String[] permission,
                                        Listener<Map<String, Map<String, List<String>>>> callback,
                                        ErrorListener... error) {
        if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourceName) || permission == null) {
            fail(callback, Collections.emptyMap());
            return;
        }
        invokePut(ClientUtils.formatMessage("_permissions/{0}/{1}", subjectid, resourceName),
                permission, Map.class, callback, error);
    }

    /**
     * Revokes a permission for a subject, meaning they
     * no longer will be able to access the given resource.
     * @param subjectid subject id (user id)
     * @param resourceName resource name or object type
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void revokeResourcePermission(String subjectid, String resourceName,
                                     Listener<Map<String, Map<String, List<String>>>> callback,
                                         ErrorListener... error) {
        if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourceName)) {
            fail(callback, Collections.emptyMap());
            return;
        }
        invokeDelete(ClientUtils.formatMessage("_permissions/{0}/{1}", subjectid, resourceName),
            null, Map.class, callback, error);
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
     * Checks if a subject is allowed to call method X on resource Y.
     * @param subjectid subject id
     * @param resourceName resource name (type)
     * @param httpMethod HTTP method name
     * @param callback Listener called with response object
     * @param error ErrorListener called on error
     */
    public void isAllowedTo(String subjectid, String resourceName, String httpMethod,
                               final Listener<Boolean> callback, ErrorListener... error) {
        if (StringUtils.isBlank(subjectid) || StringUtils.isBlank(resourceName) ||
                StringUtils.isBlank(httpMethod)) {
            fail(callback, false);
            return;
        }
        String url = ClientUtils.formatMessage("_permissions/{0}/{1}/{2}",
                subjectid, resourceName, httpMethod);
        invokeGet(url, null, String.class, new Listener<String>() {
            public void onResponse(String res) {
                callback.onResponse(res == null ? false : Boolean.parseBoolean(res));
            }
        }, error);
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
    @SuppressWarnings("unchecked")
    public void signIn(String provider, String providerToken, final Listener<Sysprop> callback,
                       final ErrorListener... error) {
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
                        tokenKey = (String) jwtData.get("access_token");
                        tokenKeyExpires = (Long) jwtData.get("expires");
                        tokenKeyNextRefresh = (Long) jwtData.get("refresh");
                        ClientUtils.savePref("tokenKey", tokenKey, ctx);
                        ClientUtils.savePref("tokenKeyExpires", tokenKeyExpires.toString(), ctx);
                        ClientUtils.savePref("tokenKeyNextRefresh", tokenKeyNextRefresh.toString(), ctx);
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
                        tokenKey = (String) jwtData.get("access_token");
                        tokenKeyExpires = (Long) jwtData.get("expires");
                        tokenKeyNextRefresh = (Long) jwtData.get("refresh");
                        ClientUtils.savePref("tokenKey", tokenKey, ctx);
                        ClientUtils.savePref("tokenKeyExpires", tokenKeyExpires.toString(), ctx);
                        ClientUtils.savePref("tokenKeyNextRefresh", tokenKeyNextRefresh.toString(), ctx);
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

}
