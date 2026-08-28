package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreatePlanRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateStopVisitedRequest;
import banghak.home.halley.adapter.inbound.web.dto.VisitPlanResponse;
import banghak.home.halley.application.service.ItineraryService;
import banghak.home.halley.domain.itinerary.StartLocation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/itinerary")
public class ItineraryController {

    private final ItineraryService itineraryService;

    public ItineraryController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    /** 마지막 출발지 조회 — 캐시에 없으면 null (설계 I52). */
    @GetMapping("/start-location")
    public StartLocation startLocation() {
        return itineraryService.lastStartLocation();
    }

    @PutMapping("/start-location")
    public StartLocation rememberStartLocation(@RequestBody StartLocation request) {
        return itineraryService.rememberStartLocation(request);
    }

    @PostMapping("/optimize")
    public OptimizeItineraryResponse optimize(@RequestBody OptimizeItineraryRequest request) {
        return itineraryService.optimize(request);
    }

    @PostMapping("/plans")
    public VisitPlanResponse createPlan(@RequestBody CreatePlanRequest request) {
        return itineraryService.createPlan(request);
    }

    @GetMapping("/plans/{id}")
    public VisitPlanResponse getPlan(@PathVariable Long id) {
        return itineraryService.getPlan(id);
    }

    @PatchMapping("/plans/{id}/stops/{stopId}")
    public VisitPlanResponse toggleStopVisited(@PathVariable Long id,
                                               @PathVariable Long stopId,
                                               @RequestBody UpdateStopVisitedRequest request) {
        return itineraryService.toggleStopVisited(id, stopId, request.visited());
    }

    @PostMapping("/plans/{id}/recompute")
    public VisitPlanResponse recompute(@PathVariable Long id) {
        return itineraryService.recompute(id);
    }
}

