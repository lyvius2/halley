'use strict';

/**
 * 카카오맵 JS SDK 를 부르는 모양만 흉내 낸다 (설계 I276) — 실물은 브라우저에서만 뜬다.
 *
 * @param {object} [options]
 * @param {boolean} [options.deferLoad] `kakao.maps.load` 콜백을 즉시 부르지 않고
 *   {@link returned.flushLoad} 를 불러야 실행되게 한다 — SDK 로딩이 비동기라는
 *   사실 자체가 버그의 원인이었던 시험(예: I275)에 쓴다
 */
function kakaoStub({ deferLoad = false } = {}) {
    const pending = [];
    const maps = {
        load: (cb) => {
            if (deferLoad) {
                pending.push(cb);
            } else {
                cb();
            }
        },
        LatLng: function (lat, lng) {
            this.lat = lat;
            this.lng = lng;
        },
        LatLngBounds: function () {
            this.points = [];
            this.extend = (p) => this.points.push(p);
        },
        CustomOverlay: function (opts) {
            this.opts = opts;
            this.setMap = () => {};
            this.setZIndex = (z) => { this.zIndex = z; };
            this.getContent = () => opts.content;
        },
        Map: function (el, opts) {
            this.element = el;
            this.center = opts.center;
            this.level = opts.level;
            this.panTo = (pos) => { this.pannedTo = pos; };
            this.setLevel = (l) => { this.level = l; };
            this.setBounds = (bounds) => { this.boundsFit = bounds; };
            this.relayout = () => {};
        },
    };
    return {
        kakao: { maps },
        /** 미뤄 둔 `load` 콜백을 지금 흘려보낸다 — SDK 가 뒤늦게 준비된 것을 흉내낸다. */
        flushLoad: () => {
            while (pending.length > 0) {
                pending.shift()();
            }
        },
    };
}

module.exports = { kakaoStub };
