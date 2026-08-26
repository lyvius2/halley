package banghak.home.halley.adapter.outbound.external.naver;

import banghak.home.halley.config.exception.ListingCheckFailedException;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class NaverArticleFallbackFactory implements FallbackFactory<NaverArticleFeignClient> {

    @Override
    public NaverArticleFeignClient create(Throwable cause) {
        return articleNo -> {
            final Integer status = cause instanceof FeignException feign ? feign.status() : null;
            throw new ListingCheckFailedException(cause, status);
        };
    }
}
