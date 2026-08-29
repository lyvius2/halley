package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CommentRequest;
import banghak.home.halley.adapter.inbound.web.dto.CommentResponse;
import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.CommentForbiddenException;
import banghak.home.halley.config.exception.DuplicateCommentException;
import banghak.home.halley.config.exception.InvalidCommentException;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("매물 코멘트 (설계 I56)")
class PropertyCommentServiceTest {

    @Autowired
    private PropertyCommentService commentService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private static final java.util.concurrent.atomic.AtomicInteger SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    private Long propertyId;
    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void setUp() {
        // 같은 스프링 컨텍스트를 공유하므로 테스트마다 아이디가 겹치지 않게 한다
        final String tag = "c" + SEQ.incrementAndGet();
        aliceId = userService.create(new CreateUserRequest(
                "alice-" + tag, "앨리스-" + tag, "alice-" + tag + "@example.com", "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L)).id();
        bobId = userService.create(new CreateUserRequest(
                "bob-" + tag, "밥-" + tag, "bob-" + tag + "@example.com", "password1!", UserRole.MEMBER,
                "회사", new BigDecimal("37.5"), new BigDecimal("127.0"), 300_000_000L, 60_000_000L, 0L)).id();

        login(aliceId);
        propertyId = propertyService.create(new PropertyRequest(
                "코멘트 대상", null, DealType.SALE, 500_000_000L, null, null,
                "서울시", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null)).id();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("남긴 코멘트는 목록에 뜨고 본인 글은 mine으로 표시된다")
    void listMarksOwnComment() {
        // given
        login(aliceId);
        commentService.create(propertyId, new CommentRequest("채광이 좋았습니다"));
        login(bobId);
        commentService.create(propertyId, new CommentRequest("주차가 좁습니다"));

        // when — 밥이 목록을 본다
        final List<CommentResponse> asBob = commentService.list(propertyId);

        // then
        assertThat(asBob).hasSize(2);
        assertThat(asBob).extracting(CommentResponse::nickname)
                .satisfiesExactly(n -> assertThat(n).startsWith("앨리스"), n -> assertThat(n).startsWith("밥"));
        assertThat(asBob).extracting(CommentResponse::mine).containsExactly(false, true);
    }

    @Test
    @DisplayName("한 사람이 같은 매물에 두 번 남길 수 없다 — 고쳐 쓴다")
    void oneCommentPerPersonPerProperty() {
        // given
        login(aliceId);
        commentService.create(propertyId, new CommentRequest("첫 인상"));

        // when · then
        assertThatThrownBy(() -> commentService.create(propertyId, new CommentRequest("다시 생각해보니")))
                .isInstanceOf(DuplicateCommentException.class);
    }

    @Test
    @DisplayName("수정하면 내용과 updatedAt이 바뀐다")
    void updateOwnComment() {
        // given
        login(aliceId);
        final CommentResponse created = commentService.create(propertyId, new CommentRequest("첫 인상"));
        assertThat(created.updatedAt()).isNull();

        // when
        final CommentResponse updated =
                commentService.update(propertyId, created.id(), new CommentRequest("다시 보니 괜찮습니다"));

        // then
        assertThat(updated.content()).isEqualTo("다시 보니 괜찮습니다");
        assertThat(updated.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("남의 코멘트는 수정·삭제할 수 없다")
    void cannotTouchOthersComment() {
        // given — 앨리스가 남긴 글
        login(aliceId);
        final CommentResponse aliceComment = commentService.create(propertyId, new CommentRequest("앨리스 생각"));

        // when · then — 밥이 건드리려 하면 막힌다
        login(bobId);
        assertThatThrownBy(() -> commentService.update(propertyId, aliceComment.id(), new CommentRequest("바꿔치기")))
                .isInstanceOf(CommentForbiddenException.class);
        assertThatThrownBy(() -> commentService.delete(propertyId, aliceComment.id()))
                .isInstanceOf(CommentForbiddenException.class);
    }

    @Test
    @DisplayName("삭제하면 목록에서 빠지고 다시 남길 수 있다")
    void deleteFreesTheSlot() {
        // given
        login(aliceId);
        final CommentResponse created = commentService.create(propertyId, new CommentRequest("지울 글"));

        // when
        commentService.delete(propertyId, created.id());

        // then
        assertThat(commentService.list(propertyId)).isEmpty();
        assertThat(commentService.create(propertyId, new CommentRequest("새로 쓴 글")).content())
                .isEqualTo("새로 쓴 글");
    }

    @Test
    @DisplayName("빈 내용은 거부한다")
    void rejectsBlankContent() {
        login(aliceId);
        assertThatThrownBy(() -> commentService.create(propertyId, new CommentRequest("   ")))
                .isInstanceOf(InvalidCommentException.class);
    }

    private void login(Long userId) {
        final HalleyUserDetails principal =
                new HalleyUserDetails(userRepository.findById(userId).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
