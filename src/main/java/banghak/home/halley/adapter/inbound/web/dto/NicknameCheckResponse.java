package banghak.home.halley.adapter.inbound.web.dto;

/** 닉네임 중복 확인. */
public record NicknameCheckResponse(String nickname, boolean available) {
}
