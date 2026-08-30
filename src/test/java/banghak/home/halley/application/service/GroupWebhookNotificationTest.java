package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.SlackPort;
import banghak.home.halley.config.SlackProperties;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 그룹 웹훅 알림 (설계 I96).
 *
 * <p>웹훅이 <b>없으면 아무것도 나가지 않아야</b> 합니다 — 전역 주소로 흘려보내면 우리 매물이
 * 남의 채널에 뜹니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(GroupWebhookNotificationTest.StubConfig.class)
@DisplayName("그룹 웹훅 알림 (설계 I96)")
class GroupWebhookNotificationTest {

    private static final String WEBHOOK = "https://hooks.slack.com/services/T/B/group";

    @TestConfiguration
    static class StubConfig {

        /** 어디로 무엇을 보냈는지 기록한다. */
        final List<String> targets = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        SlackPort slackPort() {
            return (webhookUrl, text) -> {
                targets.add(webhookUrl);
                return true;
            };
        }
    }

    @Autowired
    private StubConfig stub;

    @Autowired
    private SlackProperties slackProperties;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    private Long groupId;

    @BeforeEach
    void setUp() {
        stub.targets.clear();
        slackProperties.setEnabled(true);
        slackProperties.setNotifyPropertyCreated(true);
        groupId = GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
    }

    @AfterEach
    void clearAuth() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("웹훅이 없으면 아무것도 보내지 않는다 — 전역 주소로 흘리지 않는다")
    void sendsNothingWithoutWebhook() {
        // given — 웹훅을 넣지 않은 그룹

        // when
        notificationService.sendPropertyDeleted(groupId, "사라진 매물");

        // then
        assertThat(stub.targets).isEmpty();
    }

    @Test
    @DisplayName("웹훅이 있으면 그 그룹의 주소로만 나간다")
    void sendsToOwnGroupWebhook() {
        // given
        userGroupRepository.updateWebhook(groupId, WEBHOOK);

        // when
        notificationService.sendPropertyDeleted(groupId, "삭제된 매물");

        // then
        assertThat(stub.targets).containsExactly(WEBHOOK);
    }

    @Test
    @DisplayName("다른 그룹의 알림은 우리 채널로 오지 않는다")
    void otherGroupNeverReachesUs() {
        // given — 우리 그룹에는 웹훅이 있고, 남의 그룹에는 없다
        userGroupRepository.updateWebhook(groupId, WEBHOOK);
        final Long otherGroup = GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);

        // when — 남의 그룹 매물이 지워진다
        notificationService.sendPropertyDeleted(otherGroup, "남의 매물");

        // then — 웹훅이 없는 그룹이므로 아무 데도 안 간다
        assertThat(stub.targets).isEmpty();
    }
}
