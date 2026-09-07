package banghak.home.halley.ingest.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextDocument {

    private final String raw;
    private final List<String> lines;

    public TextDocument(String raw) {
        this.raw = raw;
        this.lines = List.of(raw.split("\\R"));
    }

    public String raw() {
        return raw;
    }

    public String firstNonBlankLine() {
        for (final String line : lines) {
            if (!line.trim().isEmpty()) {
                return line.trim();
            }
        }
        return "";
    }

 /** 라벨 뒤에 붙어 오는 화면 문구. */
    private static final List<String> LABEL_SUFFIXES = List.of(
            "상세내용 숨기기", "상세내용 보기", "도움말 보기", "상세보기", "더보기", "도움말");

 /** 이 줄이 그 라벨인가. 뒤에 붙은 화면 문구는 떼고 본다. */
    private static boolean isLabel(String line, String label) {
        final String trimmed = line.trim();
        if (trimmed.equals(label)) {
            return true;
        }
        if (!trimmed.startsWith(label)) {
            return false;
        }
        final String rest = trimmed.substring(label.length()).trim();
        return LABEL_SUFFIXES.contains(rest);
    }

 /** 라벨 바로 다음 줄을 값으로 읽는다. 네이버의 "라벨 \n 값" 구조에 대응. */
    public Optional<String> valueAfter(String label) {
        for (int i = 0; i < lines.size() - 1; i++) {
            if (isLabel(lines.get(i), label)) {
                final String value = lines.get(i + 1).trim();
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

 /** 라벨과 값이 한 줄에 붙어 있는 경우. */
    public Optional<String> valueOnSameLine(String label) {
        for (final String line : lines) {
            final String trimmed = line.trim();
            if (!trimmed.startsWith(label) || trimmed.length() == label.length()) {
                continue;
            }
            final String rest = trimmed.substring(label.length()).trim();
            if (!rest.isEmpty() && !Character.isLetter(rest.charAt(0))) {
                return Optional.of(rest);
            }
        }
        return Optional.empty();
    }

 /** 라벨 다음 maxLines개(비어 있지 않은) 라인을 반환한다. 지하철·학교처럼 여러 줄 블록을 파싱할 때 사용. */
    public List<String> linesAfter(String label, int maxLines) {
        return linesAfterUntil(label, Set.of(), maxLines);
    }

 /** 라벨 다음 줄을 수집하되, stopLabels에 속한 줄이 나오면 그 앞에서 멈춘다. */
    public List<String> linesAfterUntil(String label, Set<String> stopLabels, int maxLines) {
        for (int i = 0; i < lines.size() - 1; i++) {
            if (isLabel(lines.get(i), label)) {
                final List<String> result = new ArrayList<>();
                for (int j = i + 1; j < lines.size() && result.size() < maxLines; j++) {
                    final String line = lines.get(j).trim();
                    if (!line.isEmpty()) {
                        if (stopLabels.contains(line)) {
                            break;
                        }
                        result.add(line);
                    }
                }
                return result;
            }
        }
        return List.of();
    }

 /** 라벨이 처음 등장한 지점 이후만 담은 문서를 돌려준다. */
    public TextDocument after(String label) {
        for (int i = 0; i < lines.size(); i++) {
            if (isLabel(lines.get(i), label)) {
                return new TextDocument(String.join("\n", lines.subList(i + 1, lines.size())));
            }
        }
        return new TextDocument("");
    }

 /** 원문 전체에서 정규식에 걸리는 모든 그룹(1)을 순서대로 반환한다. */
    public List<String> allMatches(String regex) {
        final Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(raw);
        final List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

 /** 원문 전체에서 첫 정규식 그룹(1)을 반환한다. MULTILINE 필수. */
    public Optional<String> firstMatch(String regex) {
        final Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(raw);
        return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
    }
}
