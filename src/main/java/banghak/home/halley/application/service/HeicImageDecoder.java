package banghak.home.halley.application.service;

import banghak.home.halley.config.exception.InvalidPropertyImageException;
import banghak.home.halley.config.exception.PropertyImageCodecUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

@Component
public class HeicImageDecoder {

    private static final int HEADER_LENGTH = 64;
    private static final Set<String> HEIC_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs");

    public boolean matches(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            final byte[] header = input.readNBytes(HEADER_LENGTH);
            if (header.length < 12 || !"ftyp".equals(ascii(header, 4))) {
                return false;
            }
            for (int offset = 8; offset + 4 <= header.length; offset += 4) {
                if (HEIC_BRANDS.contains(ascii(header, offset))) {
                    return true;
                }
            }
            return false;
        }
    }

    public BufferedImage decode(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new InvalidPropertyImageException();
            }
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new PropertyImageCodecUnavailableException();
            }
            final ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                final BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new InvalidPropertyImageException();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (PropertyImageCodecUnavailableException | InvalidPropertyImageException e) {
            throw e;
        } catch (IllegalCallerException | LinkageError e) {
            throw new PropertyImageCodecUnavailableException();
        } catch (IOException | RuntimeException e) {
            throw new InvalidPropertyImageException();
        }
    }

    private String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }
}
