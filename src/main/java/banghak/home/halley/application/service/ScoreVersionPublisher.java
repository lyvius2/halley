package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.cache.EditVersionStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ScoreVersionPublisher {

    private final EditVersionStore editVersionStore;

    public ScoreVersionPublisher(EditVersionStore editVersionStore) {
        this.editVersionStore = editVersionStore;
    }

    public long current(Long propertyId) {
        return editVersionStore.current(key(propertyId));
    }

    /** 트랜잭션 안이면 커밋 뒤에, 밖이면 그 자리에서 올린다.  */
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

    private String key(Long id) {
        return "score:" + id;
    }
}
