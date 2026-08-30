package banghak.home.halley.domain.group;

import java.time.Instant;

/**
 * 회원과 매물이 속하는 단위 (설계 I87).
 *
 * <p>회원은 <b>하나의 그룹에만</b> 속하고, 매물은 등록한 회원의 그룹에 딸립니다. 같은 그룹의
 * 회원만 그 매물을 볼 수 있습니다 — 이 앱에서 <b>격리의 경계</b>입니다.
 *
 * <p>{@code createdBy}는 누가 만들었는지 남기는 기록일 뿐 권한이 아닙니다. 이름은 그룹의
 * 누구나 바꿉니다 — 만든 사람이 나가면 아무도 못 바꾸는 상태가 되기 때문입니다.
 */
public record UserGroup(
        Long id,
        String name,
        Long createdBy,
        Instant createdAt
) {
}
