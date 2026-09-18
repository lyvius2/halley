package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.openbanking.OpenBankingAccount;
import banghak.home.halley.domain.openbanking.OpenBankingConnection;
import banghak.home.halley.domain.openbanking.OpenBankingProvider;
import banghak.home.halley.domain.openbanking.OpenBankingSyncLog;
import banghak.home.halley.domain.openbanking.OpenBankingSyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@DisplayName("금융결제원 연결 Repository")
class OpenBankingRepositoryIntegrationTest {
    @Autowired private OpenBankingConnectionRepository connections;
    @Autowired private OpenBankingAccountRepository accounts;
    @Autowired private OpenBankingSyncLogRepository syncLogs;

    @Test
    @DisplayName("연결 계좌와 동기화 이력을 저장하고 사용자별로 조회한다")
    void savesConnectionAccountAndSyncLog() {
        // given
        final Instant now = Instant.parse("2026-09-08T00:00:00Z");
        final OpenBankingConnection connection = connections.save(new OpenBankingConnection(null, 1L,
                OpenBankingProvider.KFTC_OPEN_BANKING, "v1:access", "v1:refresh", now.plusSeconds(3600), now,
                null, null, null));

        // when
        final OpenBankingAccount account = accounts.save(new OpenBankingAccount(null, connection.id(), "a".repeat(64),
                "v1:fintech", "************1234", "004", "국민은행", "예금", "DEMAND", 1_000_000L,
                900_000L, now, null, true, null, null));
        final OpenBankingSyncLog syncLog = syncLogs.save(new OpenBankingSyncLog(null, connection.id(), now, now,
                OpenBankingSyncStatus.SUCCEEDED, null, 1, null));

        // then
        assertThat(connections.findActiveByUserId(1L)).hasValueSatisfying(saved ->
                assertThat(saved.id()).isEqualTo(connection.id()));
        assertThat(accounts.findByConnectionId(connection.id())).extracting(OpenBankingAccount::id)
                .contains(account.id());
        assertThat(syncLogs.findByConnectionId(connection.id())).extracting(OpenBankingSyncLog::id)
                .contains(syncLog.id());
    }
}
