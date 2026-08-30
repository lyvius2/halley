package banghak.home.halley.adapter.inbound.web.dto;

/** 닉네임 중복 확인 (설계 I89 · 규칙 17). */
public record NicknameCheckResponse(String nickname, boolean available) {
}
