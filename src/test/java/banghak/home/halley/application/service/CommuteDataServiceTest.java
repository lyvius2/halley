package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.scoring.TransitResult;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class CommuteDataServiceTest {

    @TestConfiguration
    static class StubConfig {

        final AtomicInteger calls = new AtomicInteger();

        @Bean
        @Primary
        OdsayTransitPort odsayTransitPort() {
            return (startX, startY, endX, endY) -> {
                calls.incrementAndGet();
                return new TransitResult(45, 1, 8);
            };
        }
    }

    @Autowired
    private StubConfig stubConfig;

    @Autowired
    private CommuteDataService commuteDataService;

    @Autowired
    private UserService userService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @BeforeEach
    void resetCounter() {
        stubConfig.calls.set(0);
    }

    @Test
    @DisplayName("직장이 있는 활성 사용자의 통근 시간을 조회하고 이후에는 캐시를 사용한다")
    void fetchesAndCachesCommute() {
        // given
        userService.create(new CreateUserRequest(
                "worker", "직장인", "worker@example.com", "pw12345!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("126.9"), 0L));
        stubConfig.calls.set(0); // 사용자 생성 시 rescoreAll 호출분 제외
        final User user = userRepository.findByEmail("worker@example.com").orElseThrow();
        final Property property = propertyWithCoords();

        // when
        final Map<Long, Integer> first = commuteDataService.ensureCommuteMinutes(property, List.of(user));
        final Map<Long, Integer> second = commuteDataService.ensureCommuteMinutes(property, List.of(user));

        // then
        assertThat(first).containsEntry(user.id(), 45);
        assertThat(second).containsEntry(user.id(), 45);
        assertThat(stubConfig.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("직장 좌표가 없는 사용자는 통근 조회에서 제외된다")
    void userWithoutWorkplaceSkipped() {
        // given
        userService.create(new CreateUserRequest(
                "no-worker", "무직장", "no-worker@example.com", "pw12345!", UserRole.MEMBER, null, null, null, 0L));
        stubConfig.calls.set(0); // 사용자 생성 시 rescoreAll 호출분 제외
        final User user = userRepository.findByEmail("no-worker@example.com").orElseThrow();
        final Property property = propertyWithCoords();

        // when
        final Map<Long, Integer> result = commuteDataService.ensureCommuteMinutes(property, List.of(user));

        // then
        assertThat(result).isEmpty();
        assertThat(stubConfig.calls.get()).isZero();
    }

    private Property propertyWithCoords() {
        return propertyRepository.findById(propertyService.create(new PropertyRequest(
                "통근 매물", null, DealType.SALE, 500_000_000L, null, null,
                "서울시", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null)).id()).orElseThrow();
    }
}
