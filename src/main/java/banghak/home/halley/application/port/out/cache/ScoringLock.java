package banghak.home.halley.application.port.out.cache;

/** 매물별 채점 잠금. */
public interface ScoringLock {


    boolean tryLock(Long propertyId);

    void unlock(Long propertyId);
}
