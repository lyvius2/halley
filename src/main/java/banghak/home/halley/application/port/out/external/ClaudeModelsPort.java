package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.llm.LlmModelOption;

import java.util.List;

/** 쓸 수 있는 모델을 물어 온다. 손으로 적어 두면 모델이 바뀔 때마다 배포해야 한다. */
public interface ClaudeModelsPort {


    List<LlmModelOption> list();
}
