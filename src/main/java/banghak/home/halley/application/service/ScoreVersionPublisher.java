package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.cache.EditVersionStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 카드가 바뀐 것을 화면에 알리는 판 번호 (설계 I85 · I285).
 *
 * <p>목록은 매물마다 이 번호를 들고 있다가 서버 값과 달라지면 다시 받습니다.
 * <b>번호가 안 오르면 화면은 영영 옛것을 봅니다.</b>
 *
 * <h4>올리는 곳이 둘입니다</h4>
 *
 * <p>채점이 끝날 때와 <b>가격 전망이 저장될 때</b>입니다. 둘 다 카드에 그려지는 것이라
 * 어느 쪽이 바뀌어도 화면은 다시 받아야 합니다. 전망은 오래 걸려(LLM) 채점보다 한참
 * 뒤에 끝나는데, 예전에는 그때 아무 신호도 없어 <b>카드가 「분석 중」 표시(◌)에서
 * 화살표로 바뀌지 않았습니다.</b>
 *
 * <p>키와 올리는 방법을 <b>한 곳에</b> 둡니다 — 부르는 쪽마다 `"score:" + id` 를
 * 되풀이하면 언젠가 한 곳이 어긋납니다([I239]에서 캐시로 같은 일을 겪었습니다).
 *
 * <h4>커밋한 뒤에 올립니다</h4>
 *
 * <p>번호는 Redis 에 있고 값은 DB 에 있습니다. 트랜잭션 안에서 올리면 <b>번호가 먼저
 * 보이고 값은 커밋 뒤에</b> 보입니다. 그 틈에 화면이 읽으면 "번호는 새것인데 값은
 * 비어 있는" 판을 새것으로 알고 붙들고, 그 뒤로는 번호가 다시 바뀔 일이 없어
 * <b>영영 못 빠져나옵니다.</b> 틈은 좁지만 한 번 걸리면 되돌아오지 않습니다.
 */
@Component
public class ScoreVersionPublisher {

    private final EditVersionStore editVersionStore;

    public ScoreVersionPublisher(EditVersionStore editVersionStore) {
        this.editVersionStore = editVersionStore;
    }

    public long current(Long propertyId) {
        return editVersionStore.current(key(propertyId));
    }

    /** 트랜잭션 안이면 커밋 뒤에, 밖이면 그 자리에서 올린다. */
    public void bump(Long propertyId) {
        final String key = key(propertyId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            editVersionStore.bump(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                editVersionStore.bump(key);
            }
        });
    }

    /**
     * 편집 버전(`property:`)과 <b>키를 나눕니다.</b> 매물 정보를 고치지 않아도 채점·전망은
     * 바뀝니다. 한 키에 섞으면 화면이 "무엇이 바뀌었는지" 구분하지 못합니다.
     */
    private String key(Long id) {
        return "score:" + id;
    }
}
