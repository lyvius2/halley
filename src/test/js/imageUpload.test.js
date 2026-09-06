'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

test('사진 업로드가 실패하면 서버가 알려 준 원인을 파일명과 함께 표시한다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    app.photoProperty = { property: { id: 7 } };
    app.loadPhotoImages = async () => {};
    app.refreshDetailImages = async () => {};
    window.fetch = async () => ({
        ok: false,
        status: 503,
        json: async () => ({ message: 'HEIC 이미지 처리기가 준비되지 않았습니다' }),
    });
    const event = {
        target: {
            files: [new window.File(['heic'], 'IMG_0001.HEIC', { type: 'image/heic' })],
            value: 'selected',
        },
    };

    // when
    await app.uploadImages(event, 'PHOTO');

    // then
    assert.equal(app.error, 'IMG_0001.HEIC: HEIC 이미지 처리기가 준비되지 않았습니다');
    assert.equal(event.target.value, '');
    assert.equal(app.loading, false);
});

test('프록시가 HTML 413을 반환해도 사진 용량 문제라고 표시한다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    const response = {
        status: 413,
        json: async () => { throw new SyntaxError('HTML response'); },
    };

    // when
    const message = await app.imageUploadFailureMessage(response, 'iphone.jpeg');

    // then
    assert.equal(message, 'iphone.jpeg: 사진 용량이 업로드 한도를 초과했습니다');
});
