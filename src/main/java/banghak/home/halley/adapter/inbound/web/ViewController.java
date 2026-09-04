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
 * <p>주소는 {@code #} 뒤에 둡니다 (설계 I244). 서버는 <b>늘 같은 껍데기 하나</b>를
 * 주고, 무엇을 보여 줄지는 앱이 정합니다.
 */
@Controller
public class ViewController {

    private final String kakaoJsKey;
    /** 배포마다 바뀌는 값 (설계 I277) — 정적 파일 주소에 붙여 브라우저 캐시를 무효화한다. */
    private final String assetVersion = String.valueOf(System.currentTimeMillis());

    public ViewController(@Value("${kakao.js-key:}") String kakaoJsKey) {
        this.kakaoJsKey = kakaoJsKey;
    }

    /**
     * 주소는 <b>서버로 오지 않습니다</b> (설계 I244).
     *
     * <p>화면과 모달의 주소를 {@code #} 뒤로 옮겼습니다. 브라우저는 {@code #} 뒤를
     * 서버에 보내지 않으므로, 어느 화면을 열든 <b>요청은 늘 이 하나</b>입니다.
     *
     * <p>전에는 여기에 주소 열여덟 개를 나열해 두었습니다. <b>동작의 근거는 아니었습니다</b> —
     * 딥링크를 받아 준 것은 {@code SpaRoutingFilter} 였고, 이 목록은 "어떤 주소가
     * 있는지 적어 둔 것"이었습니다. 그러면서 {@code app.js} 의 {@code ROUTES} 와
     * <b>같은 것을 두 곳에 적는</b> 상태였습니다 — 늘리려면 둘 다 고쳐야 했고,
     * 하나를 빠뜨려도 아무 일도 안 일어나 <b>틀린 줄도 몰랐습니다.</b>
     *
     * <p>이제 주소는 {@code app.js} 한 곳에만 있습니다.
     */
    @GetMapping("/")
    public String shell(Model model) {
        model.addAttribute("kakaoJsKey", kakaoJsKey);
        model.addAttribute("assetVersion", assetVersion);
        return "index";
    }
}
