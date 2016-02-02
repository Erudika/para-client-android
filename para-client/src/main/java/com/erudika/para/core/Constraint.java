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
package com.erudika.para.core;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a validation constraint.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public abstract class Constraint {

    private String name;
    private Map<String, Object> payload;

    private Constraint(String name, Map<String, Object> payload) {
        this.name = name;
        this.payload = payload;
    }

    /**
     * The constraint name.
     * @return a name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the constraint.
     * @param name a name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * The payload (a map)
     * @return a map
     */
    public Map<String, Object> getPayload() {
        if (payload == null) {
            payload = new LinkedHashMap<String, Object>();
        }
        return payload;
    }

    /**
     * Sets the payload.
     * @param payload a map
     */
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    /**
     * Creates a new map representing a simple validation constraint.
     * @param name the name of the constraint
     * @return a map
     */
    static Map<String, Object> simplePayload(final String name) {
        if (name == null) {
            return null;
        }
        return new LinkedHashMap<String, Object>() {
            {
                put("message", "messages." + name);
            }
        };
    }

    /**
     * Creates a new map representing a Min validation constraint.
     * @param min the minimum value
     * @return a map
     */
    static Map<String, Object> minPayload(final Object min) {
        if (min == null) {
            return null;
        }
        return new LinkedHashMap<String, Object>() {
            {
                put("value", min);
                put("message", "messages.min");
            }
        };
    }

    /**
     * Creates a new map representing a Max validation constraint.
     * @param max the maximum value
     * @return a map
     */
    static Map<String, Object> maxPayload(final Object max) {
        if (max == null) {
            return null;
        }
        return new LinkedHashMap<String, Object>() {
            {
                put("value", max);
                put("message", "messages.max");
            }
        };
    }

    /**
     * Creates a new map representing a Size validation constraint.
     * @param min the minimum length
     * @param max the maximum length
     * @return a map
     */
    static Map<String, Object> sizePayload(final Object min, final Object max) {
        if (min == null || max == null) {
            return null;
        }
        return new LinkedHashMap<String, Object>() {
            {
                put("min", min);
                put("max", max);
                put("message", "messages.size");
            }
        };
    }

    /**
     * Creates a new map representing a Digits validation constraint.
     * @param i the max size of the integral part of the number
     * @param f the max size of the fractional part of the number
     * @return a map
     */
    static Map<String, Object> digitsPayload(final Object i, final Object f) {
        if (i == null || f == null) {
            return null;
        }
        return new LinkedHashMap<String, Object>() {
            {
                put("integer", i);
                put("fraction", f);
                put("message", "messages.digits");
            }
        };
    }

    /**
     * Creates a new map representing a Pattern validation constraint.
     * @param regex a regular expression
     * @return a map
     */
    static Map<String, Object> patternPayload(final Object regex) {
        if (regex == null) {
            return null;
        }
        return new LinkedHashMap<String, Object>() {
            {
                put("value", regex);
                put("message", "messages.pattern");
            }
        };
    }

    /**
     * The 'required' constraint - marks a field as required.
     * @return constraint
     */
    public static Constraint required() {
        return new Constraint("required", simplePayload("required")) {};
    }

    /**
     * The 'min' constraint - field must contain a number larger than or equal to min.
     * @param min the minimum value
     * @return constraint
     */
    public static Constraint min(final Object min) {
        return new Constraint("min", minPayload(min)) {};
    }

    /**
     * The 'max' constraint - field must contain a number smaller than or equal to max.
     * @param max the maximum value
     * @return constraint
     */
    public static Constraint max(final Object max) {
        return new Constraint("max", maxPayload(max)) {};
    }

    /**
     * The 'size' constraint - field must be a {@link String},
     * {@link Map}, {@link Collection} or array
     * with a given minimum and maximum length.
     * @param min the minimum length
     * @param max the maximum length
     * @return constraint
     */
    public static Constraint size(final Object min, final Object max) {
        return new Constraint("size", sizePayload(min, max)) {};
    }

    /**
     * The 'digits' constraint - field must be a {@link Number} or {@link String}
     * containing digits where the
     * number of digits in the integral part is limited by 'integer', and the
     * number of digits for the fractional part is limited
     * by 'fraction'.
     * @param integer the max number of digits for the integral part
     * @param fraction the max number of digits for the fractional part
     * @return constraint
     */
    public static Constraint digits(final Object integer, final Object fraction) {
        return new Constraint("digits", digitsPayload(integer, fraction)) {};
    }

    /**
     * The 'pattern' constraint - field must contain a value matching a regular expression.
     * @param regex a regular expression
     * @return constraint
     */
    public static Constraint pattern(final Object regex) {
        return new Constraint("pattern", patternPayload(regex)) {};
    }

    /**
     * The 'email' constraint - field must contain a valid email.
     * @return constraint
     */
    public static Constraint email() {
        return new Constraint("email", simplePayload("email")) {};
    }

    /**
     * The 'falsy' constraint - field value must not be equal to 'true'.
     * @return constraint
     */
    public static Constraint falsy() {
        return new Constraint("false", simplePayload("false")) {};
    }

    /**
     * The 'truthy' constraint - field value must be equal to 'true'.
     * @return constraint
     */
    public static Constraint truthy() {
        return new Constraint("true", simplePayload("true")) {};
    }

    /**
     * The 'future' constraint - field value must be a {@link Date} or a timestamp in the future.
     * @return constraint
     */
    public static Constraint future() {
        return new Constraint("future", simplePayload("future")) {};
    }

    /**
     * The 'past' constraint - field value must be a {@link Date} or a timestamp in the past.
     * @return constraint
     */
    public static Constraint past() {
        return new Constraint("past", simplePayload("past")) {};
    }

    /**
     * The 'url' constraint - field value must be a valid URL.
     * @return constraint
     */
    public static Constraint url() {
        return new Constraint("url", simplePayload("url")) {};
    }
}
