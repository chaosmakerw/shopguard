package com.codesprint.lesson0831.demo;

import java.util.ArrayList;
import java.util.List;

/** 仅在演示包装器开启时记录安全结构信息，不保存问题、参数值或检索正文。 */
public final class DemoTrace {
    private static final ThreadLocal<List<Event>> EVENTS = new ThreadLocal<>();
    private DemoTrace() {}
    public static void begin() { EVENTS.set(new ArrayList<>()); }
    public static void add(String stage, String code, String detail) {
        var events = EVENTS.get();
        if (events != null) events.add(new Event(events.size() + 1, stage, code, detail));
    }
    public static List<Event> snapshot() {
        var events = EVENTS.get();
        return events == null ? List.of() : List.copyOf(events);
    }
    public static void clear() { EVENTS.remove(); }
    public record Event(int step, String stage, String code, String detail) {}
}
