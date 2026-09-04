'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

/** 내가 쾌적함을 매겼는가 (설계 I264) — 이름만 있고 규칙이 틀려도 여기서 잡는다. */
test('내 점수(myScore)가 있어야 참이다 — 구성원 점수만으론 안 된다', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    const mine = { scores: [{ code: 'COMFORT', myScore: 5, effectiveScore: 4 }] };
    const others = { scores: [{ code: 'COMFORT', myScore: null, effectiveScore: 4 }] };

    assert.equal(app.scoredComfort(mine), true);
    assert.equal(app.scoredComfort(others), false);
});

test('COMFORT 가 아닌 항목 점수는 안 본다', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    const other = { scores: [{ code: 'PRICE', myScore: 5 }] };

    assert.equal(app.scoredComfort(other), false);
});

test('점수가 아예 없어도 죽지 않는다', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    assert.equal(app.scoredComfort(null), false);
    assert.equal(app.scoredComfort({ scores: [] }), false);
    assert.equal(app.scoredComfort({}), false);
});

test('visitedTitle 이 scoredComfort 와 같은 규칙을 쓴다 — 두 벌이면 언젠가 어긋난다', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    const mine = { scores: [{ code: 'COMFORT', myScore: 5 }] };
    const others = { scores: [{ code: 'COMFORT', myScore: null, effectiveScore: 4 }] };

    assert.match(app.visitedTitle(mine), /내가 공간의 쾌적함을 매겼습니다/);
    assert.match(app.visitedTitle(others), /구성원 중 누군가/);
});
