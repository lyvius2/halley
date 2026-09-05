'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { bootWindow, mountHalley } = require('./support/harness.js');

const TEMPLATE = path.resolve(
    __dirname, '../../main/resources/templates/index.mustache');
const STYLESHEET = path.resolve(
    __dirname, '../../main/resources/static/css/app.css');

function mobileApp() {
    const { window } = bootWindow();
    window.matchMedia = () => ({ matches: true });
    return { window, app: mountHalley(window) };
}

function dragEvent(panel, pointerId, clientY) {
    return {
        button: 0,
        pointerId,
        clientY,
        currentTarget: {
            closest: () => panel,
            setPointerCapture: () => {},
        },
        preventDefault: () => {},
    };
}

test('접힌 시트를 위로 밀면 헤더 아래 전체로 펼쳐진다', () => {
    // given
    const { app } = mobileApp();
    const panel = { clientHeight: 800 };

    // when
    app.startMobileSheetDrag(dragEvent(panel, 1, 700));
    const collapsedOffset = app.mobileSheetOffsetPx;
    app.moveMobileSheetDrag(dragEvent(panel, 1, 600));
    app.endMobileSheetDrag(dragEvent(panel, 1, 600));

    // then
    assert.equal(collapsedOffset, 600, '800px 본문에서 아래 200px만 보여야 한다');
    assert.equal(app.mobileSheetExpanded, true);
    assert.equal(app.mobileSheetDragging, false);
    assert.equal(app.mobileSheetOffsetPx, null);
});

test('펼친 시트를 아래로 밀면 25% 높이로 접힌다', () => {
    // given
    const { app } = mobileApp();
    const panel = { clientHeight: 800 };
    app.mobileSheetExpanded = true;
    app.startMobileSheetDrag(dragEvent(panel, 2, 100));

    // when
    app.moveMobileSheetDrag(dragEvent(panel, 2, 170));
    app.endMobileSheetDrag(dragEvent(panel, 2, 170));

    // then
    assert.equal(app.mobileSheetExpanded, false);
});

test('짧게 건드린 드래그는 상태를 바꾸지 않고 이어지는 click도 무시한다', () => {
    // given
    const { app } = mobileApp();
    const panel = { clientHeight: 800 };
    app.startMobileSheetDrag(dragEvent(panel, 3, 700));

    // when
    app.moveMobileSheetDrag(dragEvent(panel, 3, 680));
    app.endMobileSheetDrag(dragEvent(panel, 3, 680));
    app.toggleMobileSheet();

    // then
    assert.equal(app.mobileSheetExpanded, false);
    app.toggleMobileSheet();
    assert.equal(app.mobileSheetExpanded, true, '그 다음의 실제 탭은 동작해야 한다');
});

test('드래그 중 시트 위치는 펼침과 25% 높이 사이를 벗어나지 않는다', () => {
    // given
    const { app } = mobileApp();
    const panel = { clientHeight: 800 };
    app.startMobileSheetDrag(dragEvent(panel, 4, 700));

    // when
    app.moveMobileSheetDrag(dragEvent(panel, 4, -100));

    // then
    assert.equal(app.mobileSheetOffsetPx, 0);
    assert.equal(app.mobileSheetStyle(), 'transform: translateY(0px)');

    // when
    app.moveMobileSheetDrag(dragEvent(panel, 4, 1600));

    // then
    assert.equal(app.mobileSheetOffsetPx, 600);
});

test('지도 마커를 선택하면 모바일 목록 시트가 펼쳐진다', () => {
    // given
    const { app } = mobileApp();
    app.properties = [{ property: { id: 7 } }];
    app.openRoadview = () => {};

    // when
    app.selectMarker(7);

    // then
    assert.equal(app.mobileSheetExpanded, true);
});

test('매물명을 누르면 지도 중심을 옮기고 목록 시트를 25% 상태로 내린다', () => {
    // given
    const { app } = mobileApp();
    const item = { property: { id: 9, lat: 37.5, lng: 127 } };
    let focused = null;
    app.mobileSheetExpanded = true;
    app.focusProperty = target => { focused = target; };

    // when
    app.revealPropertyOnMap(item);

    // then
    assert.equal(focused, item);
    assert.equal(app.mobileSheetExpanded, false);
});

