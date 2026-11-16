package vn.edu.registration.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoundRobinSchedulerTest {
    record Entry(long studentId, String cohort) {}
    private static final List<Entry> WAITING = List.of(
            new Entry(32, "K24"), new Entry(10, "K25"),
            new Entry(21, "K24"), new Entry(40, "K26"));

    @Test void rotatesAcrossAllStudentsNotByJoinedAtOrCohort() {
        assertEquals(List.of(10L, 21L, 32L, 40L),
                RoundRobinScheduler.order(WAITING, Entry::studentId, null).stream().map(Entry::studentId).toList());
    }
    @Test void resumesAndWrapsAfterLastServedStudent() {
        assertEquals(32L, RoundRobinScheduler.next(WAITING, Entry::studentId, 21L).studentId());
        assertEquals(10L, RoundRobinScheduler.next(WAITING, Entry::studentId, 40L).studentId());
    }
    @Test void skipsMissingStudentsAndDoesNotUseCohort() {
        assertEquals(List.of(40L, 10L, 21L, 32L),
                RoundRobinScheduler.order(WAITING, Entry::studentId, 32L).stream().map(Entry::studentId).toList());
        assertNull(RoundRobinScheduler.next(List.<Entry>of(), Entry::studentId, null));
    }
    @Test void fiveCandidatesOneOrThreeAvailableSeatsPreserveRingOrder() {
        List<Entry> five=List.of(new Entry(2,"K22"),new Entry(5,"K22"),new Entry(7,"K23"),new Entry(9,"K24"),new Entry(11,"K25"));
        List<Long> ordered=RoundRobinScheduler.order(five,Entry::studentId,5L).stream().map(Entry::studentId).toList();
        assertEquals(List.of(7L,9L,11L,2L,5L),ordered);
        assertEquals(List.of(7L),ordered.subList(0,1));
        assertEquals(List.of(7L,9L,11L),ordered.subList(0,3));
    }
    @Test void skipsRemovedCandidateAndWrapsAcrossMultipleTurns() {
        List<Entry> current=List.of(new Entry(10,"K22"),new Entry(20,"K22"),new Entry(30,"K22"));
        assertEquals(10L,RoundRobinScheduler.next(current,Entry::studentId,30L).studentId());
        assertEquals(30L,RoundRobinScheduler.next(current,Entry::studentId,20L).studentId());
        current=List.of(new Entry(10,"K22"),new Entry(30,"K22"));
        assertEquals(10L,RoundRobinScheduler.next(current,Entry::studentId,30L).studentId());
    }
}
