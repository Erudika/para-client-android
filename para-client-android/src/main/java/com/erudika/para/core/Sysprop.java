/*
 * Copyright 2013-2021 Erudika. https://erudika.com
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

import com.erudika.para.client.utils.ClientUtils;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * A generic system class for storing data.
 * It is essentially a map of keys and values.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class Sysprop implements ParaObject {

    private String id;
    private Long timestamp;
    private String type;
    private String appid;
    private String parentid;
    private String creatorid;
    private Long updated;
    private String name;
    private List<String> tags;
    private Integer votes;
    private Long version;
    private Boolean stored;
    private Boolean indexed;
    private Boolean cached;
    private String plural;

    private Map<String, Object> properties;

    /**
     * No-args constructor
     */
    public Sysprop() {
        this(null);
    }

    /**
     * The default constructor
     * @param id the object id
     */
    public Sysprop(String id) {
        setId(id);
        setName(getName());
    }

    /**
     * Adds a new key/value pair to the map.
     * @param name a key
     * @param value a value
     * @return this
     */
    @JsonAnySetter
    public Sysprop addProperty(String name, Object value) {
        if (!StringUtils.isBlank(name) && value != null) {
            getProperties().put(name, value);
        }
        return this;
    }

    /**
     * Returns the value of a property for a given key
     * @param name the key
     * @return the value
     */
    public Object getProperty(String name) {
        if (!StringUtils.isBlank(name)) {
            return getProperties().get(name);
        }
        return null;
    }

    /**
     * Removes a property from the map
     * @param name the key
     * @return this
     */
    public Sysprop removeProperty(String name) {
        if (!StringUtils.isBlank(name)) {
            getProperties().remove(name);
        }
        return this;
    }

    /**
     * Checks for the existence of a property
     * @param name the key
     * @return true if a property with this key exists
     */
    public boolean hasProperty(String name) {
        if (StringUtils.isBlank(name)) {
            return false;
        }
        return getProperties().containsKey(name);
    }

    /**
     * A map of all properties (key/values)
     * @return a map
     */
    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = new HashMap<String, Object>();
        }
        return properties;
    }

    /**
     * Overwrites the map.
     * @param properties a new map
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    ////////////////////////////////////////////////////////

    public final String getId() {
        return id;
    }

    public final void setId(String id) {
        this.id = id;
    }

    public final String getType() {
        type = (type == null) ? this.getClass().getSimpleName().toLowerCase() : type;
        return type;
    }

    public final void setType(String type) {
        this.type = type;
    }

    public String getAppid() {
        appid = (appid == null) ? "para" : appid;
        return appid;
    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getObjectURI() {
        String defurl = "/".concat(ClientUtils.urlEncode(getType()));
        return (getId() != null) ? defurl.concat("/").concat(ClientUtils.urlEncode(getId())) : defurl;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public Boolean getStored() {
        return stored;
    }

    @Override
    public void setStored(Boolean isStored) {
        this.stored = isStored;
    }

    @Override
    public Boolean getIndexed() {
        return indexed;
    }

    @Override
    public void setIndexed(Boolean isIndexed) {
        this.indexed = isIndexed;
    }

    @Override
    public Boolean getCached() {
        return cached;
    }

    @Override
    public void setCached(Boolean isCached) {
        this.cached = isCached;
    }

    public Long getTimestamp() {
        return (timestamp != null && timestamp != 0) ? timestamp : null;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getCreatorid() {
        return creatorid;
    }

    public void setCreatorid(String creatorid) {
        this.creatorid = creatorid;
    }

    public final String getName() {
        return this.name;
    }

    public final void setName(String name) {
        this.name = (name == null || !name.isEmpty()) ? name : this.name;
    }

    public String getPlural() {
        return this.plural;
    }

    public void setPlural(String plural) {
        this.plural = plural;
    }

    public String getParentid() {
        return parentid;
    }

    public void setParentid(String parentid) {
        this.parentid = parentid;
    }

    public Long getUpdated() {
        return (updated != null && updated != 0) ? updated : null;
    }

    public void setUpdated(Long updated) {
        this.updated = updated;
    }

    public Integer getVotes() {
        return (votes == null) ? 0 : votes;
    }

    public void setVotes(Integer votes) {
        this.votes = votes;
    }

    public Long getVersion() {
        return (version == null) ? 0 : version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + (id == null ? "" : id).hashCode() +
                (name == null ? "" : name).hashCode();
        return hash;
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Sysprop other = (Sysprop) obj;
        if (getId() == null || !getId().equals(other.getId())) {
            return false;
        }
        return true;
    }

    public String toString() {
        try {
            return ClientUtils.getJsonWriter().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "ParaObject #" + id;
        }
    }
}
