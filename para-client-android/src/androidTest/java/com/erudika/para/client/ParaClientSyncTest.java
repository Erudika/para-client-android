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
package com.erudika.para.client;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import com.android.volley.VolleyError;
import com.erudika.para.client.utils.Pager;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static com.android.volley.Response.ErrorListener;
import static com.android.volley.Response.Listener;
import static com.erudika.para.client.utils.ClientUtils.ALLOW_ALL;
import static com.erudika.para.core.Constraint.required;
import static org.junit.Assert.*;

/**
 * ParaClient integration tests - execute on device!
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@SuppressWarnings("unchecked")
public class ParaClientSyncTest {

    private static final Logger logger = LoggerFactory.getLogger(ParaClientSyncTest.class);
    private static ParaClient pc;
    private static ParaClient pc2;
    private static final String catsType = "cat";
    private static final String dogsType = "dog";
    private static final String APP_ID = "app:para";

    protected static Sysprop u;
    protected static Sysprop u1;
    protected static Sysprop u2;
    protected static Sysprop t;
    protected static Sysprop s1;
    protected static Sysprop s2;
    protected static Sysprop a1;
    protected static Sysprop a2;

    private static Context ctx;

    private static boolean ranOnce = false;

    public ParaClientSyncTest() {
    }

    private static ParaClient pc() {
        if (pc == null) {
            pc = new ParaClient("app:para", "xC2/S0vrq41lYlFliGmKfmuuQBe1ixf2DXbgzbCq0q6TIu6W66uH3g==", ctx);
            pc.setEndpoint("http://192.168.0.113:8080");
        }
        return pc;
    }

    private static ParaClient pc2() {
        if (pc2 == null) {
            pc2 = new ParaClient("app:para", null, ctx);
            pc2.setEndpoint("http://192.168.0.113:8080");
        }
        return pc2;
    }

    private static Sysprop u() {
        if (u == null) {
            u = new Sysprop("111");
            u.setName("John Doe");
            u.setTimestamp(System.currentTimeMillis());
            u.setTags(Arrays.asList(new String[]{"one", "two", "three"}));
        }
        return u;
    }

    private static Sysprop u1() {
        if (u1 == null) {
            u1 = new Sysprop("222");
            u1.setName("Joe Black");
            u1.setTimestamp(System.currentTimeMillis());
            u1.setTags(Arrays.asList(new String[]{"two", "four", "three"}));
        }
        return u1;
    }

    private static Sysprop u2() {
        if (u2 == null) {
            u2 = new Sysprop("333");
            u2.setName("Ann Smith");
            u2.setTimestamp(System.currentTimeMillis());
            u2.setTags(Arrays.asList(new String[]{"four", "five", "three"}));
        }
        return u2;
    }

    private static Sysprop t() {
        if (t == null) {
            t = new Sysprop("tag:test");
            t.setType("tag");
            t.addProperty("tag", "test");
            t.addProperty("count", 3);
            t.setTimestamp(System.currentTimeMillis());
        }
        return t;
    }

    private static Sysprop a1() {
        if (a1 == null) {
            a1 = new Sysprop("adr1");
            a1.setType("address");
            a1.setName("Place 1");
            a1.addProperty("address", "NYC");
            a1.addProperty("country", "US");
            a1.addProperty("latlng", "40.67,-73.94");
            a1.setParentid(u().getId());
            a1.setCreatorid(u().getId());
        }
        return a1;
    }

    private static Sysprop a2() {
        if (a2 == null) {
            a2 = new Sysprop("adr2");
            a2.setType("address");
            a2.setName("Place 2");
            a2.addProperty("address", "NYC");
            a2.addProperty("country", "US");
            a2.addProperty("latlng", "40.69,-73.95");
            a2.setParentid(t().getId());
            a2.setCreatorid(t().getId());
        }
        return a2;
    }

    private static Sysprop s1() {
        if (s1 == null) {
            s1 = new Sysprop("s1");
            s1.addProperty("text", "This is a little test sentence. Testing, one, two, three.");
            s1.setTimestamp(System.currentTimeMillis());
        }
        return s1;
    }

    private static Sysprop s2() {
        if (s2 == null) {
            s2 = new Sysprop("s2");
            s2.addProperty("text", "We are testing this thing. This sentence is a test. One, two.");
            s2.setTimestamp(System.currentTimeMillis());
        }
        return s2;
    }

    @BeforeClass
    public static void setUp() throws Exception {
        ctx = InstrumentationRegistry.getContext();

        if (!ranOnce) {
            ranOnce = true;
            pc().me(null, new ErrorListener() {
                public void onErrorResponse(VolleyError volleyError) {
                    fail("Para server must be running before testing!");
                }
            });

            List<ParaObject> parr = new ArrayList<ParaObject>();
            parr.addAll(Arrays.asList(u(), u1(), u2(), t(), s1(), s2(), a1(), a2()));

            pc().createAll(parr, new Listener<List<ParaObject>>() {
                public void onResponse(List<ParaObject> paraObjects) {
                    logger.info("{} Objects created!", paraObjects.size());
                }
            });

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testCRUD() {
        assertNull(pc().createSync(null));

        Sysprop t1 = new Sysprop("tag:test1");
        t1.addProperty("tag", "test1");
        t1.setType("tag");
        ParaObject ux = new Sysprop("u1");
        ux.setType("user");

        assertNotNull(pc().createSync(t1));
        assertNull(pc().createSync(ux));

        assertNull(pc().readSync(null, null));
        assertNull(pc().readSync(null, ""));

        Sysprop trID = pc().readSync(t1.getId());
        assertNotNull(trID);
        assertNotNull(trID.getTimestamp());
        assertEquals(t1.getProperty("tag"), trID.getProperty("tag"));

        Sysprop tr = pc().readSync(t1.getClass(), t1.getId());
        assertNotNull(tr);
        assertNotNull(tr.getTimestamp());
        assertEquals(t1.getProperty("tag"), tr.getProperty("tag"));

        tr.addProperty("count", 15);
        Sysprop tu = pc().updateSync(tr);
        assertNull(pc().updateSync(new Sysprop("null")));
        assertNotNull(tu);
        assertEquals(tu.getProperty("count"), tr.getProperty("count"));
        assertNotNull(tu.getUpdated());

        Sysprop s = new Sysprop();
        s.setType(dogsType);
        s.addProperty("foo", "bark!");
        s = pc().createSync(s);

        Sysprop dog = pc().readSync(s.getClass(), s.getId());
        assertTrue(dog.hasProperty("foo"));
        assertEquals("bark!", dog.getProperty("foo"));

        pc().deleteSync(t1);
        pc().deleteSync(dog);
        assertNull(pc().readSync(tr.getClass(), tr.getId()));
    }

    @Test
    public void testBatchCRUD() throws InterruptedException {
        ArrayList<Sysprop> dogs = new ArrayList<Sysprop>();
        for (int i = 0; i < 3; i++) {
            Sysprop s = new Sysprop();
            s.setType(dogsType);
            s.addProperty("foo", "bark!");
            dogs.add(s);
        }

        assertTrue(pc().createAllSync(null).isEmpty());
        List<Sysprop> l1 = pc().createAllSync(dogs);
        assertEquals(3, l1.size());
        assertNotNull(l1.get(0).getId());

        assertTrue(pc().readAllSync(null).isEmpty());
        ArrayList<String> nl = new ArrayList<String>(3);
        assertTrue(pc().readAllSync(nl).isEmpty());
        nl.add(l1.get(0).getId());
        nl.add(l1.get(1).getId());
        nl.add(l1.get(2).getId());
        List<Sysprop> l2 = pc().readAllSync(nl);
        assertEquals(3, l2.size());
        assertEquals(l1.get(0).getId(), l2.get(0).getId());
        assertEquals(l1.get(1).getId(), l2.get(1).getId());
        assertTrue(l2.get(0).hasProperty("foo"));
        assertEquals("bark!", l2.get(0).getProperty("foo"));

        assertTrue(pc().updateAllSync(null).isEmpty());

        Sysprop part1 = new Sysprop(l1.get(0).getId());
        Sysprop part2 = new Sysprop(l1.get(1).getId());
        Sysprop part3 = new Sysprop(l1.get(2).getId());
        part1.setType(dogsType);
        part2.setType(dogsType);
        part3.setType(dogsType);

        part1.addProperty("custom", "prop");
        part1.setName("NewName1");
        part2.setName("NewName2");
        part3.setName("NewName3");

        List<Sysprop> l3 = pc().updateAllSync(Arrays.asList(part1, part2, part3));

        assertTrue(l3.get(0).hasProperty("custom"));
        assertEquals(dogsType, l3.get(0).getType());
        assertEquals(dogsType, l3.get(1).getType());
        assertEquals(dogsType, l3.get(2).getType());

        assertEquals(part1.getName(), l3.get(0).getName());
        assertEquals(part2.getName(), l3.get(1).getName());
        assertEquals(part3.getName(), l3.get(2).getName());

        pc().deleteAllSync(nl);
        Thread.sleep(1000);

        List<Sysprop> l4 = pc().listSync(dogsType);
        assertTrue(l4.isEmpty());

        Map<String, String> datatypes = (Map<String, String>)
                ((Sysprop) pc().meSync()).getProperty("datatypes");
        assertTrue(datatypes.containsValue(dogsType));
    }

    @Test
    public void testList() throws InterruptedException {
        ArrayList<ParaObject> cats = new ArrayList<ParaObject>();
        for (int i = 0; i < 3; i++) {
            Sysprop s = new Sysprop(catsType + i);
            s.setType(catsType);
            cats.add(s);
        }
        pc().createAllSync(cats);
        Thread.sleep(1000);

        assertTrue(pc().listSync(null).isEmpty());
        assertTrue(pc().listSync("").isEmpty());

        List<Sysprop> list1 = pc().listSync(catsType);
        assertFalse(list1.isEmpty());
        assertEquals(3, list1.size());
        assertEquals(Sysprop.class, list1.get(0).getClass());

        List<Sysprop> list2 = pc().listSync(catsType, new Pager(2));
        assertFalse(list2.isEmpty());
        assertEquals(2, list2.size());

        ArrayList<String> nl = new ArrayList<String>(3);
        nl.add(cats.get(0).getId());
        nl.add(cats.get(1).getId());
        nl.add(cats.get(2).getId());
        pc().deleteAllSync(nl);

        Map<String, String> datatypes = (Map<String, String>)
                ((Sysprop) pc().meSync()).getProperty("datatypes");
        assertTrue(datatypes.containsValue(catsType));
    }


    @Test
    public void testSearch() throws InterruptedException {
        assertNull(pc().findByIdSync(null));
        assertNull(pc().findByIdSync(""));
        assertNotNull(pc().findByIdSync(u().getId()));
        assertNotNull(pc().findByIdSync(t().getId()));

        assertTrue(pc().findByIdsSync(null).isEmpty());
        assertEquals(3, pc().findByIdsSync(Arrays.asList(u().getId(), u1().getId(), u2().getId())).size());

        assertTrue(pc().findNearbySync(null, null, 100, 1, 1).isEmpty());
        assertFalse(pc().findNearbySync(u().getType(), "*", 10, 40.60, -73.90).isEmpty());
        assertFalse(pc().findNearbySync(t().getType(), "*", 10, 40.62, -73.91).isEmpty());

        assertTrue(pc().findPrefixSync(null, null, "").isEmpty());
        assertTrue(pc().findPrefixSync("", "null", "xx").isEmpty());
        assertFalse(pc().findPrefixSync(u().getType(), "name", "Ann").isEmpty());

        assertFalse(pc().findQuerySync(null, null).isEmpty());
        assertFalse(pc().findQuerySync("", "*").isEmpty());
        assertEquals(2, pc().findQuerySync(a1().getType(), "country:US").size());
        assertFalse(pc().findQuerySync(u().getType(), "Ann*").isEmpty());
        assertFalse(pc().findQuerySync(u().getType(), "Ann*").isEmpty());
        assertTrue(pc().findQuerySync(null, "*").size() > 4);

        Pager p = new Pager();
        assertEquals(0, p.getCount());
        List<ParaObject> res = pc().findQuerySync(u().getType(), "*", p);
        assertEquals(res.size(), p.getCount());
        assertTrue(p.getCount() > 0);

        assertTrue(pc().findSimilarSync(t().getType(), "", null, null).isEmpty());
        assertTrue(pc().findSimilarSync(t().getType(), "", new String[0], "").isEmpty());
        res = pc().findSimilarSync(s1().getType(), s1().getId(), new String[]{"properties.text"},
                (String) s1().getProperty("text"));
        assertFalse(res.isEmpty());
        assertEquals(s2(), res.get(0));

        int i0 = pc().findTaggedSync(u().getType(), null).size();
        int i1 = pc().findTaggedSync(u().getType(), new String[]{"two"}).size();
        int i2 = pc().findTaggedSync(u().getType(), new String[]{"one", "two"}).size();
        int i3 = pc().findTaggedSync(u().getType(), new String[]{"three"}).size();
        int i4 = pc().findTaggedSync(u().getType(), new String[]{"four", "three"}).size();
        int i5 = pc().findTaggedSync(u().getType(), new String[]{"five", "three"}).size();
        int i6 = pc().findTaggedSync(t().getType(), new String[]{"four", "three"}).size();

        assertEquals(0, i0);
        assertEquals(2, i1);
        assertEquals(1, i2);
        assertEquals(3, i3);
        assertEquals(2, i4);
        assertEquals(1, i5);
        assertEquals(0, i6);

        assertFalse(pc().findTagsSync(null).isEmpty());
        assertFalse(pc().findTagsSync("").isEmpty());
        assertTrue(pc().findTagsSync("unknown").isEmpty());
        assertTrue(pc().findTagsSync((String) t().getProperty("tag")).size() >= 1);

        assertEquals(3, pc().findTermInListSync(u().getType(), "id",
                Arrays.asList(u().getId(), u1().getId(), u2().getId(), "xxx", "yyy")).size());

        // many terms
        Map<String, Object> terms = new HashMap<String, Object>();
//		terms.put("type", u().getType());
        terms.put("id", u().getId());

        Map<String, Object> terms1 = new HashMap<String, Object>();
        terms1.put("type", null);
        terms1.put("id", " ");

        Map<String, Object> terms2 = new HashMap<String, Object>();
        terms2.put(" ", "bad");
        terms2.put("", "");

        assertEquals(1, pc().findTermsSync(u().getType(), terms, true).size());
        assertTrue(pc().findTermsSync(u().getType(), terms1, true).isEmpty());
        assertTrue(pc().findTermsSync(u().getType(), terms2, true).isEmpty());

        // single term
        assertTrue(pc().findTermsSync(null, null, true).isEmpty());
        assertTrue(pc().findTermsSync(u().getType(), Collections.singletonMap("", null), true).isEmpty());
        assertTrue(pc().findTermsSync(u().getType(), Collections.singletonMap("", ""), true).isEmpty());
        assertTrue(pc().findTermsSync(u().getType(), Collections.singletonMap("term", null), true).isEmpty());
        assertTrue(pc().findTermsSync(u().getType(), Collections.singletonMap("type", u().getType()), true).size() >= 2);

        assertTrue(pc().findWildcardSync(u().getType(), null, null).isEmpty());
        assertTrue(pc().findWildcardSync(u().getType(), "", "").isEmpty());
        assertFalse(pc().findWildcardSync(u().getType(), "name", "An*").isEmpty());

        assertTrue(pc().getCountSync(null).intValue() > 4);
        assertFalse(pc().getCountSync("").intValue() == 0);
        assertEquals(0, pc().getCountSync("test").intValue());
        assertTrue(pc().getCountSync(u().getType()).intValue() >= 3);

        assertEquals(0L, pc().getCountSync(null, null).intValue());
        assertEquals(0L, pc().getCountSync(u().getType(), Collections.singletonMap("id", " ")).intValue());
        assertEquals(1L, pc().getCountSync(u().getType(), Collections.singletonMap("id", u().getId())).intValue());
        assertTrue(pc().getCountSync(null, Collections.singletonMap("type", u().getType())).intValue() > 1);
    }

    @Test
    public void testLinks() throws InterruptedException {
        assertNotNull(pc().linkSync(u(), t().getId()));
        assertNotNull(pc().linkSync(u(), u2().getId()));

        assertFalse(pc().isLinkedSync(u(), null));
        assertTrue(pc().isLinkedSync(u(), t()));
        assertTrue(pc().isLinkedSync(u(), u2()));

        Thread.sleep(1000);

        assertEquals(1, pc().getLinkedObjectsSync(u(), "tag").size());
        assertEquals(1, pc().getLinkedObjectsSync(u(), "sysprop").size());

        assertEquals(0, pc().countLinksSync(u(), null).intValue());
        assertEquals(1, pc().countLinksSync(u(), "tag").intValue());
        assertEquals(1, pc().countLinksSync(u(), "sysprop").intValue());

        pc().unlinkAllSync(u());

        assertFalse(pc().isLinkedSync(u(), t()));
        assertFalse(pc().isLinkedSync(u(), u2()));
    }

    @Test
    public void testUtils() {
        String id1 = pc().newIdSync();
        String id2 = pc().newIdSync();
        assertNotNull(id1);
        assertFalse(id1.isEmpty());
        assertFalse(id1.equals(id2));

        final Long ts = pc().getTimestampSync();
        assertNotNull(ts);
        assertFalse(ts.intValue() == 0);

        String date1 = pc().formatDateSync("MM dd yyyy", Locale.US);
        String date2 = new SimpleDateFormat("MM dd yyyy").format(new Date());
        assertEquals(date1, date2);

        String ns1 = pc().noSpacesSync(" test  123		test ", "");
        String ns2 = "test123test";
        assertEquals(ns1, ns2);

        String st1 = pc().stripAndTrimSync(" %^&*( cool )		@!");
        String st2 = "cool";
        assertEquals(st1, st2);

        String md1 = pc().markdownToHtmlSync("### hello **test**");
        String md2 = "<h3>hello <strong>test</strong></h3>";
        assertEquals(md1.trim(), md2);

        String ht1 = pc().approximatelySync(15000);
        String ht2 = "15s";
        assertEquals(ht1, ht2);
    }

    @Test
    public void testMisc() {
        Map<String, String> types = pc().typesSync();
        assertNotNull(types);
        assertFalse(types.isEmpty());
        assertTrue(types.containsKey("users"));

        assertEquals(APP_ID, pc().meSync().getId());
    }

    @Test
    public void testValidationConstraints() {
        // Validations
        String kittenType = "kitten";
        Map<String, ?> constraints = pc().validationConstraintsSync();
        assertNotNull(constraints);
        assertFalse(constraints.isEmpty());
        assertTrue(constraints.containsKey("app"));
        assertTrue(constraints.containsKey("user"));

        Map<String, Map<String, Map<String, Map<String, ?>>>> constraint = pc().validationConstraintsSync("app");
        assertFalse(constraint.isEmpty());
        assertTrue(constraint.containsKey("app"));
        assertEquals(1, constraint.size());

        pc().addValidationConstraintSync(kittenType, "paws", required());
        constraint = pc().validationConstraintsSync(kittenType);
        assertTrue(constraint.get(kittenType).containsKey("paws"));

        Sysprop ct = new Sysprop("felix");
        ct.setType(kittenType);
        Sysprop ct2 = null;
        try {
            // validation fails
            ct2 = pc().createSync(ct);
        } catch (Exception e) {}

        assertNull(ct2);
        ct.addProperty("paws", "4");
        assertNotNull(pc().createSync(ct));

        pc().removeValidationConstraintSync(kittenType, "paws", "required");
        constraint = pc().validationConstraintsSync(kittenType);
        assertFalse(constraint.containsKey(kittenType));
    }

    @Test
    public void testResourcePermissions() {
        // Permissions
        Map<String, Map<String, List<String>>> permits = pc().resourcePermissionsSync();
        assertNotNull(permits);

        assertTrue(pc().grantResourcePermissionSync(null, dogsType, new String[0]).isEmpty());
        assertTrue(pc().grantResourcePermissionSync(" ", "", new String[0]).isEmpty());

        pc().grantResourcePermissionSync(u1().getId(), dogsType, new String[]{"GET"});
        permits = pc().resourcePermissionsSync(u1().getId());
        assertTrue(permits.containsKey(u1().getId()));
        assertTrue(permits.get(u1().getId()).containsKey(dogsType));
        assertTrue(pc().isAllowedToSync(u1().getId(), dogsType, "GET"));
        assertFalse(pc().isAllowedToSync(u1().getId(), dogsType, "POST"));
        // anonymous permissions
        assertFalse(pc().isAllowedToSync(ALLOW_ALL, "utils/timestamp", "GET"));
        assertNotNull(pc().grantResourcePermissionSync(ALLOW_ALL, "utils/timestamp", new String[]{"GET"}, true));
        assertTrue(pc2().getTimestampSync() > 0);
        assertFalse(pc().isAllowedToSync(ALLOW_ALL, "utils/timestamp", "DELETE"));

        permits = pc().resourcePermissionsSync();
        assertTrue(permits.containsKey(u1().getId()));
        assertTrue(permits.get(u1().getId()).containsKey(dogsType));

        pc().revokeResourcePermissionSync(u1().getId(), dogsType);
        permits = pc().resourcePermissionsSync(u1().getId());
        assertFalse(permits.get(u1().getId()).containsKey(dogsType));
        assertFalse(pc().isAllowedToSync(u1().getId(), dogsType, "GET"));
        assertFalse(pc().isAllowedToSync(u1().getId(), dogsType, "POST"));

        final String[] WRITE = new String[]{"POST", "PUT", "PATCH", "DELETE"};

        pc().grantResourcePermissionSync(u2().getId(), ALLOW_ALL, WRITE);
        assertTrue(pc().isAllowedToSync(u2().getId(), dogsType, "PUT"));
        assertTrue(pc().isAllowedToSync(u2().getId(), dogsType, "PATCH"));

        pc().revokeAllResourcePermissionsSync(u2().getId());
        permits = pc().resourcePermissionsSync();
        assertFalse(pc().isAllowedToSync(u2().getId(), dogsType, "PUT"));
        assertFalse(permits.containsKey(u2().getId()));
//		assertTrue(permits.get(u2().getId()).isEmpty());

        pc().grantResourcePermissionSync(u1().getId(), dogsType, WRITE);
        pc().grantResourcePermissionSync(ALLOW_ALL, catsType, WRITE);
        pc().grantResourcePermissionSync(ALLOW_ALL, ALLOW_ALL, new String[]{"GET"});
        // user-specific permissions are in effect
        assertTrue(pc().isAllowedToSync(u1().getId(), dogsType, "PUT"));
        assertFalse(pc().isAllowedToSync(u1().getId(), dogsType, "GET"));
        assertTrue(pc().isAllowedToSync(u1().getId(), catsType, "PUT"));
        assertTrue(pc().isAllowedToSync(u1().getId(), catsType, "GET"));

        pc().revokeAllResourcePermissionsSync(u1().getId());
        // user-specific permissions not found so check wildcard
        assertFalse(pc().isAllowedToSync(u1().getId(), dogsType, "PUT"));
        assertTrue(pc().isAllowedToSync(u1().getId(), dogsType, "GET"));
        assertTrue(pc().isAllowedToSync(u1().getId(), catsType, "PUT"));
        assertTrue(pc().isAllowedToSync(u1().getId(), catsType, "GET"));

        pc().revokeResourcePermissionSync(ALLOW_ALL, catsType);
        // resource-specific permissions not found so check wildcard
        assertFalse(pc().isAllowedToSync(u1().getId(), dogsType, "PUT"));
        assertFalse(pc().isAllowedToSync(u1().getId(), catsType, "PUT"));
        assertTrue(pc().isAllowedToSync(u1().getId(), dogsType, "GET"));
        assertTrue(pc().isAllowedToSync(u1().getId(), catsType, "GET"));
        assertTrue(pc().isAllowedToSync(u2().getId(), dogsType, "GET"));
        assertTrue(pc().isAllowedToSync(u2().getId(), catsType, "GET"));

        pc().revokeAllResourcePermissionsSync(ALLOW_ALL);
        pc().revokeAllResourcePermissionsSync(u1().getId());
    }

    @Test
    public void testAppSettings() {
        Map<String, Object> settings = pc().appSettingsSync();
        assertNotNull(settings);
        assertTrue(settings.isEmpty());

        pc().addAppSettingSync("", null);
        pc().addAppSettingSync(" ", " ");
        pc().addAppSettingSync(null, " ");
        pc().addAppSettingSync("prop1", 1);
        pc().addAppSettingSync("prop2", true);
        pc().addAppSettingSync("prop3", "string");

        assertEquals(3, pc().appSettingsSync().size());
        assertEquals(pc().appSettingsSync(), pc().appSettingsSync(null));
        assertEquals(Collections.singletonMap("value", 1), pc().appSettingsSync("prop1"));
        assertEquals(Collections.singletonMap("value", true), pc().appSettingsSync("prop2"));
        assertEquals(Collections.singletonMap("value", "string"), pc().appSettingsSync("prop3"));

        pc().removeAppSettingSync("prop3");
        pc().removeAppSettingSync(" ");
        pc().removeAppSettingSync(null);
        assertTrue(pc().appSettingsSync("prop3").isEmpty());
        assertEquals(2, pc().appSettingsSync().size());
        pc().setAppSettingsSync(new HashMap<String, Object>(0));
    }

    @Test
    public void testAccessTokens() {
        assertNull(pc().getAccessToken());
        assertNull(pc().signInSync("facebook", "test_token"));
        pc().signOut();
        assertFalse(pc().revokeAllTokensSync());
    }
}
