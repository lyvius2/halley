package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.llm.LlmModelOption;

import java.util.List;

/** 쓸 수 있는 모델을 물어 온다 (설계 I267) — 손으로 적어 두면 모델이 바뀔 때마다 배포해야 한다. */
public interface ClaudeModelsPort {

    /** @return 못 받으면 <b>빈 목록</b>. 예외로 올리지 않는다 — 설정 화면이 죽으면 안 된다 */
    List<LlmModelOption> list();
}
