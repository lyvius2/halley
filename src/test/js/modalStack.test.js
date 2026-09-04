'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

/** 배경을 누르면 목록에 먼저 있는 모달 하나만 닫는다 (설계 I198 · I274) — 나중에 연 순서가 아니다. */
test('둘이 같이 열려 있으면 목록에서 앞선 것 하나만 닫는다', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    // showRoadview 가 showPasteModal 보다 목록 앞자리다
    app.showRoadview = true;
    app.showPasteModal = true;

    app.closeTopModal();

    assert.equal(app.showRoadview, false, '목록에서 앞선 모달이 닫혀야 한다');
    assert.equal(app.showPasteModal, true, '뒤쪽 모달은 이번엔 안 닫혀야 한다');
});

/** [I274] 「최적 경로 산출 불가」 모달이 목록에 등록되어 있는가. */
test('경로 산출 불가 모달도 배경 클릭으로 닫힌다', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    app.showItinUnavailable = true;
    app.closeTopModal();

    assert.equal(app.showItinUnavailable, false);
});

test('열린 모달이 없으면 아무 일도 안 한다', () => {
    const { window } = bootWindow();
    const app = mountHalley(window);

    assert.doesNotThrow(() => app.closeTopModal());
});
