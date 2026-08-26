package banghak.home.halley.config.exception;

import lombok.Getter;

@Getter
public class ListingCheckFailedException extends RuntimeException {

    private final Integer httpStatus;

    public ListingCheckFailedException(Throwable cause, Integer httpStatus) {
        super(cause);
        this.httpStatus = httpStatus;
    }

}
