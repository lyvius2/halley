package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.LegalDongCodeRepository;
import banghak.home.halley.adapter.outbound.persistence.RegulatedAreaRepository;
import banghak.home.halley.adapter.outbound.persistence.RegulationNoticeRepository;
import banghak.home.halley.application.port.out.external.LawNoticePort;
import banghak.home.halley.domain.loan.RegulatedArea;
import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.domain.regulation.RegulationNotice;
import banghak.home.halley.domain.regulation.RegulationNoticeState;
import banghak.home.halley.domain.regulation.RegulationSeedStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 규제지역을 국토부 고시에서 받아 적재한다 (설계 I73).
 *
 * <p><b>사람이 관리하지 않는 이유</b>는 편의가 아니라 실패 방향입니다. 규제지역이 비어 있으면
 * {@code RegulatedAreaService}가 {@code NORMAL}로 판정하고, 비규제 LTV(0.7)는 투기과열지구(0.4)의
 * 배에 가깝습니다. 즉 <b>입력을 잊으면 한도를 과대평가</b>하는데 화면에는 아무 표시가 없습니다.
 *
 * <p>고시가 갱신되면 <b>통째로 갈아 끼웁니다.</b> 고시 본문의 `제개정이유`는 이번에 추가된 지역만
 * 담아서 그것만 반영하면 해제된 지역이 남습니다. 첨부 PDF의 현황표가 전체 목록입니다.
 */
@Slf4j
@Service
public class RegulationNoticeService {

    private final LawNoticePort lawNoticePort;
    private final SigunguNameMatcher nameMatcher;
    private final LegalDongCodeRepository legalDongCodeRepository;
    private final RegulatedAreaRepository regulatedAreaRepository;
    private final RegulationNoticeRepository noticeRepository;

    public RegulationNoticeService(LawNoticePort lawNoticePort,
                                   SigunguNameMatcher nameMatcher,
                                   LegalDongCodeRepository legalDongCodeRepository,
                                   RegulatedAreaRepository regulatedAreaRepository,
                                   RegulationNoticeRepository noticeRepository) {
        this.lawNoticePort = lawNoticePort;
        this.nameMatcher = nameMatcher;
        this.legalDongCodeRepository = legalDongCodeRepository;
        this.regulatedAreaRepository = regulatedAreaRepository;
        this.noticeRepository = noticeRepository;
    }

    /** 규제지역 값을 대출 계산에 믿고 쓸 수 있는지 — 하나라도 미완이면 false. */
    public boolean isTrustworthy() {
        for (final RegulationZone zone : seedableZones()) {
            if (!noticeRepository.find(zone).seedStatus().isTrustworthy()) {
                return false;
            }
        }
        return true;
    }

    public List<RegulationNoticeState> states() {
        return seedableZones().stream().map(noticeRepository::find).toList();
    }

    /**
     * 비어 있을 때만 채운다. 이미 값이 있으면 <b>손대지 않는다</b> — 관리 화면에서 손으로
     * 고친 값을 기동할 때마다 덮으면 수정이 사라진다.
     */
    public void seedIfEmpty() {
        for (final RegulationZone zone : seedableZones()) {
            if (regulatedAreaRepository.countByZone(zone) > 0) {
                log.info("Regulated areas already present - skipping seed. zone={}", zone);
                markReady(zone, noticeRepository.find(zone));
                continue;
            }
            refresh(zone);
        }
    }

    /** 발령일자가 바뀐 규제만 갈아 끼운다. */
    public void refreshOutdated() {
        for (final RegulationZone zone : seedableZones()) {
            final RegulationNoticeState state = noticeRepository.find(zone);
            final Optional<RegulationNotice> notice = lawNoticePort.fetchLatest(zone);
            if (notice.isEmpty()) {
                fail(zone, state, "고시를 조회하지 못했습니다");
                continue;
            }
            if (!state.isOutdatedBy(notice.get()) && state.seedStatus().isTrustworthy()) {
                log.info("Regulation notice unchanged. zone={}, announcedOn={}", zone, state.announcedOn());
                continue;
            }
            log.info("Regulation notice changed - replacing areas. zone={}, was={}, now={}",
                    zone, state.announcedOn(), notice.get().announcedOn());
            apply(zone, notice.get());
        }
    }

    /** 한 규제를 지금 고시로 다시 적재한다. */
    public void refresh(RegulationZone zone) {
        final RegulationNoticeState state = noticeRepository.find(zone);
        noticeRepository.save(running(state));
        final Optional<RegulationNotice> notice = lawNoticePort.fetchLatest(zone);
        if (notice.isEmpty()) {
            fail(zone, state, "고시를 조회하지 못했습니다");
            return;
        }
        apply(zone, notice.get());
    }

    private void apply(RegulationZone zone, RegulationNotice notice) {
        final RegulationNoticeState state = noticeRepository.find(zone);
        final Map<String, SigunguNameMatcher.Matched> codes =
                nameMatcher.match(notice.areaNames(), legalDongCodeRepository.findAll());
        if (codes.isEmpty()) {
            // 부분 적재를 허용하면 빠진 지역이 비규제로 잡혀 한도가 과대평가된다
            fail(zone, state, "지역명을 법정동코드로 바꾸지 못했습니다 (" + notice.areaNames().size() + "건)");
            return;
        }
        final List<RegulatedArea> areas = new ArrayList<>();
        codes.forEach((name, resolved) -> areas.add(new RegulatedArea(
                null, resolved.code(), zone, resolved.name(),
                notice.announcedOn(), null,
                "국토교통부공고 제" + notice.noticeNo() + "호", Instant.now())));
        regulatedAreaRepository.replaceZone(zone, areas);
        noticeRepository.save(new RegulationNoticeState(
                zone, notice.noticeNo(), notice.announcedOn(),
                RegulationSeedStatus.READY, areas.size(), null, Instant.now()));
        log.info("Regulated areas seeded. zone={}, noticeNo={}, announcedOn={}, areas={}",
                zone, notice.noticeNo(), notice.announcedOn(), areas.size());
    }

    private void fail(RegulationZone zone, RegulationNoticeState state, String message) {
        // 실패를 로그로만 남기면 화면은 비규제(0.7)로 계산한 값을 아무 표시 없이 보여준다
        log.error("Regulated area seeding failed - loan limits may be overestimated. zone={}, reason={}",
                zone, message);
        noticeRepository.save(new RegulationNoticeState(
                zone, state.noticeNo(), state.announcedOn(),
                RegulationSeedStatus.FAILED, state.areaCount(), message, Instant.now()));
    }

    private void markReady(RegulationZone zone, RegulationNoticeState state) {
        if (state.seedStatus().isTrustworthy()) {
            return;
        }
        // 손으로 넣어 둔 값이 이미 있으면 그것을 신뢰한다
        noticeRepository.save(new RegulationNoticeState(
                zone, state.noticeNo(), state.announcedOn(), RegulationSeedStatus.READY,
                regulatedAreaRepository.countByZone(zone), "수동 등록", Instant.now()));
    }

    private RegulationNoticeState running(RegulationNoticeState state) {
        return new RegulationNoticeState(state.zone(), state.noticeNo(), state.announcedOn(),
                RegulationSeedStatus.RUNNING, state.areaCount(), null, Instant.now());
    }

    /** 고시로 받아올 수 있는 규제만. `NORMAL`은 지정 대상이 아니다. */
    private List<RegulationZone> seedableZones() {
        return List.of(RegulationZone.SPECULATION_OVERHEATED, RegulationZone.ADJUSTMENT_TARGET);
    }
}
