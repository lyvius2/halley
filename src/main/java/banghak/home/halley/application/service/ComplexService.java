package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.ComplexRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.domain.property.Complex;
import banghak.home.halley.domain.property.ComplexKey;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 매물을 단지에 붙인다 (설계 I266).
 *
 * <p>매물마다 고유 번호만 있고 <b>단지라는 개념이 없었습니다.</b> 그래서 같은 단지
 * 같은 평형을 여러 건 등록하면 국토부를 그만큼 다시 불렀습니다.
 *
 * <p><b>{@code Property} 레코드에는 넣지 않았습니다.</b> 이미 55칸이라 한 칸을
 * 더하면 25곳을 고쳐야 하는데, 실거래를 찾는 데 필요한 것은 <b>이름과 주소로
 * 만드는 열쇠</b>뿐입니다. 관계는 {@code property.complex_id} 열로 남깁니다 —
 * 사람이 SQL 로 볼 수 있어야 하고, 그것이 이 요청의 본뜻입니다.
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

    /**
     * 이 매물의 단지 — 없으면 만든다.
     *
     * <p>같은 이름·같은 동·같은 번지면 <b>같은 단지</b>입니다. 102동이든 104동이든
     * 상관없습니다 — 그것이 이 표를 만든 이유입니다.
     */
    public Complex of(Property property) {
        final ComplexKey key = ComplexKey.of(property.name(), property.addressJibun());
        return complexRepository.findOrCreate(key.value(), new Complex(
                null, key.value(), property.name(), property.addressJibun(),
                property.lat(), property.lng(), null));
    }

    /**
     * 이 매물의 단지 — <b>없으면 없는 대로</b>.
     *
     * <p>읽기만 하는 자리(대출 계산 등)는 이것을 씁니다. 값을 보러 왔다가
     * <b>표를 만들고 가면</b> 안 됩니다.
     */
    public java.util.Optional<Complex> find(Property property) {
        return complexRepository.findByMatchKey(
                ComplexKey.of(property.name(), property.addressJibun()).value());
    }

    /**
     * 매물에 단지 번호를 적어 둔다.
     *
     * <p>등록·수정 때 부릅니다. 이름이나 주소를 고치면 <b>단지가 바뀔 수 있습니다</b> —
     * 그래서 수정 때도 다시 답니다.
     */
    public void attach(Property property) {
        if (property == null || property.id() == null) {
            return;
        }
        final Complex complex = of(property);
        propertyRepository.setComplexId(property.id(), complex.id());
    }
}
