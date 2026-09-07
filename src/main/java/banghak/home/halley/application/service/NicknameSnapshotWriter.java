package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.PropertyCommentRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 탈퇴 직전에 닉네임을 값으로 남긴다. */
@Slf4j
@Service
public class NicknameSnapshotWriter {

    private final PropertyRepository propertyRepository;
    private final PropertyCommentRepository commentRepository;

    public NicknameSnapshotWriter(PropertyRepository propertyRepository,
                                  PropertyCommentRepository commentRepository) {
        this.propertyRepository = propertyRepository;
        this.commentRepository = commentRepository;
    }

    public void snapshot(Long userId, String nickname) {
        final int properties = propertyRepository.snapshotCreatorNickname(userId, nickname);
        final int comments = commentRepository.snapshotAuthorNickname(userId, nickname);
        log.info("Nickname snapshotted before withdrawal. userId={}, properties={}, comments={}",
                userId, properties, comments);
    }
}
