package banghak.home.halley.adapter.inbound.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 앱 껍데기를 내려 준다.
 *
 * <p><b>화면마다 주소를 둡니다</b> (설계 I188). SPA 라도 <b>지금 보는 것을 링크로
 * 건넬 수 있어야</b> 합니다 — Slack 알림에서 그 매물로 바로 가는 것이 그것 때문입니다.
 *
 * <p>실제 화면 전환은 브라우저가 합니다. 서버는 <b>어느 주소로 들어와도 같은 껍데기</b>를
 * 주고, 그 뒤 무엇을 보여 줄지는 앱이 주소를 읽어 정합니다.
 */
@Controller
public class ViewController {

    private final String kakaoJsKey;

    public ViewController(@Value("${kakao.js-key:}") String kakaoJsKey) {
        this.kakaoJsKey = kakaoJsKey;
    }

    /**
     * 앱이 쓰는 주소들.
     *
     * <p><b>여기 적힌 것이 이 앱의 화면 목록입니다.</b> `/**` 로 뭉뚱그리지 않는 이유는
     * 404 를 살리려는 것이 아니라 — 실측해 보니 <b>이 변경 전에도 알 수 없는 경로는
     * 껍데기를 돌려주고 있었습니다</b> — <b>어떤 주소가 있는지 한 곳에서 보이게</b>
     * 하려는 것입니다. 주소를 늘리려면 여기와 `app.js` 의 `ROUTES` 를 같이 고칩니다.
     *
     * <p>정적 파일(`/image/...`)은 그대로 404 입니다. 그쪽은 리소스 처리기가 받습니다.
     */
    @GetMapping({
            "/",
            "/properties",
            "/properties/new",
            "/properties/paste",
            "/properties/{id}",
            // 모달에도 주소를 준다 (설계 I198). `{modal}` 이 무엇인지는 `app.js` 의
            // MODAL_ROUTES 가 정합니다 — 모르는 값이면 상세를 엽니다
            "/properties/{id}/{modal}",
            "/properties/{id}/photos/{index}",
            "/itinerary",
            "/me",
            "/group",
            "/weights",
            "/users",
            "/users/new",
            "/users/{id}/edit",
            "/compare",
            "/password",
            "/signup",
            "/settings"
    })
    public String shell(Model model) {
        model.addAttribute("kakaoJsKey", kakaoJsKey);
        return "index";
    }
}
