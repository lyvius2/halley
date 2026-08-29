package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateRegulationProfileRequest;
import banghak.home.halley.adapter.inbound.web.dto.RegulatedAreaRequest;
import banghak.home.halley.adapter.inbound.web.dto.RegulationParamResponse;
import banghak.home.halley.adapter.inbound.web.dto.RegulationProfileResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateRegulationParamRequest;
import banghak.home.halley.config.exception.InvalidRegulationException;
import banghak.home.halley.domain.loan.RegulationZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("규제 파라미터·규제지역 관리 (설계 I68)")
class RegulationAdminServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private RegulationAdminService service;

    @Test
    @DisplayName("활성 프로파일의 파라미터를 키순으로 돌려준다")
    void listsActiveProfileParams() {
        // when
        final RegulationProfileResponse result = service.profiles();

        // then
        assertThat(result.activeProfile()).isNotBlank();
        assertThat(result.params()).isNotEmpty();
        assertThat(result.params()).extracting(RegulationParamResponse::paramKey).isSorted();
        assertThat(result.params()).extracting(RegulationParamResponse::paramKey)
                .contains("ltv.rate", "dsr.ratio", "ltv.leaseDeduction");
    }

    @Test
    @DisplayName("값을 고치면 저장된다")
    void updatesParamValue() {
        // given
        final RegulationParamResponse target = paramOf("dsr.ratio");

        // when
        final RegulationProfileResponse result = service.updateParams(
                List.of(new UpdateRegulationParamRequest(target.id(), "0.45")));

        // then
        assertThat(valueOf(result, "dsr.ratio")).isEqualTo("0.45");

        // 원복
        service.updateParams(List.of(new UpdateRegulationParamRequest(target.id(), target.paramValue())));
    }

    @Test
    @DisplayName("숫자 칸에 글자가 들어가면 거부한다 — 조용히 기본값으로 떨어지면 안 된다")
    void rejectsNonNumericValue() {
        // given
        final RegulationParamResponse target = paramOf("ltv.rate");

        // when · then
        assertThatThrownBy(() -> service.updateParams(
                List.of(new UpdateRegulationParamRequest(target.id(), "사십퍼센트"))))
                .isInstanceOf(InvalidRegulationException.class)
                .hasMessageContaining("숫자가 아닙니다");
        assertThat(valueOf(service.profiles(), "ltv.rate")).isEqualTo(target.paramValue());
    }

    @Test
    @DisplayName("빈 값은 거부한다")
    void rejectsBlankValue() {
        final RegulationParamResponse target = paramOf("ltv.rate");
        assertThatThrownBy(() -> service.updateParams(
                List.of(new UpdateRegulationParamRequest(target.id(), "  "))))
                .isInstanceOf(InvalidRegulationException.class);
    }

    @Test
    @DisplayName("프로파일은 복제로 만든다 — 옛 값이 남아야 과거 산출을 재현할 수 있다")
    void createsProfileByCopying() {
        // given
        final RegulationProfileResponse before = service.profiles();
        final String name = "test-" + SEQ.incrementAndGet();

        // when
        final RegulationProfileResponse result = service.createProfile(
                new CreateRegulationProfileRequest(name, before.activeProfile(), false));

        // then — 새 프로파일이 생기되 활성은 그대로다
        assertThat(result.profiles()).contains(name);
        assertThat(result.activeProfile()).isEqualTo(before.activeProfile());
    }

    @Test
    @DisplayName("이미 있는 프로파일 이름은 거부한다")
    void rejectsDuplicateProfile() {
        final String active = service.profiles().activeProfile();
        assertThatThrownBy(() -> service.createProfile(
                new CreateRegulationProfileRequest(active, active, false)))
                .isInstanceOf(InvalidRegulationException.class)
                .hasMessageContaining("이미 있는");
    }

    @Test
    @DisplayName("없는 프로파일로는 전환할 수 없다")
    void rejectsUnknownProfileActivation() {
        assertThatThrownBy(() -> service.activateProfile("없는프로파일"))
                .isInstanceOf(InvalidRegulationException.class);
    }

    @Test
    @DisplayName("규제지역을 등록하면 목록에 뜨고 유효 여부가 계산된다")
    void addsRegulatedArea() {
        // given — 오늘 기준 유효한 지정
        final int before = service.areas().size();

        // when
        final var areas = service.addArea(new RegulatedAreaRequest(
                "11680", RegulationZone.SPECULATION_OVERHEATED, "서울 강남구",
                LocalDate.now().minusYears(1), null, "국토교통부 고시 제2025-000호"));

        // then
        assertThat(areas).hasSize(before + 1);
        assertThat(areas).anySatisfy(a -> {
            assertThat(a.codePrefix()).isEqualTo("11680");
            assertThat(a.zoneLabel()).isEqualTo("투기과열지구");
            assertThat(a.active()).isTrue();
            assertThat(a.note()).contains("고시");
        });

        // 정리
        areas.stream().filter(a -> "11680".equals(a.codePrefix()))
                .forEach(a -> service.deleteArea(a.id()));
    }

    @Test
    @DisplayName("법정동코드는 5자리나 10자리여야 한다")
    void rejectsMalformedCodePrefix() {
        assertThatThrownBy(() -> service.addArea(new RegulatedAreaRequest(
                "116", RegulationZone.ADJUSTMENT_TARGET, "잘못", null, null, null)))
                .isInstanceOf(InvalidRegulationException.class)
                .hasMessageContaining("5자리");
    }

    @Test
    @DisplayName("비규제지역으로는 등록할 수 없다 — 지정이 없는 것과 같다")
    void rejectsNormalZone() {
        assertThatThrownBy(() -> service.addArea(new RegulatedAreaRequest(
                "11680", RegulationZone.NORMAL, "의미없음", null, null, null)))
                .isInstanceOf(InvalidRegulationException.class);
    }

    private RegulationParamResponse paramOf(String key) {
        return service.profiles().params().stream()
                .filter(p -> p.paramKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private String valueOf(RegulationProfileResponse response, String key) {
        return response.params().stream()
                .filter(p -> p.paramKey().equals(key))
                .findFirst()
                .orElseThrow()
                .paramValue();
    }
}
