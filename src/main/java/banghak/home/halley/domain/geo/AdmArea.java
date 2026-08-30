package banghak.home.halley.domain.geo;

/**
 * V-World 행정구역 한 건 (설계 I78).
 *
 * @param code     시도는 2자리, 시군구는 5자리
 * @param fullName `경기도 고양시 덕양구`
 * @param name     `고양시 덕양구` — 상위 행정구역을 뺀 이름. 규제지역 매칭이 쓰는 값이다
 */
public record AdmArea(String code, String fullName, String name) {
}
