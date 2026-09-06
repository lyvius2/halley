'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

/**
 * 쾌적함은 사람마다 따로 매긴다 (설계 I118).
 *
 * <p>A 가 매긴 뒤 B 가 모달을 열었을 때의 응답 그대로다 — 서버는 정상이다.
 * `myScore` 는 비어 있고, `autoScore` 에는 A 의 값이 환산돼 들어 있다.
 */
const comfortAfterOthersScored = () => ({
    property: { id: 1 },
    scores: [{
        code: 'COMFORT',
        name: '공간의 쾌적함',
        scoringType: 'MANUAL',
        autoScore: 100.0,
        manualScore: null,
        effectiveScore: 100.0,
        scoreSource: 'AUTO',
        othersAverage: 5.0,
        othersCount: 1,
        myScore: null,
    }],
});

test('남이 매겨도 쾌적함 칸은 잠기지 않는다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(app.scoreLocked(comfortAfterOthersScored().scores[0]), false);
});

test('남이 매겨도 내 칸은 비어 있다 — 남의 점수를 내 것처럼 채우지 않는다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when
    app.applyScoreForm(comfortAfterOthersScored());

    // then
    assert.equal(app.scoreForm.COMFORT, '');
});

/**
 * 여기가 신고된 자리다.
 *
 * <p>내 칸이 비어 있으면 `<input type=range min=1 max=5 value="">` 는 값이 잘못돼
 * 브라우저가 <b>가운데(3)로 손잡이를 놓습니다.</b> 화면은 3 을 가리키는데 모델은
 * 빈 값이라, B 가 "3 이 맞다" 고 보고 손대지 않으면 <b>바뀐 것이 없어 아무것도
 * 안 보냅니다</b> — 저장을 눌러도 조용히 넘어갑니다.
 */
test('빈 칸일 때도 슬라이더가 가리키는 값이 모델에 있어야 한다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when
    app.applyScoreForm(comfortAfterOthersScored());

    // then — 화면이 가리키는 값과 저장될 값이 같아야 한다
    const shown = app.scoreSliderValue(app.scoreProperty.scores[0]);
    assert.equal(String(app.changedScores().COMFORT), String(shown),
        '슬라이더가 가리키는 값과 저장될 값이 다르다');
});

test('B 가 점수를 고르면 그 값이 저장 대상이 된다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    app.applyScoreForm(comfortAfterOthersScored());

    // when — 슬라이더를 4 로 옮긴다
    app.scoreForm.COMFORT = '4';

    // then
    assert.equal(app.changedScores().COMFORT, 4);
});

/**
 * 손대지 않아도 <b>아직 안 매긴 항목</b>은 보내야 한다 — 그래야 B 가 기본값
 * 그대로 저장할 수 있다. 이미 매긴 값과 같으면 보내지 않는다(설계 I111).
 */
test('아직 안 매긴 쾌적함은 손대지 않아도 저장된다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when
    app.applyScoreForm(comfortAfterOthersScored());

    // then
    assert.ok(app.changedScores().COMFORT != null,
        'B 가 슬라이더를 안 건드리면 아무것도 저장되지 않는다');
});

test('이미 내가 매긴 값을 그대로 두면 다시 보내지 않는다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    const scored = comfortAfterOthersScored();
    scored.scores[0].myScore = 3;

    // when
    app.applyScoreForm(scored);

    // then
    assert.equal(app.changedScores().COMFORT, undefined);
});

test('자동 채점된 AUTO 항목은 손대지 않으면 안 보낸다 (설계 I111)', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when
    app.applyScoreForm({
        property: { id: 1 },
        scores: [{ code: 'PRICE', name: '가격', scoringType: 'AUTO',
            autoScore: 70, manualScore: null, effectiveScore: 70, scoreSource: 'AUTO' }],
    });

    // then
    assert.equal(app.changedScores().PRICE, undefined);
});

/**
 * 미산출 항목까지 손 안 대고 저장되면 안 된다 (설계 I111).
 *
 * <p>미산출은 대개 <b>그때 외부 조회가 실패한 것</b>이라, 슬라이더 가운데 값이
 * 그대로 저장되면 <b>실패가 사람이 매긴 점수로 굳습니다.</b> 쾌적함처럼
 * 사람마다 매기는 항목만 예외다.
 */
test('미산출 항목은 손대지 않으면 저장되지 않는다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when — 조회가 실패해 점수가 비어 있는 항목
    app.applyScoreForm({
        property: { id: 1 },
        scores: [{ code: 'STATION', name: '역세권', scoringType: 'AUTO',
            autoScore: null, manualScore: null, effectiveScore: null,
            scoreSource: 'FALLBACK', fallbackReason: '조회 실패' }],
    });

    // then
    assert.equal(app.changedScores().STATION, undefined,
        '미산출이 슬라이더 가운데 값으로 굳었다');
});

test('쾌적함 슬라이더의 기본값은 1이다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when
    app.applyScoreForm(comfortAfterOthersScored());

    // then
    assert.equal(app.scoreSliderValue(app.scoreProperty.scores[0]), 1);
    assert.equal(app.changedScores().COMFORT, 1);
});

/**
 * 미산출은 <b>모르는 것</b>이지 0점이 아니다 (설계 I220).
 *
 * <p>슬라이더는 어떤 값이든 손잡이를 놓아야 하지만, 그 값을 <b>점수처럼 보여 주면</b>
 * 조회가 실패한 항목이 "0점을 받았다"로 읽힌다.
 */
test('미산출 항목을 0점으로 보여 주지 않는다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when
    app.applyScoreForm({
        property: { id: 1 },
        scores: [{ code: 'STATION', name: '역세권', scoringType: 'AUTO',
            autoScore: null, manualScore: null, effectiveScore: null,
            scoreSource: 'FALLBACK', fallbackReason: '조회 실패' }],
    });

    // then
    assert.equal(app.scoreDisplayValue(app.scoreProperty.scores[0]), '–',
        '미산출이 0점으로 보인다');
});

test('아직 안 매긴 쾌적함은 저장될 값을 그대로 보여 준다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when
    app.applyScoreForm(comfortAfterOthersScored());

    // then — 손대지 않아도 1로 저장되므로 1을 보여 주는 것이 맞다
    assert.equal(app.scoreDisplayValue(app.scoreProperty.scores[0]), 1);
    assert.equal(app.changedScores().COMFORT, 1);
});
