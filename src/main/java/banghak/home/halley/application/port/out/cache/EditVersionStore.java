package banghak.home.halley.application.port.out.cache;

/** 동시 편집 감지용 버전 저장소. */
public interface EditVersionStore {

    long current(String key);

    long bump(String key);
}
