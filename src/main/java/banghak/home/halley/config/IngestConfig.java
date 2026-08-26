package banghak.home.halley.config;

import banghak.home.halley.ingest.parser.NaverListingTextParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngestConfig {

    @Bean
    public NaverListingTextParser naverListingTextParser() {
        return new NaverListingTextParser(NaverListingTextParser.defaultExtractors());
    }
}
