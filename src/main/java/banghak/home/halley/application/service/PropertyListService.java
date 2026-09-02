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

/**
 * 매물 목록을 <b>줄 세우고 잘라서</b> 내보낸다 (설계 I240).
 *
 * <h4>왜 서버가 줄 세우는가</h4>
 *
 * <p>[I221]에서는 화면이 줄 세웠습니다. 목록에 필요한 값이 이미 전부 실려 있었으니
 * 그때는 맞았습니다. 30건씩 잘라 보내기 시작하면 <b>더는 맞지 않습니다</b> —
 * 받은 30건 안에서만 줄 세우면 2쪽의 1등이 1쪽의 꼴찌보다 앞에 옵니다.
 * <b>줄 세우는 곳과 자르는 곳은 같아야 합니다.</b>
 *
 * <h4>가 봤는지도 서버가 판단한다</h4>
 *
 * <p>기본 정렬이 "안 가 본 곳 먼저"라 <b>임장 여부가 정렬의 입력</b>이 됐습니다.
 * 그 판단이 화면에만 있으면([I226]의 규칙이 화면에만 있었습니다) 서버는 다른 순서로
 * 자릅니다. 여기로 옮깁니다.
 *
 * <h4>잘라도 지도는 전부 본다</h4>
 *
 * <p>지도와 임장 플래너는 <b>전체</b>를 알아야 합니다. 잘린 목록으로 지도를 그리면
 * 매물이 사라진 것처럼 보입니다. 그래서 좌표만 담은 얇은 목록을 따로 냅니다
 * ({@link #pins}) — 채점까지 붙은 전체 목록을 또 받으면 자른 보람이 없습니다.
 */
@Service
public class PropertyListService {

    /** 한 쪽의 기본 크기 (설계 I240). 화면이 안 보내도 이 값으로 자른다 */
    public static final int DEFAULT_PAGE_SIZE = 30;

    /**
     * 한 번에 내보낼 수 있는 최대 건수.
     *
     * <p><b>화면이 시키는 대로 다 주면 자른 의미가 없습니다.</b> {@code size=100000} 한 번에
     * 전부 받아 가면 [I219]에서 국토부에 그랬듯 <b>자른 척만</b> 하게 됩니다.
     */
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

    /**
     * 목록 한 쪽.
     *
     * <p>전체를 채점해 줄 세운 뒤 자릅니다. <b>채점 없이 SQL 로 자를 수는 없습니다</b> —
     * 총점은 어디에도 저장하지 않고 읽을 때마다 그때의 가중치로 계산하기 때문입니다
     * ([I173]). 대신 목록 전체를 모으는 데 드는 쿼리는 이미 매물 수와 무관합니다
     * ([I124]·[I238]).
     *
     * <p>그래서 이 자르기가 줄이는 것은 <b>왕복 크기와 화면이 그리는 양</b>입니다.
     * 매물 하나에 채점 14줄과 전망 요약이 붙으니 그것만으로도 작지 않습니다.
     */
    public ScoredPropertyPage page(DealType dealType, PropertySort sort, int page, int size) {
        return page(dealType, sort, page, size, false);
    }

    /**
     * @param archived 아카이빙한 것만 볼 것인가 (설계 I241)
     */
    public ScoredPropertyPage page(DealType dealType, PropertySort sort, int page, int size,
                                   boolean archived) {
        final List<ScoredPropertyResponse> all = sorted(scoringService.list(dealType, archived), sort);
        // 치워 둔 것의 수는 지금 보는 탭과 무관하게 실린다 (설계 I241).
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

    /** 지도와 임장 플래너가 쓰는 얇은 전체 목록. */
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

    /**
     * 가 본 곳 (설계 I226).
     *
     * <p>둘 중 하나면 가 본 것입니다 — <b>방문 기록</b>이 있거나 <b>쾌적함을 매겼거나</b>.
     * 쾌적함은 가 보지 않고는 매길 수 없는 항목이라, 매겼다면 다녀온 것입니다.
     */
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

    /**
     * <b>내가</b> 쾌적함을 매긴 매물 (설계 I118).
     *
     * <p>그룹 평균으로 보면 <b>남이 다녀온 곳이 내 목록에서 뒤로 밀립니다</b> —
     * 정작 나는 안 가 봤는데요.
     */
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
        // 같은 값이면 이름순 — 새로고침마다 순서가 흔들리면 눈이 못 따라간다 (설계 I221).
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

    /**
     * 아직 안 잰 것은 <b>맨 뒤</b>로 (설계 I221).
     *
     * <p>등록 직후에는 점수도 직주근접도 없습니다([I220]). 그걸 0으로 보면
     * <b>"나쁜 매물"로 줄 세워집니다</b> — 아직 모르는 것과 나쁜 것은 다릅니다.
     *
     * <p>오름차순에서도 뒤로 갑니다. "싼 순"으로 세웠는데 <b>가격을 모르는 것이 맨 앞</b>에
     * 오면 안 됩니다.
     */
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

    /**
     * 항목 하나의 점수. 아직 안 잰 것은 null 이다 — 0이 아니다 (설계 I221).
     *
     * <p>{@code findFirst()} 로 받으면 <b>점수가 없는 항목에서 터집니다</b>
     * ({@code Optional.of(null)}). 등록 직후에는 직주근접이 비어 있는 것이 정상이라
     * 드문 일도 아닙니다 — 쪽 나누기 테스트가 잡아냈습니다.
     */
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
