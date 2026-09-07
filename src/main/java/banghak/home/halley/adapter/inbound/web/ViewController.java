package banghak.home.halley.adapter.inbound.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 앱 껍데기를 내려 준다. */
@Controller
public class ViewController {

    private final String kakaoJsKey;
 /** 배포마다 바뀌는 값. 정적 파일 주소에 붙여 브라우저 캐시를 무효화한다. */
    private final String assetVersion = String.valueOf(System.currentTimeMillis());

    public ViewController(@Value("${kakao.js-key:}") String kakaoJsKey) {
        this.kakaoJsKey = kakaoJsKey;
    }

 /** 주소는 서버로 오지 않습니다. */
    @GetMapping("/")
    public String shell(Model model) {
        model.addAttribute("kakaoJsKey", kakaoJsKey);
        model.addAttribute("assetVersion", assetVersion);
        return "index";
    }
}
