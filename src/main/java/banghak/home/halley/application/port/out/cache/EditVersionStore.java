package banghak.home.halley.application.port.out.cache;

public interface EditVersionStore {

    long current(String key);

    long bump(String key);
}
