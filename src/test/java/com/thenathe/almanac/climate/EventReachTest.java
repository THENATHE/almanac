package com.thenathe.almanac.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventReachTest {
    @Test
    void sameSeedAndEventAlwaysAgree() {
        assertEquals(EventReach.strength(12345L, 7), EventReach.strength(12345L, 7));
        assertNotEquals(EventReach.strength(12345L, 7), EventReach.strength(12345L, 8));
        assertNotEquals(EventReach.strength(12345L, 7), EventReach.strength(54321L, 7));
    }

    @Test
    void strengthStaysInRange() {
        for (long event = 0; event < 10_000; event++) {
            double strength = EventReach.strength(-4_242_424_242L, event);
            assertTrue(strength >= 0.0 && strength < 1.0, "event " + event);
        }
    }

    @Test
    void everyShareGetsItsShareOfEvents() {
        for (int share : new int[] {0, 25, 50, 60, 80, 100}) {
            int reached = 0;
            int events = 20_000;
            for (long event = 1; event <= events; event++) {
                if (EventReach.reaches(EventReach.strength(99L, event), share)) {
                    reached++;
                }
            }
            assertEquals(share / 100.0, reached / (double) events, 0.015, "share " + share);
        }
    }

    @Test
    void edgesAndNesting() {
        assertFalse(EventReach.reaches(0.0, 0));
        assertTrue(EventReach.reaches(0.999_999, 100));
        // An event that reaches a smaller share reaches every bigger one.
        for (long event = 0; event < 2000; event++) {
            double strength = EventReach.strength(5L, event);
            if (EventReach.reaches(strength, 50)) {
                assertTrue(EventReach.reaches(strength, 60));
            }
        }
    }
}
