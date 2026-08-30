package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class PropertyRepositoryTest {

    @Autowired
    private PropertyRepository propertyRepository;

    @Test
    @DisplayName("매물을 저장하면 id가 부여되고 입력값이 조회된다")
    void saveAndFindById() {
        // given
        final Property property = property("한빛아파트");

        // when
        final Property saved = propertyRepository.save(property);

        // then
        assertThat(saved.id()).isNotNull();
        assertThat(saved.sourceType()).isEqualTo(SourceType.MANUAL);
        assertThat(saved.listingStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(saved.createdAt()).isNotNull();

        final Optional<Property> found = propertyRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("한빛아파트");
        assertThat(found.get().dealType()).isEqualTo(DealType.SALE);
        assertThat(found.get().priceDeposit()).isEqualTo(550_000_000L);
    }

    @Test
    @DisplayName("매물을 수정하면 모든 편집 필드가 갱신된다")
    void update() {
        // given
        final Property saved = propertyRepository.save(property("수정 전"));

        // when
        final Property updated = propertyRepository.update(new Property(
                saved.id(), "수정 후", "101동 1001호", DealType.JEONSE,
                350_000_000L, 20,
                "서울시 도로명주소", "서울시 지번주소", new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, new BigDecimal("84.9"), "중층", 5, 12, null,
                "3/2", "남향", 2020, null, null,
                new BigDecimal("1.2"), 300, "개별난방", 4, 800_000_000L, null, null, null, null, null, null, null, null, null, null, null,
                saved.sourceType(), saved.sourceUrl(), saved.naverArticleNo(),
                saved.rawPasteText(), saved.parserVersion(), saved.parseConfidence(),
                saved.isDraft(), saved.listingStatus(), saved.active(),
                saved.lastCheckedAt(), saved.checkFailStreak(), saved.soldDetectedAt(),
                saved.groupId(), saved.createdByNickname(),
                saved.createdBy(), saved.createdAt()));

        // then
        assertThat(updated.name()).isEqualTo("수정 후");
        assertThat(updated.dealType()).isEqualTo(DealType.JEONSE);
        assertThat(updated.priceDeposit()).isEqualTo(350_000_000L);
        assertThat(updated.buildingCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("저장된 매물 전체가 findAll로 조회된다")
    void findAll() {
        // given
        propertyRepository.save(property("a"));
        propertyRepository.save(property("b"));

        // when
        final List<Property> all = propertyRepository.findAll();

        // then
        assertThat(all).extracting(Property::name).contains("a", "b");
    }

    @Test
    @DisplayName("매물을 삭제하면 조회되지 않는다")
    void delete() {
        // given
        final Property saved = propertyRepository.save(property("c"));

        // when
        propertyRepository.delete(saved.id());

        // then
        assertThat(propertyRepository.findById(saved.id())).isEmpty();
    }

    private Property property(String name) {
        return new Property(
                null, name, null, DealType.SALE,
                550_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null, null, null, null,
                SourceType.MANUAL, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true,
                null, 0, null, 1L, "테스터", null, null);
    }
}
