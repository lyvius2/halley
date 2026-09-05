'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, templateFragment, renderFragment } = require('./support/harness.js');

/**
 * 슬라이더가 <b>실제로</b> 무엇을 가리키고 무엇을 되돌려주는가 (설계 I287).
 *
 * <p>이번 버그는 "DOM 슬라이더 값과 Alpine 모델 값이 어긋난 것" 이었습니다. 도우미
 * 함수끼리 견주면 <b>템플릿의 `:value`·`@input` 이 다시 어긋나도 통과합니다.</b>
 * 그래서 여기서는 템플릿 조각을 그대로 떼어 Alpine 으로 그리고, <b>진짜 input 요소</b>를
 * 읽습니다.
 */

/** 채점 모달의 슬라이더 조각을 템플릿에서 그대로 가져온다. */
const sliderFragment = () => templateFragment(
    '<div class="score-item-slider"', '</div>');

const comfortUnscored = {
    property: { id: 1 },
    scores: [{
        code: 'COMFORT', name: '공간의 쾌적함', scoringType: 'MANUAL',
        autoScore: 100.0, manualScore: null, effectiveScore: 100.0,
        scoreSource: 'AUTO', othersAverage: 5.0, othersCount: 1, myScore: null,
    }],
};

const missingItem = {
    property: { id: 1 },
    scores: [{
        code: 'STATION', name: '역세권', scoringType: 'AUTO',
        autoScore: null, manualScore: null, effectiveScore: null,
        scoreSource: 'FALLBACK', fallbackReason: '조회 실패',
    }],
};

/** 조각은 `s` 를 쓰므로 x-for 대신 하나만 그린다. */
async function renderSlider(window, scored) {
    const html = `<template x-for="s in (scoreProperty?.scores || [])" :key="s.code">
        <div>${sliderFragment()}</div>
    </template>`;
    return renderFragment(window, html, app => app.applyScoreForm(scored));
}

test('아직 안 매긴 쾌적함은 슬라이더가 1을 가리킨다 — 실제 input 값으로 확인', async () => {
    // given
    const { window } = bootWindow();

    // when
    const { root } = await renderSlider(window, comfortUnscored);

    // then
    const range = root.querySelector('input[type=range]');
    assert.ok(range, '슬라이더가 안 그려졌다');
    assert.equal(range.value, '1');
    assert.equal(range.min, '1');
    assert.equal(range.max, '5');
});

test('슬라이더를 움직이면 그 값이 저장 대상이 된다 — 실제 input 이벤트로 확인', async () => {
    // given
    const { window } = bootWindow();
    const { root, app } = await renderSlider(window, comfortUnscored);
    const range = root.querySelector('input[type=range]');

    // when — 사람이 손잡이를 4 로 옮긴 것과 같다
    range.value = '4';
    range.dispatchEvent(new window.Event('input', { bubbles: true }));
    await new Promise(r => window.setTimeout(r, 50));

    // then
    assert.equal(app.scoreForm.COMFORT, '4');
    assert.equal(app.changedScores().COMFORT, 4);
});

/**
 * 손잡이는 어딘가에 놓이지만, 그 자리를 <b>점수로 보여 주면</b> 조회가 실패한 항목이
 * "0점을 받았다" 로 읽힌다 (설계 I220).
 */
test('미산출 항목은 손잡이가 어디에 있든 숫자를 – 로 보여 준다', async () => {
    // given
    const { window } = bootWindow();

    // when
    const { root } = await renderSlider(window, missingItem);

    // then
    assert.equal(root.querySelector('.gauge-value').textContent.trim(), '–');
});

test('미산출 항목은 읽어 줄 때도 0점이라고 하지 않는다', async () => {
    // given
    const { window } = bootWindow();

    // when
    const { root } = await renderSlider(window, missingItem);

    // then
    const range = root.querySelector('input[type=range]');
    assert.equal(range.getAttribute('aria-valuetext'), '아직 산출되지 않음');
});

test('쾌적함은 저장될 값을 숫자로도 보여 준다', async () => {
    // given
    const { window } = bootWindow();

    // when
    const { root } = await renderSlider(window, comfortUnscored);

    // then
    assert.equal(root.querySelector('.gauge-value').textContent.trim(), '1');
    assert.equal(root.querySelector('input[type=range]').getAttribute('aria-valuetext'), '1점');
});
