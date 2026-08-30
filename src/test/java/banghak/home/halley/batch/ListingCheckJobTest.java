package banghak.home.halley.batch;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.ListingCheckLogRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.service.PropertyService;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingAliveChecker;
import banghak.home.halley.domain.property.ListingCheckResult;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.ListingVerdict;
import banghak.home.halley.domain.property.Property;
import org.junit.jupiter.api.DisplayName;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ListingCheckJobTest {

    @TestConfiguration
    static class StubConfig {

        final AtomicReference<ListingVerdict> verdict = new AtomicReference<>(ListingVerdict.GONE);

        @Bean
        @Primary
        ListingAliveChecker checker() {
            return url -> ListingCheckResult.of(verdict.get(), "stub", 200);
        }
    }

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository groupTestUserRepository;

    /** 매물은 그룹에 딸리므로 그룹에 속한 회원으로 로그인해 둔다 (설계 I87). */
    @BeforeEach
    void loginAsGroupMember() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, groupTestUserRepository);
    }

    @AfterEach
    void clearLogin() {
        GroupTestSupport.logout();
    }

    @Autowired
    private StubConfig stubConfig;

    @Autowired
    private ListingCheckJob listingCheckJob;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private ListingCheckLogRepository listingCheckLogRepository;

    @Test
    @DisplayName("GONE 3일 연속 판정 시에만 SOLD_OUT으로 확정된다")
    void goneThreeTimesMarksSoldOut() {
        // given
        stubConfig.verdict.set(ListingVerdict.GONE);
        final Long id = propertyService.create(request("생존 테스트", "https://fin.land.naver.com/articles/111111")).id();
        assertThat(property(id).sourceUrl()).isEqualTo("https://fin.land.naver.com/articles/111111");
        assertThat(propertyRepository.findBatchTargets()).extracting(Property::id).contains(id);

        // when — 3회 실행
        listingCheckJob.run();
        assertThat(property(id).listingStatus()).isEqualTo(ListingStatus.UNREACHABLE);
        assertThat(property(id).checkFailStreak()).isEqualTo(1);

        listingCheckJob.run();
        assertThat(property(id).listingStatus()).isEqualTo(ListingStatus.UNREACHABLE);
        assertThat(property(id).checkFailStreak()).isEqualTo(2);

        listingCheckJob.run();

        // then
        assertThat(property(id).listingStatus()).isEqualTo(ListingStatus.SOLD_OUT);
        assertThat(property(id).active()).isFalse();
        assertThat(property(id).checkFailStreak()).isEqualTo(3);
        assertThat(listingCheckLogRepository.findByPropertyId(id)).hasSize(3);
    }

    @Test
    @DisplayName("ALIVE 판정은 실패 연속 횟수를 0으로 리셋한다")
    void aliveResetsStreak() {
        // given
        stubConfig.verdict.set(ListingVerdict.GONE);
        final Long id = propertyService.create(request("리셋 테스트", "https://fin.land.naver.com/articles/222222")).id();
        listingCheckJob.run();
        listingCheckJob.run();
        assertThat(property(id).checkFailStreak()).isEqualTo(2);

        stubConfig.verdict.set(ListingVerdict.ALIVE);

        // when
        listingCheckJob.run();

        // then
        assertThat(property(id).checkFailStreak()).isZero();
        assertThat(property(id).listingStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    @DisplayName("과반 GONE이면 서킷이 열려 상태를 변경하지 않는다")
    void majorityGoneOpensCircuit() {
        // given
        stubConfig.verdict.set(ListingVerdict.GONE);
        final Long a = propertyService.create(request("서킷A", "https://fin.land.naver.com/articles/333333")).id();
        final Long b = propertyService.create(request("서킷B", "https://fin.land.naver.com/articles/444444")).id();

        // when
        listingCheckJob.run();

        // then — 두 매물 모두 상태/스트릭 변경 없음
        assertThat(property(a).listingStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(property(b).listingStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(property(a).checkFailStreak()).isZero();
    }

    private Property property(Long id) {
        return propertyRepository.findById(id).orElseThrow();
    }

    private PropertyRequest request(String name, String sourceUrl) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null, null,
                "서울시", null, null, null,
                null, null, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                sourceUrl, null, null);
    }
}
