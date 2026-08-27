package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateHistoryResponse;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateRequest;
import banghak.home.halley.adapter.inbound.web.dto.LoanEstimateResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.LoanEstimateRepository;
import banghak.home.halley.domain.property.DealType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class LoanEstimateServiceTest {

    @Autowired
    private LoanEstimateService loanEstimateService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private LoanEstimateRepository loanEstimateRepository;

    @Test
    @DisplayName("대출 시뮬레이션 결과를 계산하고 저장한다")
    void estimate() {
        // given
        final PropertyResponse property = propertyService.create(request("대출 테스트", 800_000_000L));

        // when
        final LoanEstimateResponse result = loanEstimateService.estimate(property.id(),
                new LoanEstimateRequest(50_000_000L, 300_000_000L, true));

        // then
        assertThat(result.ltvLimit()).isEqualTo(320_000_000L);
        assertThat(result.finalLimit()).isLessThanOrEqualTo(result.ltvLimit());
        assertThat(result.requiredCash()).isEqualTo(800_000_000L - result.finalLimit());
        assertThat(result.acquisitionTax()).isEqualTo(9_333_333L);
        assertThat(loanEstimateRepository.findByPropertyId(property.id())).isNotEmpty();

        // history
        final List<LoanEstimateHistoryResponse> history = loanEstimateService.history(property.id());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().finalLimit()).isEqualTo(result.finalLimit());
    }

    private PropertyRequest request(String name, Long priceDeposit) {
        return new PropertyRequest(
                name, null, DealType.SALE, priceDeposit, null, null,
                "서울시", null, null, null,
                null, null, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null,
                null, null, null);
    }
}
