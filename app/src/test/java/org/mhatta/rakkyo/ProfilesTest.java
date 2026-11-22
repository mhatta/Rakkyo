package org.mhatta.rakkyo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ProfilesTest {
    private static List<Profiles.Profile> two() {
        List<Profiles.Profile> list = Profiles.add(Collections.<Profiles.Profile>emptyList(), "Firefox", Passwords.generate());
        return Profiles.add(list, "メッセンジャー", Passwords.generate());
    }

    @Test
    public void formatAndParseRoundTrip() {
        final List<Profiles.Profile> list = two();
        final List<Profiles.Profile> back = Profiles.parse(Profiles.format(list));
        assertEquals(2, back.size());
        assertEquals("Firefox", back.get(0).name);
        assertEquals(list.get(0).password, back.get(0).password);
        assertEquals("メッセンジャー", back.get(1).name);
        assertEquals(list.get(1).password, back.get(1).password);
    }

    @Test
    public void parseSkipsBadLines() {
        final String good = Passwords.generate();
        final String stored = "A\t" + good + "\n\tnoname\nB\tshort\nnotab\nA\t" + Passwords.generate();
        final List<Profiles.Profile> list = Profiles.parse(stored);
        assertEquals(1, list.size());
        assertEquals("A", list.get(0).name);
        assertTrue(Profiles.parse(null).isEmpty());
        assertTrue(Profiles.parse("").isEmpty());
    }

    @Test
    public void parseKeepsAtMostMax() {
        List<Profiles.Profile> list = new ArrayList<Profiles.Profile>();
        for (int i = 0; i < Profiles.MAX + 3; i++) {
            list = Profiles.add(list, "app" + i, Passwords.generate());
        }
        assertEquals(Profiles.MAX, Profiles.parse(Profiles.format(list)).size());
    }

    @Test
    public void checkName() {
        final List<Profiles.Profile> list = two();
        assertNull(Profiles.checkName("Chrome", list));
        assertEquals(Profiles.NameProblem.EMPTY, Profiles.checkName(" ", list));
        assertEquals(Profiles.NameProblem.TAKEN, Profiles.checkName("firefox", list));
        assertEquals(Profiles.NameProblem.CONTROL, Profiles.checkName("a\tb", list));
        assertEquals(Profiles.NameProblem.CONTROL, Profiles.checkName("a\nb", list));
        final StringBuilder longName = new StringBuilder();
        for (int i = 0; i <= Profiles.MAX_NAME; i++) {
            longName.append('x');
        }
        assertEquals(Profiles.NameProblem.TOO_LONG, Profiles.checkName(longName.toString(), list));
    }

    @Test
    public void renewAndRemove() {
        final List<Profiles.Profile> list = two();
        final String fresh = Passwords.generate();
        final List<Profiles.Profile> renewed = Profiles.renew(list, "Firefox", fresh);
        assertEquals(fresh, Profiles.find(renewed, "Firefox").password);
        assertEquals(list.get(1).password, Profiles.find(renewed, "メッセンジャー").password);
        final List<Profiles.Profile> removed = Profiles.remove(renewed, "Firefox");
        assertEquals(1, removed.size());
        assertNull(Profiles.find(removed, "Firefox"));
        assertEquals(2, list.size());
    }

    @Test
    public void passwordsKeepOrder() {
        final List<Profiles.Profile> list = two();
        final Map<String, String> map = Profiles.passwords(list);
        assertEquals("[Firefox, メッセンジャー]", map.keySet().toString());
        assertEquals(list.get(0).password, map.get("Firefox"));
    }
}
