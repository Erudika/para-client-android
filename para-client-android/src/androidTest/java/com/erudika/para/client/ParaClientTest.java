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

import android.app.Instrumentation;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import static com.android.volley.Response.*;
import com.android.volley.VolleyError;
import com.erudika.para.client.test.TestActivity;
import com.erudika.para.client.utils.Pager;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Sysprop;
import static com.erudika.para.core.Constraint.*;
import static com.erudika.para.client.utils.ClientUtils.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ParaClient integration tests - execute on device!
 * @author Alex Bogdanovski [alex@erudika.com]
 */
@SmallTest
@SuppressWarnings("unchecked")
public class ParaClientTest extends ActivityInstrumentationTestCase2<TestActivity> {

    private static final Logger logger = LoggerFactory.getLogger(ParaClientTest.class);
    private ParaClient pc;
    private ParaClient pc2;
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
    private Instrumentation inst;

    private static boolean ranOnce = false;

    public ParaClientTest() {
        super(TestActivity.class);
    }

    private ParaClient pc() {
        if (pc == null) {
            pc = new ParaClient("app:para", "+txE6EaXYLFSm4zXMkHOJRcFS6mXGcvJWHvV2Xm/rr6ei22M7vcUJw==", ctx);
            pc.setEndpoint("http://192.168.0.114:8080");
        }
        return pc;
    }

    private ParaClient pc2() {
        if (pc2 == null) {
            pc2 = new ParaClient("app:para", null, ctx);
            pc2.setEndpoint("http://192.168.0.114:8080");
        }
        return pc2;
    }

    private Sysprop u() {
        if (u == null) {
            u = new Sysprop("111");
            u.setName("John Doe");
            u.setTimestamp(System.currentTimeMillis());
            u.setTags(Arrays.asList(new String[]{"one", "two", "three"}));
        }
        return u;
    }

    private Sysprop u1() {
        if (u1 == null) {
            u1 = new Sysprop("222");
            u1.setName("Joe Black");
            u1.setTimestamp(System.currentTimeMillis());
            u1.setTags(Arrays.asList(new String[]{"two", "four", "three"}));
        }
        return u1;
    }

    private Sysprop u2() {
        if (u2 == null) {
            u2 = new Sysprop("333");
            u2.setName("Ann Smith");
            u2.setTimestamp(System.currentTimeMillis());
            u2.setTags(Arrays.asList(new String[]{"four", "five", "three"}));
        }
        return u2;
    }

    private Sysprop t() {
        if (t == null) {
            t = new Sysprop("tag:test");
            t.setType("tag");
            t.addProperty("tag", "test");
            t.addProperty("count", 3);
            t.setTimestamp(System.currentTimeMillis());
        }
        return t;
    }

    private Sysprop a1() {
        if (a1 == null) {
            a1 = new Sysprop("adr1");
            a1.setType("address");
            a1.setName("Place 1");
            a1.addProperty("address", "NYC");
            a1.addProperty("country", "US");
            a1.addProperty("latlng", "40.67,-73.94");
            a1.setParentid(u.getId());
            a1.setCreatorid(u.getId());
        }
        return a1;
    }

    private Sysprop a2() {
        if (a2 == null) {
            a2 = new Sysprop("adr2");
            a2.setType("address");
            a2.setName("Place 2");
            a2.addProperty("address", "NYC");
            a2.addProperty("country", "US");
            a2.addProperty("latlng", "40.69,-73.95");
            a2.setParentid(t.getId());
            a2.setCreatorid(t.getId());
        }
        return a2;
    }

    private Sysprop s1() {
        if (s1 == null) {
            s1 = new Sysprop("s1");
            s1.setName("This is a little test sentence. Testing, one, two, three.");
            s1.setTimestamp(System.currentTimeMillis());
        }
        return s1;
    }

