package banghak.home.halley.application.port.out.cache;

/**
 * 동시 편집 감지용 버전 저장소 (설계 I11 — 낙관적 락).
 * local은 인메모리, live는 Redis의 원자적 increment를 사용한다.
 */
public interface EditVersionStore {

    long current(String key);

    long bump(String key);
}
