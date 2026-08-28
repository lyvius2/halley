package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CommentRequest;
import banghak.home.halley.adapter.inbound.web.dto.CommentResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyCommentRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.AuthenticationRequiredException;
import banghak.home.halley.config.exception.CommentForbiddenException;
import banghak.home.halley.config.exception.CommentNotFoundException;
import banghak.home.halley.config.exception.DuplicateCommentException;
import banghak.home.halley.config.exception.InvalidCommentException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.property.PropertyComment;
import banghak.home.halley.domain.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 매물 코멘트 (설계 I56).
 *
 * <p>사람당 매물 하나에 한 건입니다. 이미 남겼으면 새로 쓰지 않고 자기 글을 고칩니다.
 * 남의 글은 읽을 수만 있습니다.
 */
@Service
public class PropertyCommentService {

    private static final int MAX_LENGTH = 2000;

    private final PropertyCommentRepository commentRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    public PropertyCommentService(PropertyCommentRepository commentRepository,
                                  PropertyRepository propertyRepository,
                                  UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
    }

    public List<CommentResponse> list(Long propertyId) {
        requireProperty(propertyId);
        final Long me = currentUserId();
        final Map<Long, String> nicknames = nicknames();
        return commentRepository.findByPropertyId(propertyId).stream()
                .map(c -> toResponse(c, nicknames, me))
                .toList();
    }

    @Transactional
    public CommentResponse create(Long propertyId, CommentRequest request) {
        requireProperty(propertyId);
        final Long me = requireCurrentUserId();
        if (commentRepository.findByPropertyIdAndUserId(propertyId, me).isPresent()) {
            throw new DuplicateCommentException();
        }
        final PropertyComment saved = commentRepository.save(new PropertyComment(
                null, propertyId, me, validated(request), Instant.now(), null));
        return toResponse(saved, nicknames(), me);
    }

    @Transactional
    public CommentResponse update(Long propertyId, Long commentId, CommentRequest request) {
        final PropertyComment existing = requireOwnComment(propertyId, commentId);
        final PropertyComment updated = commentRepository.update(new PropertyComment(
                existing.id(), existing.propertyId(), existing.userId(),
                validated(request), existing.createdAt(), Instant.now()));
        return toResponse(updated, nicknames(), existing.userId());
    }

    @Transactional
    public void delete(Long propertyId, Long commentId) {
        requireOwnComment(propertyId, commentId);
        commentRepository.delete(commentId);
    }

    private PropertyComment requireOwnComment(Long propertyId, Long commentId) {
        requireProperty(propertyId);
        final PropertyComment existing = commentRepository.findById(commentId)
                .orElseThrow(CommentNotFoundException::new);
        if (!existing.propertyId().equals(propertyId)) {
            throw new CommentNotFoundException();
        }
        if (!existing.userId().equals(requireCurrentUserId())) {
            throw new CommentForbiddenException();
        }
        return existing;
    }

    private String validated(CommentRequest request) {
        final String content = request == null || request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new InvalidCommentException("코멘트 내용은 필수입니다");
        }
        if (content.length() > MAX_LENGTH) {
            throw new InvalidCommentException("코멘트는 " + MAX_LENGTH + "자를 넘을 수 없습니다");
        }
        return content;
    }

    private void requireProperty(Long propertyId) {
        propertyRepository.findById(propertyId).orElseThrow(NotFoundListingsException::new);
    }

    private Map<Long, String> nicknames() {
        return userRepository.findAll().stream()
                .collect(Collectors.toMap(User::id, User::nickname, (a, b) -> a));
    }

    private CommentResponse toResponse(PropertyComment c, Map<Long, String> nicknames, Long me) {
        return new CommentResponse(
                c.id(), c.propertyId(), c.userId(),
                nicknames.getOrDefault(c.userId(), "알 수 없음"),
                c.content(), c.userId().equals(me), c.createdAt(), c.updatedAt());
    }

    private Long currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }

    private Long requireCurrentUserId() {
        final Long id = currentUserId();
        if (id == null) {
            throw new AuthenticationRequiredException();
        }
        return id;
    }
}
