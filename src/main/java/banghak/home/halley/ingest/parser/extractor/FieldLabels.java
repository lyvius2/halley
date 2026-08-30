package banghak.home.halley.ingest.parser.extractor;

import java.util.Set;

public final class FieldLabels {

    /** 블록 스캔 시 다음 섹션으로 넘어가지 않도록 멈추는 라벨 집합 */
    public static final Set<String> SECTION_STOPS = Set.of(
            "초등학교", "배정 초등학교", "버스", "지하철", "주차", "난방", "향", "세대수",
            "사용승인일", "입주가능일", "KB시세", "관리비", "공급면적", "전용면적",
            "해당층/총층", "방/욕실", "방수/욕실수", "동/호", "단지명", "매매가", "보증금",
            "위치", "지번주소", "매물번호", "복층여부");

    private FieldLabels() {
    }
}
