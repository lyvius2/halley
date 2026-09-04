'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

/** 이동시간을 못 받으면 숫자(999) 대신 그렇다고 말한다 (설계 I270). */
test('구간 시간을 못 받으면 "이동시간 미확인"', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    assert.equal(app.legMinutesLabel({ minutes: null }), '이동시간 미확인');
    assert.equal(app.legMinutesLabel(null), '이동시간 미확인');
});

test('받았으면 분으로 보여 준다', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    assert.equal(app.legMinutesLabel({ minutes: 12 }), '12분');
    assert.equal(app.legMinutesLabel({ minutes: 0 }), '0분', '0분은 못 받은 것과 다르다 — != null 로 갈라야 한다');
});

test('못 받은 구간이 있으면 합계가 못 미덥다고 말한다', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    app.itinResult = { unknownLegs: 2 };
    assert.match(app.itinTotalNote(), /2개는 이동시간을 받지 못했습니다/);

    app.itinResult = { unknownLegs: 0 };
    assert.equal(app.itinTotalNote(), '', '멀쩡할 때 경고가 뜨면 안 된다');

    app.itinResult = null;
    assert.equal(app.itinTotalNote(), '', '결과가 없을 때도 죽으면 안 된다');
});
