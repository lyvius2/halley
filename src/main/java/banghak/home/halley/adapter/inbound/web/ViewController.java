package banghak.home.halley.adapter.inbound.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    private final String kakaoJsKey;

    public ViewController(@Value("${kakao.js-key:}") String kakaoJsKey) {
        this.kakaoJsKey = kakaoJsKey;
    }

    @GetMapping("/")
    public String shell(Model model) {
        model.addAttribute("kakaoJsKey", kakaoJsKey);
        return "index";
    }
}
