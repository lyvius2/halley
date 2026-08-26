package banghak.home.halley.ingest.parser;

public interface FieldExtractor<T> {

    String key();

    ParseResult<T> extract(TextDocument doc);
}
