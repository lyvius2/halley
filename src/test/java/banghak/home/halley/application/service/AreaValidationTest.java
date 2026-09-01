package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.exception.InvalidPropertyRequestException;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 전용면적은 공급면적보다 클 수 없다 (설계 I233).
 *
 * <p>둘을 바꿔 넣는 일이 실제로 있었습니다 — 상계주공7단지에 전용 `49.94` 대신
 * <b>공급 `71.02`</b> 가 들어갔고, 그 때문에 국토부 실거래가 <b>한 건도 안
 * 맞았습니다.</b> 화면은 그냥 "거래 내역이 없습니다" 였습니다.
 *
 * <p>채점·대출·실거래가 모두 전용면적을 봅니다. <b>틀려도 아무 데서도 안 걸리는</b>
 * 값이라 입력 시점에 막습니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("면적 검증 (설계 I233)")
class AreaValidationTest {

    /** 이 테스트는 검증만 본다 — 보정이 붙으면 느려지고 실패 원인이 흐려진다. */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired private PropertyService propertyService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void login() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
    }

    @AfterEach
    void logout() {
        GroupTestSupport.logout();
    }

    /** 상계주공7단지에서 실제로 일어난 일 그대로입니다. */
    @Test
    @DisplayName("전용이 공급보다 크면 거부한다 — 실제로 바꿔 넣은 일이 있었다")
    void rejectsSwappedAreas() {
        assertThatThrownBy(() -> propertyService.create(
                request("뒤바뀐매물", new BigDecimal("49.94"), new BigDecimal("71.02"))))
                .isInstanceOf(InvalidPropertyRequestException.class)
                .hasMessageContaining("71.02")
                .hasMessageContaining("49.94");
    }

    @Test
    @DisplayName("제대로 넣으면 통과한다")
    void acceptsCorrectAreas() {
        assertThatCode(() -> propertyService.create(
                request("정상매물", new BigDecimal("71.02"), new BigDecimal("49.94"))))
                .doesNotThrowAnyException();
    }

    /**
     * <b>둘 중 하나만 아는 매물</b>이 실제로 있습니다 — 붙여넣기에서 한쪽만 읽히거나,
     * 손으로 넣다 만 경우입니다. 모르는 것을 거부할 이유는 없습니다.
     */
    @Test
    @DisplayName("한쪽만 있으면 따지지 않는다 — 모르는 것과 틀린 것은 다르다")
    void skipsWhenOneSideIsUnknown() {
        assertThatCode(() -> {
            propertyService.create(request("공급만", new BigDecimal("71.02"), null));
            propertyService.create(request("전용만", null, new BigDecimal("49.94")));
            propertyService.create(request("둘다없음", null, null));
        }).doesNotThrowAnyException();
    }

    /**
     * 오피스텔·도시형생활주택은 공급과 전용이 같게 적히기도 합니다.
     * <b>같은 값은 거짓말이 아닙니다.</b>
     */
    @Test
    @DisplayName("같은 값은 통과한다")
    void equalAreasPass() {
        assertThatCode(() -> propertyService.create(
                request("같은면적", new BigDecimal("59.90"), new BigDecimal("59.90"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("수정할 때도 막는다 — 등록만 막으면 고치면서 다시 뒤집을 수 있다")
    void updateIsGuardedToo() {
        final Long id = propertyService.create(
                request("수정대상", new BigDecimal("71.02"), new BigDecimal("49.94"))).id();

        assertThatThrownBy(() -> propertyService.update(id,
                request("수정대상", new BigDecimal("49.94"), new BigDecimal("71.02")), null))
                .isInstanceOf(InvalidPropertyRequestException.class);

        // 원래 값은 그대로다 — 막았으니 바뀌지 않아야 한다
        assertThat(propertyService.get(id).areaExclusiveM2())
                .isEqualByComparingTo(new BigDecimal("49.94"));
    }

    private PropertyRequest request(String name, BigDecimal supply, BigDecimal exclusive) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                supply, exclusive, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
