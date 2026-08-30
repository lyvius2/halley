package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.PropertyCommentRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 탈퇴 직전에 닉네임을 <b>값으로</b> 남긴다 (설계 I88).
 *
 * <p>탈퇴하면 users 행이 사라지므로 조회로는 이름을 알 수 없습니다. 그런데 매물과 코멘트는
 * 그룹이 살아 있는 한 남고(규칙 15), 거기에는 <b>누가 올렸는지가 보여야</b> 합니다.
 *
 * <p>users 행을 익명화해 남기는 방법도 있지만 그러면 탈퇴자가 닉네임을 영구 점유해
 * 규칙 17(닉네임 중복 불가)과 부딪힙니다.
 */
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
