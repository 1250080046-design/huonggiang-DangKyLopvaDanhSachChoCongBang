package vn.edu.registration.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Pure student-level Round Robin. Candidates are placed on a stable ring by
 * student ID. The last successfully served student ID is persisted for each
 * class; the cursor survives a restart and does not depend on join time.
 */
public final class RoundRobinScheduler {
    private RoundRobinScheduler() {}

    public static <T> T next(List<T> waiting, ToLongFunction<T> studentIdOf, Long lastServedId) {
        if (waiting.isEmpty()) return null;
        List<T> ring = new ArrayList<>(waiting);
        ring.sort(Comparator.comparingLong(studentIdOf));
        if (lastServedId != null) {
            for (T e : ring) if (studentIdOf.applyAsLong(e) > lastServedId) return e;
        }
        return ring.get(0);
    }

    public static <T> List<T> order(List<T> waiting, ToLongFunction<T> studentIdOf, Long lastServedId) {
        List<T> remaining = new ArrayList<>(waiting);
        List<T> result = new ArrayList<>(waiting.size());
        Long cursor = lastServedId;
        while (!remaining.isEmpty()) {
            T chosen = next(remaining, studentIdOf, cursor);
            result.add(chosen);
            remaining.remove(chosen);
            cursor = studentIdOf.applyAsLong(chosen);
        }
        return result;
    }
}