test('데스크톱에서는 손잡이 동작이 레이아웃 상태를 바꾸지 않는다', () => {
    // given
    const { window } = bootWindow();
    window.matchMedia = () => ({ matches: false });
    const app = mountHalley(window);

    // when
    app.startMobileSheetDrag(dragEvent({ clientHeight: 800 }, 5, 700));
    app.toggleMobileSheet();

    // then
    assert.equal(app.mobileSheetDragging, false);
    assert.equal(app.mobileSheetExpanded, false);
});

test('실제 템플릿 손잡이가 pointer·키보드 동작을 app.js에 연결한다', () => {
    // given
    const template = fs.readFileSync(TEMPLATE, 'utf8');

    // when
    const bindings = [
        /@pointerdown="startMobileSheetDrag\(\$event\)"/,
        /@pointermove\.window="moveMobileSheetDrag\(\$event\)"/,
        /@pointerup\.window="endMobileSheetDrag\(\$event\)"/,
        /@keydown\.enter\.prevent="toggleMobileSheet\(\)"/,
        /:aria-expanded="mobileSheetExpanded"/,
        /@click\.stop="revealPropertyOnMap\(r\)"/,
    ].map(pattern => pattern.test(template));

    // then
    assert.deepEqual(bindings, [true, true, true, true, true, true]);
});

test('실제 모바일 CSS가 고정 헤더·25% 시트·가로 넘침 방지를 함께 둔다', () => {
    // given
    const stylesheet = fs.readFileSync(STYLESHEET, 'utf8');

    // when
    const rules = [
        /--mobile-header-height:\s*49px/,
        /\.list-panel\s*\{[^}]*transform:\s*translateY\(75%\)/s,
        /\.list-panel-scroll\s*\{[^}]*overflow-x:\s*hidden/s,
        /\.property-list,\s*\.property-card\s*\{[^}]*min-width:\s*0/s,
    ].map(pattern => pattern.test(stylesheet));

    // then
    assert.deepEqual(rules, [true, true, true, true]);
});

test('매물 상세 모달은 고정 헤더보다 위에 놓인다', () => {
    // given
    const stylesheet = fs.readFileSync(STYLESHEET, 'utf8');

    // when
    const header = /\.topbar\s*\{[^}]*z-index:\s*(\d+)/s.exec(stylesheet);
    const detail = /\.modal\.modal-detail\s*\{[^}]*z-index:\s*(\d+)/s.exec(stylesheet);

    // then
    assert.ok(header, '헤더의 레이어 순서를 찾을 수 있어야 한다');
    assert.ok(detail, '매물 상세 모달의 레이어 순서를 찾을 수 있어야 한다');
    assert.ok(Number(detail[1]) > Number(header[1]), '매물 상세가 헤더를 덮어야 한다');
});

test('PC 카드의 기존 한 줄 배치를 복원해도 모바일 두 줄 규칙은 남는다', () => {
    // given
    const stylesheet = fs.readFileSync(STYLESHEET, 'utf8');

    // when
    const desktopGroupsAreTransparent =
        /@media \(min-width:\s*768px\)[\s\S]*?\.property-list \.property-card-title-row,[\s\S]*?\.property-list \.property-card-badges\s*\{\s*display:\s*contents/.test(stylesheet);
    const desktopPriceIsLast =
        /\.property-list \.property-card-title-row \.property-card-price\s*\{\s*order:\s*1/.test(stylesheet);
    const mobileGroupsKeepTheirWidth =
        /@media \(max-width:\s*767px\)[\s\S]*?\.property-card-title-row,\s*\.property-card-badges\s*\{[^}]*width:\s*100%/.test(stylesheet);

    // then
    assert.equal(desktopGroupsAreTransparent, true);
    assert.equal(desktopPriceIsLast, true);
    assert.equal(mobileGroupsKeepTheirWidth, true);
});
