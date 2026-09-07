package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyImageResponse;
import banghak.home.halley.adapter.outbound.persistence.PropertyImageRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.config.ImageStorage;
import banghak.home.halley.config.exception.BusinessException;
import banghak.home.halley.config.exception.InvalidPropertyImageException;
import banghak.home.halley.config.exception.InvalidPropertyRequestException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.property.ImageType;
import banghak.home.halley.domain.property.PropertyImage;
import net.coobird.thumbnailator.Thumbnails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
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
    private final PropertyAccessGuard propertyAccessGuard;
    private final PropertyImageRepository propertyImageRepository;
    private final ImageStorage imageStorage;
    private final HeicImageDecoder heicImageDecoder;

    public PropertyImageService(PropertyAccessGuard propertyAccessGuard,
                                  PropertyRepository propertyRepository,
                                  PropertyImageRepository propertyImageRepository,
                                  ImageStorage imageStorage,
                                  HeicImageDecoder heicImageDecoder) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.propertyRepository = propertyRepository;
        this.propertyImageRepository = propertyImageRepository;
        this.imageStorage = imageStorage;
        this.heicImageDecoder = heicImageDecoder;
    }

 /** 이미지 한 장을 올린다. */
    public PropertyImageResponse upload(Long propertyId, MultipartFile file, ImageType type) {
        propertyAccessGuard.require(propertyId);
        if (file == null || file.isEmpty()) {
            throw new InvalidPropertyRequestException("이미지 파일이 필요합니다");
        }
        if (type == null) {
            throw new InvalidPropertyRequestException("이미지 종류(평면도/매물사진)는 필수입니다");
        }
        final String id = UUID.randomUUID().toString().substring(0, 8);
        final Path dir = imageStorage.dirOf(propertyId);
        final String originalName = type + "_" + id + "_original.jpg";
        final String thumbName = type + "_" + id + "_thumb.jpg";
        final Path original = dir.resolve(originalName);
        final Path thumb = dir.resolve(thumbName);
        boolean heic = false;
        try {
            Files.createDirectories(dir);
            heic = heicImageDecoder.matches(file);
            if (heic) {
                final BufferedImage decoded = heicImageDecoder.decode(file);
                writeHeicJpeg(decoded, ORIGINAL_MAX, original);
                writeHeicJpeg(decoded, THUMB_SIZE, thumb);
            } else {
                writeStandardJpeg(file, ORIGINAL_MAX, original);
                writeStandardJpeg(file, THUMB_SIZE, thumb);
            }
            final PropertyImage saved = propertyImageRepository.save(new PropertyImage(
                    null, propertyId, type,
                    "/uploads/" + propertyId + "/" + originalName, nextSortOrder(propertyId, type)));
            if (type == ImageType.FLOOR_PLAN) {
                replaceExistingFloorPlan(propertyId, saved.id());
            }
            return toResponse(saved);
        } catch (BusinessException e) {
            deleteFailedUpload(original, thumb);
            log.warn("Image upload rejected. propertyId={}, size={}, contentType={}, heic={}, code={}, cause={}",
                    propertyId, file.getSize(), file.getContentType(), heic, e.getCode(), e.getMessage());
            throw e;
        } catch (IOException e) {
            deleteFailedUpload(original, thumb);
            log.warn("Image upload failed. propertyId={}, size={}, contentType={}, heic={}, cause={}",
                    propertyId, file.getSize(), file.getContentType(), heic, e.getMessage());
            throw new InvalidPropertyImageException();
        } catch (RuntimeException e) {
            deleteFailedUpload(original, thumb);
            log.error("Image upload failed unexpectedly. propertyId={}, size={}, contentType={}, heic={}",
                    propertyId, file.getSize(), file.getContentType(), heic, e);
            throw e;
        }
    }

    private void writeHeicJpeg(BufferedImage image, int size, Path target) throws IOException {
        Thumbnails.of(image)
                .size(size, size)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .toFile(target.toFile());
    }

    private void writeStandardJpeg(MultipartFile file, int size, Path target) throws IOException {
        try (InputStream input = file.getInputStream()) {
            Thumbnails.of(input)
                    .size(size, size)
                    .keepAspectRatio(true)
                    .useExifOrientation(true)
                    .outputFormat("jpg")
                    .toFile(target.toFile());
        }
    }

    private void deleteFailedUpload(Path original, Path thumb) {
        try {
            Files.deleteIfExists(original);
            Files.deleteIfExists(thumb);
        } catch (IOException e) {
            log.warn("Failed to clean up rejected image files. original={}, cause={}", original, e.getMessage());
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

    private void replaceExistingFloorPlan(Long propertyId, Long savedImageId) {
        propertyImageRepository.findByPropertyId(propertyId).stream()
                .filter(i -> i.imageType() == ImageType.FLOOR_PLAN)
                .filter(i -> !i.id().equals(savedImageId))
                .forEach(this::removeImage);
    }

 /** 잘못 올린 사진을 지운다. 파일 삭제가 실패해도 레코드는 지운다. 화면에 남는 편이 더 나쁘다. */
    public void delete(Long propertyId, Long imageId) {
        propertyAccessGuard.require(propertyId);
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

 /** 원본과 썸네일은 파일명 규칙(_original/_thumb)으로 짝지어 저장돼 있다. */
    private void deleteFiles(PropertyImage image) {
        try {
            final String fileName = Paths.get(image.storagePath()).getFileName().toString();
            final Path dir = imageStorage.dirOf(image.propertyId());
            Files.deleteIfExists(dir.resolve(fileName));
            Files.deleteIfExists(dir.resolve(fileName.replace("_original.jpg", "_thumb.jpg")));
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to delete image files - removing the record anyway. imageId={}, cause={}",
                    image.id(), e.getMessage());
        }
    }

 /** 평면도가 먼저, 그다음 매물사진 순. */
    public List<PropertyImageResponse> list(Long propertyId) {
        propertyAccessGuard.require(propertyId);
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
