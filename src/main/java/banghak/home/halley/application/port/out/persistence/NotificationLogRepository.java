package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.notification.NotificationLog;

import java.util.List;
import java.util.Optional;

public interface NotificationLogRepository {

    NotificationLog save(NotificationLog log);

    Optional<NotificationLog> findById(Long id);

    List<NotificationLog> findAll();

    void delete(Long id);
}
