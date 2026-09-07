package banghak.home.halley.config.exception;

/** ODsay 하루치를 다 썼다. */
public class TransitQuotaExceededException extends RuntimeException {

    public TransitQuotaExceededException(String message) {
        super(message);
    }
}
