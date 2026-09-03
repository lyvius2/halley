package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.ComplexRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.domain.property.Complex;
import banghak.home.halley.domain.property.ComplexKey;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 매물을 단지에 붙인다 (설계 I266). 관계는 {@code property.complex_id} 열로 남긴다 —
 * {@code Property} 레코드(55칸)에 더하지 않고, 실거래 매칭에 쓰는 열쇠만 여기서 만든다.
 */
@Slf4j
@Service
public class ComplexService {

    private final ComplexRepository complexRepository;
    private final PropertyRepository propertyRepository;

    public ComplexService(ComplexRepository complexRepository, PropertyRepository propertyRepository) {
        this.complexRepository = complexRepository;
        this.propertyRepository = propertyRepository;
    }

    /** 이 매물의 단지 — 없으면 만든다. 이름·동·번지가 같으면 102동이든 104동이든 같은 단지다. */
    public Complex of(Property property) {
        final ComplexKey key = ComplexKey.of(property.name(), property.addressJibun());
        return complexRepository.findOrCreate(key.value(), new Complex(
                null, key.value(), property.name(), property.addressJibun(),
                property.lat(), property.lng(), null));
    }

    /** 이 매물의 단지 — 없으면 없는 대로. 읽기만 하는 자리가 쓴다, 표를 새로 만들지 않는다. */
    public Optional<Complex> find(Property property) {
        return complexRepository.findByMatchKey(
                ComplexKey.of(property.name(), property.addressJibun()).value());
    }

    /** 매물에 단지 번호를 적어 둔다. 이름·주소를 고치면 단지가 바뀔 수 있어 수정 때도 다시 부른다. */
    public void attach(Property property) {
        if (property == null || property.id() == null) {
            return;
        }
        final Complex complex = of(property);
        propertyRepository.setComplexId(property.id(), complex.id());
    }
}
