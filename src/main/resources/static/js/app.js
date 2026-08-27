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
        moveInDate: '',
        editVersion: null
    };
}

function emptyUserForm() {
    return {
        nickname: '',
        email: '',
        password: '',
        role: 'MEMBER',
        workplaceName: '',
        workplaceLat: '',
        workplaceLng: '',
        availableBudget: ''
    };
}

function halley() {
    return {
        session: { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false },
        view: 'list',
        mobileTab: 'map',
        dealTypeFilter: 'ALL',
        properties: [],
        visibleProperties: [],
        showSoldOut: false,
        users: [],
        soldOutRecent: [],
        showSoldOutAlert: false,
        soldOutAlertShown: false,
        showCheckLogs: false,
        checkLogs: [],
        checkLogProperty: null,
        showLoanModal: false,
        loanProperty: null,
        loanForm: { annualIncome: '', cash: '', firstHome: false },
        loanResult: null,
        showRefModal: false,
        refProperty: null,
        refForm: { legalDongCode: '', dealMonth: '' },
        refCard: null,
        itinProperties: [],
        itinMode: 'DRIVING',
        itinStart: { address: '', lat: '', lng: '' },
        itinWindowStart: '09:00',
        itinStay: 25,
        itinResult: null,
        itinPlan: null,
        _itinMarkers: {},
        _itinPolyline: null,
        sessionExpiresAt: 0,
        _sessionTimer: null,
        showSessionWarn: false,
        showUserForm: false,
        userForm: emptyUserForm(),
        editingUserId: null,
        tempPassword: null,
        confirmState: null,
        profile: null,
        workplaceQuery: '',
        workplaceResults: [],
        showChangePw: false,
        changePwForm: { currentPassword: '', newPassword: '' },
        showM2: false,
        detailItem: null,
        showPhotoModal: false,
        photoProperty: null,
        photoImages: [],
        photoType: 'PHOTO',
        photoFile: null,
        photoViewerIndex: -1,
        showAgentModal: false,
        agentProperty: null,
        agentLinks: [],
        agentQuery: '',
        agentResults: [],
        newAgentForm: { officeName: '', agentName: '', phone: '', mobile: '' },
        showLogin: false,
        showPassword: false,
        showPropertyForm: false,
        propertyForm: emptyPropertyForm(),
        propertyQuery: '',
        propertyAddrResults: [],
        showAddMenu: false,
        showPasteModal: false,
        pasteText: '',
        pasteParsing: false,
        pastePreview: null,
        pasteForm: {},
        pasteError: null,
        _pasteTimer: null,
        showDraftModal: false,
        draftForm: { sourceUrl: '', memo: '' },
        pasteDraftId: null,
        pasteDraftName: null,
        showScoreModal: false,
        scoreProperty: null,
        scoreForm: {},
        weights: [],
        settings: [],
        settingsForm: {},
        notifications: [],
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
            window.addEventListener('resize', () => {
                if (this.map) {
                    this.map.relayout();
                }
            });
            await this.checkSession();
        },

        setMobileTab(tab) {
            this.mobileTab = tab;
            if (tab === 'map') {
                this.renderMap();
            }
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
                this.sessionExpiresAt = body.expiresInSeconds != null
                    ? Date.now() + body.expiresInSeconds * 1000 : 0;
                this.startSessionTimer();
                this.showLogin = false;
                this.showPassword = body.mustChangePassword === true;
                if (this.session.role === 'ADMIN' && !this.showPassword) {
                    await this.loadUsers();
                }
                if (!this.showPassword) {
                    await this.loadProperties();
                    await this.checkSoldOutAlert();
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
            if (view === 'settings') {
                this.loadSettings();
                this.loadNotifications();
            }
            if (view === 'me') {
                this.loadProfile();
            }
            if (view === 'itinerary') {
                this.renderItinerary();
            }
        },

        async loadUsers() {
            const { ok, body } = await this.request('/api/users');
            if (ok) {
                this.itinPlan = body;
                this.renderItinerary();
            }
        },


        openAddUser() {
            this.editingUserId = null;
            this.userForm = emptyUserForm();
            this.error = null;
            this.showUserForm = true;
        },

        openEditUser(u) {
            this.editingUserId = u.id;
            this.userForm = {
                nickname: u.nickname,
                email: u.email,
                password: '',
                role: u.role,
                workplaceName: u.workplaceName || '',
                workplaceLat: u.workplaceLat ?? '',
                workplaceLng: u.workplaceLng ?? '',
                availableBudget: u.availableBudget ?? ''
            };
            this.error = null;
            this.showUserForm = true;
        },

        closeUserForm() {
            this.showUserForm = false;
            this.userForm = emptyUserForm();
            this.editingUserId = null;
            this.error = null;
        },

        async saveUser() {
            this.loading = true;
            this.error = null;
            const editing = this.editingUserId;
            const body = {
                nickname: this.userForm.nickname,
                email: this.userForm.email,
                workplaceName: this.userForm.workplaceName || null,
                workplaceLat: toNum(this.userForm.workplaceLat),
                workplaceLng: toNum(this.userForm.workplaceLng),
                availableBudget: toNum(this.userForm.availableBudget)
            };
            if (!editing) {
                body.password = this.userForm.password;
                body.role = this.userForm.role;
            }
            try {
                const { ok, body: resBody } = await this.request(
                    editing ? `/api/users/${editing}` : '/api/users', {
                        method: editing ? 'PUT' : 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(body)
                    });
                if (ok) {
                    this.closeUserForm();
                    await this.loadUsers();
                } else {
                    this.error = (resBody && resBody.message) || '저장에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        removeUser(u) {
            this.askConfirm('사용자 삭제', `'${u.nickname}' 사용자를 삭제할까요?`, async () => {
                await this.request(`/api/users/${u.id}`, { method: 'DELETE' });
                await this.loadUsers();
            });
        },

        resetUserPassword(u) {
            this.askConfirm('비밀번호 리셋', `'${u.nickname}'의 임시 비밀번호를 발급할까요?`, async () => {
                const { ok, body } = await this.request(`/api/users/${u.id}/reset-password`, { method: 'POST' });
                if (ok) {
                    this.tempPassword = body.temporaryPassword;
                }
                await this.loadUsers();
            });
        },

        askConfirm(title, message, action) {
            this.confirmState = { title, message, action };
        },

        confirmYes() {
            const state = this.confirmState;
            this.confirmState = null;
            if (state && state.action) {
                state.action();
            }
        },

        confirmNo() {
            this.confirmState = null;
        },

        async loadProfile() {
            const { ok, body } = await this.request('/api/users/me');
            if (ok) {
                this.profile = body;
                this.workplaceQuery = body.workplaceName || '';
            }
        },

        async searchWorkplace() {
            const query = this.workplaceQuery;
            if (!query || !query.trim()) {
                return;
            }
            const { ok, body } = await this.request('/api/geo/search?query=' + encodeURIComponent(query));
            this.workplaceResults = ok ? (body || []) : [];
        },

        selectWorkplace(r) {
            this.profile.workplaceName = r.addressName;
            this.profile.workplaceLat = r.lat;
            this.profile.workplaceLng = r.lng;
            this.workplaceQuery = r.addressName;
            this.workplaceResults = [];
        },

        async saveWorkplace() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/users/me/workplace', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        workplaceName: this.profile.workplaceName || null,
                        workplaceLat: toNum(this.profile.workplaceLat),
                        workplaceLng: toNum(this.profile.workplaceLng)
                    })
                });
                if (ok) {
                    this.profile = body;
                } else {
                    this.error = (body && body.message) || '직장 위치 저장에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        openChangePw() {
            this.changePwForm = { currentPassword: '', newPassword: '' };
            this.error = null;
            this.showChangePw = true;
        },

        closeChangePw() {
            this.showChangePw = false;
            this.changePwForm = { currentPassword: '', newPassword: '' };
            this.error = null;
        },

        async changeMyPassword() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/auth/password', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(this.changePwForm)
                });
                if (ok) {
                    this.closeChangePw();
                } else {
                    this.error = (body && body.message) || '비밀번호 변경에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        openDetail(item) {
            this.detailItem = item;
            this.showM2 = true;
        },

        closeDetail() {
            this.showM2 = false;
            this.detailItem = null;
        },

        async openPhotoModal(item) {
            this.photoProperty = item;
            this.photoImages = [];
            this.photoType = 'PHOTO';
            this.photoFile = null;
            this.showPhotoModal = true;
            await this.loadPhotoImages();
        },

        closePhotoModal() {
            this.showPhotoModal = false;
            this.photoProperty = null;
            this.photoImages = [];
            this.photoFile = null;
            this.photoViewerIndex = -1;
        },

        openPhotoViewer(index) {
            this.photoViewerIndex = index;
        },

        closePhotoViewer() {
            this.photoViewerIndex = -1;
        },

        photoPrev() {
            if (this.photoViewerIndex > 0) {
                this.photoViewerIndex--;
            }
        },

        photoNext() {
            if (this.photoViewerIndex < this.photoImages.length - 1) {
                this.photoViewerIndex++;
            }
        },

        onPhotoFile(e) {
            this.photoFile = e.target.files && e.target.files[0] ? e.target.files[0] : null;
        },

        async loadPhotoImages() {
            if (!this.photoProperty) {
                return;
            }
            const { ok, body } = await this.request(`/api/properties/${this.photoProperty.property.id}/images`);
            if (ok) {
                this.photoImages = body || [];
            }
        },

        async uploadPhoto() {
            if (!this.photoFile) {
                return;
            }
            this.loading = true;
            this.error = null;
            try {
                const form = new FormData();
                form.append('file', this.photoFile);
                form.append('imageType', this.photoType);
                const res = await fetch(`/api/properties/${this.photoProperty.property.id}/images`, {
                    method: 'POST',
                    body: form
                });
                if (res.ok) {
                    this.photoFile = null;
                    await this.loadPhotoImages();
                } else {
                    this.error = '업로드에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async openAgentModal(item) {
            this.agentProperty = item;
            this.agentLinks = [];
            this.agentQuery = '';
            this.agentResults = [];
            this.error = null;
            this.showAgentModal = true;
            await this.loadAgentLinks();
        },

        closeAgentModal() {
            this.showAgentModal = false;
            this.agentProperty = null;
            this.agentLinks = [];
            this.agentResults = [];
            this.error = null;
        },

        async loadAgentLinks() {
            const { ok, body } = await this.request(`/api/properties/${this.agentProperty.property.id}/agents`);
            if (ok) {
                this.agentLinks = body || [];
            }
        },

        async persistAgentLinks() {
            const body = this.agentLinks.map(l => ({ agentId: l.agentId, isPrimary: l.isPrimary }));
            const { ok, body: resBody } = await this.request(
                `/api/properties/${this.agentProperty.property.id}/agents`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });
            if (ok) {
                this.agentLinks = resBody || [];
            } else {
                this.error = (resBody && resBody.message) || '저장에 실패했습니다';
            }
        },

        async addLinkedAgent(agentId) {
            this.agentLinks.push({ agentId, isPrimary: this.agentLinks.length === 0 });
            this.agentQuery = '';
            this.agentResults = [];
            await this.persistAgentLinks();
        },

        async unlinkAgent(agentId) {
            this.agentLinks = this.agentLinks.filter(l => l.agentId !== agentId);
            await this.persistAgentLinks();
        },

        async setPrimaryAgent(agentId) {
            this.agentLinks = this.agentLinks.map(l => ({ ...l, isPrimary: l.agentId === agentId }));
            await this.persistAgentLinks();
        },

        async searchAgents() {
            const query = this.agentQuery;
            if (!query || !query.trim()) {
                return;
            }
            const { ok, body } = await this.request('/api/agents?query=' + encodeURIComponent(query));
            if (ok) {
                this.agentResults = (body || []).filter(a => !this.agentLinks.some(l => l.agentId === a.id));
            }
        },

        async createNewAgent() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/agents', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        officeName: this.newAgentForm.officeName,
                        agentName: this.newAgentForm.agentName,
                        phone: this.newAgentForm.phone,
                        mobile: this.newAgentForm.mobile
                    })
                });
                if (ok) {
                    this.newAgentForm = { officeName: '', agentName: '', phone: '', mobile: '' };
                    await this.addLinkedAgent(body.id);
                } else {
                    this.error = (body && body.message) || '중개인 등록에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        dragStartWeight(index) {
            this._dragIndex = index;
        },

        dragOverWeight(index) {
            if (this._dragIndex == null || this._dragIndex === index) {
                return;
            }
            const arr = this.weights.slice();
            const [moved] = arr.splice(this._dragIndex, 1);
            arr.splice(index, 0, moved);
            this.weights = arr;
            this._dragIndex = index;
        },

        dragEndWeight() {
            this._dragIndex = null;
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
                    this.sessionExpiresAt = body.expiresInSeconds != null
                        ? Date.now() + body.expiresInSeconds * 1000 : 0;
                    this.startSessionTimer();
                    this.loginForm = { email: '', password: '' };
                    this.showLogin = false;
                    this.showPassword = body.mustChangePassword === true;
                    if (this.session.role === 'ADMIN' && !this.showPassword) {
                        await this.loadUsers();
                    }
                    if (!this.showPassword) {
                        await this.loadProperties();
                        await this.checkSoldOutAlert();
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
            this.stopSessionTimer();
            this.session = { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false };
            this.sessionExpiresAt = 0;
            this.users = [];
            this.properties = [];
            this.visibleProperties = [];
            this.weights = [];
            this.view = 'list';
            this.dealTypeFilter = 'ALL';
            this.soldOutAlertShown = false;
            this.showLogin = true;
            this.showPassword = false;
            this.showSessionWarn = false;
            this.clearItinerary();
        },

        startSessionTimer() {
            if (!this._sessionTimer) {
                this._sessionTimer = setInterval(() => this.tickSession(), 15000);
            }
        },

        stopSessionTimer() {
            if (this._sessionTimer) {
                clearInterval(this._sessionTimer);
                this._sessionTimer = null;
            }
        },

        tickSession() {
            if (!this.session.authenticated) {
                return;
            }
            const remain = this.sessionExpiresAt - Date.now();
            if (remain <= 0) {
                this.logout();
                return;
            }
            if (remain < 180000 && !this.showSessionWarn) {
                this.showSessionWarn = true;
            }
        },

        async extendSession() {
            await this.checkSession();
            this.showSessionWarn = false;
        },

        async loadProperties() {
            const url = '/api/properties'
                + (this.dealTypeFilter !== 'ALL' ? '?dealType=' + this.dealTypeFilter : '');
            const { ok, body } = await this.request(url);
            if (ok) {
                this.properties = body || [];
                this.applySoldOutFilter();
                this.renderMap();
            }
        },

        applySoldOutFilter() {
            this.visibleProperties = this.showSoldOut
                ? this.properties
                : this.properties.filter(r => r.property.listingStatus !== 'SOLD_OUT');
        },

        toggleSoldOut() {
            this.showSoldOut = !this.showSoldOut;
            this.applySoldOutFilter();
            this.renderMap();
        },

        async setDealTypeFilter(filter) {
            this.dealTypeFilter = filter;
            await this.loadProperties();
        },

        async checkSoldOutAlert() {
            if (this.soldOutAlertShown) {
                return;
            }
            const { ok, body } = await this.request('/api/properties/sold-out/recent');
            if (ok && body && body.length > 0) {
                this.soldOutRecent = body;
                this.showSoldOutAlert = true;
                this.soldOutAlertShown = true;
            }
        },

        closeSoldOutAlert() {
            this.showSoldOutAlert = false;
        },

        async openCheckLogs(item) {
            this.checkLogProperty = item;
            const { ok, body } = await this.request(`/api/properties/${item.property.id}/check-logs`);
            this.checkLogs = ok ? (body || []) : [];
            this.showCheckLogs = true;
        },

        closeCheckLogs() {
            this.showCheckLogs = false;
            this.checkLogs = [];
            this.checkLogProperty = null;
        },

        restoreListing(item) {
            this.askConfirm('판매중 복구', `'${item.property.name}' 매물을 판매중으로 복구할까요?`, async () => {
                await this.request(`/api/properties/${item.property.id}/status`, {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ listingStatus: 'ACTIVE' })
                });
                this.closeCheckLogs();
                await this.loadProperties();
            });
        },

        verdictLabel(verdict) {
            return { ALIVE: '생존', GONE: '삭제', BLOCKED: '차단', ERROR: '오류' }[verdict] || verdict;
        },

        openLoanModal(item) {
            this.loanProperty = item;
            this.loanForm = { annualIncome: '', cash: '', firstHome: false };
            this.loanResult = null;
            this.error = null;
            this.showLoanModal = true;
        },

        closeLoanModal() {
            this.showLoanModal = false;
            this.loanProperty = null;
            this.loanResult = null;
            this.error = null;
        },

        async runLoanEstimate() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request(
                    `/api/properties/${this.loanProperty.property.id}/loan-estimate`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            annualIncome: toNum(this.loanForm.annualIncome),
                            cash: toNum(this.loanForm.cash),
                            firstHome: this.loanForm.firstHome
                        })
                    });
                if (ok) {
                    this.loanResult = body;
                } else {
                    this.error = (body && body.message) || '계산에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        openRefModal(item) {
            this.refProperty = item;
            this.refForm = { legalDongCode: '', dealMonth: '' };
            this.refCard = null;
            this.error = null;
            this.showRefModal = true;
        },

        closeRefModal() {
            this.showRefModal = false;
            this.refProperty = null;
            this.refCard = null;
            this.error = null;
        },

        async loadReference() {
            this.loading = true;
            this.error = null;
            try {
                const params = new URLSearchParams({
                    legalDongCode: this.refForm.legalDongCode || '',
                    dealMonth: this.refForm.dealMonth || ''
                }).toString();
                const { ok, body } = await this.request(
                    `/api/properties/${this.refProperty.property.id}/reference-transactions?${params}`);
                if (ok) {
                    this.refCard = body;
                } else {
                    this.error = (body && body.message) || '조회에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        toggleItineraryProperty(id) {
            const idx = this.itinProperties.indexOf(id);
            if (idx >= 0) {
                this.itinProperties.splice(idx, 1);
            } else {
                if (this.itinProperties.length >= 12) {
                    alert('하루 임장은 최대 12건입니다.');
                    return;
                }
                this.itinProperties.push(id);
            }
            this.itinResult = null;
            this.itinPlan = null;
        },

        async optimizeItinerary() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/itinerary/optimize', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        propertyIds: this.itinProperties,
                        travelMode: this.itinMode,
                        startLat: toNum(this.itinStart.lat),
                        startLng: toNum(this.itinStart.lng)
                    })
                });
                if (ok) {
                    this.itinResult = body;
                    this.itinPlan = null;
                    this.renderItinerary();
                } else {
                    this.error = (body && body.message) || '경로 계산에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async savePlan() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/itinerary/plans', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        propertyIds: this.itinProperties,
                        travelMode: this.itinMode,
                        startLat: toNum(this.itinStart.lat),
                        startLng: toNum(this.itinStart.lng),
                        startAddress: this.itinStart.address || null,
                        windowStart: this.itinWindowStart || null,
                        stayMinutesDefault: toNum(this.itinStay)
                    })
                });
                if (ok) {
                    this.itinPlan = body;
                    this.renderItinerary();
                } else {
                    this.error = (body && body.message) || '계획 저장에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async toggleItineraryStop(stopId, visited) {
            if (!this.itinPlan) {
                return;
            }
            const { ok, body } = await this.request(
                `/api/itinerary/plans/${this.itinPlan.id}/stops/${stopId}`, {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ visited })
                });
            if (ok) {
                this.itinPlan = body;
            }
        },

        async recomputePlan() {
            if (!this.itinPlan) {
                return;
            }
            this.loading = true;
            const { ok, body } = await this.request(
                `/api/itinerary/plans/${this.itinPlan.id}/recompute`, { method: 'POST' });
            this.loading = false;
            if (ok) {
                this.itinPlan = body;
                this.renderItinerary();
            }
        },

        propertyName(id) {
            const item = this.properties.find(x => x.property.id === id);
            return item ? item.property.name : '#' + id;
        },

        renderItinerary() {
            if (typeof kakao === 'undefined' || !kakao.maps || !this.map) {
                return;
            }
            this.clearItinerary();
            const ids = this.itinPlan
                ? this.itinPlan.stops.map(s => s.propertyId)
                : (this.itinResult ? this.itinResult.orderedPropertyIds : []);
            if (ids.length === 0) {
                return;
            }
            const points = [];
            const startLat = toNum(this.itinStart.lat);
            const startLng = toNum(this.itinStart.lng);
            if (startLat != null && startLng != null) {
                points.push(new kakao.maps.LatLng(startLat, startLng));
            }
            this._itinMarkers = {};
            ids.forEach((id, i) => {
                const item = this.properties.find(x => x.property.id === id);
                if (!item || !item.property.lat || !item.property.lng) {
                    return;
                }
                const position = new kakao.maps.LatLng(item.property.lat, item.property.lng);
                const overlay = new kakao.maps.CustomOverlay({
                    position,
                    content: `<div class="itin-marker">${i + 1}</div>`,
                    yAnchor: 1
                });
                overlay.setMap(this.map);
                this._itinMarkers[id] = overlay;
                points.push(position);
            });
            if (points.length >= 2) {
                this._itinPolyline = new kakao.maps.Polyline({
                    path: points,
                    strokeWeight: 4,
                    strokeColor: '#2d8ba8',
                    strokeOpacity: 0.85,
                    strokeStyle: 'solid'
                });
                this._itinPolyline.setMap(this.map);
                const bounds = new kakao.maps.LatLngBounds();
                points.forEach(p => bounds.extend(p));
                this.map.setBounds(bounds);
            }
        },

        clearItinerary() {
            if (this._itinPolyline) {
                this._itinPolyline.setMap(null);
                this._itinPolyline = null;
            }
            if (this._itinMarkers) {
                Object.values(this._itinMarkers).forEach(m => m.setMap(null));
            }
            this._itinMarkers = {};
        },

        openAddProperty() {
            this.propertyForm = emptyPropertyForm();
            this.propertyQuery = '';
            this.propertyAddrResults = [];
            this.error = null;
            this.showPropertyForm = true;
        },

        openAddMenu() {
            this.showAddMenu = true;
        },

        closeAddMenu() {
            this.showAddMenu = false;
        },

        startManual() {
            this.closeAddMenu();
            this.openAddProperty();
        },

        openDraftModal() {
            this.closeAddMenu();
            this.draftForm = { sourceUrl: '', memo: '' };
            this.error = null;
            this.showDraftModal = true;
        },

        closeDraftModal() {
            this.showDraftModal = false;
            this.draftForm = { sourceUrl: '', memo: '' };
            this.error = null;
        },

        async saveDraft() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/properties/draft', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        sourceUrl: this.draftForm.sourceUrl,
                        memo: this.draftForm.memo
                    })
                });
                if (ok) {
                    this.closeDraftModal();
                    await this.loadProperties();
                } else {
                    this.error = (body && body.message) || '저장에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        openPasteModal(item) {
            this.closeAddMenu();
            this.pasteDraftId = item ? item.property.id : null;
            this.pasteDraftName = item ? item.property.name : null;
            this.showPasteModal = true;
            this.pasteText = '';
            this.pastePreview = null;
            this.pasteForm = {};
            this.pasteError = null;
            setTimeout(() => {
                const el = document.getElementById('pasteText');
                if (el) {
                    el.focus();
                }
            }, 50);
        },

        closePasteModal() {
            this.showPasteModal = false;
            this.pasteText = '';
            this.pastePreview = null;
            this.pasteForm = {};
            this.pasteError = null;
            this.pasteDraftId = null;
            this.pasteDraftName = null;
            clearTimeout(this._pasteTimer);
        },

        onPasteInput() {
            clearTimeout(this._pasteTimer);
            this._pasteTimer = setTimeout(() => this.parsePaste(), 300);
        },

        async parsePaste() {
            const text = this.pasteText;
            if (!text || !text.trim()) {
                this.pastePreview = null;
                return;
            }
            this.pasteParsing = true;
            this.pasteError = null;
            const { ok, body } = await this.request('/api/properties/parse-preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text })
            });
            this.pasteParsing = false;
            if (ok) {
                this.pastePreview = body;
                const form = {};
                (body.fields || []).forEach(f => {
                    form[f.key] = f.value != null ? String(f.value) : '';
                });
                this.pasteForm = form;
            } else {
                this.pasteError = (body && body.message) || '파싱에 실패했습니다';
            }
        },

        async savePaste() {
            this.pasteParsing = true;
            this.pasteError = null;
            try {
                const url = this.pasteDraftId ? `/api/properties/${this.pasteDraftId}` : '/api/properties';
                const method = this.pasteDraftId ? 'PUT' : 'POST';
                const { ok, body } = await this.request(url, {
                    method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(this.buildPasteRequest())
                });
                if (ok) {
                    this.showPasteModal = false;
                    this.pasteText = '';
                    this.pastePreview = null;
                    this.pasteForm = {};
                    this.pasteDraftId = null;
                    this.pasteDraftName = null;
                    await this.loadProperties();
                } else {
                    this.pasteError = (body && body.message) || '등록에 실패했습니다';
                }
            } catch (e) {
                this.pasteError = '네트워크 오류가 발생했습니다';
            } finally {
                this.pasteParsing = false;
            }
        },

        buildPasteRequest() {
            const value = (k) => (this.pasteForm[k] != null ? String(this.pasteForm[k]).trim() : '');
            const dealCode = { 매매: 'SALE', 전세: 'JEONSE', 월세: 'MONTHLY' }[value('dealType')] || null;
            const floor = value('floor').split('/');
            const moveIn = value('moveIn');
            let moveInType = null;
            let moveInDate = null;
            if (/즉시/.test(moveIn)) {
                moveInType = 'IMMEDIATE';
            } else if (/협의/.test(moveIn)) {
                moveInType = 'NEGOTIABLE';
            } else {
                const note = this.pasteNote('moveIn');
                const match = note && note.match(/(\d{4}-\d{2}-\d{2})/);
                if (match) {
                    moveInType = 'DATE';
                    moveInDate = match[1];
                }
            }
            return {
                name: value('name'),
                dealType: dealCode,
                priceDeposit: toNum(value('priceDeposit')),
                priceMonthly: toNum(value('priceMonthly')),
                kbPrice: toNum(value('kbPrice')),
                areaSupplyM2: toNum(value('areaSupplyM2')),
                areaExclusiveM2: toNum(value('areaExclusiveM2')),
                floorNo: toNum(floor[0]),
                floorTotal: toNum(floor[1]),
                direction: value('direction') || null,
                addressJibun: value('addressJibun') || null,
                approvalYear: toNum(value('approvalYear')),
                totalHouseholds: toNum(value('totalHouseholds')),
                parkingPerHousehold: toNum(value('parkingPerHousehold')),
                moveInType,
                moveInDate,
                naverArticleNo: value('naverArticleNo') || null,
                rawPasteText: this.pasteText
            };
        },

        pasteNote(key) {
            if (!this.pastePreview) {
                return null;
            }
            const field = this.pastePreview.fields.find(f => f.key === key);
            return field ? field.note : null;
        },

        fieldLabel(key) {
            return {
                name: '단지명', naverArticleNo: '매물번호', dongHo: '동/호', dealType: '거래유형',
                priceDeposit: '매매가/보증금', priceMonthly: '월세', kbPrice: 'KB시세',
                maintenanceFee: '관리비',
                areaSupplyM2: '공급면적', areaExclusiveM2: '전용면적', floor: '해당층/총층',
                roomBath: '방/욕실', direction: '향', heatingType: '난방',
                addressJibun: '지번주소', approvalYear: '사용승인년도',
                totalHouseholds: '세대수', parkingPerHousehold: '주차(세대당)', moveIn: '입주가능일',
                subway: '지하철', subwayMinutes: '역 도보(분)',
                school: '배정 초등학교', schoolMinutes: '학교 도보(분)'
            }[key] || key;
        },

        confidenceLabel(confidence) {
            return { EXACT: '확정', DERIVED: '추정', MISSING: '누락' }[confidence] || '';
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
                moveInDate: p.moveInDate || '',
                editVersion: p.editVersion ?? null
            };
            this.propertyQuery = '';
            this.propertyAddrResults = [];
            this.error = null;
            this.showPropertyForm = true;
        },

        async searchPropertyAddress() {
            const query = this.propertyQuery;
            if (!query || !query.trim()) {
                return;
            }
            const { ok, body } = await this.request('/api/geo/search?query=' + encodeURIComponent(query));
            this.propertyAddrResults = ok ? (body || []) : [];
        },

        selectPropertyAddress(r) {
            this.propertyForm.addressRoad = r.roadAddressName || r.addressName || '';
            this.propertyForm.addressJibun = r.addressName || '';
            this.propertyForm.lat = r.lat != null ? String(r.lat) : '';
            this.propertyForm.lng = r.lng != null ? String(r.lng) : '';
            this.propertyQuery = r.addressName || '';
            this.propertyAddrResults = [];
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
                const headers = { 'Content-Type': 'application/json' };
                if (id && this.propertyForm.editVersion != null) {
                    headers['X-Edit-Version'] = String(this.propertyForm.editVersion);
                }
                const { ok, body: resBody } = await this.request(url, {
                    method,
                    headers,
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

        removeProperty(item) {
            const p = item.property;
            this.askConfirm('매물 삭제', `'${p.name}' 매물을 삭제할까요?`, async () => {
                await this.request(`/api/properties/${p.id}`, { method: 'DELETE' });
                await this.loadProperties();
            });
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

        confirmHybridScore(code) {
            const score = (this.scoreProperty.scores || []).find(s => s.code === code);
            if (score && score.effectiveScore != null) {
                this.scoreForm[code] = String(score.effectiveScore);
            }
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

        async loadSettings() {
            const { ok, body } = await this.request('/api/admin/settings');
            if (ok) {
                this.settings = body || [];
                const form = {};
                this.settings.forEach(s => {
                    form[s.configKey] = s.configValue || '';
                });
                this.settingsForm = form;
            }
        },

        async saveSettings() {
            this.loading = true;
            this.error = null;
            try {
                const body = this.settings.map(s => ({
                    configKey: s.configKey,
                    configValue: this.settingsForm[s.configKey] ?? s.configValue
                }));
                const { ok, body: resBody } = await this.request('/api/admin/settings', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });
                if (ok) {
                    await this.loadSettings();
                } else {
                    this.error = (resBody && resBody.message) || '설정 저장에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async testSlack() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/admin/settings/slack/test', { method: 'POST' });
                if (ok && body && body.sent) {
                    alert('Slack 테스트 메시지를 보냈습니다.');
                } else {
                    alert('Slack 전송에 실패했습니다. 환경변수(SLACK_WEBHOOK_URL)를 확인하세요.');
                }
            } catch (e) {
                alert('네트워크 오류가 발생했습니다');
            } finally {
                this.loading = false;
            }
        },

        async runListingCheck() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/admin/listing-check/run', { method: 'POST' });
                if (!ok) {
                    this.error = (body && body.message) || '배치 실행에 실패했습니다';
                } else {
                    this.error = '생존 확인 배치를 실행했습니다.';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async loadNotifications() {
            const { ok, body } = await this.request('/api/admin/notifications');
            if (ok) {
                this.notifications = body || [];
            }
        },

        settingsByCategory(category) {
            return this.settings.filter(s => s.category === category);
        },

        configCategoryLabel(category) {
            return { BATCH: '배치', SCORING: '채점', LOAN: '대출' }[category] || category;
        },

        fmtTime(iso) {
            if (!iso) {
                return '-';
            }
            return new Date(iso).toLocaleString('ko-KR');
        },

        renderMap() {
            if (typeof kakao === 'undefined' || !kakao.maps) {
                setTimeout(() => this.renderMap(), 300);
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
            const coords = this.visibleProperties.filter(r => r.property.lat && r.property.lng);
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
            const item = this.visibleProperties.find(x => x.property.id === id);
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
