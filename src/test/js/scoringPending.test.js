'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

/**
 * 「분석 중」이 끝나지 않던 것 (설계 I284 · I285).
 *
 * <p>보정이 실패하면 점수가 영영 안 채워지는데 카드는 계속 돌았고, 판 번호만 믿는
 * 갱신은 <b>번호는 새것인데 점수가 빈</b> 판을 한 번 붙들면 빠져나오지 못했다.
 */

const ago = ms => new Date(Date.now() - ms).toISOString();
const scored = (createdAt, scores) => ({
    property: { id: 1, createdAt },
    scoreVersion: 2,
    scores,
});

test('등록 직후 점수가 비어 있으면 「분석 중」이다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(app.scoring(scored(ago(5 * 1000), [])), true);
});

test('점수가 채워지면 더는 분석 중이 아니다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(app.scoring(scored(ago(5 * 1000), [{ code: 'PRICE' }])), false);
});

/** 끝나지 않는 진행 표시는 없느니만 못하다 — 지킬 수 없는 약속이다 (설계 I284). */
test('유예가 지나도 비어 있으면 표시를 거둔다 — 못 낸 것이지 기다리는 것이 아니다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(app.scoring(scored(ago(10 * 60 * 1000), [])), false);
});

test('등록 시각을 모르면 예전처럼 기다린다 — 모른다고 감추지 않는다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);

    // when / then
    assert.equal(app.scoring({ property: {}, scores: [] }), true);
    assert.equal(app.scoring(null), false);
});

/**
 * 판 번호가 같아도 「분석 중」인 카드가 있으면 다시 받는다 (설계 I285).
 * 번호만 믿으면 빈 판을 붙든 순간 회복 경로가 없다.
 */
test('번호가 같아도 분석 중인 카드가 있으면 목록을 다시 받는다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    let reloads = 0;
    app.session = { authenticated: true };
    app.propertyTotal = 1;
    app.properties = [scored(ago(5 * 1000), [])];
    app.listFilterQuery = () => '';
    app.request = async () => ({ ok: true, body: [{ propertyId: 1, scoreVersion: 2 }] });
    app.loadProperties = async () => { reloads += 1; };

    // when
    await app.checkScoreVersions();

    // then
    assert.equal(reloads, 1);
});

test('할 일이 없으면 다시 받지 않는다 — 3초마다 목록을 긁으면 안 된다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    let reloads = 0;
    app.session = { authenticated: true };
    app.propertyTotal = 1;
    app.properties = [scored(ago(5 * 1000), [{ code: 'PRICE' }])];
    app.listFilterQuery = () => '';
    app.request = async () => ({ ok: true, body: [{ propertyId: 1, scoreVersion: 2 }] });
    app.loadProperties = async () => { reloads += 1; };

    // when
    await app.checkScoreVersions();

    // then
    assert.equal(reloads, 0);
});

test('유예가 지난 미채점 매물은 계속 긁지 않는다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    let reloads = 0;
    app.session = { authenticated: true };
    app.propertyTotal = 1;
    app.properties = [scored(ago(60 * 60 * 1000), [])];
    app.listFilterQuery = () => '';
    app.request = async () => ({ ok: true, body: [{ propertyId: 1, scoreVersion: 2 }] });
    app.loadProperties = async () => { reloads += 1; };

    // when
    await app.checkScoreVersions();

    // then
    assert.equal(reloads, 0);
});