    private Sysprop s2() {
        if (s2 == null) {
            s2 = new Sysprop("s2");
            s2.setName("We are testing this thing. This sentence is a test. One, two.");
            s2.setTimestamp(System.currentTimeMillis());
        }
        return s2;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        inst = this.getInstrumentation();
        ctx = inst.getTargetContext();

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
        pc().create(null, new Listener<ParaObject>() {
            public void onResponse(ParaObject res) {
                assertNull(res);
            }
        });
        pc().read(null, new Listener<ParaObject>() {
            public void onResponse(ParaObject res) {
                assertNull(res);
            }
        });
        pc().read(null, "", new Listener<ParaObject>() {
            public void onResponse(ParaObject res) {
                assertNull(res);
            }
        });

        final ParaObject t1 = new Sysprop("test1");
        t1.setType("tag");
        ParaObject ux = new Sysprop("u1");
        ux.setType("user");

        pc().create(t1, new Listener<ParaObject>() {
            public void onResponse(final ParaObject t1) {
                assertNotNull(t1);
                pc().read(t1.getId(), new Listener<ParaObject>() {
                    public void onResponse(ParaObject trID) {
                        assertNotNull(trID);
                        assertNotNull(trID.getTimestamp());
                        assertEquals(t1.getId(), trID.getId());
                    }
                });
                pc().read(Sysprop.class, t1.getId(), new Listener<ParaObject>() {
                    public void onResponse(final ParaObject tr) {
                        assertNotNull(tr);
                        assertNotNull(tr.getTimestamp());
                        assertEquals(t1.getId(), tr.getId());

                        ((Sysprop) tr).addProperty("count", 15);
                        pc().update(tr, new Listener<ParaObject>() {
                            public void onResponse(ParaObject tu) {
                                assertNotNull(tu);
                                assertEquals(((Sysprop) tu).getProperty("count"),
                                        ((Sysprop) tr).getProperty("count"));
                                assertNotNull(tu.getUpdated());
                            }
                        });

                        pc().update(new Sysprop("null"), new Listener<ParaObject>() {
                            public void onResponse(ParaObject res) {
                                assertNull(res);
                            }
                        });
                    }
                });
            }
        });

        pc().create(ux, new Listener<ParaObject>() {
            public void onResponse(ParaObject ux) {
                fail("user should not be created");
            }
        }, new ErrorListener() {
            public void onErrorResponse(VolleyError volleyError) {
                assertTrue(true);
            }
        });

        Sysprop s = new Sysprop();
        s.setType(dogsType);
        s.addProperty("foo", "bark!");
        pc().create(s, new Listener<ParaObject>() {
            public void onResponse(ParaObject s) {
                assertNotNull(s);

                pc().read(Sysprop.class, s.getId(), new Listener<Sysprop>() {
                    public void onResponse(final Sysprop dog) {
                        assertTrue(dog.hasProperty("foo"));
                        assertEquals("bark!", dog.getProperty("foo"));
                        pc().delete(dog, null);
                    }
                });
            }
        });

        pc().delete(t1, new Listener<ParaObject>() {
            public void onResponse(ParaObject res) {
                assertNull(res);
                pc().read(t1.getId(), new Listener<ParaObject>() {
                    public void onResponse(ParaObject res) {
                        assertNull(res);
                    }
                });
            }
        });
    }

    @Test
    public void testBatchCRUD() {
        ArrayList<ParaObject> dogs = new ArrayList<ParaObject>();
        for (int i = 0; i < 3; i++) {
            Sysprop s = new Sysprop();
            s.setType(dogsType);
            s.addProperty("foo", "bark!");
            dogs.add(s);
        }

        pc().createAll(null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });

