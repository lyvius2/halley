package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.geo.LegalDongCode;

import java.util.Optional;

public interface LegalDongCodeRepository {

    LegalDongCode save(LegalDongCode legalDongCode);

    Optional<LegalDongCode> findById(String code);

    void delete(String code);
}
