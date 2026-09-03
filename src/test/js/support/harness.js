'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');

const REPO_ROOT = path.resolve(__dirname, '..', '..', '..', '..');
const ALPINE_PATH = path.join(REPO_ROOT, 'src/main/resources/static/js/vendor/alpine.min.js');
const APP_JS_PATH = path.join(REPO_ROOT, 'src/main/resources/static/js/app.js');

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

module.exports = { bootWindow, mountHalley };
