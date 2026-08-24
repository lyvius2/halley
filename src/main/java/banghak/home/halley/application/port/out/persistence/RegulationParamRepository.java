package banghak.home.halley.application.port.out.persistence;

import banghak.home.halley.domain.loan.RegulationParam;

import java.util.List;
import java.util.Optional;

public interface RegulationParamRepository {

    RegulationParam save(RegulationParam param);

    Optional<RegulationParam> findById(Long id);

    List<RegulationParam> findByProfile(String profile);

    void delete(Long id);
}
