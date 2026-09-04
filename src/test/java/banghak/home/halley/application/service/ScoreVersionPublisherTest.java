package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.cache.EditVersionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판 번호는 <b>커밋한 뒤에</b> 올라가야 한다 (설계 I285).
 *
 * <p>번호는 Redis 에, 값은 DB 에 있습니다. 트랜잭션 안에서 올리면 번호가 먼저 보이고
 * 값은 커밋 뒤에 보입니다. 그 틈에 화면이 읽으면 "번호는 새것인데 값은 비어 있는" 판을
 * 새것으로 알고 붙들고, 그 뒤로 번호가 다시 바뀔 일이 없어 <b>영영 못 빠져나옵니다.</b>
 */
@DisplayName("채점·전망 판 번호 (설계 I285)")
class ScoreVersionPublisherTest {

    private final Map<String, Long> store = new HashMap<>();

    private final EditVersionStore editVersionStore = new EditVersionStore() {
        @Override
        public long current(String key) {
            return store.getOrDefault(key, 0L);
        }

        @Override
        public long bump(String key) {
            return store.merge(key, 1L, Long::sum);
        }
    };

    private final ScoreVersionPublisher publisher = new ScoreVersionPublisher(editVersionStore);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋 전에 올리지 않는다")
    void doesNotBumpBeforeCommit() {
        // given — 트랜잭션이 열려 있다
        TransactionSynchronizationManager.initSynchronization();

        // when
        publisher.bump(7L);

        // then — 아직이다. 여기서 올리면 값보다 번호가 먼저 보인다
        assertThat(publisher.current(7L))
                .as("커밋 전에 올리면 화면이 빈 판을 새것으로 붙든다")
                .isZero();
    }

    @Test
    @DisplayName("커밋한 뒤에 올라간다")
    void bumpsAfterCommit() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        publisher.bump(7L);

        // when — 커밋을 흉내 낸다
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());

        // then
        assertThat(publisher.current(7L)).isEqualTo(1L);
    }

    @Test
    @DisplayName("트랜잭션 밖에서는 그 자리에서 올린다")
    void bumpsImmediatelyOutsideATransaction() {
        // when
        publisher.bump(7L);

        // then
        assertThat(publisher.current(7L)).isEqualTo(1L);
    }

    @Test
    @DisplayName("매물마다 번호를 따로 센다")
    void countsPerProperty() {
        // when
        publisher.bump(7L);
        publisher.bump(7L);
        publisher.bump(8L);

        // then
        assertThat(publisher.current(7L)).isEqualTo(2L);
        assertThat(publisher.current(8L)).isEqualTo(1L);
    }
}
