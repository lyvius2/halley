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

    public PropertyImageResponse upload(Long propertyId, MultipartFile file, ImageType type) {
        propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        if (file == null || file.isEmpty()) {
            throw new InvalidPropertyRequestException("이미지 파일이 필요합니다");
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
            final int sortOrder = propertyImageRepository.findByPropertyId(propertyId).size();
            final PropertyImage saved = propertyImageRepository.save(new PropertyImage(
                    null, propertyId, type,
                    "/uploads/" + propertyId + "/" + originalName, sortOrder));
            return new PropertyImageResponse(saved.id(), saved.propertyId(), saved.imageType(), saved.storagePath());
        } catch (IOException e) {
            throw new InvalidPropertyRequestException("이미지 처리에 실패했습니다: " + e.getMessage());
        }
    }

    public List<PropertyImageResponse> list(Long propertyId) {
        propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        return propertyImageRepository.findByPropertyId(propertyId).stream()
                .sorted(Comparator.comparing(PropertyImage::sortOrder))
                .map(i -> new PropertyImageResponse(i.id(), i.propertyId(), i.imageType(), i.storagePath()))
                .toList();
    }
}
