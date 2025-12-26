/*
 * Copyright 2013-2026 Erudika. https://erudika.com
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

import com.android.volley.Request;
import com.android.volley.Response;
import com.github.davidmoten.aws.lw.client.internal.auth.AwsSignatureVersion4;
import com.github.davidmoten.aws.lw.client.internal.util.Util;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends {@code AWS4Signer} implementing the AWS Signature Version 4 algorithm.
 * Also contains a method for signature validation. The signatures that this class produces are
 * compatible with the original AWS SDK implementation.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class Signer {

    private static final Logger logger = LoggerFactory.getLogger(Signer.class);
    private static final String SERVICE_NAME = "para";
    private static final String REGION = "us-east-1";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.
            ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("Z"));

    /**
     * If true, resource path will be URL-encoded twice for compatibility with older Para instances.
     */
    private static final boolean DOUBLE_URL_ENCODE = true;
    /**
     * Signs a request using AWS signature V4.
     * @param httpMethod GET/POST/PUT... etc.
     * @param endpoint the hostname of the API server
     * @param resourcePath the path of the resource (starting from root e.g. "/path/to/res")
     * @param headers the headers map
     * @param params the params map
     * @param entity the entity object or null
     * @param accessKey the app's access key
     * @param secretKey the app's secret key
     * @return a signed request. The actual signature is inside the {@code Authorization} header.
     */
    public Map<String, String> sign(String httpMethod, String endpoint, String resourcePath,
                                    Map<String, String> headers, Map<String, String> params, InputStream entity,
                                    String accessKey, String secretKey) {
        return sign(httpMethod, endpoint, resourcePath, headers, params, entity, accessKey, secretKey,
                SERVICE_NAME, REGION, DOUBLE_URL_ENCODE);
    }

    /**
     * Signs a request using AWS signature V4.
     * @param httpMethod GET/POST/PUT... etc.
     * @param endpoint the hostname of the API server
     * @param resourcePath the path of the resource (starting from root e.g. "/path/to/res")
     * @param headers the headers map
     * @param params the params map
     * @param entity the entity object or null
     * @param accessKey the app's access key
     * @param secretKey the app's secret key
     * @param serviceName service name override
     * @param region region override
     * @param doubleUrlEncodePath if true, resouce path will be double url-encoded
     * @return a signed request. The actual signature is inside the {@code Authorization} header.
     */
    public Map<String, String> sign(String httpMethod, String endpoint, String resourcePath,
                                    Map<String, String> headers, Map<String, String> params, InputStream entity,
                                    String accessKey, String secretKey, String serviceName, String region, boolean doubleUrlEncodePath) {

        Map<String, String> headerz = new HashMap<>();
        Map<String, String> h = Optional.ofNullable(headers).orElse(Collections.emptyMap());
        String date = h.getOrDefault("x-amz-date", h.get("X-Amz-Date"));
        com.github.davidmoten.aws.lw.client.internal.Clock clock
                = () -> date != null ? parseAWSInstant(date).toEpochMilli() : Instant.now().toEpochMilli();
        if (headers != null) {
            headerz.putAll(headers);
            headerz.remove("host");
            headerz.remove("Host");
            headerz.remove("x-amz-date");
            headerz.remove("X-Amz-Date");
        }

        try {
            final String contentHashString;
            byte[] requestBody = entity == null ? null : entity.readAllBytes();
            if (requestBody == null || requestBody.length == 0) {
                contentHashString = AwsSignatureVersion4.EMPTY_BODY_SHA256;
            } else {
                contentHashString = Util.toHex(Util.sha256(requestBody));
            }
            URL endpointURL = URI.create(endpoint + urlEncodeExceptSlashes(resourcePath, doubleUrlEncodePath)).toURL();

            // https://github.com/davidmoten/aws-lightweight-client-java/pull/232
            headerz.put("Authorization", AwsSignatureVersion4.computeSignatureForAuthorizationHeader(endpointURL,
                    httpMethod, serviceName, region, clock, headerz, params, contentHashString, accessKey, secretKey));
            // clean up headers and normalize case
            headerz.put("X-Amz-Date", headerz.get("x-amz-date"));
            headerz.remove("x-amz-date");
        } catch (Exception e) {
            logger.info("Request signature failed: {}", e.getMessage());
        }
        return headerz;
    }

    /**
     * Returns a parsed Date.
     * @param date a date in the AWS format yyyyMMdd'T'HHmmss'Z'
     * @return a date
     */
    public static Date parseAWSDate(String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        return Date.from(parseAWSInstant(date));
    }

    /**
     * Returns a parsed Instant.
     * @param date a date in the AWS format yyyyMMdd'T'HHmmss'Z'
     * @return a date
     */
    public static Instant parseAWSInstant(String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        return LocalDateTime.from(TIME_FORMATTER.parse(date)).toInstant(ZoneOffset.UTC);
    }

    private static String urlEncodeExceptSlashes(String value, boolean doubleUrlEncode) {
        if (value == null) {
            return null;
        }
        if (doubleUrlEncode) {
            return Util.urlEncode(value, true);
        }
        return value;
    }

    /**
     * Builds and signs a request to an API endpoint using the provided credentials.
     * @param accessKey access key
     * @param secretKey secret key
     * @param httpMethod the method (GET, POST...)
     * @param endpointURL protocol://host:port
     * @param reqPath the API resource path relative to the endpointURL
     * @param headers headers map
     * @param params parameters map
     * @param jsonEntity an object serialized to JSON byte array (payload), could be null
     * @return a map containing the "Authorization" header
     */
    public Map<String, String> signRequest(String accessKey, String secretKey,
                                           String httpMethod, String endpointURL, String reqPath,
                                           Map<String, String> headers, Map<String, List<String>> params, byte[] jsonEntity) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        if (StringUtils.isBlank(accessKey)) {
            logger.error("Blank access key: {} {}", httpMethod, reqPath);
            return headers;
        }

        if (StringUtils.isBlank(secretKey)) {
            logger.debug("Anonymous request: {} {}", httpMethod, reqPath);
            headers.put("Authorization", "Anonymous " + accessKey);
            return headers;
        }

        if (httpMethod == null) {
            httpMethod = "GET";
        }

        InputStream in = null;
        Map<String, String> sigParams = new HashMap<>();

        if (params != null) {
            for (Map.Entry<String, List<String>> param : params.entrySet()) {
                String key = param.getKey();
                List<String> value = param.getValue();
                if (value != null && !value.isEmpty() && value.get(0) != null) {
                    sigParams.put(key, value.get(0));
                }
            }
        }

        if (jsonEntity != null && jsonEntity.length > 0) {
            in = new ByteArrayInputStream(jsonEntity);
        }

        return sign(httpMethod, endpointURL, reqPath, headers, sigParams, in, accessKey, secretKey);
    }

    /**
     * Builds, signs and executes a request to an API endpoint using the provided credentials.
     * Signs the request using the Amazon Signature 4 algorithm and returns the response.
     * @param <T> t
     * @param accessKey access key
     * @param secretKey secret key
     * @param httpMethod the method (GET, POST...)
     * @param endpointURL protocol://host:port
     * @param reqPath the API resource path relative to the endpointURL
     * @param headers headers map
     * @param params parameters map
     * @param type type
     * @param body an object that will be serialized to JSON byte array (payload), could be null
     * @param success success handler
     * @param error error handler
     * @return a response object
     */
    @SuppressWarnings("unchecked")
    public <T> ParaRequest<T> invokeSignedRequest(String accessKey, String secretKey,
            int httpMethod, String endpointURL, String reqPath,
            Map<String, String> headers, Map<String, List<String>> params, T body, Class<?> type,
            Response.Listener<?> success, Response.ErrorListener error) {

        String url = endpointURL + reqPath;
        String queryString;
        byte[] entity = jsonBytes(body);
        boolean isJWT = StringUtils.startsWithIgnoreCase(secretKey, "Bearer");

        if (type == null) {
            type = (Class<T>) ((body != null) ? body.getClass() : Map.class);
        }
        url = setQueryParameters(url, params);
        Map<String, String> signedHeaders = null;
        if (!isJWT) {
            signedHeaders = signRequest(accessKey, secretKey, getMethodString(httpMethod),
                    endpointURL, reqPath, headers, params, entity);
        }
        if (headers == null) {
            headers = new HashMap<String, String>();
        }
        if (isJWT) {
            headers.put("Authorization", secretKey);
        } else {
            headers.put("Authorization", signedHeaders.get("Authorization"));
            headers.put("X-Amz-Date", signedHeaders.get("X-Amz-Date"));
        }
        return new ParaRequest(httpMethod, url, headers, entity, type, success, error);
    }

    private String getMethodString(int httpMethod) {
        String method;
        switch (httpMethod) {
            case Request.Method.GET: method = "GET"; break;
            case Request.Method.PUT: method = "PUT"; break;
            case Request.Method.POST: method = "POST"; break;
            case Request.Method.HEAD: method = "HEAD"; break;
            case Request.Method.PATCH: method = "PATCH"; break;
            case Request.Method.DELETE: method = "DELETE"; break;
            case Request.Method.OPTIONS: method = "OPTIONS"; break;
            default: method = "GET";
        }
        return method;
    }

    private byte[] jsonBytes(Object o) {
        if (o == null) {
            return new byte[0];
        }
        try {
            return ClientUtils.getJsonWriterNoIdent().writeValueAsBytes(o);
        } catch (Exception e) {
            logger.error("Object could not be converted to JSON byte[]", e);
            return new byte[0];
        }
    }

    private String setQueryParameters(String uri, Map<String, List<String>> params) {
        if (params != null) {
            List<String> paramz = new LinkedList<>();
            for (Map.Entry<String, List<String>> param : params.entrySet()) {
                String key = param.getKey();
                List<String> value = param.getValue();
                if (value != null && !value.isEmpty() && value.get(0) != null) {
                    for (String pv : value) {
                        paramz.add(key + "=" + Util.urlEncode(pv, false));
                    }
                }
            }
            if (!paramz.isEmpty()) {
                uri = uri + "?" + String.join("&", paramz);
            }
        }
        return uri;
    }
}
