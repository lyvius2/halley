package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyImageResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyImageRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.config.exception.InvalidPropertyRequestException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.property.ImageType;
import banghak.home.halley.domain.property.PropertyImage;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class PropertyImageService {

    private static final int ORIGINAL_MAX = 1920;
    private static final int THUMB_SIZE = 320;

    private final PropertyRepository propertyRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final String imagesDir;

    public PropertyImageService(PropertyRepository propertyRepository,
                                PropertyImageRepository propertyImageRepository,
                                @Value("${app.images.dir:uploads}") String imagesDir) {
        this.propertyRepository = propertyRepository;
        this.propertyImageRepository = propertyImageRepository;
        this.imagesDir = imagesDir;
    }

    /**
     * 이미지 한 장을 올린다 (설계 I63).
     *
     * <p><b>평면도는 매물당 한 장</b>입니다(설계 7.1 D12 "도면 1장 + 실사 N장").
     * 다시 올리면 기존 평면도를 지우고 대체합니다 — 도면이 여러 장 쌓이면 어느 것이 맞는지 알 수 없습니다.
     * 매물사진은 여러 장 쌓입니다.
     *
     * <p>정렬은 평면도가 항상 먼저(0), 매물사진이 그 뒤로 붙습니다. 목록에서 도면을 먼저 보게 하기 위한 것입니다.
     */
    public PropertyImageResponse upload(Long propertyId, MultipartFile file, ImageType type) {
        propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        if (file == null || file.isEmpty()) {
            throw new InvalidPropertyRequestException("이미지 파일이 필요합니다");
        }
        if (type == null) {
            throw new InvalidPropertyRequestException("이미지 종류(평면도/매물사진)는 필수입니다");
        }
        if (type == ImageType.FLOOR_PLAN) {
            replaceExistingFloorPlan(propertyId);
        }
        final String id = UUID.randomUUID().toString().substring(0, 8);
        final Path dir = Paths.get(imagesDir, String.valueOf(propertyId));
        try {
            Files.createDirectories(dir);
            final String originalName = type + "_" + id + "_original.jpg";
            final String thumbName = type + "_" + id + "_thumb.jpg";
            try (InputStream in = file.getInputStream()) {
                Thumbnails.of(in)
                        .size(ORIGINAL_MAX, ORIGINAL_MAX)
                        .keepAspectRatio(true)
                        .useExifOrientation(true)
                        .outputFormat("jpg")
                        .toFile(dir.resolve(originalName).toFile());
            }
            try (InputStream in = file.getInputStream()) {
                Thumbnails.of(in)
                        .size(THUMB_SIZE, THUMB_SIZE)
                        .keepAspectRatio(true)
                        .useExifOrientation(true)
                        .outputFormat("jpg")
                        .toFile(dir.resolve(thumbName).toFile());
            }
            final PropertyImage saved = propertyImageRepository.save(new PropertyImage(
                    null, propertyId, type,
                    "/uploads/" + propertyId + "/" + originalName, nextSortOrder(propertyId, type)));
            return toResponse(saved);
        } catch (IOException e) {
            throw new InvalidPropertyRequestException("이미지 처리에 실패했습니다: " + e.getMessage());
        }
    }

    /** 평면도는 0번, 매물사진은 기존 사진 뒤에 붙는다. */
    private int nextSortOrder(Long propertyId, ImageType type) {
        if (type == ImageType.FLOOR_PLAN) {
            return 0;
        }
        return 1 + (int) propertyImageRepository.findByPropertyId(propertyId).stream()
                .filter(i -> i.imageType() == ImageType.PHOTO)
                .count();
    }

    private void replaceExistingFloorPlan(Long propertyId) {
        propertyImageRepository.findByPropertyId(propertyId).stream()
                .filter(i -> i.imageType() == ImageType.FLOOR_PLAN)
                .forEach(this::removeImage);
    }

    /** 잘못 올린 사진을 지운다. 파일 삭제가 실패해도 레코드는 지운다 — 화면에 남는 편이 더 나쁘다. */
    public void delete(Long propertyId, Long imageId) {
        propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        final PropertyImage image = propertyImageRepository.findById(imageId)
                .orElseThrow(NotFoundListingsException::new);
        if (!image.propertyId().equals(propertyId)) {
            throw new NotFoundListingsException();
        }
        removeImage(image);
    }

    private void removeImage(PropertyImage image) {
        deleteFiles(image);
        propertyImageRepository.delete(image.id());
    }

    /** 원본과 썸네일은 파일명 규칙(`_original`/`_thumb`)으로 짝지어 저장돼 있다. */
    private void deleteFiles(PropertyImage image) {
        try {
            final String fileName = Paths.get(image.storagePath()).getFileName().toString();
            final Path dir = Paths.get(imagesDir, String.valueOf(image.propertyId()));
            Files.deleteIfExists(dir.resolve(fileName));
            Files.deleteIfExists(dir.resolve(fileName.replace("_original.jpg", "_thumb.jpg")));
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to delete image files - removing the record anyway. imageId={}, cause={}",
                    image.id(), e.getMessage());
        }
    }

    /** 평면도가 먼저, 그다음 매물사진 순. */
    public List<PropertyImageResponse> list(Long propertyId) {
        propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        return propertyImageRepository.findByPropertyId(propertyId).stream()
                .sorted(Comparator.comparing((PropertyImage i) -> i.imageType() == ImageType.FLOOR_PLAN ? 0 : 1)
                        .thenComparing(PropertyImage::sortOrder))
                .map(this::toResponse)
                .toList();
    }

    private PropertyImageResponse toResponse(PropertyImage i) {
        return new PropertyImageResponse(i.id(), i.propertyId(), i.imageType(), i.storagePath());
    }
}
