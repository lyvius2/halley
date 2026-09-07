package banghak.home.halley.domain.regulation;

/** 규제지역 적재 상태. */
public enum RegulationSeedStatus {

 /** 아직 시도한 적 없음. */
    NOT_STARTED,
 /** 적재 중. 기동 직후 잠시 이 상태다. */
    RUNNING,
 /** 정상 적재됨. */
    READY,
 /** 실패. 값을 믿을 수 없다. */
    FAILED;

 /** 이 상태의 규제지역 값을 대출 계산에 믿고 쓸 수 있는지. */
    public boolean isTrustworthy() {
        return this == READY;
    }
}
