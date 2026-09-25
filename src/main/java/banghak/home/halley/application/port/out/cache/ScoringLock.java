package banghak.home.halley.application.port.out.cache;

public interface ScoringLock {

    /** @return 이 호출이 잠갔으면 true. 이미 누가 채점 중이면 false  */
    boolean tryLock(Long propertyId);

    void unlock(Long propertyId);
}
