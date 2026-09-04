'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

/**
 * 층은 숫자만이 아니다 (설계 I286).
 *
 * <p>네이버가 저층을 감추면 `저/15층` 처럼 밴드로 온다. 채점기도 DB도 밴드를 아는데
 * 화면이 숫자만 받아 <b>그 매물의 층이 통째로 사라졌다.</b>
 */

const typed = (app, value) => app.floorInput({ target: { value } });

test('층 칸에는 숫자와 저·중·고만 들어간다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(typed(app, '15'), '15');
    assert.equal(typed(app, '저'), '저');
    assert.equal(typed(app, '중'), '중');
    assert.equal(typed(app, '고'), '고');
});

test('숫자도 밴드도 아닌 글자는 걸러낸다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(typed(app, 'a3!'), '3');
    assert.equal(typed(app, '옥'), '');
});

test('밴드와 숫자를 섞으면 밴드만 남는다 — `저3` 은 뜻이 없다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(typed(app, '저3'), '저');
});

test('입력칸의 값도 함께 고쳐 준다 — 모델만 고치면 걸러지기 전 글자가 잠깐 남는다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    const el = { value: '저3' };

    // when
    app.floorInput({ target: el });

    // then
    assert.equal(el.value, '저');
});

test('밴드 층은 `저/15층` 으로 보여 준다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(app.floorLabel({ floorBand: 'LOW', floorTotal: 15 }), '저/15층');
    assert.equal(app.floorLabel({ floorBand: 'MID', floorTotal: 15 }), '중/15층');
    assert.equal(app.floorLabel({ floorNo: 3, floorTotal: 12 }), '3/12층');
});

test('총 층수가 없으면 층만 보여 준다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(app.floorLabel({ floorNo: 3 }), '3층');
    assert.equal(app.floorLabel({ floorBand: 'HIGH' }), '고층');
});

test('층이 없으면 빈 값이다 — 카드에서 통째로 감춘다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(app.floorLabel({}), '');
    assert.equal(app.floorLabel(null), '');
});

/**
 * 수정 화면이 밴드를 되살리지 못하면, 다른 항목만 고쳐 저장해도
 * <b>있던 층이 지워진다</b> — 화면에 빈 칸으로 보이기 때문이다.
 */
test('밴드로 저장된 매물을 수정하면 층 칸에 글자가 되살아난다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    const property = { id: 1, name: '저층매물', floorBand: 'LOW', floorTotal: 15 };

    // when
    app.openEditProperty({ property });

    // then
    assert.equal(app.propertyForm.floorRaw, '저');
});

test('숫자로 저장된 매물도 그대로 되살아난다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when
    app.openEditProperty({ property: { id: 1, name: '숫자층', floorNo: 7, floorTotal: 12 } });

    // then
    assert.equal(app.propertyForm.floorRaw, 7);
});
