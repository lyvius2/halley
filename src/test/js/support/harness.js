'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');

const REPO_ROOT = path.resolve(__dirname, '..', '..', '..', '..');
const ALPINE_PATH = path.join(REPO_ROOT, 'src/main/resources/static/js/vendor/alpine.min.js');
const APP_JS_PATH = path.join(REPO_ROOT, 'src/main/resources/static/js/app.js');
const TEMPLATE_PATH = path.join(REPO_ROOT, 'src/main/resources/templates/index.mustache');

/** 실제 서비스에 나가는 파일을 그대로 실행한다 (설계 I276) — 사본이 아니다. */
function bootWindow() {
    const dom = new JSDOM('<!doctype html><html><body></body></html>', {
        url: 'http://localhost/',
        runScripts: 'dangerously',
        pretendToBeVisual: true,
    });
    const { window } = dom;

    window.eval(fs.readFileSync(ALPINE_PATH, 'utf8'));
    window.eval(fs.readFileSync(APP_JS_PATH, 'utf8'));

    return { dom, window };
}

/** `halley()` 컴포넌트를 만든다 — DOM 바인딩 없이 데이터·메서드만 부른다. */
function mountHalley(window) {
    if (typeof window.halley !== 'function') {
        throw new Error('app.js 에서 halley() 를 찾지 못했다 — 함수 이름이 바뀌었는가');
    }
    return window.halley();
}

/**
 * 템플릿에서 조각 하나를 <b>그대로</b> 떼어 온다 (설계 I276 · I287).
 *
 * <p>손으로 옮겨 적으면 템플릿이 바뀌어도 시험은 옛 사본을 계속 통과시킵니다 —
 * 바인딩(`:value`·`@input`)이 다시 어긋나도 못 잡습니다.
 *
 * @param startsWith 조각이 시작하는 줄에 들어 있는 글
 * @param endsWith   조각이 끝나는 줄에 들어 있는 글
 */
function templateFragment(startsWith, endsWith) {
    const lines = fs.readFileSync(TEMPLATE_PATH, 'utf8').split('\n');
    const from = lines.findIndex(l => l.includes(startsWith));
    if (from === -1) {
        throw new Error(`템플릿에서 "${startsWith}" 를 못 찾았다`);
    }
    const to = lines.findIndex((l, i) => i > from && l.includes(endsWith));
    if (to === -1) {
        throw new Error(`템플릿에서 "${endsWith}" 를 못 찾았다`);
    }
    return lines.slice(from, to + 1).join('\n');
}

/** 조각을 Alpine 으로 실제로 그린다. 컴포넌트는 `halley()` 그대로다. */
async function renderFragment(window, html, setup) {
    // `init()` 이 세션·설정을 부른다. 조각을 보려는 것이지 네트워크를 보려는 것이
    // 아니므로 빈 답을 준다 — 여기서 실패해도 그리는 데는 지장이 없다
    if (typeof window.fetch !== 'function') {
        window.fetch = async () => ({
            ok: false, status: 401,
            json: async () => ({}),
            text: async () => '',
        });
    }
    if (!window.kakao) {
        window.kakao = require('./kakaoStub.js').kakaoStub().kakao;
    }
    const root = window.document.createElement('div');
    root.setAttribute('x-data', 'halley()');
    root.innerHTML = html;
    window.document.body.appendChild(root);
    window.Alpine.start();
    await new Promise(r => window.setTimeout(r, 50));
    const app = window.Alpine.$data(root);
    if (setup) {
        setup(app);
        await new Promise(r => window.setTimeout(r, 50));
    }
    return { root, app };
}

module.exports = { bootWindow, mountHalley, templateFragment, renderFragment };
