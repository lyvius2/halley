package banghak.home.halley.domain.regulation;

/**
 * 규제지역 적재 상태 (설계 I73).
 *
 * <p><b>이 값이 필요한 이유는 실패 방향이 위험하기 때문입니다.</b> `RegulatedAreaService`는 매칭에
 * 실패하면 {@code RegulationZone.NORMAL}로 떨어지고, 비규제 LTV는 0.7로 투기과열지구(0.4)보다
 * 훨씬 높습니다. 즉 <b>데이터가 없으면 한도를 과대평가</b>하는데 그 사실이 화면에 드러나지 않습니다.
 * 적재가 끝나지 않았거나 실패했음을 대출 결과에 실어 보내려고 둡니다.
 */
public enum RegulationSeedStatus {

    /** 아직 시도한 적 없음. */
    NOT_STARTED,
    /** 적재 중 — 기동 직후 잠시 이 상태다. */
    RUNNING,
    /** 정상 적재됨. */
    READY,
    /** 실패 — 값을 믿을 수 없다. */
    FAILED;

    /** 이 상태의 규제지역 값을 대출 계산에 믿고 쓸 수 있는지. */
    public boolean isTrustworthy() {
        return this == READY;
    }
}
