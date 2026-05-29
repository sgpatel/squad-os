package io.squados.dashboard.api.controller;

import io.squados.dashboard.api.model.ActivityEvent;
import io.squados.dashboard.api.service.ActivityEventService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/activity/stream — real-time SSE stream of dashboard activity events.
 * GET /api/v1/activity         — last N events (default 100).
 * GET /api/v1/activity/stats   — subscriber count + event count.
 */
@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {

    private final ActivityEventService eventService;

    public ActivityController(ActivityEventService eventService) {
        this.eventService = eventService;
    }

    /** SSE stream — browsers / UI subscribe here for real-time updates. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return eventService.subscribe();
    }

    /** Last N events — useful for initial page load. */
    @GetMapping
    public ResponseEntity<List<ActivityEvent>> history(
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        List<ActivityEvent> events = eventService.getHistory();
        int end = Math.min(limit, events.size());
        return ResponseEntity.ok(events.subList(0, end));
    }

    /** Connection stats. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "subscribers", eventService.subscriberCount(),
                "eventCount",  eventService.getHistory().size()
        ));
    }
}
