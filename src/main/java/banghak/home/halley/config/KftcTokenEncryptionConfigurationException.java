package banghak.home.halley.config;

/** 금융결제원 토큰 암호화 키가 유효하지 않을 때 발생한다. */
public class KftcTokenEncryptionConfigurationException extends RuntimeException {

    public KftcTokenEncryptionConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public KftcTokenEncryptionConfigurationException(String message) {
        super(message);
    }
}
