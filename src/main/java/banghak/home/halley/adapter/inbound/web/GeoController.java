package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.application.service.GeoService;
import banghak.home.halley.domain.geo.GeoSearchResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final GeoService geoService;

    public GeoController(GeoService geoService) {
        this.geoService = geoService;
    }

    @GetMapping("/search")
    public List<GeoSearchResult> search(@RequestParam("query") String query) {
        return geoService.search(query);
    }
}
