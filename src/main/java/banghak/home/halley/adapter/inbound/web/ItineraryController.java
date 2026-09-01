package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.ItineraryDraft;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryRequest;
import banghak.home.halley.adapter.inbound.web.dto.OptimizeItineraryResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateVisitedRequest;
import banghak.home.halley.application.service.ItineraryService;
import banghak.home.halley.domain.itinerary.StartLocation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * 작업 중인 것 (설계 I179). <b>계정마다 다릅니다.</b>
     */
    @GetMapping("/draft")
    public ItineraryDraft draft() {
        return itineraryService.loadDraft();
    }

    @PutMapping("/draft")
    public ItineraryDraft saveDraft(@RequestBody ItineraryDraft draft) {
        itineraryService.saveDraft(draft);
        return draft;
    }

    @DeleteMapping("/draft")
    public void clearDraft() {
        itineraryService.clearDraft();
    }

    @PostMapping("/optimize")
    public OptimizeItineraryResponse optimize(@RequestBody OptimizeItineraryRequest request) {
        return itineraryService.optimize(request);
    }

    /**
     * 가 본 곳 (설계 I197).
     *
     * <p>계획 저장을 없앴으므로 <b>방문 기록만</b> DB에 남습니다.
     * 계산 결과는 draft 캐시로 충분하지만, 어디를 가 봤는지는 그렇지 않습니다.
     */
    @GetMapping("/visits")
    public List<Long> visits() {
        return itineraryService.visitedPropertyIds();
    }

    @PutMapping("/visits/{propertyId}")
    public void markVisited(@PathVariable Long propertyId,
                            @RequestBody UpdateVisitedRequest request) {
        itineraryService.markVisited(propertyId, request.visited());
    }
}

