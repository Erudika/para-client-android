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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang3.StringUtils;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Client utilities.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class ClientUtils {

    public static final String ALLOW_ALL = "*";
    private static final String PREFS_FILE = "ParaClientPrefs";
    private static final Pattern emailz = Pattern.compile(Email.EMAIL_PATTERN);
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final ObjectReader jsonReader;
    private static final ObjectWriter jsonWriter;

    static {
        jsonMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonMapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        jsonReader = jsonMapper.reader();
        jsonWriter = jsonMapper.writer();
    }

    private ClientUtils() { }

    /**
     * A Jackson {@code ObjectMapper}.
     *
     * @return JSON object mapper
     */
    public static ObjectMapper getJsonMapper() {
        return jsonMapper;
    }

    /**
     * A Jackson JSON reader.
     *
     * @param type the type to read
     * @return JSON object reader
     */
    public static ObjectReader getJsonReader(Class<?> type) {
        return jsonReader.forType(type);
    }

    /**
     * A Jackson JSON writer. Pretty print is on.
     *
     * @return JSON object writer
     */
    public static ObjectWriter getJsonWriter() {
        return jsonWriter;
    }

    /**
     * A Jackson JSON writer. Pretty print is off.
     *
     * @return JSON object writer with indentation disabled
     */
    public static ObjectWriter getJsonWriterNoIdent() {
        return jsonWriter.without(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Strips all symbols, punctuation, whitespace and control chars from a string.
     * @param str a dirty string
     * @return a clean string
     */
    public static String stripAndTrim(String str) {
        return stripAndTrim(str, "");
    }

    /**
     * Strips all symbols, punctuation, whitespace and control chars from a string.
     * @param str a dirty string
     * @param replaceWith a string to replace spaces with
     * @return a clean string
     */
    public static String stripAndTrim(String str, String replaceWith) {
        return StringUtils.isBlank(str) ? "" :
                str.replaceAll("[\\p{S}\\p{P}\\p{C}]", replaceWith).replaceAll("\\p{Z}+", " ").trim();
    }

    /**
     * Converts spaces to dashes.
     * @param str a string with spaces
     * @param replaceWith a string to replace spaces with
     * @return a string with dashes
     */
    public static String noSpaces(String str, String replaceWith) {
        return StringUtils.isBlank(str) ? "" : str.trim().replaceAll("[\\p{C}\\p{Z}]+",
                StringUtils.trimToEmpty(replaceWith)).toLowerCase();
    }

    /**
     * Formats a messages containing {0}, {1}... etc. Used for translation.
     * @param msg a message with placeholders
     * @param params objects used to populate the placeholders
     * @return a formatted message
     */
    public static String formatMessage(String msg, Object... params) {
        try {
            // required by MessageFormat, single quotes break string interpolation!
            msg = StringUtils.replace(msg, "'", "''");
            return StringUtils.isBlank(msg) ? "" : MessageFormat.format(msg, params);
        } catch (IllegalArgumentException e) {
            return msg;
        }
    }

    /**
     * Encodes a byte array to Base64
     * @param str the byte array
     * @return an encoded string
     */
    public static String base64enc(byte[] str) {
        if (str == null) {
            return "";
        }
        return new String(Base64.encode(str, Base64.DEFAULT));
    }

    /**
     * Encodes a byte array to Base64. URL safe.
     * @param str the byte array
     * @return an encoded string
     */
    public static String base64encURL(byte[] str) {
        if (str == null) {
            return "";
        }
        return new String(Base64.encode(str, Base64.URL_SAFE));
    }

    /**
     * Decodes a string from Base64
     * @param str the encoded string
     * @return a decoded string
     */
    public static String base64dec(String str) {
        if (str == null) {
            return "";
        }
        try {
            return new String(Base64.decode(str, Base64.DEFAULT), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            return "";
        }
    }

    /**
     * URL validation
     * @param url a URL
     * @return true if the URL is valid
     */
    public static boolean isValidURL(String url) {
        return toURL(url) != null;
    }

    /**
     * Email validation
     * @param url a URL
     * @return true if the URL is valid
     */
    public static boolean isValidEmail(String url) {
        return emailz.matcher(url).matches();
    }

    private static URL toURL(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        URL u;
        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
            // the URL is not in a valid form
            u = null;
        }
        return u;
    }

    /**
     * Quick and dirty singular to plural conversion.
     * @param singul a word
     * @return a guess of its plural form
     */
    public static String singularToPlural(String singul) {
        return StringUtils.isBlank(singul) ? singul :
                (singul.endsWith("s") ? singul + "es" :
                        (singul.endsWith("y") ? StringUtils.removeEndIgnoreCase(singul, "y") + "ies" :
                                singul + "s"));
    }

    /**
     * Checks if a class is primitive, String or a primitive wrapper.
     *
     * @param clazz a class
     * @return true if primitive or wrapper
     */
    public static boolean isBasicType(Class<?> clazz) {
        return (clazz == null) ? false : (clazz.isPrimitive()
                || clazz.equals(String.class)
                || clazz.equals(Long.class)
                || clazz.equals(Integer.class)
                || clazz.equals(Boolean.class)
                || clazz.equals(Byte.class)
                || clazz.equals(Short.class)
                || clazz.equals(Float.class)
                || clazz.equals(Double.class)
                || clazz.equals(Character.class));
    }

    /**
     * Populates a new ParaObject with data from a map.
     * @param data some data
     * @param <P> object type
     * @return a ParaObject
     */
    @SuppressWarnings("unchecked")
    public static <P extends ParaObject> P setFields(Class<P> type, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        if (type == null) {
            type = (Class<P>) Sysprop.class;
        }
        return getJsonMapper().convertValue(data, type);
    }

    public static void savePref(String key, String value, Context ctx) {
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            if (key != null && value != null) {
                editor.putString(key, value);
            }
            editor.commit();
        }
    }

    public static String loadPref(String key, Context ctx) {
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
            return prefs.getString(key, null);
        }
        return null;
    }

    public static void clearPref(String key, Context ctx) {
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
            prefs.edit().remove(key).commit();
        }
    }
}
