'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

/**
 * 상태를 바꾸는 요청에 CSRF 토큰 헤더가 실리는가 (설계 I295).
 *
 * <p>request() 를 실제로 불러 fetch 에 무엇이 넘어가는지 본다 — 도우미만 보면
 * request() 가 도우미를 안 쓰게 바뀌어도 통과한다.
 */
function capture(window) {
    const calls = [];
    window.fetch = async (url, options) => {
        calls.push({ url, options: options || {} });
        return { status: 200, ok: true, json: async () => ({}) };
    };
    return calls;
}

test('POST 에는 쿠키의 토큰이 X-XSRF-TOKEN 헤더로 실린다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    window.document.cookie = 'XSRF-TOKEN=abc%3D123';
    const calls = capture(window);

    // when
    await app.request('/api/x', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });

    // then — 디코딩되어 실리고, 원래 헤더는 그대로다
    assert.equal(calls[0].options.headers['X-XSRF-TOKEN'], 'abc=123');
    assert.equal(calls[0].options.headers['Content-Type'], 'application/json');
});

test('GET 에는 붙이지 않는다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    window.document.cookie = 'XSRF-TOKEN=abc';
    const calls = capture(window);

    // when
    await app.request('/api/x');

    // then
    assert.equal(calls[0].options.headers, undefined);
});

test('쿠키가 없으면 헤더도 없다 — 빈 값을 보내지 않는다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    const calls = capture(window);

    // when
    await app.request('/api/x', { method: 'DELETE' });

    // then
    assert.equal(calls[0].options.headers['X-XSRF-TOKEN'], undefined);
});
