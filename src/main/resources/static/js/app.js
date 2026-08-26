function emptyPropertyForm() {
    return {
        id: null,
        name: '',
        dealType: 'SALE',
        priceDeposit: '',
        priceMonthly: '',
        maintenanceFee: '',
        addressRoad: '',
        addressJibun: '',
        lat: '',
        lng: '',
        areaSupplyM2: '',
        areaExclusiveM2: '',
        floorNo: '',
        floorTotal: '',
        direction: '',
        approvalYear: '',
        buildingCount: '',
        totalHouseholds: '',
        moveInType: '',
        moveInDate: ''
    };
}

function halley() {
    return {
        session: { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false },
        view: 'list',
        dealTypeFilter: 'ALL',
        properties: [],
        users: [],
        showLogin: false,
        showPassword: false,
        showPropertyForm: false,
        propertyForm: emptyPropertyForm(),
        showScoreModal: false,
        scoreProperty: null,
        scoreForm: {},
        weights: [],
        map: null,
        markers: {},
        activePropertyId: null,
        showRoadview: false,
        roadviewProperty: null,
        roadviewState: 'loading',
        roadview: null,
        loginForm: { email: '', password: '' },
        passwordForm: { currentPassword: '', newPassword: '' },
        error: null,
        loading: false,

        async init() {
            await this.checkSession();
        },

        async request(url, options) {
            const res = await fetch(url, options);
            let body = null;
            try {
                body = await res.json();
            } catch (e) {
                body = null;
            }
            return { ok: res.ok, status: res.status, body };
        },

        async checkSession() {
            const { ok, body } = await this.request('/api/auth/session');
            if (ok) {
                this.session = Object.assign({ authenticated: true }, body);
                this.showLogin = false;
                this.showPassword = body.mustChangePassword === true;
                if (this.session.role === 'ADMIN' && !this.showPassword) {
                    await this.loadUsers();
                }
                if (!this.showPassword) {
                    await this.loadProperties();
                }
            } else {
                this.session = { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false };
                this.showLogin = true;
            }
        },

        setView(view) {
            this.view = view;
            if (view === 'users') {
                this.loadUsers();
            }
            if (view === 'weights') {
                this.loadWeights();
            }
        },

        async loadUsers() {
            const { ok, body } = await this.request('/api/users');
            if (ok) {
                this.users = body || [];
            }
        },

        async login() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(this.loginForm)
                });
                if (ok) {
                    this.session = Object.assign({ authenticated: true }, body);
                    this.loginForm = { email: '', password: '' };
                    this.showLogin = false;
                    this.showPassword = body.mustChangePassword === true;
                    if (this.session.role === 'ADMIN' && !this.showPassword) {
                        await this.loadUsers();
                    }
                    if (!this.showPassword) {
                        await this.loadProperties();
                    }
                } else {
                    this.error = (body && body.message) || '로그인에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async changePassword() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/auth/password', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(this.passwordForm)
                });
                if (ok) {
                    this.session.mustChangePassword = false;
                    this.passwordForm = { currentPassword: '', newPassword: '' };
                    this.showPassword = false;
                    this.error = null;
                    if (this.session.role === 'ADMIN') {
                        await this.loadUsers();
                    }
                    await this.loadProperties();
                } else {
                    this.error = (body && body.message) || '비밀번호 변경에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async logout() {
            try {
                await fetch('/api/auth/logout', { method: 'POST' });
            } catch (e) {
                // ignore
            }
            this.session = { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false };
            this.users = [];
            this.properties = [];
            this.weights = [];
            this.view = 'list';
            this.dealTypeFilter = 'ALL';
            this.showLogin = true;
            this.showPassword = false;
        },

        async loadProperties() {
            const url = '/api/properties'
                + (this.dealTypeFilter !== 'ALL' ? '?dealType=' + this.dealTypeFilter : '');
            const { ok, body } = await this.request(url);
            if (ok) {
                this.properties = body || [];
                this.renderMap();
            }
        },

        async setDealTypeFilter(filter) {
            this.dealTypeFilter = filter;
            await this.loadProperties();
        },

        openAddProperty() {
            this.propertyForm = emptyPropertyForm();
            this.error = null;
            this.showPropertyForm = true;
        },

        openEditProperty(item) {
            const p = item.property;
            this.propertyForm = {
                id: p.id,
                name: p.name || '',
                dealType: p.dealType || 'SALE',
                priceDeposit: p.priceDeposit ?? '',
                priceMonthly: p.priceMonthly ?? '',
                maintenanceFee: p.maintenanceFee ?? '',
                addressRoad: p.addressRoad || '',
                addressJibun: p.addressJibun || '',
                lat: p.lat ?? '',
                lng: p.lng ?? '',
                areaSupplyM2: p.areaSupplyM2 ?? '',
                areaExclusiveM2: p.areaExclusiveM2 ?? '',
                floorNo: p.floorNo ?? '',
                floorTotal: p.floorTotal ?? '',
                direction: p.direction || '',
                approvalYear: p.approvalYear ?? '',
                buildingCount: p.buildingCount ?? '',
                totalHouseholds: p.totalHouseholds ?? '',
                moveInType: p.moveInType || '',
                moveInDate: p.moveInDate || ''
            };
            this.error = null;
            this.showPropertyForm = true;
        },

        closePropertyForm() {
            this.showPropertyForm = false;
            this.error = null;
        },

        async saveProperty() {
            this.loading = true;
            this.error = null;
            const body = {
                name: this.propertyForm.name,
                dealType: this.propertyForm.dealType,
                priceDeposit: toNum(this.propertyForm.priceDeposit),
                priceMonthly: toNum(this.propertyForm.priceMonthly),
                maintenanceFee: toNum(this.propertyForm.maintenanceFee),
                addressRoad: this.propertyForm.addressRoad || null,
                addressJibun: this.propertyForm.addressJibun || null,
                lat: toNum(this.propertyForm.lat),
                lng: toNum(this.propertyForm.lng),
                areaSupplyM2: toNum(this.propertyForm.areaSupplyM2),
                areaExclusiveM2: toNum(this.propertyForm.areaExclusiveM2),
                floorNo: toNum(this.propertyForm.floorNo),
                floorTotal: toNum(this.propertyForm.floorTotal),
                direction: this.propertyForm.direction || null,
                approvalYear: toNum(this.propertyForm.approvalYear),
                buildingCount: toNum(this.propertyForm.buildingCount),
                totalHouseholds: toNum(this.propertyForm.totalHouseholds),
                moveInType: this.propertyForm.moveInType || null,
                moveInDate: this.propertyForm.moveInDate || null
            };
            try {
                const id = this.propertyForm.id;
                const url = id ? `/api/properties/${id}` : '/api/properties';
                const method = id ? 'PUT' : 'POST';
                const { ok, body: resBody } = await this.request(url, {
                    method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });
                if (ok) {
                    this.showPropertyForm = false;
                    await this.loadProperties();
                } else {
                    this.error = (resBody && resBody.message) || '저장에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async removeProperty(item) {
            const p = item.property;
            if (!confirm(`'${p.name}' 매물을 삭제할까요?`)) {
                return;
            }
            const { ok } = await this.request(`/api/properties/${p.id}`, { method: 'DELETE' });
            if (ok) {
                await this.loadProperties();
            }
        },

        openScoreModal(item) {
            this.scoreProperty = item;
            const form = {};
            (item.scores || []).forEach(s => {
                form[s.code] = s.manualScore != null ? String(s.manualScore) : '';
            });
            this.scoreForm = form;
            this.error = null;
            this.showScoreModal = true;
        },

        closeScoreModal() {
            this.showScoreModal = false;
            this.scoreProperty = null;
            this.scoreForm = {};
            this.error = null;
        },

        async saveScore() {
            this.loading = true;
            this.error = null;
            const scores = {};
            for (const code in this.scoreForm) {
                const value = toNum(this.scoreForm[code]);
                if (value != null) {
                    scores[code] = value;
                }
            }
            try {
                const { ok, body } = await this.request(
                    `/api/properties/${this.scoreProperty.property.id}/scores`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ scores })
                    });
                if (ok) {
                    this.showScoreModal = false;
                    await this.loadProperties();
                } else {
                    this.error = (body && body.message) || '채점 저장에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async loadWeights() {
            const { ok, body } = await this.request('/api/criteria/weights');
            if (ok) {
                this.weights = body || [];
            }
        },

        moveWeight(index, dir) {
            const target = index + dir;
            if (target < 0 || target >= this.weights.length) {
                return;
            }
            const arr = this.weights.slice();
            const tmp = arr[index];
            arr[index] = arr[target];
            arr[target] = tmp;
            this.weights = arr;
        },

        async saveWeights() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/criteria/weights', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ order: this.weights.map(w => w.criterionCode) })
                });
                if (ok) {
                    this.weights = body || [];
                    await this.loadProperties();
                } else {
                    this.error = (body && body.message) || '가중치 저장에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        renderMap() {
            if (typeof kakao === 'undefined' || !kakao.maps) {
                return;
            }
            kakao.maps.load(() => {
                this.initMapIfNeeded();
                this.renderMarkers();
            });
        },

        initMapIfNeeded() {
            if (this.map) {
                return;
            }
            const el = document.getElementById('map');
            if (!el) {
                return;
            }
            this.map = new kakao.maps.Map(el, {
                center: new kakao.maps.LatLng(37.5665, 126.9780),
                level: 8
            });
        },

        renderMarkers() {
            if (!this.map) {
                return;
            }
            Object.values(this.markers).forEach(m => m.setMap(null));
            this.markers = {};
            const coords = this.properties.filter(r => r.property.lat && r.property.lng);
            coords.forEach(r => {
                const p = r.property;
                const position = new kakao.maps.LatLng(p.lat, p.lng);
                const marker = new kakao.maps.Marker({ position, map: this.map });
                kakao.maps.event.addListener(marker, 'click', () => this.selectMarker(p.id));
                this.markers[p.id] = marker;
            });
            if (coords.length > 0) {
                const bounds = new kakao.maps.LatLngBounds();
                coords.forEach(r => {
                    const p = r.property;
                    bounds.extend(new kakao.maps.LatLng(p.lat, p.lng));
                });
                this.map.setBounds(bounds);
            }
        },

        focusProperty(item) {
            const p = item.property;
            this.activePropertyId = p.id;
            if (!this.map || !p.lat || !p.lng) {
                return;
            }
            const position = new kakao.maps.LatLng(p.lat, p.lng);
            this.map.panTo(position);
            this.map.setLevel(4);
        },

        selectMarker(id) {
            this.activePropertyId = id;
            const el = document.getElementById('prop-' + id);
            if (el) {
                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
            const item = this.properties.find(x => x.property.id === id);
            if (item) {
                this.openRoadview(item);
            }
        },

        openRoadview(item) {
            this.roadviewProperty = item.property;
            this.showRoadview = true;
            this.roadviewState = 'loading';
            setTimeout(() => this.loadRoadview(item.property), 0);
        },

        loadRoadview(p) {
            const container = document.getElementById('roadview');
            if (!container || !p.lat || !p.lng) {
                this.roadviewState = 'missing';
                return;
            }
            const position = new kakao.maps.LatLng(p.lat, p.lng);
            const client = new kakao.maps.RoadviewClient();
            client.getNearestPanoId(position, 50, (panoId) => {
                if (panoId === null) {
                    this.roadviewState = 'missing';
                    return;
                }
                this.roadview = new kakao.maps.Roadview(container, { panoId, position });
                this.roadviewState = 'ready';
            });
        },

        closeRoadview() {
            this.showRoadview = false;
            this.roadviewProperty = null;
            this.roadviewState = 'loading';
            this.roadview = null;
        },

        dealLabel(type) {
            return { SALE: '매매', JEONSE: '전세', MONTHLY: '월세' }[type] || type;
        },

        dealBadge(type) {
            return { SALE: 'b-sale', JEONSE: 'b-jeonse', MONTHLY: 'b-monthly' }[type] || '';
        },

        scoreSourceLabel(source) {
            return { AUTO: '자동', MANUAL: '수동', FALLBACK: '폴백' }[source] || '';
        },

        fmtScore(n) {
            if (n == null) {
                return '-';
            }
            return Number(n).toFixed(0);
        },

        fmtWon(won) {
            if (won == null || won === 0) {
                return '0원';
            }
            const n = Number(won);
            const eok = Math.floor(n / 100000000);
            const man = Math.floor((n % 100000000) / 10000);
            let s = '';
            if (eok) s += eok.toLocaleString('ko-KR') + '억 ';
            if (man) s += man.toLocaleString('ko-KR') + '만';
            return s.trim() + '원';
        }
    };
}

function toNum(v) {
    if (v === null || v === undefined || v === '') {
        return null;
    }
    const n = Number(v);
    return Number.isNaN(n) ? null : n;
}