        pc().createAll(dogs, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                final List<ParaObject> l1 = res;
                assertEquals(3, l1.size());
                assertNotNull(l1.get(0).getId());

                final ArrayList<String> nl = new ArrayList<String>(3);
                nl.add(l1.get(0).getId());
                nl.add(l1.get(1).getId());
                nl.add(l1.get(2).getId());
                pc().readAll(nl, new Listener<List<ParaObject>>() {
                    public void onResponse(List<ParaObject> res) {
                        List<ParaObject> l2 = res;
                        assertEquals(3, l2.size());
                        assertEquals(l1.get(0).getId(), l2.get(0).getId());
                        assertEquals(l1.get(1).getId(), l2.get(1).getId());
                        assertTrue(((Sysprop) l2.get(0)).hasProperty("foo"));
                        assertEquals("bark!", ((Sysprop) l2.get(0)).getProperty("foo"));
                    }
                });

                final ParaObject part1 = new Sysprop(l1.get(0).getId());
                final ParaObject part2 = new Sysprop(l1.get(1).getId());
                final ParaObject part3 = new Sysprop(l1.get(2).getId());
                part1.setType(dogsType);
                part2.setType(dogsType);
                part3.setType(dogsType);

                ((Sysprop) part1).addProperty("custom", "prop");
                part1.setName("NewName1");
                part2.setName("NewName2");
                part3.setName("NewName3");

                pc().updateAll(Arrays.asList(part1, part2, part3), new Listener<List<ParaObject>>() {
                    public void onResponse(List<ParaObject> res) {
                        List<ParaObject> l3 = res;

                        assertTrue(((Sysprop) l3.get(0)).hasProperty("custom"));
                        assertEquals(dogsType, l3.get(0).getType());
                        assertEquals(dogsType, l3.get(1).getType());
                        assertEquals(dogsType, l3.get(2).getType());

                        assertEquals(part1.getName(), l3.get(0).getName());
                        assertEquals(part2.getName(), l3.get(1).getName());
                        assertEquals(part3.getName(), l3.get(2).getName());

                        pc().deleteAll(nl, new Listener<List<ParaObject>>() {
                            public void onResponse(List<ParaObject> res) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                pc().list(dogsType, null, new Listener<List<ParaObject>>() {
                                    public void onResponse(List<ParaObject> res) {
                                        assertTrue(res.isEmpty());
                                    }
                                });

                                pc().me(new Listener<ParaObject>() {
                                    public void onResponse(ParaObject res) {
                                        Map<String, String> datatypes = (Map<String, String>)
                                                ((Sysprop)res).getProperty("datatypes");
                                        assertTrue(datatypes.containsValue(dogsType));
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });

        pc().readAll(null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().readAll(new ArrayList<String>(0), new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().updateAll(null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().updateAll(new ArrayList<ParaObject>(0), new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
    }

    @Test
    public void testList() {
        final ArrayList<ParaObject> cats = new ArrayList<ParaObject>();
        for (int i = 0; i < 3; i++) {
            ParaObject s = new Sysprop(catsType + i);
            s.setType(catsType);
            cats.add(s);
        }

        pc().list(null, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().list("", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });

        pc().createAll(cats, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                pc().list(catsType, null, new Listener<List<ParaObject>>() {
                    public void onResponse(List<ParaObject> res) {
                        List<ParaObject> list1 = res;
                        assertFalse(list1.isEmpty());
                        assertEquals(3, list1.size());
                        assertEquals(Sysprop.class, list1.get(0).getClass());
                    }
                });

                pc().list(catsType, new Pager(2), new Listener<List<ParaObject>>() {
                    public void onResponse(List<ParaObject> res) {
                        List<ParaObject> list2 = res;
                        assertFalse(list2.isEmpty());
                        assertEquals(2, list2.size());

                        ArrayList<String> nl = new ArrayList<String>(3);
                        nl.add(cats.get(0).getId());
                        nl.add(cats.get(1).getId());
                        nl.add(cats.get(2).getId());

                        pc().deleteAll(nl, new Listener<List<ParaObject>>() {
                            public void onResponse(List<ParaObject> res) {
                                pc().me(new Listener<ParaObject>() {
                                    public void onResponse(ParaObject res) {
                                        Map<String, String> datatypes = (Map<String, String>)
                                                ((Sysprop)res).getProperty("datatypes");
                                        assertTrue(datatypes.containsValue(catsType));
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    @Test
    public void testSearch() throws InterruptedException {
        pc().findById(null, new Listener<ParaObject>() {
            public void onResponse(ParaObject res) {
                assertNull(res);
            }
        });
        pc().findById("", new Listener<ParaObject>() {
            public void onResponse(ParaObject res) {
                assertNull(res);
            }
        });
        pc().findById(u().getId(), new Listener<ParaObject>() {
            public void onResponse(ParaObject res) {
                assertNotNull(res);
            }
        });
        pc().findById(t().getId(), new Listener<ParaObject>() {
            public void onResponse(ParaObject res) {
                assertNotNull(res);
            }
        });

        pc().findByIds(null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findByIds(Arrays.asList(u().getId(), u1().getId(), u2().getId()),
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(3, res.size());
            }
        });
        pc().findNearby(null, null, 100, 1, 1, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findNearby(u().getType(), "*", 10, 40.60, -73.90, null,
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });
        pc().findNearby(t().getType(), "*", 20, 40.62, -73.91, null,
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });

        pc().findPrefix(null, null, "", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });

        pc().findPrefix("", "null", "xx", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });

        pc().findPrefix(u().getType(), "name", "ann", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });

        pc().findQuery(null, null, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });
        pc().findQuery("", "*", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });
        pc().findQuery(a1().getType(), "country:US", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(2, res.size());
            }
        });
        pc().findQuery(u().getType(), "ann", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });
        pc().findQuery(u().getType(), "Ann", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });
        pc().findQuery(null, "*", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.size() > 4);
            }
        });

        final Pager p = new Pager();
        assertEquals(0, p.getCount());
        pc().findQuery(u().getType(), "*", p, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(res.size(), p.getCount());
                assertTrue(p.getCount() > 0);
            }
        });

        pc().findSimilar(t().getType(), "", null, null, null,
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findSimilar(t().getType(), "", new String[0], "", null,
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findSimilar(s1().getType(), s1().getId(), new String[]{"name"}, s1().getName(), null,
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
                assertEquals(s2(), res.get(0));
            }
        });

        pc().findTagged(u().getType(), null, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(0, res.size());
            }
        });
        pc().findTagged(u().getType(), new String[]{"two"}, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(2, res.size());
            }
        });
        pc().findTagged(u().getType(), new String[]{"one", "two"}, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(1, res.size());
            }
        });
        pc().findTagged(u().getType(), new String[]{"three"}, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(3, res.size());
            }
        });
        pc().findTagged(u().getType(), new String[]{"four", "three"}, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(2, res.size());
            }
        });
        pc().findTagged(u().getType(), new String[]{"five", "three"}, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(1, res.size());
            }
        });
        pc().findTagged(t().getType(), new String[]{"four", "three"}, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(0, res.size());
            }
        });

        pc().findTags(null, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });
        pc().findTags("", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });
        pc().findTags("unknown", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findTags(t().getProperty("tag").toString(), null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.size() >= 1);
            }
        });

        pc().findTermInList(u().getType(), "id", Arrays.asList(u().getId(),
              u1().getId(), u2().getId(), "xxx", "yyy"), null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(3, res.size());
            }
        });

        // many terms
        Map<String, Object> terms = new HashMap<String, Object>();
