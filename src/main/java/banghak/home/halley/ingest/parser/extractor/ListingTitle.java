package banghak.home.halley.ingest.parser.extractor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 제목 첫 줄을 가른다 (설계 I283).
 *
 * <p>붙여넣기의 첫 줄은 <b>공백 없이 이어 붙어</b> 옵니다.
 *
 * <pre>
 * 휘경롯데 102동매매 8억 8,0003,481만원/3.3㎡평당가 도움말
 * └단지명┘ └동┘└유형┘└─────── 가격·평당가·버튼 ───────┘
 * </pre>
 *
 * <p>그래서 <b>거래유형을 경계로 삼습니다.</b> 그 앞까지가 "단지명 [동]" 이고,
 * 뒤는 값이 아니라 화면에 같이 붙어 온 것들입니다. 예전에는 동/호를 줄 <b>끝</b>에서만
 * 찾아서, 뒤에 뭐라도 붙으면 단지명이 줄 전체가 되고 동/호는 빠졌습니다.
 *
 * <p>거래유형이 안 보이면 줄 전체를 머리로 봅니다 — 제목만 있는 붙여넣기도 있습니다.
 */
final class ListingTitle {

    /** 제목에서 값의 시작을 알리는 말. 여기서부터는 단지명이 아니다. */
    private static final Pattern DEAL_TYPE = Pattern.compile("(매매|전세|월세|단기임대)");

    /** 머리 끝의 "102동" · "101동 501호". */
    private static final Pattern DONG_HO = Pattern.compile("^(.*?)\\s*(\\d+동(?:\\s*\\d+호)?)$");

    private ListingTitle() {
    }

    /** 제목에서 단지명만. 동/호가 안 붙어 있으면 머리 전체가 이름이다. */
    static String name(String title) {
        final String head = head(title);
        final Matcher matcher = DONG_HO.matcher(head);
        if (!matcher.matches()) {
            return head;
        }
        final String name = matcher.group(1).trim();
        // "102동" 만 있는 제목이면 그것이 이름이다 — 이름을 빈 값으로 만들지 않는다
        return name.isEmpty() ? head : name;
    }

    /** 제목에 붙어 온 동/호. 없으면 비어 있다. */
    static Optional<String> dongHo(String title) {
        final Matcher matcher = DONG_HO.matcher(head(title));
        if (!matcher.matches() || matcher.group(1).trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(2).trim());
    }

    /** 제목에 붙어 온 거래유형. 아래쪽 "매매/전세" 토글보다 이쪽이 정확하다. */
    static Optional<String> dealType(String title) {
        final Matcher matcher = DEAL_TYPE.matcher(title);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** 거래유형 앞까지 — "단지명 [동]". */
    private static String head(String title) {
        final Matcher matcher = DEAL_TYPE.matcher(title);
        return (matcher.find() ? title.substring(0, matcher.start()) : title).trim();
    }
}
