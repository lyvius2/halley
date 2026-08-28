package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.LegalDongCode;
import banghak.home.halley.domain.geo.PoiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalDongCodeKakaoFallbackTest {

    private final LegalDongCodeRepository repository = mock(LegalDongCodeRepository.class);

    @Test
    @DisplayName("테이블에 없으면 카카오 b_code 앞 5자리를 시군구코드로 쓴다")
    void fallsBackToKakaoBCode() {
        // given
        when(repository.findBySigunguAndDong(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        final LegalDongCodeService service = service(query -> List.of(result("1135010500")));

        // when
        final Optional<String> code = service.deriveSigunguCode("서울시 노원구 상계동 771");

        // then
        assertThat(code).contains("11350");
    }

    @Test
    @DisplayName("테이블에 캐시된 동은 카카오를 호출하지 않는다")
    void tableHitSkipsKakao() {
        // given
        when(repository.findBySigunguAndDong("노원구", "상계동")).thenReturn(Optional.of(
                new LegalDongCode("1135010500", null, "노원구", "상계동", null, true, null)));
        final List<String> queries = new ArrayList<>();
        final LegalDongCodeService service = service(query -> {
            queries.add(query);
            return List.of();
        });

        // when
        final Optional<String> code = service.deriveSigunguCode("서울시 노원구 상계동 771");

        // then
        assertThat(code).contains("11350");
        assertThat(queries).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 번지로 실패하면 동까지만 잘라 다시 조회한다")
    void retriesWithoutBunji() {
        // given — 전체 주소는 결과 없음, 동까지만 자르면 조회됨
        when(repository.findBySigunguAndDong(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        final List<String> queries = new ArrayList<>();
        final LegalDongCodeService service = service(query -> {
            queries.add(query);
            return "경기도 성남시 분당구 정자동".equals(query) ? List.of(result("4113510300")) : List.of();
        });

        // when
        final Optional<String> code = service.deriveSigunguCode("경기도 성남시 분당구 정자동 178");

        // then
        assertThat(code).contains("41135");
        assertThat(queries).containsExactly("경기도 성남시 분당구 정자동 178", "경기도 성남시 분당구 정자동");
    }

    @Test
    @DisplayName("카카오로 확보한 코드는 legal_dong_code에 캐시한다")
    void cachesResolvedCode() {
        // given
        when(repository.findBySigunguAndDong(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findById("1135010500")).thenReturn(Optional.empty());
        final LegalDongCodeService service = service(query -> List.of(result("1135010500")));

        // when
        service.deriveSigunguCode("서울시 노원구 상계동 771");

        // then
        verify(repository).save(any(LegalDongCode.class));
    }

    @Test
    @DisplayName("이미 캐시된 코드는 다시 저장하지 않는다")
    void doesNotDuplicateCache() {
        // given
        when(repository.findBySigunguAndDong(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findById("1135010500")).thenReturn(Optional.of(
                new LegalDongCode("1135010500", null, "노원구", "상계동", null, true, null)));
        final LegalDongCodeService service = service(query -> List.of(result("1135010500")));

        // when
        service.deriveSigunguCode("서울시 노원구 상계동 771");

        // then
        verify(repository, never()).save(any(LegalDongCode.class));
    }

    @Test
    @DisplayName("카카오가 b_code를 주지 않으면 빈 값을 반환한다")
    void emptyWhenNoBCode() {
        // given
        when(repository.findBySigunguAndDong(anyString(), anyString())).thenReturn(Optional.empty());
        final LegalDongCodeService service = service(query -> List.of(result(null)));

        // when
        final Optional<String> code = service.deriveSigunguCode("서울시 노원구 상계동 771");

        // then
        assertThat(code).isEmpty();
    }

    @Test
    @DisplayName("번지를 떼어낸 주소를 만든다")
    void stripsBunji() {
        // then
        assertThat(LegalDongCodeService.stripBunji("서울 노원구 상계동 771")).isEqualTo("서울 노원구 상계동");
        assertThat(LegalDongCodeService.stripBunji("경기도 성남시 분당구 정자동 178")).isEqualTo("경기도 성남시 분당구 정자동");
        assertThat(LegalDongCodeService.stripBunji("서울 종로구 세종로 1-1")).isEqualTo("서울 종로구 세종로");
    }

    private LegalDongCodeService service(AddressSearch search) {
        return new LegalDongCodeService(repository, new KakaoLocalPort() {
            @Override
            public List<GeoSearchResult> searchAddress(String query) {
                return search.apply(query);
            }

            @Override
            public List<PoiResult> searchCategory(String categoryGroupCode, double x, double y, int radius) {
                return List.of();
            }

            @Override
            public List<PoiResult> searchKeyword(String query, String categoryGroupCode,
                                                 double x, double y, int radius) {
                return List.of();
            }
        });
    }

    private static GeoSearchResult result(String bCode) {
        return new GeoSearchResult("주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"), bCode);
    }

    @FunctionalInterface
    private interface AddressSearch {
        List<GeoSearchResult> apply(String query);
    }
}