//		terms.put("type", u.getType());
        terms.put("id", u().getId());

        Map<String, Object> terms1 = new HashMap<String, Object>();
        terms1.put("type", null);
        terms1.put("id", " ");

        Map<String, Object> terms2 = new HashMap<String, Object>();
        terms2.put(" ", "bad");
        terms2.put("", "");

        pc().findTerms(u().getType(), terms, true, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertEquals(1, res.size());
            }
        });
        pc().findTerms(u().getType(), terms1, true, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findTerms(u().getType(), terms2, true, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });

        // single term
        pc().findTerms(null, null, true, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findTerms(u().getType(), Collections.singletonMap("", null), true, null,
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findTerms(u().getType(), Collections.singletonMap("", ""), true, null,
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findTerms(u().getType(), Collections.singletonMap("term", null), true, null,
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findTerms(u().getType(), Collections.singletonMap("type", u().getType()), true, null,
                new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.size() >= 2);
            }
        });

        pc().findWildcard(u().getType(), null, null, null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findWildcard(u().getType(), "", "", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().findWildcard(u().getType(), "name", "an*", null, new Listener<List<ParaObject>>() {
            public void onResponse(List<ParaObject> res) {
                assertFalse(res.isEmpty());
            }
        });

        pc().getCount(null, new Listener<Long>() {
            public void onResponse(Long res) {
                assertTrue(res.intValue() > 4);
            }
        });
        pc().getCount("", new Listener<Long>() {
            public void onResponse(Long res) {
                assertNotEquals(0, res.intValue());
            }
        });
        pc().getCount("test", new Listener<Long>() {
            public void onResponse(Long res) {
                assertEquals(0, res.intValue());
            }
        });
        pc().getCount(u().getType(), new Listener<Long>() {
            public void onResponse(Long res) {
                assertTrue(res.intValue() >= 3);
            }
        });

        pc().getCount(null, null, new Listener<Long>() {
            public void onResponse(Long res) {
                assertEquals(0, res.intValue());
            }
        });
        pc().getCount(u().getType(), Collections.singletonMap("id", " "), new Listener<Long>() {
            public void onResponse(Long res) {
                assertEquals(0, res.intValue());
            }
        });
        pc().getCount(u().getType(), Collections.singletonMap("id", u().getId()), new Listener<Long>() {
            public void onResponse(Long res) {
                assertEquals(1, res.intValue());
            }
        });
        pc().getCount(null, Collections.singletonMap("type", u().getType()), new Listener<Long>() {
            public void onResponse(Long res) {
                assertTrue(res.intValue() > 1);
            }
        });
    }

    @Test
    public void testLinks() {
        pc().link(u(), t().getId(), new Listener<String>() {
            public void onResponse(String res) {
                assertNotNull(res);
                pc().link(u(), u2().getId(), new Listener<String>() {
                    public void onResponse(String res) {
                        assertNotNull(res);

                        pc().isLinked(u(), null, new Listener<Boolean>() {
                            public void onResponse(Boolean res) {
                                assertFalse(res);
                            }
                        });
                        pc().isLinked(u(), t(), new Listener<Boolean>() {
                            public void onResponse(Boolean res) {
                                assertTrue(res);
                            }
                        });
                        pc().isLinked(u(), u2(), new Listener<Boolean>() {
                            public void onResponse(Boolean res) {
                                assertTrue(res);
                            }
                        });

                        pc().getLinkedObjects(u(), "tag", null, new Listener<List<ParaObject>>() {
                            public void onResponse(List<ParaObject> res) {
                                assertEquals(1, res.size());
                            }
                        });
                        pc().getLinkedObjects(u(), "sysprop", null, new Listener<List<ParaObject>>() {
                            public void onResponse(List<ParaObject> res) {
                                assertEquals(1, res.size());
                            }
                        });

                        pc().countLinks(u(), null, new Listener<Long>() {
                            public void onResponse(Long res) {
                                assertEquals(0, res.intValue());
                            }
                        });
                        pc().countLinks(u(), "tag", new Listener<Long>() {
                            public void onResponse(Long res) {
                                assertEquals(1, res.intValue());
                            }
                        });
                        pc().countLinks(u(), "sysprop", new Listener<Long>() {
                            public void onResponse(Long res) {
                                assertEquals(1, res.intValue());
                            }
                        });

                        pc().unlinkAll(u(), new Listener<Map>() {
                            public void onResponse(Map map) {
                                pc().isLinked(u(), t(), new Listener<Boolean>() {
                                    public void onResponse(Boolean res) {
                                        assertFalse(res);
                                    }
                                });
                                pc().isLinked(u(), u2(), new Listener<Boolean>() {
                                    public void onResponse(Boolean res) {
                                        assertFalse(res);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        }, new ErrorListener() {
            public void onErrorResponse(VolleyError volleyError) {
                fail("Link test failed.");
            }
        });
    }

    @Test
    public void testUtils() {
        pc().newId(new Listener<String>() {
            public void onResponse(final String id1) {
                pc().newId(new Listener<String>() {
                    public void onResponse(String id2) {
                        assertNotNull(id1);
                        assertFalse(id1.isEmpty());
                        assertNotEquals(id1, id2);
                    }
                });
            }
        });

        pc().getTimestamp(new Listener<Long>() {
            public void onResponse(Long ts) {
                assertNotNull(ts);
                assertNotEquals(0, ts.intValue());
            }
        });

        pc().formatDate("MM dd yyyy", Locale.US, new Listener<String>() {
            public void onResponse(String date1) {
                String date2 = new SimpleDateFormat("MM dd yyyy").format(new Date());
                assertEquals(date1, date2);
            }
        });

        pc().noSpaces(" test  123		test ", "", new Listener<String>() {
            public void onResponse(String ns1) {
                String ns2 = "test123test";
                assertEquals(ns1, ns2);
            }
        });

        pc().stripAndTrim(" %^&*( cool )		@!", new Listener<String>() {
            public void onResponse(String st1) {
                String st2 = "cool";
                assertEquals(st1, st2);
            }
        });

        pc().markdownToHtml("### hello **test**", new Listener<String>() {
            public void onResponse(String md1) {
                String md2 = "<h3>hello <strong>test</strong></h3>";
                assertEquals(md1.trim(), md2);
            }
        });

        pc().approximately(15000, new Listener<String>() {
            public void onResponse(String ht1) {
                String ht2 = "15s";
                assertEquals(ht1, ht2);
            }
        });
    }

    @Test
    public void testMisc() {
        pc().types(new Listener<Map<String, String>>() {
            public void onResponse(Map<String, String> types) {
                assertNotNull(types);
                assertFalse(types.isEmpty());
                assertTrue(types.containsKey("users"));
            }
        });

        pc().me(new Listener<ParaObject>() {
            public void onResponse(ParaObject app) {
                assertEquals(APP_ID, app.getId());
            }
        });
    }

    @Test
    public void testValidationConstraints() {
        // Validations
        final String kittenType = "kitten";
        pc().validationConstraints(
                new Listener<Map<String, Map<String, Map<String, Map<String, ?>>>>>() {
            public void onResponse(Map<String, Map<String, Map<String, Map<String, ?>>>> constraints) {
                assertNotNull(constraints);
                assertFalse(constraints.isEmpty());
                assertTrue(constraints.containsKey("app"));
                assertTrue(constraints.containsKey("user"));
            }
        });

        pc().validationConstraints("app",
                new Listener<Map<String, Map<String, Map<String, Map<String, ?>>>>>() {
            public void onResponse(Map<String, Map<String, Map<String, Map<String, ?>>>> constraint) {
                assertFalse(constraint.isEmpty());
                assertTrue(constraint.containsKey("app"));
                assertEquals(1, constraint.size());
            }
        });

        pc().addValidationConstraint(kittenType, "paws", required(),
                new Listener<Map<String, Map<String, Map<String, Map<String, ?>>>>>() {
            public void onResponse(Map<String, Map<String, Map<String, Map<String, ?>>>> constraint) {
                pc().validationConstraints(kittenType,
                        new Listener<Map<String, Map<String, Map<String, Map<String, ?>>>>>() {
                    public void onResponse(Map<String, Map<String, Map<String, Map<String, ?>>>> constraint) {
                        assertTrue(constraint.get(kittenType).containsKey("paws"));

                        final Sysprop ct = new Sysprop("felix");
                        ct.setType(kittenType);
                        // validation fails
                        pc().create(ct, new Listener<ParaObject>() {
                            public void onResponse(ParaObject res) {
                                assertNull(res);
                                // fix
                                ct.addProperty("paws", "4");
                                pc().create(ct, new Listener<ParaObject>() {
                                    public void onResponse(ParaObject res) {
                                        assertNotNull(res);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });


        pc().removeValidationConstraint(kittenType, "paws", "required",
                new Listener<Map<String, Map<String, Map<String, Map<String, ?>>>>>() {
            public void onResponse(Map<String, Map<String, Map<String, Map<String, ?>>>> stringMapMap) {
                pc().validationConstraints(kittenType,
                        new Listener<Map<String, Map<String, Map<String, Map<String, ?>>>>>() {
                    public void onResponse(Map<String, Map<String, Map<String, Map<String, ?>>>> constraint) {
                        assertFalse(constraint.containsKey(kittenType));
                    }
                });
            }
        });
    }

    @Test
    public void testResourcePermissions() {
        // Permissions
        pc().resourcePermissions(new Listener<Map<String, Map<String, List<String>>>>() {
            public void onResponse(Map<String, Map<String, List<String>>> res) {
                assertNotNull(res);
            }
        });
        pc().grantResourcePermission(null, dogsType, new String[0],
                new Listener<Map<String, Map<String, List<String>>>>() {
            public void onResponse(Map<String, Map<String, List<String>>> res) {
                assertTrue(res.isEmpty());
            }
        });
        pc().grantResourcePermission(" ", "", new String[0],
                new Listener<Map<String, Map<String, List<String>>>>() {
            public void onResponse(Map<String, Map<String, List<String>>> res) {
                assertTrue(res.isEmpty());
            }
        });

        pc().grantResourcePermission(u1().getId(), dogsType, new String[]{"GET"},
                new Listener<Map<String, Map<String, List<String>>>>() {
            public void onResponse(final Map<String, Map<String, List<String>>> res) {
                pc().resourcePermissions(u1().getId(), new Listener<Map<String, Map<String, List<String>>>>() {
                    public void onResponse(Map<String, Map<String, List<String>>> permits) {
                        assertTrue(permits.containsKey(u1().getId()));
                        assertTrue(permits.get(u1().getId()).containsKey(dogsType));
                        pc().isAllowedTo(u1().getId(), dogsType, "GET",
                                new Listener<Boolean>() {
                            public void onResponse(Boolean res) {
                                assertTrue(res);
                            }
                        });
                        pc().isAllowedTo(u1().getId(), dogsType, "POST",
                                new Listener<Boolean>() {
                            public void onResponse(Boolean res) {
                                assertFalse(res);
                            }
                        });
                    }
                });

                // anonymous permissions
                pc().isAllowedTo(ALLOW_ALL, "utils/timestamp", "GET", new Listener<Boolean>() {
                    public void onResponse(Boolean res) {
                        assertFalse(res);
                    }
                });
                pc().grantResourcePermission(ALLOW_ALL, "utils/timestamp", new String[]{"GET"}, true,
                    new Listener<Map<String, Map<String, List<String>>>>() {
                        public void onResponse(Map<String, Map<String, List<String>>> res) {
                            assertNotNull(res);
                            pc2().getTimestamp(new Listener<Long>() {
                                public void onResponse(Long res) {
                                    assertTrue(res > 0);
                                }
                            });

                            pc().isAllowedTo("*", "utils/timestamp", "DELETE", new Listener<Boolean>() {
                                public void onResponse(Boolean res) {
                                    assertFalse(res);
                                }
                            });
                        }
                    });

                pc().resourcePermissions(new Listener<Map<String, Map<String, List<String>>>>() {
                    public void onResponse(Map<String, Map<String, List<String>>> permits) {
                        assertTrue(permits.containsKey(u1().getId()));
                        assertTrue(permits.get(u1().getId()).containsKey(dogsType));
                    }
                });

                pc().revokeResourcePermission(u1().getId(), dogsType,
                        new Listener<Map<String, Map<String, List<String>>>>() {
                    public void onResponse(Map<String, Map<String, List<String>>> res) {
                        pc().resourcePermissions(u1().getId(),
                                new Listener<Map<String, Map<String, List<String>>>>() {
                            public void onResponse(Map<String, Map<String, List<String>>> permits) {
                                assertFalse(permits.get(u1().getId()).containsKey(dogsType));
                                pc().isAllowedTo(u1().getId(), dogsType, "GET",
                                        new Listener<Boolean>() {
                                    public void onResponse(Boolean res) {
                                        assertFalse(res);
                                    }
                                });
                                pc().isAllowedTo(u1().getId(), dogsType, "POST",
                                        new Listener<Boolean>() {
                                    public void onResponse(Boolean res) {
                                        assertFalse(res);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });

        final String[] WRITE = new String[]{"POST", "PUT", "PATCH", "DELETE"};

        pc().grantResourcePermission(u2().getId(), ALLOW_ALL, WRITE,
                new Listener<Map<String, Map<String, List<String>>>>() {
            public void onResponse(Map<String, Map<String, List<String>>> stringMapMap) {
                pc().isAllowedTo(u2().getId(), dogsType, "PUT",
                        new Listener<Boolean>() {
                    public void onResponse(Boolean res) {
                        assertTrue(res);
                    }
                });
                pc().isAllowedTo(u2().getId(), dogsType, "PATCH",
                        new Listener<Boolean>() {
                    public void onResponse(Boolean res) {
                        assertTrue(res);
                    }
                });

                pc().revokeAllResourcePermissions(u2().getId(),
                        new Listener<Map<String, Map<String, List<String>>>>() {
                    public void onResponse(Map<String, Map<String, List<String>>> stringMapMap) {
                        pc().resourcePermissions(new Listener<Map<String, Map<String, List<String>>>>() {
                            public void onResponse(Map<String, Map<String, List<String>>> permits) {
                                pc().isAllowedTo(u2().getId(), dogsType, "PUT",
                                        new Listener<Boolean>() {
                                    public void onResponse(Boolean res) {
                                        assertFalse(res);
                                    }
                                });
                                assertFalse(permits.containsKey(u2().getId()));
                            }
                        });

                    }
                });
            }
        });

        pc().grantResourcePermission(u1().getId(), dogsType, WRITE,
                new Listener<Map<String, Map<String, List<String>>>>() {
            public void onResponse(Map<String, Map<String, List<String>>> stringMapMap) {
                pc().grantResourcePermission(ALLOW_ALL, catsType, WRITE,
                        new Listener<Map<String, Map<String, List<String>>>>() {
                    public void onResponse(Map<String, Map<String, List<String>>> stringMapMap) {
                        pc().grantResourcePermission(ALLOW_ALL, ALLOW_ALL, new String[]{"GET"},
                                new Listener<Map<String, Map<String, List<String>>>>() {
                            public void onResponse(Map<String, Map<String, List<String>>> stringMapMap) {
                                // user-specific permissions are in effect
                                pc().isAllowedTo(u1().getId(), dogsType, "PUT",
                                        new Listener<Boolean>() {
                                    public void onResponse(Boolean res) {
                                        assertTrue(res);
                                    }
                                });
                                pc().isAllowedTo(u1().getId(), dogsType, "GET",
                                        new Listener<Boolean>() {
                                    public void onResponse(Boolean res) {
                                        assertFalse(res);
                                    }
                                });
                                pc().isAllowedTo(u1().getId(), catsType, "PUT",
                                        new Listener<Boolean>() {
                                    public void onResponse(Boolean res) {
                                        assertTrue(res);
                                    }
                                });
                                pc().isAllowedTo(u1().getId(), catsType, "GET",
                                        new Listener<Boolean>() {
                                    public void onResponse(Boolean res) {
                                        assertTrue(res);
                                    }
                                });

                                pc().revokeAllResourcePermissions(u1().getId(),
                                        new Listener<Map<String, Map<String, List<String>>>>() {
                                    public void onResponse(Map<String, Map<String, List<String>>> stringMapMap) {
                                        // user-specific permissions not found so check wildcard
                                        pc().isAllowedTo(u1().getId(), dogsType, "PUT",
                                                new Listener<Boolean>() {
                                            public void onResponse(Boolean res) {
                                                assertFalse(res);
                                            }
                                        });
                                        pc().isAllowedTo(u1().getId(), dogsType, "GET",
                                                new Listener<Boolean>() {
                                            public void onResponse(Boolean res) {
                                                assertTrue(res);
                                            }
                                        });
                                        pc().isAllowedTo(u1().getId(), catsType, "PUT",
                                                new Listener<Boolean>() {
                                            public void onResponse(Boolean res) {
                                                assertTrue(res);
                                            }
                                        });
                                        pc().isAllowedTo(u1().getId(), catsType, "GET",
                                                new Listener<Boolean>() {
                                            public void onResponse(Boolean res) {
                                                assertTrue(res);
                                                pc().revokeResourcePermission(ALLOW_ALL, catsType,
                                                        new Listener<Map<String, Map<String, List<String>>>>() {
                                                    public void onResponse(Map<String, Map<String, List<String>>> stringMapMap) {
                                                        // resource-specific permissions not found so check wildcard
                                                        pc().isAllowedTo(u1().getId(), dogsType, "PUT",
                                                                new Listener<Boolean>() {
                                                            public void onResponse(Boolean res) {
                                                                assertFalse(res);
                                                            }
                                                        });
                                                        pc().isAllowedTo(u1().getId(), catsType, "PUT",
                                                                new Listener<Boolean>() {
                                                            public void onResponse(Boolean res) {
                                                                assertFalse(res);
                                                            }
                                                        });
                                                        pc().isAllowedTo(u1().getId(), dogsType, "GET",
                                                                new Listener<Boolean>() {
                                                            public void onResponse(Boolean res) {
                                                                assertTrue(res);
                                                            }
                                                        });
                                                        pc().isAllowedTo(u1().getId(), catsType, "GET",
                                                                new Listener<Boolean>() {
                                                            public void onResponse(Boolean res) {
                                                                assertTrue(res);
                                                            }
                                                        });
                                                        pc().isAllowedTo(u2().getId(), dogsType, "GET",
                                                                new Listener<Boolean>() {
                                                            public void onResponse(Boolean res) {
                                                                assertTrue(res);

                                                            }
                                                        });
                                                        pc().isAllowedTo(u2().getId(), catsType, "GET",
                                                                new Listener<Boolean>() {
                                                            public void onResponse(Boolean res) {
                                                                assertTrue(res);
                                                                pc().revokeAllResourcePermissions(ALLOW_ALL, null);
                                                                pc().revokeAllResourcePermissions(u1().getId(), null);
                                                            }
                                                        });
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    @Test
    public void testAccessTokens() {
        assertNull(pc().getAccessToken());
        pc().signIn("facebook", "test_token", new Listener<Sysprop>() {
            public void onResponse(Sysprop res) {
                assertNull(res);
            }
        });
        pc().signOut();
        pc().revokeAllTokens(new Listener<Boolean>() {
            public void onResponse(Boolean res) {
                assertFalse(res);
            }
        });
    }
}
