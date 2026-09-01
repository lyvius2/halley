package banghak.home.halley.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "slack")
public class SlackProperties {
    private boolean enabled = false;
    /**
     * 매물 등록 알림만 스위치가 따로 있습니다.
     *
     * <p>등록은 <b>보정이 끝나야</b> 쓸 만한 내용이 나오는데(점수·AI 추천도), 그 전에 보내면
     * 반쯤 빈 카드가 갑니다. 그래서 기본은 꺼 두고 필요할 때 켭니다.
     * 코멘트·쾌적함·삭제는 <b>사람이 방금 한 일</b>이라 바로 보내도 내용이 찹니다.
     */
    private boolean notifyPropertyCreated = false;
}
