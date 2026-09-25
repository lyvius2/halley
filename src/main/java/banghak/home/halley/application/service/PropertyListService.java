package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CriterionScoreView;
import banghak.home.halley.adapter.inbound.web.dto.PropertyPinResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyPage;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyVisitRepository;
import banghak.home.halley.adapter.outbound.persistence.UserCriterionScoreRepository;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.PropertySort;
import banghak.home.halley.domain.itinerary.PropertyVisit;
import banghak.home.halley.domain.scoring.UserCriterionScore;
import banghak.home.halley.config.HalleyUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PropertyListService {

    /** 한 쪽의 기본 크기. 화면이 안 보내도 이 값으로 자른다  */
    public static final int DEFAULT_PAGE_SIZE = 30;

    private static final int MAX_PAGE_SIZE = 100;

    private static final String COMFORT_CODE = "COMFORT";

    private final ScoringService scoringService;
    private final PropertyVisitRepository propertyVisitRepository;
    private final UserCriterionScoreRepository userCriterionScoreRepository;

    public PropertyListService(ScoringService scoringService,
                               PropertyVisitRepository propertyVisitRepository,
                               UserCriterionScoreRepository userCriterionScoreRepository) {
        this.scoringService = scoringService;
        this.propertyVisitRepository = propertyVisitRepository;
        this.userCriterionScoreRepository = userCriterionScoreRepository;
    }

    public ScoredPropertyPage page(DealType dealType, PropertySort sort, int page, int size) {
        return page(dealType, sort, page, size, false);
    }

    public ScoredPropertyPage page(DealType dealType, PropertySort sort, int page, int size,
                                   boolean archived) {
        final List<ScoredPropertyResponse> all = sorted(scoringService.list(dealType, archived), sort);
        // 치워 둔 것의 수는 지금 보는 탭과 무관하게 실린다.
        // 채점까지 붙이지 않고 매물만 센다 — 뱃지 하나 때문에 전체를 다시 채점할 이유가 없다
        final int archivedTotal = scoringService.visibleProperties(null, true).size();
        final int pageSize = clampSize(size);
        final int from = Math.max(page, 0) * pageSize;
        if (from >= all.size()) {
            // 마지막 쪽을 지나쳐 물어도 빈 쪽을 준다 — 오류가 아니다
            return new ScoredPropertyPage(List.of(), Math.max(page, 0), pageSize, all.size(),
                    false, archivedTotal);
        }
        final int to = Math.min(from + pageSize, all.size());
        return new ScoredPropertyPage(all.subList(from, to), Math.max(page, 0), pageSize,
                all.size(), to < all.size(), archivedTotal);
    }

    /** 지도와 임장 플래너가 쓰는 얇은 전체 목록.  */
    public List<PropertyPinResponse> pins(DealType dealType) {
        return pins(dealType, false);
    }

    public List<PropertyPinResponse> pins(DealType dealType, boolean archived) {
        final Set<Long> byComfort = comfortScoredPropertyIds();
        final Set<Long> visited = visitedPropertyIds();
        return scoringService.visibleProperties(dealType, archived).stream()
                .map(p -> toPin(p, visited.contains(p.id()), byComfort.contains(p.id())))
                .toList();
    }

    public Set<Long> visitedPropertyIds() {
        final Long userId = currentUserId();
        if (userId == null) {
            return Set.of();
        }
        final Set<Long> visited = new HashSet<>(propertyVisitRepository.findByUser(userId).stream()
                .map(PropertyVisit::propertyId)
                .toList());
        visited.addAll(comfortScoredPropertyIds());
        return visited;
    }

    private Set<Long> comfortScoredPropertyIds() {
        final Long userId = currentUserId();
        if (userId == null) {
            return Set.of();
        }
        return userCriterionScoreRepository.findByUserId(userId).stream()
                .filter(s -> COMFORT_CODE.equals(s.criterionCode()))
                .map(UserCriterionScore::propertyId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<ScoredPropertyResponse> sorted(List<ScoredPropertyResponse> rows, PropertySort sort) {
        final Set<Long> visited = sort == PropertySort.DEFAULT ? visitedPropertyIds() : Set.of();
        final Collator korean = Collator.getInstance(Locale.KOREAN);
        // 같은 값이면 이름순 — 새로고침마다 순서가 흔들리면 눈이 못 따라간다.
        // 쪽을 나눠 받을 때는 더 중요하다: 순서가 흔들리면 같은 매물이 두 쪽에 나오거나
        // 어느 쪽에도 안 나온다
        final Comparator<ScoredPropertyResponse> byName = Comparator.comparing(
                r -> r.property().name() == null ? "" : r.property().name(), korean);
        return rows.stream()
                .sorted(comparatorFor(sort, visited).thenComparing(byName))
                .toList();
    }

    private Comparator<ScoredPropertyResponse> comparatorFor(PropertySort sort, Set<Long> visited) {
        return switch (sort) {
            // 아직 안 가 본 곳이 먼저, 그 안에서 추천점수가 높은 순
            case DEFAULT -> Comparator
                    .<ScoredPropertyResponse, Integer>comparing(r -> visited.contains(r.property().id()) ? 1 : 0)
                    .thenComparing(desc(ScoredPropertyResponse::totalScore));
            case PRICE -> asc(r -> toDecimal(r.property().priceDeposit()));
            case AREA -> desc(r -> r.property().areaExclusiveM2());
            case SCORE -> desc(ScoredPropertyResponse::totalScore);
            case COMMUTE -> desc(r -> criterionScore(r, "COMMUTE"));
        };
    }

    private Comparator<ScoredPropertyResponse> unknownLast(
            java.util.function.Function<ScoredPropertyResponse, BigDecimal> value, boolean ascending) {
        return (a, b) -> {
            final BigDecimal x = value.apply(a);
            final BigDecimal y = value.apply(b);
            if (x == null && y == null) {
                return 0;
            }
            if (x == null) {
                return 1;
            }
            if (y == null) {
                return -1;
            }
            return ascending ? x.compareTo(y) : y.compareTo(x);
        };
    }

    private Comparator<ScoredPropertyResponse> desc(
            java.util.function.Function<ScoredPropertyResponse, BigDecimal> value) {
        return unknownLast(value, false);
    }

    private Comparator<ScoredPropertyResponse> asc(
            java.util.function.Function<ScoredPropertyResponse, BigDecimal> value) {
        return unknownLast(value, true);
    }

    private BigDecimal criterionScore(ScoredPropertyResponse scored, String code) {
        if (scored.scores() == null) {
            return null;
        }
        for (final CriterionScoreView view : scored.scores()) {
            if (code.equals(view.code())) {
                return view.effectiveScore();
            }
        }
        return null;
    }

    private BigDecimal toDecimal(Long value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private PropertyPinResponse toPin(Property p, boolean visited, boolean byComfort) {
        return new PropertyPinResponse(p.id(), p.name(), p.dongHo(), p.dealType(), p.priceDeposit(),
                p.areaExclusiveM2(), p.lat(), p.lng(), visited, byComfort,
                p.active(), p.isDraft());
    }

    private Long currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }
}
