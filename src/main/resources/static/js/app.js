

let routeApplying = false;
let routeQueued = false;
let routeTarget = null;


function currentHashPath() {
    const raw = decodeURIComponent(window.location.hash.slice(1));
    return raw.startsWith('/') ? raw : '/list';
}

function todayIso() {
    const now = new Date();
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
}

function emptyPropertyForm() {
    return {
        id: null,
        name: '',
        dongHo: '',
        dealType: 'SALE',
        priceDeposit: '',
        maintenanceFee: '',
        addressRoad: '',
        addressJibun: '',
        lat: '',
        lng: '',
        areaSupplyM2: '',
        areaExclusiveM2: '',
        floorRaw: '',
        floorTotal: '',
        direction: '',
        approvalYear: '',
        buildingCount: '',
        totalHouseholds: '',
        parkingPerHousehold: '',
        moveInType: '',
        moveInDate: '',
        editVersion: null,
        carry: {}
    };
}

function emptyUserForm() {
    return {
        loginId: '',
        nickname: '',
        groupId: '',
        password: '',
        role: 'MEMBER',
        workplaceName: '',
        workplaceLat: '',
        workplaceLng: '',
        availableBudget: '',
        annualIncome: '',
        existingLoan: ''
    };
}


const COMPARE_MIN_PROPERTIES = 4;

const PAGE_SIZE = 30;

const INFINITE_SCROLL_MARGIN_PX = 400;

const PIN_Z = { visited: 1, fresh: 2, hover: 10 };

const MOBILE_BREAKPOINT_PX = 767;
const MOBILE_SHEET_PEEK_RATIO = 0.25;
const MOBILE_SHEET_DRAG_THRESHOLD_PX = 48;



const SCORE_WATCH_MS = 3000;

const SCORING_GRACE_MS = 5 * 60 * 1000;
const SHOW_LOADING_AFTER_MS = 250;
const LLM_POLL_INTERVAL_MS = 2000;
const LLM_POLL_MAX_ATTEMPTS = 60;


const SCORE_POLL_INTERVAL_MS = 3000;

const SCORE_POLL_MAX_ATTEMPTS = 40;
const REF_POLL_INTERVAL_MS = 3000;

const REF_POLL_MAX_ATTEMPTS = 20;
const FORECAST_POLL_INTERVAL_MS = 5000;
const FORECAST_POLL_MAX_ATTEMPTS = 36;


function floorText(p) {
    const band = { LOW: '저', MID: '중', HIGH: '고' };
    if (p?.floorBand) {
        return band[p.floorBand] ?? '';
    }
    return p?.floorNo ?? '';
}


function csrfHeader() {
    const found = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return found ? { 'X-XSRF-TOKEN': decodeURIComponent(found[1]) } : {};
}

function withCsrf(options) {
    const method = String(options?.method || 'GET').toUpperCase();
    if (method === 'GET' || method === 'HEAD') {
        return options;
    }
    return { ...options, headers: { ...csrfHeader(), ...(options?.headers || {}) } };
}

function emptyRegAreaForm() {
    return {
        codePrefix: '',
        zone: 'SPECULATION_OVERHEATED',
        areaName: '',
        designatedOn: '',
        releasedOn: '',
        note: ''
    };
}

function halley() {
    return {
        session: { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false },
        view: 'list',
        mobileSheetExpanded: false,
        mobileSheetDragging: false,
        mobileSheetOffsetPx: null,
        _mobileSheetPointerId: null,
        _mobileSheetStartY: 0,
        _mobileSheetStartOffsetPx: 0,
        _mobileSheetTravelPx: 0,
        _mobileSheetStartedExpanded: false,
        _mobileSheetMoved: false,
        _mobileSheetSuppressClick: false,
        dealTypeFilter: 'ALL',

        sortKey: 'DEFAULT',
        sortOpen: false,

        PAGE_SIZE,

        properties: [],
        scoreWatchTimer: null,
        _scrollObserver: null,

        propertyPage: 0,

        propertyTotal: 0,
        propertyHasNext: false,
        loadingMore: false,

        archivedTotal: 0,

        pins: [],
        users: [],
        soldOutRecent: [],
        showItinUnavailable: false,
        itinUnavailableMessage: '',
        showSoldOutAlert: false,
        soldOutAlertShown: false,
        showLoanModal: false,
        loanProperty: null,
        loanForm: { firstHome: false, mortgageInsured: false, ownedHouseCount: 0, rateType: 'VARIABLE' },
        loanResult: null,
        loanAmount: 0,
        loanShowInputs: false,
        showMciHelp: false,
        loanOverride: { annualIncome: '', cash: '', existingLoan: '' },
        showRefModal: false,
        refProperty: null,
        refForm: { legalDongCode: '', dealMonth: '' },
        refCard: null,
        itinProperties: [],
        itinMode: 'DRIVING',
        itinStart: { address: '', lat: '', lng: '' },
        itinDate: todayIso(),
        itinWindowStart: '09:00',
        itinStay: 25,
        itinResult: null,

        itinVisited: [],
        _itinMarkers: {},
        _itinPolyline: null,
        _itinPolylines: [],
        sessionExpiresAt: null,
        _sessionTimer: null,
        showSessionWarn: false,
        showUserForm: false,
        userForm: emptyUserForm(),
        editingUserId: null,
        tempPassword: null,

        notifySettings: null,
        confirmState: null,
        profile: null,
        profileForm: { nickname: '', workplaceName: '', workplaceLat: '', workplaceLng: '',
            availableBudget: '', annualIncome: '', existingLoan: '' },
        showChangePw: false,
        changePwForm: { currentPassword: '', newPassword: '' },
        showM2: false,
        detailItem: null,
        showCompare: false,
        compareStatus: null,
        compareRunning: false,
        compareError: null,
        regActiveProfile: '',
        regProfiles: [],
        regParams: [],
        regParamForm: {},
        regNewProfile: '',
        regAreas: [],
        regAreaForm: emptyRegAreaForm(),
        regError: null,
        showComments: false,
        commentProperty: null,
        comments: [],
        commentNewText: '',
        commentEditingId: null,
        commentEditText: '',
        detailAgents: [],
        detailRef: null,
        _refTimer: null,
        _scoreTimer: null,
        detailLlm: null,
        detailLandUse: [],
        llmPending: false,
        _llmTimer: null,
        showSettings: false,
        showUsers: false,
        showProfileSetup: false,
        setupForm: { nickname: '', workplaceName: '', workplaceLat: '', workplaceLng: '',
            availableBudget: '', annualIncome: '', existingLoan: '' },
        showPhotoModal: false,
        photoProperty: null,
        photoImages: [],
        photoViewerIndex: -1,

        viewerImages: [],

        detailImages: [],
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
        propertyAddrError: null,
        showAddMenu: false,
        showPasteModal: false,
        pasteText: '',
        pasteParsing: false,
        pastePreview: null,
        pasteForm: {},
        pasteError: null,
        _pasteTimer: null,
        pasteDraftId: null,

        pasteFloorPlan: null,
        pastePhotos: [],
        pasteDraftName: null,
        showScoreModal: false,
        scoreProperty: null,
        scoreForm: {},
        _scoreFormAtOpen: {},
        showForecast: false,
        forecastProperty: null,
        forecastDetail: null,
        forecastNews: [],
        _loading: {},
        _loadingTimers: {},
        weights: [],
        settings: [],
        llmModels: null,
        llmForm: {},
        settingsForm: {},
        notifications: [],
        map: null,
        markers: {},
        activePropertyId: null,

        pendingFocus: null,
        showRoadview: false,
        roadviewProperty: null,
        roadviewState: 'loading',
        roadview: null,
        loginForm: { loginId: '', password: '', rememberId: true, rememberMe: true },
        signUpOpen: false,
        showSignUp: false,
        signUpForm: { loginId: '', nickname: '', password: '' },
        signUpNickname: null,
        profileNickname: null,
        debts: [],
        debtForm: [],
        debtTypes: [
            { code: 'MORTGAGE', label: '주택담보대출' },
            { code: 'CREDIT', label: '신용대출' },
            { code: 'NEGATIVE_ACCOUNT', label: '마이너스통장 (한도)' },
            { code: 'JEONSE', label: '전세자금대출' },
            { code: 'OTHER_SECURED', label: '기타담보대출' },
            { code: 'INSTALLMENT', label: '할부·리스' }
        ],
        myGroup: null,
        groups: [],
        newGroupName: '',

        _dragIndex: null,
        groupForm: { name: '', slackWebhookUrl: '' },
        joinForm: { code: '' },
        inviteCode: null,
        groupDetail: null,
        withdrawForm: { password: '' },
        passwordForm: { currentPassword: '', newPassword: '' },
        error: null,
        loading: false,

        savingKey: null,


        async loadPublicConfig() {
            const { ok, body } = await this.request('/api/auth/config');
            this.signUpOpen = ok && body ? body.signUpOpen === true : false;
        },


        guardNumberInputs() {
            document.addEventListener('wheel', (event) => {
                const el = event.target;
                if (el instanceof HTMLInputElement && el.type === 'number'
                        && document.activeElement === el) {
                    el.blur();
                }
            }, { passive: true });
        },

        async init() {
            this.guardNumberInputs();
            this.watchModalOpen();
            this.restoreLoginId();
            const onNavigate = () => {
                if (this.session.authenticated) {
                    routeApplying = true;
                    this.closeAllModals();
                    routeApplying = false;
                    this.applyRoute();
                }
            };
            window.addEventListener('popstate', onNavigate);
            window.addEventListener('hashchange', onNavigate);
            await this.loadPublicConfig();
            window.addEventListener('resize', () => {
                this.resetMobileSheetDrag();
                if (this.map) {
                    this.map.relayout();
                }
            });
            await this.checkSession();
        },

        isMobileViewport() {
            if (typeof window.matchMedia === 'function') {
                return window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT_PX}px)`).matches;
            }
            return window.innerWidth <= MOBILE_BREAKPOINT_PX;
        },

        mobileSheetStyle() {
            if (this.mobileSheetOffsetPx == null) {
                return '';
            }
            return `transform: translateY(${Math.round(this.mobileSheetOffsetPx)}px)`;
        },

        toggleMobileSheet() {
            if (this._mobileSheetSuppressClick) {
                this._mobileSheetSuppressClick = false;
                return;
            }
            if (this.isMobileViewport()) {
                this.mobileSheetExpanded = !this.mobileSheetExpanded;
            }
        },

        expandMobileSheet() {
            if (this.isMobileViewport()) {
                this.mobileSheetExpanded = true;
            }
        },

        collapseMobileSheet() {
            this.mobileSheetExpanded = false;
        },

        revealPropertyOnMap(item) {
            this.focusProperty(item);
            this.collapseMobileSheet();
        },

        startMobileSheetDrag(event) {
            if (!this.isMobileViewport() || (event.button != null && event.button !== 0)) {
                return;
            }
            const panel = event.currentTarget.closest('.list-panel');
            const panelHeight = panel ? panel.clientHeight : 0;
            const travel = panelHeight * (1 - MOBILE_SHEET_PEEK_RATIO);
            if (travel <= 0) {
                return;
            }
            this.mobileSheetDragging = true;
            this._mobileSheetPointerId = event.pointerId;
            this._mobileSheetStartY = event.clientY;
            this._mobileSheetTravelPx = travel;
            this._mobileSheetStartedExpanded = this.mobileSheetExpanded;
            this._mobileSheetStartOffsetPx = this.mobileSheetExpanded ? 0 : travel;
            this.mobileSheetOffsetPx = this._mobileSheetStartOffsetPx;
            this._mobileSheetMoved = false;
            this._mobileSheetSuppressClick = false;
            if (typeof event.currentTarget.setPointerCapture === 'function') {
                event.currentTarget.setPointerCapture(event.pointerId);
            }
            event.preventDefault();
        },

        moveMobileSheetDrag(event) {
            if (!this.mobileSheetDragging || event.pointerId !== this._mobileSheetPointerId) {
                return;
            }
            const delta = event.clientY - this._mobileSheetStartY;
            this.mobileSheetOffsetPx = Math.min(
                this._mobileSheetTravelPx,
                Math.max(0, this._mobileSheetStartOffsetPx + delta));
            this._mobileSheetMoved = this._mobileSheetMoved || Math.abs(delta) > 8;
            event.preventDefault();
        },

        endMobileSheetDrag(event) {
            if (!this.mobileSheetDragging || event.pointerId !== this._mobileSheetPointerId) {
                return;
            }
            const delta = event.clientY - this._mobileSheetStartY;
            if (delta <= -MOBILE_SHEET_DRAG_THRESHOLD_PX) {
                this.mobileSheetExpanded = true;
            } else if (delta >= MOBILE_SHEET_DRAG_THRESHOLD_PX) {
                this.mobileSheetExpanded = false;
            } else {
                this.mobileSheetExpanded = this._mobileSheetStartedExpanded;
            }
            this._mobileSheetSuppressClick = this._mobileSheetMoved;
            if (this._mobileSheetSuppressClick) {
                setTimeout(() => {
                    this._mobileSheetSuppressClick = false;
                }, 0);
            }
            this.resetMobileSheetDrag(false);
        },

        cancelMobileSheetDrag(event) {
            if (!this.mobileSheetDragging || event.pointerId !== this._mobileSheetPointerId) {
                return;
            }
            this.mobileSheetExpanded = this._mobileSheetStartedExpanded;
            this.resetMobileSheetDrag();
        },

        resetMobileSheetDrag(clearSuppressedClick = true) {
            this.mobileSheetDragging = false;
            this.mobileSheetOffsetPx = null;
            this._mobileSheetPointerId = null;
            this._mobileSheetStartY = 0;
            this._mobileSheetStartOffsetPx = 0;
            this._mobileSheetTravelPx = 0;
            this._mobileSheetMoved = false;
            if (clearSuppressedClick) {
                this._mobileSheetSuppressClick = false;
            }
        },

        async request(url, options) {
            const res = await fetch(url, withCsrf(options));
            let body = null;
            try {
                body = await res.json();
            } catch (e) {
                body = null;
            }
            if (res.status === 403 && body && body.code === 'PROFILE_SETUP_REQUIRED') {
                this.showProfileSetup = true;
            }
            if (res.status === 403 && body && body.code === 'MUST_CHANGE_PASSWORD') {
                this.showPassword = true;
            }
            if (res.status === 401) {
                const hadSession = this.session.authenticated;
                this.session = { authenticated: false, userId: null, nickname: null,
                    role: null, mustChangePassword: false };
                this.showLogin = true;
                this.focusLogin();
                if (hadSession) {
                    this.error = '로그인이 풀렸습니다. 다시 로그인해 주세요';
                }
            }
            return { ok: res.ok, status: res.status, body };
        },

        async checkSession() {
            const { ok, body } = await this.request('/api/auth/session');
            if (ok) {
                this.session = Object.assign({ authenticated: true }, body);
                this.sessionExpiresAt = body.expiresInSeconds != null
                    ? Date.now() + body.expiresInSeconds * 1000 : null;
                this.startSessionTimer();
                this.showLogin = false;
                this.showPassword = body.mustChangePassword === true;
                this.showProfileSetup = !this.showPassword && body.profileConfirmed === false;
                if (this.showProfileSetup) {
                    await this.prefillSetupForm();
                }
                const setupPending = this.showPassword || this.showProfileSetup;
                if (this.session.role === 'ADMIN' && !setupPending) {
                    await this.loadUsers();
                    await this.loadGroups();
                }
                if (!setupPending) {
                    await this.loadMyGroup();
                    await this.loadDebts();
                    await this.loadVisited();
                    await this.loadProperties();
                    await this.checkSoldOutAlert();
                    this.startScoreWatch();
                    this.startInfiniteScroll();
                    this.applyRoute();
                }
            } else {
                this.session = { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false };
                this.showLogin = true;
                this.focusLogin();
            }
        },


        ROUTES: {
            list: '/list',
            itinerary: '/tour-plan'
        },


        MODAL_ROUTES: [
            { key: 'score', flag: 'showScoreModal', prop: 'scoreProperty', suffix: 'score',
              open: (app, item) => app.openScoreModal(item) },
            { key: 'loan', flag: 'showLoanModal', prop: 'loanProperty', suffix: 'loan',
              open: (app, item) => app.openLoanModal(item) },
            { key: 'comments', flag: 'showComments', prop: 'commentProperty', suffix: 'comments',
              open: (app, item) => app.openComments(item) }
        ],


        currentPath() {
            let chosen = null;
            for (const route of this.MODAL_ROUTES) {
                if (this[route.flag] === true && chosen === null) {
                    chosen = route;
                }
            }
            const detail = this.detailItem;
            const base = (this.showM2 && detail)
                ? `/properties/${detail.property.id}`
                : (this.ROUTES[this.view] || this.ROUTES.list);
            if (chosen === null) {
                return base;
            }
            const id = this[chosen.prop]?.property?.id ?? this[chosen.prop]?.id;
            return id == null ? base : `/properties/${id}/${chosen.suffix}`;
        },


        syncRoute() {
            const path = this.currentPath();
            if (routeApplying) {
                return;
            }
            routeTarget = path;
            if (routeQueued) {
                return;
            }
            routeQueued = true;
            queueMicrotask(() => {
                routeQueued = false;
                if (!routeApplying) {
                    this.pushRoute(routeTarget);
                }
            });
        },


        pushRoute(path) {
            if (currentHashPath() !== path) {
                window.history.pushState({}, '', '#' + path);
            }
        },


        async applyRoute() {
            const path = currentHashPath();
            routeApplying = true;
            try {
                await this.openRoute(path);
            } finally {
                routeApplying = false;
            }
        },

        async openRoute(path) {
            const scoped = path.match(/^\/properties\/(\d+)\/([a-z]+)$/);
            if (scoped) {
                this.view = 'list';
                const item = await this.findProperty(Number(scoped[1]));
                const route = this.MODAL_ROUTES.find(r => r.key === scoped[2]);
                if (item && route) {
                    await route.open(this, item);
                    return;
                }
                if (item) {
                    this.openDetail(item);
                }
                return;
            }
            const detail = path.match(/^\/properties\/(\d+)$/);
            if (detail) {
                this.view = 'list';
                await this.openDetailById(Number(detail[1]));
                return;
            }
            const entry = Object.entries(this.ROUTES).find(([, p]) => p === path);
            this.setView(entry ? entry[0] : 'list');
        },


        async findProperty(id) {
            if ((this.properties || []).length === 0) {
                await this.loadProperties();
            }
            const loaded = this.properties.find(x => x.property.id === id);
            if (loaded) {
                return loaded;
            }
            const { ok, body } = await this.request('/api/properties/' + id);
            return ok && body ? body : null;
        },


        async openDetailById(id) {
            const item = await this.findProperty(id);
            if (item) {
                this.openDetail(item);
            }
        },

        setView(view) {
            const leaving = this.view;
            this.view = view;
            if (view !== 'list') {
                this.expandMobileSheet();
            }
            if (leaving === 'itinerary' && view !== 'itinerary') {
                this.clearItinerary();
            }
            if (view === 'weights') {
                this.loadWeights();
            }
            if (view === 'me') {
                this.loadProfile();
            }
            if (view === 'group') {
                this.loadGroupDetail();
            }
            if (view === 'itinerary') {
                this.normalizeItinStart();
                this.loadStartLocation();
                this.loadItineraryDraft();
                this.loadVisited();
            }
        },


        openSettings() {
            if (this.session.role !== 'ADMIN') {
                return;
            }
            this.showSettings = true;
            this.error = null;
            this.regError = null;
            this.loadSettings();
            this.loadLlmModels();
            this.loadNotifications();
            this.loadNotifySettings();
            this.loadRegulations();
        },


        closeSettings() {
            this.showSettings = false;
            this.error = null;
            this.regError = null;
            this.settings = [];
            this.settingsForm = {};
            this.llmModels = null;
            this.llmForm = {};
            this.notifications = [];
            this.notifySettings = null;
            this.regActiveProfile = '';
            this.regProfiles = [];
            this.regParams = [];
            this.regParamForm = {};
            this.regNewProfile = '';
            this.regAreas = [];
            this.regAreaForm = emptyRegAreaForm();
        },


        openUsers() {
            if (this.session.role !== 'ADMIN') {
                return;
            }
            this.showUsers = true;
            this.error = null;
            this.withLoading('users', () => this.loadUsers());
        },

        closeUsers() {
            this.showUsers = false;
            this.users = [];
            this.error = null;
        },

        async loadUsers() {
            const { ok, body } = await this.request('/api/users');
            if (ok) {
                this.users = body || [];
            }
        },


        async openAddUser() {
            this.editingUserId = null;
            this.userForm = emptyUserForm();
            this.error = null;
            this.showUserForm = true;
            await this.withLoading('groups', () => this.loadGroups());
        },

        async openEditUser(u) {
            this.editingUserId = u.id;
            this.userForm = {
                loginId: u.loginId,
                nickname: u.nickname,
                groupId: '',
                password: '',
                role: u.role,
                workplaceName: u.workplaceName || '',
                workplaceLat: u.workplaceLat ?? '',
                workplaceLng: u.workplaceLng ?? '',
                availableBudget: u.availableBudget ?? '',
                annualIncome: u.annualIncome ?? '',
                existingLoan: u.existingLoan ?? ''
            };
            this.error = null;
            this.showUserForm = true;
            await this.withLoading('groups', () => this.loadGroups());
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
                loginId: this.userForm.loginId,
                nickname: this.userForm.nickname,
                groupId: this.userForm.groupId ? Number(this.userForm.groupId) : null,
                workplaceName: this.userForm.workplaceName || null,
                workplaceLat: toNum(this.userForm.workplaceLat),
                workplaceLng: toNum(this.userForm.workplaceLng),
                availableBudget: toNum(this.userForm.availableBudget),
                annualIncome: toNum(this.userForm.annualIncome),
                existingLoan: toNum(this.userForm.existingLoan)
            };
            if (!editing) {
                body.password = this.userForm.password;
                body.role = 'MEMBER';
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
                    this.tempPassword = { loginId: u.loginId, nickname: u.nickname,
                        password: body.temporaryPassword, copied: false };
                } else {
                    this.error = '비밀번호 초기화에 실패했습니다';
                }
                await this.loadUsers();
            });
        },



        dismissTempPassword() {
            this.tempPassword = null;
        },

        async copyTempPassword() {
            if (!this.tempPassword) {
                return;
            }
            try {
                await navigator.clipboard.writeText(this.tempPassword.password);
                this.tempPassword.copied = true;
            } catch (e) {
                this.tempPassword.copied = false;
            }
        },

        openSignUp() {
            this.showLogin = false;
            this.showSignUp = true;
            this.error = null;
            this.signUpNickname = null;
        },

        openLogin() {
            this.showSignUp = false;
            this.showLogin = true;
            this.focusLogin();
            this.error = null;
        },


        async signUp() {
            if (this.signUpNickname === false) {
                this.error = '다른 닉네임을 골라주세요';
                return;
            }
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/users/sign-up', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(this.signUpForm)
                });
                if (!ok) {
                    this.error = (body && body.message) || '가입에 실패했습니다';
                    return;
                }
                this.loginForm = Object.assign({}, this.loginForm,
                        { loginId: this.signUpForm.loginId, password: this.signUpForm.password });
                this.showSignUp = false;
                this.signUpForm = { loginId: '', nickname: '', password: '' };
                await this.login();
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },


        async checkNickname(target) {
            const nickname = target === 'signUp' ? this.signUpForm.nickname : this.profileForm.nickname;
            if (!nickname) {
                return;
            }
            const { ok, body } = await this.request(
                '/api/users/nickname-check?nickname=' + encodeURIComponent(nickname));
            const available = ok && body ? body.available : false;
            if (target === 'signUp') {
                this.signUpNickname = available;
            } else {
                this.profileNickname = available;
            }
        },


        async loadGroups() {
            const { ok, body } = await this.request('/api/admin/groups');
            this.groups = ok && body ? body : [];
        },

        async createGroupAsAdmin() {
            this.error = null;
            const { ok, body } = await this.request('/api/admin/groups', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: this.newGroupName.trim() || null })
            });
            if (!ok) {
                this.error = (body && body.message) || '그룹을 만들지 못했습니다';
                return;
            }
            this.newGroupName = '';
            await this.loadGroups();
        },


        async loadDebts() {
            const { ok, body } = await this.request('/api/users/me/debts');
            this.debts = ok && body ? body : [];
            this.debtForm = this.debts.map(d => ({ type: d.type, amount: String(d.amount) }));
        },

        addDebt() {
            this.debtForm.push({ type: 'CREDIT', amount: '' });
        },

        removeDebt(index) {
            this.debtForm.splice(index, 1);
        },

        async saveDebts() {
            const payload = this.debtForm
                .filter(d => d.type && toNum(d.amount) > 0)
                .map(d => ({ type: d.type, amount: toNum(d.amount) }));
            const { ok, body } = await this.request('/api/users/me/debts', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!ok) {
                this.error = (body && body.message) || '부채를 저장하지 못했습니다';
                return;
            }
            this.debts = body || [];
            this.debtForm = this.debts.map(d => ({ type: d.type, amount: String(d.amount) }));
        },

        async loadMyGroup() {
            const { ok, body } = await this.request('/api/groups/me');
            this.myGroup = ok ? body : null;
            this.groupForm.name = this.myGroup ? this.myGroup.name : '';
            this.groupForm.slackWebhookUrl = this.myGroup ? (this.myGroup.slackWebhookUrl || '') : '';
        },

        async renameGroup() {
            const { ok, body } = await this.request('/api/groups/me', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: this.groupForm.name })
            });
            if (ok) {
                this.myGroup = body;
                if (this.groupDetail) {
                    this.groupDetail = { ...this.groupDetail, name: body.name };
                }
            } else {
                this.error = (body && body.message) || '그룹 이름을 바꾸지 못했습니다';
            }
        },


        async saveWebhook() {
            const { ok, body } = await this.request('/api/groups/me/webhook', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ slackWebhookUrl: this.groupForm.slackWebhookUrl || null })
            });
            if (ok) {
                this.myGroup = body;
                if (this.groupDetail) {
                    this.groupDetail = { ...this.groupDetail, slackWebhookUrl: body.slackWebhookUrl };
                }
            } else {
                this.error = (body && body.message) || '웹훅을 저장하지 못했습니다';
            }
        },


        async testWebhook() {
            const { ok, body } = await this.request('/api/groups/me/webhook/test', { method: 'POST' });
            this.error = (ok && body && body.sent)
                ? null
                : '테스트 메시지를 보내지 못했습니다. 웹훅 주소를 확인해 주세요';
        },


        async loadGroupDetail() {
            const { ok, body } = await this.withLoading('groupDetail',
                () => this.request('/api/groups/me/detail'));
            this.groupDetail = ok ? body : null;
            if (this.groupDetail) {
                this.groupForm.name = this.groupDetail.name || '';
                this.groupForm.slackWebhookUrl = this.groupDetail.slackWebhookUrl || '';
            }
        },

        async createInvite() {
            const { ok, body } = await this.withLoading('invite',
                () => this.request('/api/groups/me/invites', { method: 'POST' }));
            if (ok) {
                this.inviteCode = body;
            } else {
                this.error = (body && body.message) || '초대 코드를 만들지 못했습니다';
            }
        },


        confirmJoinGroup() {
            const alone = this.myGroup && this.myGroup.memberCount <= 1;
            const message = alone
                ? `지금 그룹('${this.myGroup.name}')에는 회원님만 있습니다.\n`
                    + '옮기면 이 그룹과 여기 등록된 매물이 모두 삭제되며 되돌릴 수 없습니다.\n\n계속할까요?'
                : `지금 그룹('${this.myGroup ? this.myGroup.name : ''}')에서 나가 초대받은 그룹으로 옮깁니다.\n`
                    + '옮기면 지금 그룹의 매물은 더 이상 보이지 않습니다.\n\n계속할까요?';
            this.askConfirm('그룹 변경', message, async () => {
                const { ok, body } = await this.request('/api/groups/join', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ code: this.joinForm.code.trim() })
                });
                if (!ok) {
                    this.error = (body && body.message) || '그룹 가입에 실패했습니다';
                    return;
                }
                this.joinForm.code = '';
                this.inviteCode = null;
                await this.loadMyGroup();
                await this.loadProperties();
            });
        },

        confirmWithdraw() {
            const alone = this.myGroup && this.myGroup.memberCount <= 1;
            const message = (alone
                ? `그룹('${this.myGroup.name}')에 회원님만 있습니다. 탈퇴하면 그룹과 매물이 모두 삭제됩니다.\n`
                : '올리신 매물과 코멘트는 그룹에 남습니다.\n')
                + '닉네임을 제외한 모든 정보가 삭제되며 되돌릴 수 없습니다.\n\n정말 탈퇴할까요?';
            this.askConfirm('회원 탈퇴', message, async () => {
                const { ok, body } = await this.request('/api/users/me/withdraw', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ password: this.withdrawForm.password })
                });
                if (!ok) {
                    this.error = (body && body.message) || '탈퇴에 실패했습니다';
                    return;
                }
                window.location.reload();
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


        async prefillSetupForm() {
            const { ok, body } = await this.request('/api/users/me');
            if (!ok || !body) {
                return;
            }
            this.setupForm = {
                nickname: body.nickname || '',
                workplaceName: body.workplaceName || '',
                workplaceLat: body.workplaceLat ?? '',
                workplaceLng: body.workplaceLng ?? '',
                availableBudget: body.availableBudget ?? '',
                annualIncome: body.annualIncome ?? '',
                existingLoan: body.existingLoan ?? ''
            };
        },

        async loadProfile() {
            const { ok, body } = await this.request('/api/users/me');
            if (ok) {
                this.profile = body;
                this.profileForm = {
                    nickname: body.nickname || '',
                    workplaceName: body.workplaceName || '',
                    workplaceLat: body.workplaceLat ?? '',
                    workplaceLng: body.workplaceLng ?? '',
                    availableBudget: body.availableBudget ?? '',
                    annualIncome: body.annualIncome ?? '',
                    existingLoan: body.existingLoan ?? ''
                };
            }
            await this.loadMyGroup();
        },


        searchPostcodeFor(target) {
            if (!window.daum || !window.daum.Postcode) {
                this.error = '우편번호 서비스를 불러오지 못했습니다. 새로고침 후 다시 시도하세요.';
                return;
            }
            new window.daum.Postcode({
                oncomplete: async (data) => {
                    const address = data.userSelectedType === 'R' ? data.roadAddress : data.jibunAddress;
                    const label = data.buildingName ? `${address} (${data.buildingName})` : address;
                    const coords = await this.geocodeAddress(address);

                    if (target === 'itinerary') {
                        if (!coords) {
                            this.error = '선택한 주소의 좌표를 찾지 못했습니다. 다른 주소로 시도해 주세요.';
                            return;
                        }
                        this.itinStart = { address: label, lat: coords.lat, lng: coords.lng };
                        await this.rememberStartLocation();
                        return;
                    }
                    const form = target === 'setup' ? this.setupForm
                        : target === 'user' ? this.userForm
                        : this.profileForm;
                    form.workplaceName = label;
                    form.workplaceLat = coords ? coords.lat : '';
                    form.workplaceLng = coords ? coords.lng : '';
                    if (!coords) {
                        this.error = '주소는 담았지만 좌표를 찾지 못했습니다 — 직주근접 점수는 산출되지 않습니다.';
                    }
                }
            }).open();
        },


        async rememberStartLocation() {
            await this.request('/api/itinerary/start-location', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    address: this.itinStart.address || null,
                    lat: toNum(this.itinStart.lat),
                    lng: toNum(this.itinStart.lng)
                })
            });
        },

        async loadStartLocation() {
            const { ok, body } = await this.request('/api/itinerary/start-location');
            if (ok && body && body.lat != null) {
                this.itinStart = { address: body.address || '', lat: body.lat, lng: body.lng };
            }
        },

        async geocodeAddress(address) {
            const { ok, body } = await this.request(`/api/geo/search?query=${encodeURIComponent(address)}`);
            if (ok && Array.isArray(body) && body.length > 0) {
                return { lat: body[0].lat, lng: body[0].lng };
            }
            return null;
        },

        async saveProfileSetup() {
            if (!this.setupForm.nickname || !this.setupForm.nickname.trim()) {
                this.error = '닉네임을 입력해 주세요';
                return;
            }
            if (!this.setupForm.workplaceLat || !this.setupForm.workplaceLng) {
                this.error = '주소 검색으로 직장 위치를 선택해 주세요';
                return;
            }
            if (!(toNum(this.setupForm.availableBudget) > 0)) {
                this.error = '보유 현금을 0보다 큰 값으로 입력해 주세요';
                return;
            }
            if (!(toNum(this.setupForm.annualIncome) > 0)) {
                this.error = '연소득을 0보다 큰 값으로 입력해 주세요';
                return;
            }
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/users/me/profile', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        nickname: this.setupForm.nickname,
                        workplaceName: this.setupForm.workplaceName,
                        workplaceLat: toNum(this.setupForm.workplaceLat),
                        workplaceLng: toNum(this.setupForm.workplaceLng),
                        availableBudget: toNum(this.setupForm.availableBudget),
                        annualIncome: toNum(this.setupForm.annualIncome),
                        existingLoan: toNum(this.setupForm.existingLoan) ?? 0
                    })
                });
                if (!ok) {
                    this.error = (body && body.message) || '프로필 저장에 실패했습니다';
                    return;
                }
                this.showProfileSetup = false;
                await this.checkSession();
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async saveProfile() {
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request('/api/users/me/profile', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        nickname: this.profileForm.nickname || null,
                        workplaceName: this.profileForm.workplaceName || null,
                        workplaceLat: toNum(this.profileForm.workplaceLat),
                        workplaceLng: toNum(this.profileForm.workplaceLng),
                        availableBudget: toNum(this.profileForm.availableBudget),
                        annualIncome: toNum(this.profileForm.annualIncome),
                        existingLoan: toNum(this.profileForm.existingLoan) ?? 0
                    })
                });
                if (ok) {
                    this.profile = body;
                    this.session.nickname = body.nickname;
                    await this.loadProperties();
                } else {
                    this.error = (body && body.message) || '프로필 저장에 실패했습니다';
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
        async loadRegulations() {
            const [reg, areas] = await Promise.all([
                this.request('/api/admin/regulations').catch(() => ({ ok: false })),
                this.request('/api/admin/regulated-areas').catch(() => ({ ok: false }))
            ]);
            if (reg.ok && reg.body) {
                this.applyRegulations(reg.body);
            }
            this.regAreas = areas.ok ? (areas.body || []) : [];
        },

        applyRegulations(body) {
            this.regActiveProfile = body.activeProfile;
            this.regProfiles = body.profiles || [];
            this.regParams = body.params || [];
            this.regParamForm = {};
            this.regParams.forEach(p => {
                this.regParamForm[p.id] = p.paramValue;
            });
        },

        async saveRegParams() {
            const changed = this.regParams
                .filter(p => String(this.regParamForm[p.id] ?? '') !== String(p.paramValue))
                .map(p => ({ id: p.id, paramValue: String(this.regParamForm[p.id] ?? '').trim() }));
            if (changed.length === 0) {
                this.regError = '바뀐 값이 없습니다';
                return;
            }
            this.loading = true;
            this.savingKey = 'regParams';
            this.regError = null;
            try {
                const { ok, body } = await this.request('/api/admin/regulations/params', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(changed)
                });
                if (ok) {
                    this.applyRegulations(body);
                    await this.loadProperties();
                } else {
                    this.regError = (body && body.message) || '저장에 실패했습니다';
                }
            } catch (e) {
                this.regError = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
                this.savingKey = null;
            }
        },

        async createRegProfile() {
            const profile = this.regNewProfile.trim();
            if (!profile) {
                return;
            }
            this.loading = true;
            this.regError = null;
            try {
                const { ok, body } = await this.request('/api/admin/regulations/profiles', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ profile, copyFrom: this.regActiveProfile, activate: false })
                });
                if (ok) {
                    this.regNewProfile = '';
                    this.applyRegulations(body);
                } else {
                    this.regError = (body && body.message) || '프로파일 생성에 실패했습니다';
                }
            } catch (e) {
                this.regError = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async activateRegProfile() {
            this.loading = true;
            this.regError = null;
            try {
                const { ok, body } = await this.request(
                    `/api/admin/regulations/profiles/${encodeURIComponent(this.regActiveProfile)}/activate`,
                    { method: 'PUT' });
                if (ok) {
                    this.applyRegulations(body);
                    await this.loadProperties();
                } else {
                    this.regError = (body && body.message) || '프로파일 전환에 실패했습니다';
                }
            } catch (e) {
                this.regError = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async addRegArea() {
            this.loading = true;
            this.regError = null;
            try {
                const { ok, body } = await this.request('/api/admin/regulated-areas', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        codePrefix: this.regAreaForm.codePrefix.trim(),
                        zone: this.regAreaForm.zone,
                        areaName: this.regAreaForm.areaName || null,
                        designatedOn: this.regAreaForm.designatedOn || null,
                        releasedOn: this.regAreaForm.releasedOn || null,
                        note: this.regAreaForm.note || null
                    })
                });
                if (ok) {
                    this.regAreas = body || [];
                    this.regAreaForm = emptyRegAreaForm();
                    await this.loadProperties();
                } else {
                    this.regError = (body && body.message) || '규제지역 등록에 실패했습니다';
                }
            } catch (e) {
                this.regError = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async deleteRegArea(area) {
            if (!confirm(`${area.areaName || area.codePrefix} 지정을 삭제할까요?`)) {
                return;
            }
            this.loading = true;
            this.regError = null;
            try {
                const { ok, body } = await this.request(
                    `/api/admin/regulated-areas/${area.id}`, { method: 'DELETE' });
                if (ok) {
                    this.regAreas = body || [];
                    await this.loadProperties();
                } else {
                    this.regError = (body && body.message) || '삭제에 실패했습니다';
                }
            } catch (e) {
                this.regError = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        comparableCount() {
            return this.pins.filter(p => p.active && !p.draft).length;
        },

        canCompare() {
            const min = this.compareStatus ? this.compareStatus.minProperties : COMPARE_MIN_PROPERTIES;
            if (this.comparableCount() < min) {
                return false;
            }
            return this.compareStatus ? this.compareStatus.analysable : true;
        },


        compareHint() {
            const min = this.compareStatus ? this.compareStatus.minProperties : COMPARE_MIN_PROPERTIES;
            const count = this.comparableCount();
            if (count < min) {
                return `매물이 ${min}건 이상이어야 비교할 수 있습니다 (현재 ${count}건)`;
            }
            if (this.compareStatus && !this.compareStatus.analysable) {
                return 'AI 분석을 사용할 수 없습니다. LLM 연동 설정을 확인해 주세요';
            }
            return '등록된 매물 전체를 견주어 순위를 매깁니다';
        },

        hasCompareResult() {
            return !!(this.compareStatus && this.compareStatus.rankings && this.compareStatus.rankings.length > 0);
        },

        async openCompare() {
            this.compareError = null;
            this.showCompare = true;
            await this.loadCompareStatus();
        },

        closeCompare() {
            this.showCompare = false;
            this.compareError = null;
        },

        async loadCompareStatus() {
            const { ok, body } = await this.request('/api/properties/comparative-analysis');
            if (ok) {
                this.compareStatus = body;
                this.compareRunning = !!body.pending;
            }
        },

        async runCompare() {
            if (!this.canCompare()) {
                this.compareError = this.compareHint();
                return;
            }
            this.compareRunning = true;
            this.compareError = null;
            try {
                const { ok, body } = await this.request('/api/properties/comparative-analysis', { method: 'POST' });
                if (ok) {
                    this.compareStatus = body;
                    await this.loadProperties();
                } else {
                    this.compareError = (body && body.message) || '분석에 실패했습니다';
                }
            } catch (e) {
                this.compareError = '네트워크 오류가 발생했습니다';
            } finally {
                this.compareRunning = false;
            }
        },
        openComments(item) {
            this.commentProperty = item;
            this.comments = [];
            this.commentNewText = '';
            this.commentEditingId = null;
            this.error = null;
            this.showComments = true;
            this.restoreCommentDraft();
            this.withLoading('comments', () => this.loadComments());
        },


        commentDraftKey() {
            const id = this.commentProperty?.property?.id;
            return id ? `halley.commentDraft.${id}` : null;
        },

        saveCommentDraft() {
            const key = this.commentDraftKey();
            if (!key) {
                return;
            }
            try {
                if (this.commentNewText.trim()) {
                    localStorage.setItem(key, this.commentNewText);
                } else {
                    localStorage.removeItem(key);
                }
            } catch (e) {
            }
        },

        restoreCommentDraft() {
            const key = this.commentDraftKey();
            if (!key) {
                return;
            }
            try {
                this.commentNewText = localStorage.getItem(key) || '';
            } catch (e) {
                this.commentNewText = '';
            }
        },

        clearCommentDraft() {
            const key = this.commentDraftKey();
            try {
                if (key) {
                    localStorage.removeItem(key);
                }
            } catch (e) {
            }
        },

        closeComments() {
            this.saveCommentDraft();
            this.showComments = false;
            this.commentProperty = null;
            this.comments = [];
            this.commentEditingId = null;
            this.error = null;
        },

        async loadComments() {
            const { ok, body } = await this.request(
                `/api/properties/${this.commentProperty.property.id}/comments`);
            this.comments = ok ? (body || []) : [];
        },


        get myComment() {
            return this.comments.find(c => c.mine) || null;
        },

        async addComment() {
            const content = this.commentNewText.trim();
            if (!content) {
                return;
            }
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request(
                    `/api/properties/${this.commentProperty.property.id}/comments`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ content })
                    });
                if (ok) {
                    this.commentNewText = '';
                    this.clearCommentDraft();
                    await this.loadComments();
                } else {
                    this.error = (body && body.message) || '코멘트 등록에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        startEditComment(comment) {
            this.commentEditingId = comment.id;
            this.commentEditText = comment.content;
            this.error = null;
        },

        cancelEditComment() {
            this.commentEditingId = null;
            this.commentEditText = '';
        },

        async saveEditComment(comment) {
            const content = this.commentEditText.trim();
            if (!content) {
                return;
            }
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request(
                    `/api/properties/${this.commentProperty.property.id}/comments/${comment.id}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ content })
                    });
                if (ok) {
                    this.commentEditingId = null;
                    await this.loadComments();
                } else {
                    this.error = (body && body.message) || '코멘트 수정에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        async removeComment(comment) {
            if (!confirm('이 코멘트를 삭제할까요?')) {
                return;
            }
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request(
                    `/api/properties/${this.commentProperty.property.id}/comments/${comment.id}`,
                    { method: 'DELETE' });
                if (ok) {
                    await this.loadComments();
                } else {
                    this.error = (body && body.message) || '코멘트 삭제에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },

        openDetail(item) {
            this.detailItem = item;
            this.detailAgents = [];
            this.detailRef = null;
            this.detailLlm = null;
            this.detailLandUse = [];
            this.detailImages = [];
            this.llmPending = false;
            this.stopLlmPolling();
            this.stopRefPolling();
            this.stopScorePolling();
            this.showM2 = true;
            this.withLoading('detail', () => this.loadDetailExtras(item.property.id));
        },
        async loadDetailExtras(propertyId) {
            const [agents, ref, llm, landUse, images] = await Promise.all([
                this.request(`/api/properties/${propertyId}/agents`).catch(() => ({ ok: false })),
                this.request(`/api/properties/${propertyId}/reference-transactions`).catch(() => ({ ok: false })),
                this.request(`/api/properties/${propertyId}/llm-recommendation`).catch(() => ({ ok: false })),
                this.request(`/api/properties/${propertyId}/land-use`).catch(() => ({ ok: false })),
                this.request(`/api/properties/${propertyId}/images`).catch(() => ({ ok: false }))
            ]);
            if (this.detailItem && this.detailItem.property.id !== propertyId) {
                return;
            }
            this.detailImages = images.ok ? (images.body || []) : [];
            this.detailAgents = agents.ok ? (agents.body || []) : [];
            this.detailRef = ref.ok ? ref.body : null;
            this.detailLlm = llm.ok && llm.body && llm.body.score != null ? llm.body : null;
            this.detailLandUse = landUse.ok ? (landUse.body || []) : [];
            this.llmPending = !this.detailLlm && !!(llm.ok && llm.body && llm.body.pending);
            if (this.llmPending) {
                this.startLlmPolling(propertyId);
            }
            if (this.detailRef?.looking) {
                this.startRefPolling(propertyId);
            }
            if (this.scoring(this.detailItem)) {
                this.startScorePolling(propertyId);
            }
        },


        startScorePolling(propertyId) {
            this.stopScorePolling();
            let attempts = 0;
            this._scoreTimer = setInterval(async () => {
                if (++attempts > SCORE_POLL_MAX_ATTEMPTS || !this.showM2
                        || !this.detailItem || this.detailItem.property.id !== propertyId) {
                    this.stopScorePolling();
                    return;
                }
                const { ok, body } = await this.request('/api/properties/' + propertyId)
                    .catch(() => ({ ok: false }));
                if (!ok || !body) {
                    return;
                }
                if (this.detailItem && this.detailItem.property.id === propertyId) {
                    this.detailItem = body;
                }
                if (!this.scoring(body)) {
                    this.stopScorePolling();
                }
            }, SCORE_POLL_INTERVAL_MS);
        },

        stopScorePolling() {
            if (this._scoreTimer) {
                clearInterval(this._scoreTimer);
                this._scoreTimer = null;
            }
        },


        startRefPolling(propertyId) {
            this.stopRefPolling();
            let attempts = 0;
            this._refTimer = setInterval(async () => {
                const gaveUp = ++attempts > REF_POLL_MAX_ATTEMPTS;
                if (gaveUp || !this.showM2
                        || !this.detailItem || this.detailItem.property.id !== propertyId) {
                    this.stopRefPolling();
                    if (gaveUp && this.detailRef
                            && this.detailItem && this.detailItem.property.id === propertyId) {
                        this.detailRef = { ...this.detailRef, looking: false, timedOut: true };
                    }
                    return;
                }
                const { ok, body } = await this.request(
                    `/api/properties/${propertyId}/reference-transactions`).catch(() => ({ ok: false }));
                if (!ok || !body) {
                    return;
                }
                if (this.detailItem && this.detailItem.property.id === propertyId) {
                    this.detailRef = body;
                }
                if (!body.looking) {
                    this.stopRefPolling();
                }
            }, REF_POLL_INTERVAL_MS);
        },


        async reloadReferences(propertyId) {
            const { ok, body } = await this.request(
                `/api/properties/${propertyId}/reference-transactions`).catch(() => ({ ok: false }));
            if (!ok || !body || !this.detailItem || this.detailItem.property.id !== propertyId) {
                return;
            }
            this.detailRef = body;
            if (body.looking) {
                this.startRefPolling(propertyId);
            }
        },

        stopRefPolling() {
            if (this._refTimer) {
                clearInterval(this._refTimer);
                this._refTimer = null;
            }
        },


        notableLandUse() {
            return this.detailLandUse.filter(l => l.notable);
        },


        landUseGroups() {
            const order = [
                { key: 'INCLUDED', label: '포함' },
                { key: 'OVERLAP', label: '저촉' },
                { key: 'ADJACENT', label: '접함' }
            ];
            return order
                .map(g => ({
                    ...g,
                    names: [...new Set(this.detailLandUse
                        .filter(l => l.conflict === g.key)
                        .map(l => l.zoneName))]
                }))
                .filter(g => g.names.length > 0);
        },

        async refreshLandUse() {
            const id = this.detailItem.property.id;
            this.loading = true;
            this.error = null;
            try {
                const { ok, body } = await this.request(
                    `/api/properties/${id}/land-use`, { method: 'POST' });
                if (ok) {
                    this.detailLandUse = body || [];
                    if (this.detailLandUse.length === 0) {
                        this.error = '토지이용계획을 받지 못했습니다. 좌표·주소와 V-World 키를 확인해 주세요';
                    }
                } else {
                    this.error = (body && body.message) || '토지이용계획 조회에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },


        startLlmPolling(propertyId) {
            this.stopLlmPolling();
            let attempts = 0;
            this._llmTimer = setInterval(async () => {
                if (!this.showM2 || !this.detailItem || this.detailItem.property.id !== propertyId) {
                    this.stopLlmPolling();
                    return;
                }
                if (++attempts > LLM_POLL_MAX_ATTEMPTS) {
                    this.llmPending = false;
                    this.stopLlmPolling();
                    return;
                }
                const { ok, body } = await this.request(
                    `/api/properties/${propertyId}/llm-recommendation`).catch(() => ({ ok: false }));
                if (!ok || !body) {
                    return;
                }
                if (body.score != null) {
                    this.detailLlm = body;
                    this.llmPending = false;
                    this.stopLlmPolling();
                    await this.loadProperties();
                    return;
                }
                if (!body.pending) {
                    this.llmPending = false;
                    this.stopLlmPolling();
                }
            }, LLM_POLL_INTERVAL_MS);
        },

        stopLlmPolling() {
            if (this._llmTimer) {
                clearInterval(this._llmTimer);
                this._llmTimer = null;
            }
        },

        closeDetail() {
            this.showM2 = false;
            this.detailItem = null;
            this.detailAgents = [];
            this.detailRef = null;
            this.detailLlm = null;
            this.detailLandUse = [];
            this.detailImages = [];
            this.llmPending = false;
            this.stopLlmPolling();
            this.stopRefPolling();
            this.stopScorePolling();
        },


        DRAG_THRESHOLD: 5,

        startPhotoDrag(event) {
            const strip = event.currentTarget;
            const startX = event.clientX;
            const startScroll = strip.scrollLeft;
            let moved = 0;

            const move = (e) => {
                const dx = e.clientX - startX;
                moved = Math.max(moved, Math.abs(dx));
                if (moved > this.DRAG_THRESHOLD) {
                    strip.classList.add('is-dragging');
                }
                strip.scrollLeft = startScroll - dx;
                e.preventDefault();
            };
            const up = () => {
                window.removeEventListener('pointermove', move);
                window.removeEventListener('pointerup', up);
                if (moved > this.DRAG_THRESHOLD) {
                    setTimeout(() => strip.classList.remove('is-dragging'), 0);
                } else {
                    strip.classList.remove('is-dragging');
                }
            };
            window.addEventListener('pointermove', move);
            window.addEventListener('pointerup', up);
        },


        scoring(scored) {
            if (!scored || (scored.scores || []).length > 0) {
                return false;
            }
            const createdAt = Date.parse(scored.property?.createdAt ?? '');
            if (Number.isNaN(createdAt)) {
                return true;
            }
            return Date.now() - createdAt < SCORING_GRACE_MS;
        },


        get detailFloorPlan() {
            return this.detailImages.find(i => i.imageType === 'FLOOR_PLAN') || null;
        },

        get detailPhotos() {
            return this.detailImages.filter(i => i.imageType === 'PHOTO');
        },

        async openPhotoModal(item) {
            this.photoProperty = item;
            this.photoImages = [];
            this.error = null;
            this.showPhotoModal = true;
            await this.withLoading('photos', () => this.loadPhotoImages());
        },

        closePhotoModal() {
            this.showPhotoModal = false;
            this.photoProperty = null;
            this.photoImages = [];
            this.photoViewerIndex = -1;
            this.error = null;
        },


        get floorPlan() {
            return this.photoImages.find(i => i.imageType === 'FLOOR_PLAN') || null;
        },

        get photos() {
            return this.photoImages.filter(i => i.imageType === 'PHOTO');
        },


        openPhotoViewer(index, images) {
            this.viewerImages = images || this.photoImages;
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
            if (this.photoViewerIndex < this.viewerImages.length - 1) {
                this.photoViewerIndex++;
            }
        },


        onViewerKey(direction) {
            if (!(this.photoViewerIndex >= 0)) {
                return;
            }
            if (direction < 0) {
                this.photoPrev();
            } else {
                this.photoNext();
            }
        },


        async refreshDetailImages() {
            const id = this.detailItem?.property?.id;
            if (!id || id !== this.photoProperty?.property?.id) {
                return;
            }
            const { ok, body } = await this.request(`/api/properties/${id}/images`)
                .catch(() => ({ ok: false }));
            if (ok) {
                this.detailImages = body || [];
            }
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


        async uploadImages(event, imageType) {
            const files = Array.from(event.target.files || []);
            if (files.length === 0) {
                return;
            }
            this.loading = true;
            this.error = null;
            try {
                for (const file of files) {
                    const form = new FormData();
                    form.append('file', file);
                    form.append('imageType', imageType);
                    const res = await fetch(`/api/properties/${this.photoProperty.property.id}/images`, withCsrf({
                        method: 'POST',
                        body: form
                    }));
                    if (!res.ok) {
                        this.error = await this.imageUploadFailureMessage(res, file.name);
                        break;
                    }
                }
                await this.loadPhotoImages();
                await this.refreshDetailImages();
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                event.target.value = '';
                this.loading = false;
            }
        },

        async imageUploadFailureMessage(response, fileName) {
            try {
                const body = await response.json();
                if (body?.message) {
                    return `${fileName}: ${body.message}`;
                }
            } catch (e) {
            }
            if (response.status === 413) {
                return `${fileName}: 사진 용량이 업로드 한도를 초과했습니다`;
            }
            return `${fileName} 업로드에 실패했습니다`;
        },

        async removeImage(image) {
            const label = image.imageType === 'FLOOR_PLAN' ? '평면도' : '매물사진';
            if (!confirm(`이 ${label}를 삭제할까요?`)) {
                return;
            }
            this.loading = true;
            this.error = null;
            try {
                const { ok } = await this.request(
                    `/api/properties/${this.photoProperty.property.id}/images/${image.id}`,
                    { method: 'DELETE' });
                if (ok) {
                    this.photoViewerIndex = -1;
                    await this.loadPhotoImages();
                    await this.refreshDetailImages();
                } else {
                    this.error = '삭제에 실패했습니다';
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


        rememberLoginId() {
            try {
                if (this.loginForm.rememberId) {
                    localStorage.setItem('halley.loginId', this.loginForm.loginId || '');
                } else {
                    localStorage.removeItem('halley.loginId');
                }
            } catch (e) {
            }
        },


        focusLogin() {
            setTimeout(() => {
                const id = document.getElementById('loginId');
                const pw = document.getElementById('loginPassword');
                const target = (this.loginForm.loginId || '').trim() ? (pw || id) : id;
                if (target) {
                    target.focus();
                    target.select?.();
                }
            }, 60);
        },

        restoreLoginId() {
            try {
                const saved = localStorage.getItem('halley.loginId');
                if (saved) {
                    this.loginForm.loginId = saved;
                }
            } catch (e) {
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
                    this.sessionExpiresAt = body.expiresInSeconds != null
                        ? Date.now() + body.expiresInSeconds * 1000 : null;
                    this.startSessionTimer();
                    this.rememberLoginId();
                    this.loginForm = {
                        loginId: this.loginForm.rememberId ? this.loginForm.loginId : '',
                        password: '',
                        rememberId: this.loginForm.rememberId,
                        rememberMe: this.loginForm.rememberMe
                    };
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
                    this.passwordForm = { currentPassword: '', newPassword: '' };
                    this.showPassword = false;
                    this.error = null;
                    await this.checkSession();
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
                await fetch('/api/auth/logout', withCsrf({ method: 'POST' }));
            } catch (e) {
            }
            this.stopSessionTimer();
            this.session = { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false };
            this.sessionExpiresAt = null;
            this.users = [];
            this.properties = [];
            this.pins = [];
            this.propertyTotal = 0;
            this.propertyHasNext = false;
            this.weights = [];
            this.view = 'list';
            this.dealTypeFilter = 'ALL';
            this.soldOutAlertShown = false;
            this.closeAllModals();
            this.showLogin = true;
            this.focusLogin();
            this.resetItineraryState();
        },


        clearItineraryResult() {
            this.itinResult = null;
            this.clearItinerary();
            this.error = null;
            this.saveItineraryDraft();
        },


        resetItineraryState() {
            this.clearItinerary();
            this.itinProperties = [];
            this.itinResult = null;
            this.itinStart = { address: '', lat: '', lng: '' };
            this.itinMode = 'DRIVING';
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
            if (this.sessionExpiresAt == null) {
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



        get archiveTab() {
            return this.dealTypeFilter === 'ARCHIVE';
        },


        listFilterParams() {
            const params = new URLSearchParams();
            if (this.archiveTab) {
                params.set('archived', 'true');
            } else if (this.dealTypeFilter !== 'ALL') {
                params.set('dealType', this.dealTypeFilter);
            }
            return params;
        },

        listFilterQuery() {
            const q = this.listFilterParams().toString();
            return q ? '?' + q : '';
        },

        propertiesUrl(page) {
            const params = this.listFilterParams();
            params.set('sort', this.sortKey);
            params.set('page', page);
            params.set('size', PAGE_SIZE);
            return '/api/properties?' + params.toString();
        },


        async loadProperties() {
            this.propertyPage = 0;
            const { ok, body } = await this.withLoading('properties',
                () => this.request(this.propertiesUrl(0)));
            if (ok && body) {
                this.properties = body.items || [];
                this.propertyTotal = body.total || 0;
                this.propertyHasNext = !!body.hasNext;
                this.archivedTotal = body.archivedTotal || 0;
            }
            await this.loadPins();
            this.renderMap();
        },


        async loadMoreProperties() {
            if (this.loadingMore || !this.propertyHasNext) {
                return;
            }
            this.loadingMore = true;
            try {
                const next = this.propertyPage + 1;
                const { ok, body } = await this.request(this.propertiesUrl(next));
                if (!ok || !body) {
                    return;
                }
                const seen = new Set(this.properties.map(r => r.property.id));
                const fresh = (body.items || []).filter(r => !seen.has(r.property.id));
                this.properties = [...this.properties, ...fresh];
                this.propertyPage = next;
                this.propertyTotal = body.total || 0;
                this.propertyHasNext = !!body.hasNext;
                this.archivedTotal = body.archivedTotal || 0;
            } finally {
                this.loadingMore = false;
            }
        },


        async loadPins() {
            const { ok, body } = await this.request('/api/properties/pins' + this.listFilterQuery());
            if (ok) {
                this.pins = body || [];
            }
        },



        startInfiniteScroll() {
            if (this._scrollObserver || typeof IntersectionObserver === 'undefined') {
                return;
            }
            const sentinel = document.getElementById('list-sentinel');
            if (!sentinel) {
                return;
            }
            this._scrollObserver = new IntersectionObserver(entries => {
                if (entries.some(e => e.isIntersecting)) {
                    this.loadMoreProperties();
                }
            }, { rootMargin: INFINITE_SCROLL_MARGIN_PX + 'px' });
            this._scrollObserver.observe(sentinel);
        },

        startScoreWatch() {
            if (this.scoreWatchTimer) {
                return;
            }
            this.scoreWatchTimer = setInterval(() => this.checkScoreVersions(), SCORE_WATCH_MS);
            document.addEventListener('visibilitychange', () => {
                if (!document.hidden) {
                    this.checkScoreVersions();
                }
            });
        },

        async checkScoreVersions() {
            if (document.hidden || !this.session.authenticated || this.properties.length === 0) {
                return;
            }
            const { ok, body } = await this.request(
                '/api/properties/score-versions' + this.listFilterQuery());
            if (!ok || !body) {
                return;
            }
            const latest = new Map(body.map(v => [v.propertyId, v.scoreVersion]));
            const changed = this.properties.some(
                r => latest.has(r.property.id) && latest.get(r.property.id) !== r.scoreVersion);
            const stillPending = this.properties.some(r => this.scoring(r));
            if (changed || stillPending || latest.size !== this.propertyTotal) {
                await this.loadProperties();
                if (this.detailItem) {
                    this.syncDetailItem();
                }
            }
        },


        syncDetailItem() {
            const fresh = this.properties.find(r => r.property.id === this.detailItem.property.id);
            if (fresh) {
                this.detailItem = fresh;
                if (this.showScoreModal && this.scoreProperty) {
                    this.scoreProperty = fresh;
                }
            }
        },


        SORTS: [
            { key: 'DEFAULT', label: '기본 (임장 전 · 추천점수)' },
            { key: 'PRICE', label: '매매가 낮은 순' },
            { key: 'AREA', label: '전용면적 넓은 순' },
            { key: 'SCORE', label: '추천점수 높은 순' },
            { key: 'COMMUTE', label: '직주근접 좋은 순' }
        ],

        sortLabel() {
            const found = this.SORTS.find(s => s.key === this.sortKey);
            return found ? found.label : this.SORTS[0].label;
        },


        async setSort(key) {
            this.sortKey = key;
            this.sortOpen = false;
            await this.loadProperties();
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


        archiveProperty(item) {
            this.askConfirm('아카이빙',
                `'${item.property.name}' 매물을 아카이빙할까요?\n목록에서는 사라지고 아카이빙 탭에 남습니다.`,
                () => this.setListingStatus(item.property.id, 'ARCHIVED'));
        },


        unarchiveProperty(item) {
            this.setListingStatus(item.property.id, 'ACTIVE');
        },

        async setListingStatus(id, listingStatus) {
            const { ok } = await this.request(`/api/properties/${id}/status`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ listingStatus })
            }).catch(() => ({ ok: false }));
            if (!ok) {
                this.error = '상태를 바꾸지 못했습니다';
                return;
            }
            await this.loadProperties();
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
            this.loanForm = { firstHome: false, mortgageInsured: true, ownedHouseCount: 0,
                rateType: 'VARIABLE' };
            this.loanOverride = { annualIncome: '', cash: '', existingLoan: '' };
            this.loanShowInputs = false;
            this.loanResult = null;
            this.error = null;
            this.showLoanModal = true;
            this.withLoading('loan', () => this.runLoanEstimate());
        },


        loanMonthlyAt(amount) {
            const r = this.loanResult;
            if (!r || !r.termMonths) {
                return 0;
            }
            const rate = r.monthlyRate || 0;
            if (rate === 0) {
                return Math.round(amount / r.termMonths);
            }
            return Math.round(amount * rate / (1 - Math.pow(1 + rate, -r.termMonths)));
        },


        loanOwnCapital() {
            const asking = this.loanResult?.askingPrice || 0;
            return Math.max(0, asking - this.loanAmount);
        },


        loanCashGap() {
            const need = this.loanOwnCapital() + (this.loanResult?.acquisitionTax || 0);
            return (this.loanResult?.usedCash || 0) - need;
        },

        loanPercent(part, whole) {
            if (!whole) {
                return 0;
            }
            return Math.min(100, Math.max(0, Math.round(part * 1000 / whole) / 10));
        },


        loanBindingLabel() {
            const r = this.loanResult;
            if (!r) {
                return '';
            }
            return r.dsrLimit <= r.ltvLimit ? '소득(DSR)이 한도를 정합니다' : '규제(LTV)가 한도를 정합니다';
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
                            annualIncome: toNum(this.loanOverride.annualIncome),
                            cash: toNum(this.loanOverride.cash),
                            existingLoan: toNum(this.loanOverride.existingLoan),
                            firstHome: this.loanForm.firstHome,
                            mortgageInsured: this.loanForm.mortgageInsured,
                            ownedHouseCount: this.loanForm.ownedHouseCount,
                            rateType: this.loanForm.rateType
                        })
                    });
                if (ok) {
                    this.loanResult = body;
                    this.loanAmount = body.finalLimit || 0;
                } else {
                    this.error = (body && body.message) || '계산에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
            }
        },


        refEmptyReason() {
            const card = this.refCard;
            if (!card) {
                return '';
            }
            if (card.looking) {
                return '국토부 실거래를 받아 오는 중입니다…';
            }
            if (!card.lawdCd) {
                return '지번주소에서 법정동코드를 찾지 못해 조회하지 못했습니다. 코드를 직접 넣어 보세요.';
            }
            if (card.fetched === 0) {
                return `${card.lawdCd} 지역의 그 기간에 국토부 신고 자료가 없습니다.`
                    + ' 계약년월이 미래이거나 아직 신고 전일 수 있습니다.';
            }
            const name = this.refProperty?.property?.name || '이 매물';
            if (card.nameMatched === 0) {
                return `${card.fetched}건을 받았지만 '${name}'과 이름이 맞는 거래가 없습니다.`
                    + ' 국토부 표기가 다를 수 있습니다 (예: 상계주공7단지 ↔ 상계주공7(고층)).';
            }
            const area = this.refProperty?.property?.areaExclusiveM2;
            return `${card.fetched}건 중 이름이 맞는 거래는 ${card.nameMatched}건이지만,`
                + ` 전용면적 ${area ? area + '㎡' : '(미상)'} 과 맞는 것이 없습니다.`
                + ' 같은 단지라도 평형이 다르면 제외됩니다.';
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
            this.saveItineraryDraft();
        },


        todayIso() {
            return todayIso();
        },


        minItinTime() {
            if (this.itinDate !== this.todayIso()) {
                return '00:00';
            }
            const now = new Date();
            return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
        },


        itinDepartsInPast() {
            if (!this.itinDate) {
                return false;
            }
            const departAt = new Date(`${this.itinDate}T${this.itinWindowStart || '09:00'}`);
            return !Number.isNaN(departAt.getTime()) && departAt.getTime() < Date.now();
        },


        normalizeItinStart() {
            if (!this.itinDepartsInPast()) {
                return;
            }
            const next = new Date();
            next.setSeconds(0, 0);
            next.setMinutes(next.getMinutes() + (15 - (next.getMinutes() % 15)));
            const local = new Date(next.getTime() - next.getTimezoneOffset() * 60000).toISOString();
            this.itinDate = local.slice(0, 10);
            this.itinWindowStart = local.slice(11, 16);
        },

        async optimizeItinerary() {
            if (this.itinDepartsInPast()) {
                this.error = '임장 날짜와 시작시간이 이미 지났습니다. 앞으로의 시각으로 골라 주세요';
                return;
            }
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
                        startLng: toNum(this.itinStart.lng),
                        visitDate: this.itinDate || null,
                        windowStart: this.itinWindowStart || null,
                        stayMinutes: toNum(this.itinStay)
                    })
                });
                if (ok) {
                    if (body?.status === 'UNAVAILABLE') {
                        this.clearItineraryResult();
                        this.itinUnavailableMessage = body.message;
                        this.showItinUnavailable = true;
                        return;
                    }
                    this.itinResult = body;
                    this.saveItineraryDraft();
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


        async toggleVisited(propertyId) {
            const visited = !this.itinVisited.includes(propertyId);
            this.setVisited(propertyId, visited);
            const { ok } = await this.request(`/api/itinerary/visits/${propertyId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ visited })
            }).catch(() => ({ ok: false }));
            if (!ok) {
                this.setVisited(propertyId, !visited);
                this.error = '방문 기록을 저장하지 못했습니다';
            }
        },

        setVisited(propertyId, visited) {
            this.itinVisited = visited
                ? [...this.itinVisited.filter(id => id !== propertyId), propertyId]
                : this.itinVisited.filter(id => id !== propertyId);
            this.pins = this.pins.map(p => p.id === propertyId ? { ...p, visited } : p);
            if (this.sortKey === 'DEFAULT') {
                this.loadProperties();
            }
        },


        isVisited(propertyId) {
            if (this.itinVisited.includes(propertyId)) {
                return true;
            }
            const pin = this.pins.find(p => p.id === propertyId);
            return !!(pin && pin.visited);
        },


        visitedByComfort(propertyId) {
            const pin = this.pins.find(p => p.id === propertyId);
            return !!(pin && pin.visitedByComfort);
        },


        async loadVisited() {
            const { ok, body } = await this.request('/api/itinerary/visits')
                .catch(() => ({ ok: false }));
            if (ok && Array.isArray(body)) {
                this.itinVisited = body;
            }
        },


        propertyName(id) {
            const pin = this.pins.find(p => p.id === id);
            if (pin) {
                return pin.name;
            }
            const item = this.properties.find(x => x.property.id === id);
            return item ? item.property.name : '#' + id;
        },


        async loadItineraryDraft() {
            const { ok, body } = await this.request('/api/itinerary/draft').catch(() => ({ ok: false }));
            if (ok && body) {
                this.itinProperties = body.propertyIds || [];
                this.itinMode = body.travelMode || 'DRIVING';
                this.itinResult = body.result || null;
            }
            this.renderItinerary();
        },


        saveItineraryDraft() {
            this.request('/api/itinerary/draft', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    propertyIds: this.itinProperties,
                    travelMode: this.itinMode,
                    result: this.itinResult
                })
            }).catch(() => {});
        },

        renderItinerary() {
            if (typeof kakao === 'undefined' || !kakao.maps || !this.map) {
                return;
            }
            this.clearItinerary();
            const ids = this.itinResult ? this.itinResult.orderedPropertyIds : [];
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
            this.drawItineraryPath(points);
        },



        SUBWAY_COLORS: {
            1: '#0052A4', 2: '#00A84D', 3: '#EF7C1C', 4: '#00A5DE', 5: '#996CAC',
            6: '#CD7C2F', 7: '#747F00', 8: '#E6186C', 9: '#BDB092',
            21: '#7CA8D5', 22: '#ED8B00',
            101: '#0090D2', 104: '#77C4A3', 107: '#6FB245', 108: '#0C8E72',
            109: '#D31145', 110: '#FDA600', 111: '#003DA5', 112: '#B7C452',
            113: '#8FC63F', 114: '#A17E46', 115: '#FABE00', 116: '#6789CA',
            117: '#9A6292'
        },


        TRAFFIC_COLORS: { 1: '#d64545', 2: '#e08b2f', 3: '#e0c22f', 4: '#3f9e56', 0: '#8a8378' },


        segmentStyle(style) {
            const [kind, raw] = String(style || '').split('_');
            const code = Number(raw);
            if (kind === 'SUBWAY') {
                return { color: this.SUBWAY_COLORS[code] || '#5a6b7a', weight: 6, dash: 'solid' };
            }
            if (kind === 'BUS') {
                const bus = { 1: '#3d5bab', 2: '#3d5bab', 3: '#53b332', 4: '#e0332a',
                              5: '#53b332', 6: '#aa9872', 11: '#3d5bab', 12: '#53b332',
                              13: '#53b332', 14: '#e0332a', 15: '#f99d1c' };
                return { color: bus[code] || '#53b332', weight: 6, dash: 'solid' };
            }
            if (kind === 'TRAFFIC') {
                return { color: this.TRAFFIC_COLORS[code] || '#8a8378', weight: 7, dash: 'solid' };
            }
            return { color: '#5a6b7a', weight: 5, dash: 'solid' };
        },


        drawItineraryPath(fallbackPoints) {
            const legs = (this.itinResult && this.itinResult.legs) || [];
            const bounds = new kakao.maps.LatLngBounds();
            this._itinPolylines = [];


            const add = (path, color, weight, dash) => {
                path.forEach(pt => bounds.extend(pt));
                const outline = new kakao.maps.Polyline({
                    path,
                    strokeWeight: weight + 4,
                    strokeColor: '#1c1c1c',
                    strokeOpacity: 0.55,
                    strokeStyle: dash,
                    zIndex: 1
                });
                outline.setMap(this.map);
                this._itinPolylines.push(outline);
                const line = new kakao.maps.Polyline({
                    path,
                    strokeWeight: weight,
                    strokeColor: color,
                    strokeOpacity: 1,
                    strokeStyle: dash,
                    zIndex: 2
                });
                line.setMap(this.map);
                this._itinPolylines.push(line);
            };

            const drawn = legs.filter(l => (l.path || []).some(seg => (seg.points || []).length >= 2));
            drawn.forEach(leg => {
                let previousEnd = null;
                (leg.path || []).forEach(seg => {
                    const points = (seg.points || []);
                    if (points.length < 2) {
                        return;
                    }
                    const path = points.map(p => new kakao.maps.LatLng(p.lat, p.lng));
                    if (previousEnd) {
                        add([previousEnd, path[0]], '#8a8378', 3, 'shortdash');
                    }
                    const style = this.segmentStyle(seg.style);
                    add(path, style.color, style.weight, style.dash);
                    previousEnd = path[path.length - 1];
                });
            });
            if (drawn.length < legs.length || legs.length === 0) {
                if (fallbackPoints.length >= 2) {
                    add(fallbackPoints, '#8a8378', 3, 'shortdash');
                }
            }
            if (this._itinPolylines.length > 0) {
                this.map.setBounds(bounds);
            }
        },


        SUBWAY_NAMES: {
            21: '인천1호선', 22: '인천2호선',
            101: '공항철도', 104: '경의중앙선', 107: '에버라인', 108: '경춘선',
            109: '신분당선', 110: '의정부경전철', 111: '경강선', 112: '우이신설선',
            113: '서해선', 114: '김포골드라인', 115: '수인분당선', 116: '신림선',
            117: 'GTX-A'
        },


        transitLegend() {
            const seen = {};
            ((this.itinResult && this.itinResult.legs) || []).forEach(leg => {
                const rides = (leg.path || []).filter(seg => /^(SUBWAY|BUS)_/.test(seg.style || ''));
                const named = (leg.steps || []).filter(s => s.kind === 'SUBWAY' || s.kind === 'BUS');
                const aligned = rides.length === named.length;
                rides.forEach((seg, i) => {
                    const color = this.segmentStyle(seg.style).color;
                    seen[color] = aligned ? this.rideName(named[i]) : this.styleName(seg.style);
                });
            });
            seen['#8a8378'] = '도보·직선';
            return Object.keys(seen).map(color => ({ color, name: seen[color] }));
        },


        rideName(step) {
            if (!step || !step.lineName) {
                return step && step.kind === 'BUS' ? '버스' : '지하철';
            }
            return step.kind === 'BUS' ? `${step.lineName}번 버스` : step.lineName;
        },


        styleName(style) {
            const [kind, raw] = String(style || '').split('_');
            const code = Number(raw);
            return kind === 'SUBWAY'
                ? (this.SUBWAY_NAMES[code] || `${code}호선`)
                : '버스';
        },

        stepIcon(step) {
            switch (step.kind) {
                case 'SUBWAY': return '🚇';
                case 'BUS': return '🚌';
                case 'ROAD': return '🚗';
                default: return '🚶';
            }
        },


        legStepText(step) {
            if (step.kind === 'WALK') {
                return `도보 ${step.minutes}분`;
            }
            if (step.kind === 'ROAD') {
                const km = (step.stationCount || 0) / 1000;
                return `${step.lineName} ${km.toFixed(1)}km`;
            }
            const name = step.lineName || (step.kind === 'BUS' ? '버스' : '지하철');
            const suffix = step.kind === 'BUS' ? '번 버스' : '';
            const stations = step.stationCount ? ` · ${step.stationCount}정거장` : '';
            return `${name}${suffix} ${step.from} → ${step.to} ${step.minutes}분${stations}`;
        },



        arrivalAt(index) {
            const start = String(this.itinWindowStart || '09:00').split(':');
            const stay = Number(this.itinStay) || 0;
            let minutes = Number(start[0]) * 60 + Number(start[1]);
            for (let i = 0; i <= index; i++) {
                const leg = this.legFor(i);
                if (leg && leg.minutes == null) {
                    return null;
                }
                minutes += leg ? leg.minutes : 0;
                if (i < index) {
                    minutes += stay;
                }
            }
            const days = Math.floor(minutes / 1440);
            const h = Math.floor(minutes / 60) % 24;
            const m = minutes % 60;
            const clock = `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
            return days > 0 ? `${clock} (+${days}일)` : clock;
        },

        closeItinUnavailable() {
            this.showItinUnavailable = false;
        },


        legMinutesLabel(leg) {
            return leg && leg.minutes != null ? `${leg.minutes}분` : '이동시간 미확인';
        },


        itinTotalNote() {
            const unknown = this.itinResult?.unknownLegs || 0;
            return unknown > 0
                ? `구간 ${unknown}개는 이동시간을 받지 못했습니다 — 합계에 빠져 있습니다`
                : '';
        },


        legFor(index) {
            return (this.itinResult && this.itinResult.legs && this.itinResult.legs[index]) || null;
        },

        legTitle(leg) {
            const to = this.properties.find(x => x.property.id === leg.toPropertyId);
            const from = leg.fromPropertyId
                ? this.properties.find(x => x.property.id === leg.fromPropertyId)
                : null;
            const fromName = from ? from.property.name : '출발지';
            return `${fromName} → ${to ? to.property.name : ''}`;
        },

        clearItinerary() {
            (this._itinPolylines || []).forEach(l => l.setMap(null));
            this._itinPolylines = [];
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

        openPasteModal(item) {
            this.closeAddMenu();
            this.pasteDraftId = item ? item.property.id : null;
            this.pasteDraftName = item ? item.property.name : null;
            this.showPasteModal = true;
            this.pasteText = '';
            this.pastePreview = null;
            this.pasteForm = {};
            this.pasteError = null;
            this.pasteFloorPlan = null;
            this.pastePhotos = [];
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
            this.pasteFloorPlan = null;
            this.pastePhotos = [];
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
                    const failed = await this.uploadPastedImages(body?.property?.id);
                    this.showPasteModal = false;
                    this.pasteText = '';
                    this.pastePreview = null;
                    this.pasteForm = {};
                    this.pasteDraftId = null;
                    this.pasteDraftName = null;
                    this.pasteFloorPlan = null;
                    this.pastePhotos = [];
                    await this.loadProperties();
                    if (failed.length > 0) {
                        this.error = `매물은 저장했지만 사진 ${failed.length}장을 올리지 못했습니다`
                            + ' — 매물 상세에서 다시 올려 주세요';
                    }
                } else {
                    this.pasteError = (body && body.message) || '등록에 실패했습니다';
                }
            } catch (e) {
                this.pasteError = '네트워크 오류가 발생했습니다';
            } finally {
                this.pasteParsing = false;
            }
        },


        pastePickedCount() {
            return (this.pasteFloorPlan ? 1 : 0) + this.pastePhotos.length;
        },

        pickPasteImages(event, imageType) {
            const files = Array.from(event.target.files || []);
            if (imageType === 'FLOOR_PLAN') {
                this.pasteFloorPlan = files[0] || null;
            } else {
                this.pastePhotos = [...this.pastePhotos, ...files];
            }
            event.target.value = '';
        },

        dropPastedImage(file) {
            if (this.pasteFloorPlan === file) {
                this.pasteFloorPlan = null;
                return;
            }
            this.pastePhotos = this.pastePhotos.filter(f => f !== file);
        },


        async uploadPastedImages(propertyId) {
            const id = propertyId || this.pasteDraftId;
            const queued = [
                ...(this.pasteFloorPlan ? [[this.pasteFloorPlan, 'FLOOR_PLAN']] : []),
                ...this.pastePhotos.map(f => [f, 'PHOTO'])
            ];
            if (!id || queued.length === 0) {
                return [];
            }
            const failed = [];
            for (const [file, imageType] of queued) {
                const form = new FormData();
                form.append('file', file);
                form.append('imageType', imageType);
                const res = await fetch(`/api/properties/${id}/images`, withCsrf({ method: 'POST', body: form }))
                    .catch(() => ({ ok: false }));
                if (!res.ok) {
                    failed.push(file);
                }
            }
            return failed;
        },

        buildPasteRequest() {
            const value = (k) => (this.pasteForm[k] != null ? String(this.pasteForm[k]).trim() : '');
            const dealCode = { 매매: 'SALE', 전세: 'JEONSE' }[value('dealType')] || null;
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
                dongHo: value('dongHo') || null,
                dealType: dealCode,
                priceDeposit: toNum(value('priceDeposit')),
                kbPrice: toNum(value('kbPrice')),
                areaSupplyM2: toNum(value('areaSupplyM2')),
                areaExclusiveM2: toNum(value('areaExclusiveM2')),
                floorRaw: floor[0] || null,
                floorTotal: toNum(floor[1]),
                direction: value('direction') || null,
                addressJibun: value('addressJibun') || null,
                approvalYear: toNum(value('approvalYear')),
                totalHouseholds: toNum(value('totalHouseholds')),
                parkingPerHousehold: toNum(value('parkingPerHousehold')),
                moveInType,
                moveInDate,
                naverArticleNo: value('naverArticleNo') || null,
                sourceUrl: value('sourceUrl') || null,
                maintenanceFee: toNum(value('maintenanceFee')),
                roomBath: value('roomBath') || null,
                heatingType: value('heatingType') || null,
                brokerageFee: toNum(value('brokerageFee')),
                brokerageRate: toNum(value('brokerageRate')),
                acquisitionTax: toNum(value('acquisitionTax')),
                propertyTax: toNum(value('propertyTax')),
                comprehensiveTax: value('comprehensiveTax') || null,
                schoolName: value('school') || null,
                schoolWalkMinutes: toNum(value('schoolMinutes')),
                agent: {
                    officeName: value('agentOfficeName') || null,
                    agentName: value('agentName') || null,
                    phone: value('agentPhone') || null,
                    mobile: value('agentMobile') || null,
                    registrationNo: value('agentRegistrationNo') || null,
                    address: value('agentAddress') || null,
                    lat: null,
                    lng: null
                },
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
                priceDeposit: '매매가/보증금', kbPrice: 'KB시세', maintenanceFee: '관리비',
                areaSupplyM2: '공급면적', areaExclusiveM2: '전용면적', floor: '해당층/총층',
                roomBath: '방/욕실', direction: '향', heatingType: '난방',
                addressJibun: '지번주소', approvalYear: '사용승인년도',
                totalHouseholds: '세대수', parkingPerHousehold: '주차(세대당)', moveIn: '입주가능일',
                subway: '지하철', subwayMinutes: '역 도보(분)',
                school: '배정 초등학교', schoolMinutes: '학교 도보(분)',
                agentName: '중개인', agentOfficeName: '중개사무소', agentPhone: '중개사 전화',
                agentMobile: '중개사 휴대폰', agentAddress: '중개사 위치', agentRegistrationNo: '등록번호',
                brokerageFee: '중개보수(상한액)', brokerageRate: '상한 요율',
                acquisitionTax: '취득세 합계', propertyTax: '재산세 합계', comprehensiveTax: '종합부동산세'
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
                dongHo: p.dongHo || '',
                dealType: p.dealType || 'SALE',
                priceDeposit: p.priceDeposit ?? '',
                maintenanceFee: p.maintenanceFee ?? '',
                kbPrice: p.kbPrice ?? '',
                addressRoad: p.addressRoad || '',
                sourceUrl: p.sourceUrl || '',
                addressJibun: p.addressJibun || '',
                lat: p.lat ?? '',
                lng: p.lng ?? '',
                areaSupplyM2: p.areaSupplyM2 ?? '',
                areaExclusiveM2: p.areaExclusiveM2 ?? '',
                floorRaw: floorText(p),
                floorTotal: p.floorTotal ?? '',
                direction: p.direction || '',
                approvalYear: p.approvalYear ?? '',
                buildingCount: p.buildingCount ?? '',
                totalHouseholds: p.totalHouseholds ?? '',
                parkingPerHousehold: p.parkingPerHousehold ?? '',
                moveInType: p.moveInType || '',
                moveInDate: p.moveInDate || '',
                editVersion: p.editVersion ?? null,
                carry: {
                    roomBath: p.roomBath ?? null,
                    heatingType: p.heatingType ?? null,
                    brokerageFee: p.brokerageFee ?? null,
                    brokerageRate: p.brokerageRate ?? null,
                    acquisitionTax: p.acquisitionTax ?? null,
                    propertyTax: p.propertyTax ?? null,
                    comprehensiveTax: p.comprehensiveTax ?? null,
                    schoolName: p.schoolName ?? null,
                    schoolWalkMinutes: p.schoolWalkMinutes ?? null
                }
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
            this.propertyAddrError = null;
            this.propertyAddrResults = [];
            try {
                const { ok, body } = await this.request('/api/geo/search?query=' + encodeURIComponent(query));
                if (!ok) {
                    this.propertyAddrError = (body && body.message) || '주소 검색에 실패했습니다';
                    return;
                }
                this.propertyAddrResults = body || [];
                if (this.propertyAddrResults.length === 0) {
                    this.propertyAddrError =
                        '검색 결과가 없습니다. 지번 주소로 입력해 보세요 (예: 서울 강남구 대치동 316)';
                }
            } catch (e) {
                this.propertyAddrError = '네트워크 오류가 발생했습니다';
            }
        },

        selectPropertyAddress(r) {
            this.propertyForm.addressRoad = r.roadAddressName || r.addressName || '';
            this.propertyForm.addressJibun = r.addressName || '';
            this.propertyForm.lat = r.lat != null ? String(r.lat) : '';
            this.propertyForm.lng = r.lng != null ? String(r.lng) : '';
            this.propertyQuery = r.addressName || '';
            this.propertyAddrResults = [];
            this.propertyAddrError = null;
        },

        closePropertyForm() {
            this.showPropertyForm = false;
            this.error = null;
        },

        async saveProperty() {
            this.loading = true;
            this.error = null;
            const body = {
                ...(this.propertyForm.carry || {}),
                name: this.propertyForm.name,
                dongHo: this.propertyForm.dongHo || null,
                dealType: this.propertyForm.dealType,
                priceDeposit: toNum(this.propertyForm.priceDeposit),
                maintenanceFee: toNum(this.propertyForm.maintenanceFee),
                kbPrice: toNum(this.propertyForm.kbPrice),
                addressRoad: this.propertyForm.addressRoad || null,
                addressJibun: this.propertyForm.addressJibun || null,
                sourceUrl: this.propertyForm.sourceUrl || null,
                lat: toNum(this.propertyForm.lat),
                lng: toNum(this.propertyForm.lng),
                areaSupplyM2: toNum(this.propertyForm.areaSupplyM2),
                areaExclusiveM2: toNum(this.propertyForm.areaExclusiveM2),
                floorRaw: this.propertyForm.floorRaw || null,
                floorTotal: toNum(this.propertyForm.floorTotal),
                direction: this.propertyForm.direction || null,
                approvalYear: toNum(this.propertyForm.approvalYear),
                buildingCount: toNum(this.propertyForm.buildingCount),
                totalHouseholds: toNum(this.propertyForm.totalHouseholds),
                parkingPerHousehold: toNum(this.propertyForm.parkingPerHousehold),
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

        async openScoreModal(item) {
            this.applyScoreForm(item);
            this.error = null;
            this.showScoreModal = true;
            const fresh = await this.withLoading('score',
                () => this.request(`/api/properties/${item.property.id}`).catch(() => ({ ok: false })));
            if (!this.showScoreModal || this.scoreProperty?.property?.id !== item.property.id) {
                return;
            }
            if (fresh.ok && fresh.body) {
                this.applyScoreForm(fresh.body);
            }
        },


        closeAllModals() {
            Object.keys(this).forEach(key => {
                if (key.startsWith('show') && this[key] === true) {
                    this[key] = false;
                }
            });
            this.confirmState = null;
            this.photoViewerIndex = -1;
            this.resetModalScroll();
        },


        resetModalScroll() {
            document.querySelectorAll('.modal-card').forEach(card => {
                card.scrollTop = 0;
            });
        },


        watchModalOpen() {
            const hidden = new WeakSet();
            const modals = document.querySelectorAll('.modal');
            modals.forEach(modal => hidden.add(modal));
            const observer = new MutationObserver(records => {
                records.forEach(r => {
                    const modal = r.target;
                    if (modal.style.display === 'none') {
                        hidden.add(modal);
                        return;
                    }
                    if (!hidden.has(modal)) {
                        return;
                    }
                    hidden.delete(modal);
                    modal.querySelectorAll('.modal-card').forEach(card => {
                        card.scrollTop = 0;
                    });
                });
            });
            modals.forEach(modal => {
                observer.observe(modal, { attributes: true, attributeFilter: ['style'] });
            });
        },


        closeTopModal() {
            const stack = [
                ['photoViewerIndex', () => this.closePhotoViewer()],
                ['showUserForm', () => this.closeUserForm()],
                ['showAddMenu', () => this.closeAddMenu()],
                ['confirmState', () => this.confirmNo()],
                ['showForecast', () => this.closeForecast()],
                ['showScoreModal', () => this.closeScoreModal()],
                ['showLoanModal', () => this.closeLoanModal()],
                ['showRefModal', () => this.closeRefModal()],
                ['showComments', () => this.closeComments()],
                ['showAgentModal', () => this.closeAgentModal()],
                ['showPhotoModal', () => this.closePhotoModal()],
                ['showRoadview', () => this.closeRoadview()],
                ['showPasteModal', () => this.closePasteModal()],
                ['showPropertyForm', () => this.closePropertyForm()],
                ['showM2', () => this.closeDetail()],
                ['showCompare', () => this.closeCompare()],
                ['showItinUnavailable', () => this.closeItinUnavailable()],
                ['showSoldOutAlert', () => this.closeSoldOutAlert()],
                ['showUsers', () => this.closeUsers()],
                ['showSettings', () => this.closeSettings()],
                ['showChangePw', () => this.closeChangePw()],
            ];
            for (const [flag, close] of stack) {
                const open = flag === 'photoViewerIndex' ? this[flag] >= 0 : !!this[flag];
                if (open) {
                    close();
                    return;
                }
            }
        },



        forecastArrow(scored) {
            const f = scored?.forecast;
            if (!f) {
                return '';
            }
            if (f.running) {
                return '◌';
            }
            if (!f.stored) {
                return '';
            }
            if (f.noSignal) {
                return '🤔 ▶';
            }
            return this.arrowOf(f.direction) || '▶';
        },


        forecastVerdictLabel(direction, directionLabel) {
            return (direction === 'UP' || direction === 'DOWN')
                ? directionLabel
                : '판단 보류';
        },


        forecastVerdictOf(f) {
            return this.forecastVerdictLabel(f.direction, this.DIRECTION_LABEL[f.direction]);
        },

        arrowOf(direction) {
            switch (direction) {
                case 'UP': return '▲';
                case 'DOWN': return '▼';
                case 'FLAT': case 'UNCERTAIN': return '▶';
                default: return '';
            }
        },


        arrowClassOf(direction) {
            switch (direction) {
                case 'UP': return 'up';
                case 'DOWN': return 'down';
                case 'FLAT': case 'UNCERTAIN': return 'flat';
                default: return '';
            }
        },

        forecastArrowClass(scored) {
            const f = scored?.forecast;
            if (f?.running) {
                return 'running';
            }
            if (f?.noSignal) {
                return 'note';
            }
            return this.arrowClassOf(f?.direction) || 'flat';
        },


        canTriggerForecast(scored) {
            const f = scored?.forecast;
            if (!f || f.running) {
                return false;
            }
            return !f.stored || f.direction === 'UNCERTAIN' || f.direction === 'FLAT';
        },

        forecastPriceTitle(scored) {
            if (!this.canTriggerForecast(scored)) {
                return '';
            }
            return scored.forecast.stored
                ? '판단을 보류한 전망입니다. 클릭하면 다시 분석합니다 (1~2분)'
                : '클릭하면 가격 전망을 분석합니다 (1~2분)';
        },


        async triggerForecast(scored) {
            if (!this.canTriggerForecast(scored)) {
                return;
            }
            const id = scored.property.id;
            scored.forecast.running = true;
            this.request(`/api/properties/${id}/forecast/refresh`, { method: 'POST' })
                .catch(() => {});
            this.startForecastPolling(id);
        },


        noModalOpen() {
            return !this.showForecast && !this.showM2 && !this.showPropertyForm
                && !this.showLoanModal && !this.showRefModal && !this.showComments
                && !this.showCompare && !this.showSettings && !this.showUsers;
        },


        startForecastPolling(propertyId) {
            this.stopForecastPolling();
            let attempts = 0;
            this._forecastTimer = setInterval(async () => {
                if (++attempts > FORECAST_POLL_MAX_ATTEMPTS) {
                    this.stopForecastPolling();
                    await this.loadProperties();
                    return;
                }
                await this.loadProperties();
                const found = (this.properties || []).find(r => r.property.id === propertyId);
                if (!found || !found.forecast?.running) {
                    this.stopForecastPolling();

                    if (found?.forecast?.stored && this.noModalOpen()) {
                        await this.openForecast(found);
                    }
                }
            }, FORECAST_POLL_INTERVAL_MS);
        },

        stopForecastPolling() {
            if (this._forecastTimer) {
                clearInterval(this._forecastTimer);
                this._forecastTimer = null;
            }
        },


        DIRECTION_LABEL: { UP: '상승', DOWN: '하락', FLAT: '유지', UNCERTAIN: '판단 보류' },

        forecastTitle(scored) {
            const f = scored?.forecast;
            if (!f) {
                return '';
            }
            if (f.running) {
                return '가격 전망을 분석 중입니다…';
            }
            if (f.noSignal) {
                return '방향을 가리키는 지표가 없습니다 — 이유 보기';
            }
            return '가격 전망: ' + this.forecastVerdictOf(f)
                + (f.confidenceLabel ? ' (확신도 ' + f.confidenceLabel + ')' : '');
        },

        async openForecast(scored) {
            this.forecastProperty = scored;
            this.forecastDetail = null;
            this.forecastNews = [];
            this.error = null;
            this.showForecast = true;
            const { ok, body } = await this.withLoading('forecast',
                () => this.request(`/api/properties/${scored.property.id}/forecast`));
            if (ok && body) {
                this.forecastDetail = body;
            }
            this.loadForecastNews(scored.property.id);
        },

        async loadForecastNews(propertyId) {
            const { ok, body } = await this.request(`/api/properties/${propertyId}/news`)
                .catch(() => ({ ok: false }));
            if (ok && body && this.forecastProperty?.property?.id === propertyId) {
                this.forecastNews = body;
            }
        },

        closeForecast() {
            this.showForecast = false;
            this.forecastProperty = null;
            this.forecastDetail = null;
            this.forecastNews = [];
            this.error = null;
        },


        async refreshForecast() {
            const id = this.forecastProperty?.property?.id;
            if (!id) {
                return;
            }
            const { ok, body } = await this.withLoading('forecastRefresh',
                () => this.request(`/api/properties/${id}/forecast/refresh`, { method: 'POST' }));
            if (ok && body) {
                this.forecastDetail = body;
                await this.loadProperties();
            } else {
                this.error = '다시 분석하지 못했습니다';
            }
        },



        forecastCompareNote() {
            const d = this.forecastDetail;
            if (!d || !d.codeDirection) {
                return '';
            }
            const code = this.DIRECTION_LABEL[d.codeDirection] || d.codeDirection;
            const said = d.llmDirection;
            if (!said) {
                return `규칙 기반 계산은 ${code}였습니다.`;
            }
            if (said !== 'UP' && said !== 'DOWN') {
                return `AI 모델은 판단을 보류했습니다. 규칙 기반 계산은 ${code}였습니다.`;
            }
            if (said === d.codeDirection) {
                return '규칙 기반 계산도 같은 방향입니다.';
            }
            return `AI 모델은 ${this.DIRECTION_LABEL[said]}을 예측했지만 `
                + `규칙 기반 계산은 ${code}였습니다.`;
        },



        propertyTitle(property) {
            if (!property) {
                return '';
            }
            return property.dongHo ? `${property.name} ${property.dongHo}` : property.name;
        },

        hasVisited(scored) {
            return (scored?.scores || []).some(
                s => s.code === 'COMFORT' && s.effectiveScore != null);
        },


        visitedTitle(scored) {
            return this.scoredComfort(scored)
                ? '내가 공간의 쾌적함을 매겼습니다 — 다녀온 곳입니다'
                : '구성원 중 누군가 공간의 쾌적함을 매겼습니다';
        },


        scoredComfort(scored) {
            return (scored?.scores || []).some(s => s.code === 'COMFORT' && s.myScore != null);
        },


        async recomputeScores() {
            const id = this.scoreProperty?.property?.id;
            if (!id) {
                return;
            }
            const { ok, body } = await this.withLoading('recompute',
                () => this.request(`/api/properties/${id}/scores/recompute`, { method: 'POST' }));
            if (ok && body) {
                this.applyScoreForm(body);
                await this.loadProperties();
            } else {
                this.error = '재산출에 실패했습니다';
            }
        },


        scoreRangeFill(s) {
            const min = Number(this.scoreMin(s));
            const max = Number(this.scoreMax(s));
            const raw = this.scoreForm[s.code];
            const span = max - min;
            const pct = (raw === '' || raw == null || span <= 0)
                ? 0
                : Math.min(100, Math.max(0, ((Number(raw) - min) / span) * 100));
            return `background: linear-gradient(to right,`
                + ` var(--ocean) 0 ${pct}%, var(--line2) ${pct}% 100%)`;
        },


        applyScoreForm(scored) {
            this.scoreProperty = scored;
            const form = {};
            (scored.scores || []).forEach(s => {
                if (s.code === 'COMFORT') {
                    form[s.code] = s.myScore != null ? String(s.myScore) : '';
                } else if (s.manualScore != null) {
                    form[s.code] = String(s.manualScore);
                } else {
                    form[s.code] = s.effectiveScore != null ? String(s.effectiveScore) : '';
                }
            });
            this.scoreForm = form;
            this._scoreFormAtOpen = { ...form };
        },


        scoreLocked(s) {
            return s.scoringType === 'AUTO' && s.autoScore != null;
        },


        scoreMin(s) {
            return s.code === 'COMFORT' ? 1 : 0;
        },

        scoreMax(s) {
            return s.code === 'COMFORT' ? 5 : 100;
        },


        scoreStep(s) {
            return s.code === 'COMFORT' ? 1 : 5;
        },

        closeScoreModal() {
            this.showScoreModal = false;
            this.scoreProperty = null;
            this.scoreForm = {};
            this._scoreFormAtOpen = {};
            this.error = null;
        },


        scoreSliderValue(s) {
            const held = this.scoreForm[s.code];
            if (held !== '' && held != null) {
                return held;
            }
            return this.scoreMin(s);
        },


        scoreDisplayValue(s) {
            const held = this.scoreForm[s.code];
            if (held !== '' && held != null) {
                return held;
            }
            return s.code === 'COMFORT' ? this.scoreMin(s) : '–';
        },


        scoreValueText(s) {
            const shown = this.scoreDisplayValue(s);
            return shown === '–' ? '아직 산출되지 않음' : shown + '점';
        },


        changedScores() {
            const before = this._scoreFormAtOpen || {};
            const locked = new Set((this.scoreProperty?.scores || [])
                .filter(s => this.scoreLocked(s)).map(s => s.code));
            const byCode = new Map((this.scoreProperty?.scores || []).map(s => [s.code, s]));
            const scores = {};
            for (const code in this.scoreForm) {
                if (locked.has(code)) {
                    continue;
                }
                const unscored = code === 'COMFORT' && String(before[code] ?? '') === '';
                const value = toNum(unscored
                    ? this.scoreSliderValue(byCode.get(code) || { code })
                    : this.scoreForm[code]);
                if (value == null) {
                    continue;
                }
                if (!unscored && String(this.scoreForm[code] ?? '') === String(before[code] ?? '')) {
                    continue;
                }
                scores[code] = value;
            }
            return scores;
        },

        async saveScore() {
            this.loading = true;
            this.error = null;
            const scores = this.changedScores();
            if (Object.keys(scores).length === 0) {
                this.loading = false;
                this.showScoreModal = false;
                return;
            }
            try {
                const { ok, body } = await this.request(
                    `/api/properties/${this.scoreProperty.property.id}/scores`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ scores })
                    });
                if (ok) {
                    await this.loadProperties();
                    const fresh = (this.properties || []).find(
                        r => r.property.id === this.scoreProperty.property.id);
                    if (fresh) {
                        await this.openScoreModal(fresh);
                    } else {
                        this.showScoreModal = false;
                    }
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


        async loadLlmModels() {
            const { ok, body } = await this.request('/api/admin/llm-models')
                .catch(() => ({ ok: false }));
            if (!ok || !body) {
                this.llmModels = null;
                return;
            }
            this.applyLlmModels(body);
        },


        applyLlmModels(body) {
            this.llmModels = body;
            const known = new Set((body.models || []).map(m => m.id));
            const form = {};
            (body.features || []).forEach(f => {
                form[f.key] = known.has(f.model) ? f.model : '';
            });
            this.llmForm = form;
        },

        async saveLlmModels() {
            this.loading = true;
            this.savingKey = 'llmModels';
            this.error = null;
            try {
                const payload = (this.llmModels?.features || []).map(f => ({
                    key: f.key,
                    model: this.llmForm[f.key] ?? ''
                }));
                const { ok, body } = await this.request('/api/admin/llm-models', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                if (ok) {
                    this.applyLlmModels(body);
                } else {
                    this.error = (body && body.message) || 'AI 모델 설정 저장에 실패했습니다';
                }
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
                this.savingKey = null;
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
            this.savingKey = 'settings';
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
                this.savingKey = null;
            }
        },


        async loadNotifySettings() {
            const { ok, body } = await this.request('/api/admin/notification-settings')
                .catch(() => ({ ok: false }));
            this.notifySettings = ok ? body : null;
        },

        async loadNotifications() {
            const { ok, body } = await this.request('/api/admin/notifications');
            if (ok) {
                this.notifications = body || [];
            }
        },

        settingCategories() {
            const order = ['BATCH', 'LOAN'];
            const rank = c => (order.indexOf(c) === -1 ? order.length : order.indexOf(c));
            const present = [...new Set(this.settings
                .filter(s => s.category !== 'LLM')
                .map(s => s.category))];
            return present.sort((a, b) => rank(a) - rank(b));
        },

        settingsByCategory(category) {
            return this.settings.filter(s => s.category === category);
        },


        settingEditable(s) {
            return !String(s.configKey || '').startsWith('loan.stressRate.');
        },

        configCategoryLabel(category) {
            return { BATCH: '배치', LOAN: '대출' }[category] || category;
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
                if (this.pendingFocus) {
                    const target = this.pendingFocus;
                    this.pendingFocus = null;
                    this.focusProperty(target);
                }
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
            const coords = this.pins.filter(p => p.lat && p.lng);
            const spread = this.spreadOverlappingPins(coords);
            coords.forEach(p => {
                const at = spread[p.id];
                const position = new kakao.maps.LatLng(at.lat, at.lng);
                const base = this.pinZIndex(p);
                const overlay = new kakao.maps.CustomOverlay({
                    position,
                    content: this.markerContent(p),
                    yAnchor: 1,
                    clickable: true,
                    zIndex: base
                });
                overlay.setMap(this.map);
                const el = overlay.getContent();
                if (el instanceof HTMLElement) {
                    el.addEventListener('click', () => this.selectMarker(p.id));
                    el.addEventListener('mouseenter', () => overlay.setZIndex(PIN_Z.hover));
                    el.addEventListener('mouseleave', () => overlay.setZIndex(base));
                }
                this.markers[p.id] = overlay;
            });
            const stayingOnFocused = this.activePropertyId != null
                    && coords.some(p => p.id === this.activePropertyId);
            if (coords.length > 0 && !stayingOnFocused) {
                const bounds = new kakao.maps.LatLngBounds();
                coords.forEach(p => bounds.extend(new kakao.maps.LatLng(p.lat, p.lng)));
                this.map.setBounds(bounds);
            }
        },


        spreadOverlappingPins(coords) {
            const RADIUS_DEG = 0.00014;   // 위도 1e-4 ≈ 11m
            const groups = {};
            coords.forEach(p => {
                const key = `${Number(p.lat).toFixed(5)},${Number(p.lng).toFixed(5)}`;
                (groups[key] = groups[key] || []).push(p);
            });
            const placed = {};
            Object.values(groups).forEach(group => {
                if (group.length === 1) {
                    placed[group[0].id] = { lat: Number(group[0].lat), lng: Number(group[0].lng) };
                    return;
                }
                const ordered = [...group].sort((a, b) =>
                    String(a.dongHo || '').localeCompare(String(b.dongHo || ''), 'ko')
                    || a.id - b.id);
                ordered.forEach((p, i) => {
                    const angle = (2 * Math.PI * i) / ordered.length;
                    placed[p.id] = {
                        lat: Number(p.lat) + RADIUS_DEG * Math.cos(angle),
                        lng: Number(p.lng) + RADIUS_DEG * Math.sin(angle) * 1.26
                    };
                });
            });
            return placed;
        },



        pinZIndex(p) {
            return p.visited ? PIN_Z.visited : PIN_Z.fresh;
        },


        markerContent(p) {
            const jeonse = p.dealType === 'JEONSE';

            const box = document.createElement('div');
            box.className = 'map-pin' + (jeonse ? ' is-jeonse' : '');
            if (this.isVisited(p.id)) {
                box.classList.add('is-visited');
            }

            const area = document.createElement('b');
            area.textContent = p.areaExclusiveM2 ? `${Math.round(Number(p.areaExclusiveM2))}㎡` : p.name || '';
            box.appendChild(area);

            const price = document.createElement('span');
            price.textContent = `${jeonse ? '전' : '매'} ${this.fmtWonShort(p.priceDeposit)}`;
            box.appendChild(price);

            const tail = document.createElement('i');
            box.appendChild(tail);
            box.title = this.propertyTitle(p);
            return box;
        },


        focusProperty(item) {
            const p = item.property;
            this.activePropertyId = p.id;
            if (!this.map) {
                this.pendingFocus = p.lat && p.lng ? item : null;
                return;
            }
            this.pendingFocus = null;
            if (!p.lat || !p.lng) {
                return;
            }
            const position = new kakao.maps.LatLng(p.lat, p.lng);
            this.map.setCenter(position);
            this.map.setLevel(4);
        },

        selectMarker(id) {
            this.activePropertyId = id;
            this.expandMobileSheet();
            const el = document.getElementById('prop-' + id);
            if (el) {
                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
            const item = this.properties.find(x => x.property.id === id);
            if (item) {
                this.openRoadview(item);
                return;
            }
            this.findProperty(id).then(found => {
                if (found) {
                    this.openRoadview(found);
                }
            });
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
            return { SALE: '매매', JEONSE: '전세' }[type] || type;
        },

        dealBadge(type) {
            return { SALE: 'b-sale', JEONSE: 'b-jeonse' }[type] || '';
        },

        scoreSourceLabel(source) {
            return { AUTO: '자동', MANUAL: '수동', FALLBACK: '미산출' }[source] || '';
        },

        scoreSourceBadge(source) {
            return { AUTO: 'b-on', MANUAL: 'b-admin', FALLBACK: 'b-off' }[source] || '';
        },

        scoreCount(source) {
            return (this.scoreProperty?.scores || []).filter(s => s.scoreSource === source).length;
        },


        scoreBlockers() {
            const reasons = (this.scoreProperty?.scores || [])
                .filter(s => s.scoreSource === 'FALLBACK' && s.fallbackReason)
                .map(s => s.fallbackReason);
            return [...new Set(reasons)];
        },

        fmtScore(n) {
            if (n == null) {
                return '-';
            }
            return Number(n).toFixed(0);
        },


        ownerInitial(nickname) {
            return nickname ? Array.from(nickname.trim())[0] : '';
        },


        fmtArea(m2) {
            if (m2 == null || m2 === '') {
                return '-';
            }
            const n = Number(m2);
            return `${n}㎡ (${(n / 3.3058).toFixed(1)}평)`;
        },

        moveInLabel(p) {
            if (p.moveInType === 'IMMEDIATE') {
                return '즉시 입주';
            }
            if (p.moveInType === 'NEGOTIABLE') {
                return p.moveInDate ? `${p.moveInDate} 협의 가능` : '협의 가능';
            }
            return p.moveInDate || '-';
        },


        fmtRate(monthlyRate) {
            if (monthlyRate == null) {
                return '-';
            }
            return (monthlyRate * 12 * 100).toFixed(2) + '%';
        },




        async withLoading(key, fn) {
            clearTimeout(this._loadingTimers[key]);
            this._loadingTimers[key] = setTimeout(() => {
                this._loading[key] = true;
            }, SHOW_LOADING_AFTER_MS);
            try {
                return await fn();
            } finally {
                clearTimeout(this._loadingTimers[key]);
                this._loading[key] = false;
            }
        },

        isLoading(key) {
            return this._loading[key] === true;
        },

        numericInput(e) {
            const cleaned = String(e.target.value).replace(/[^0-9]/g, '');
            e.target.value = cleaned;
            return cleaned;
        },


        floorInput(e) {
            const raw = String(e.target.value);
            const band = raw.match(/[저중고]/);
            const cleaned = band ? band[0] : raw.replace(/[^0-9]/g, '');
            e.target.value = cleaned;
            return cleaned;
        },


        floorLabel(p) {
            const band = { LOW: '저', MID: '중', HIGH: '고' };
            const here = p?.floorBand ? band[p.floorBand] : (p?.floorNo ?? null);
            if (here == null || here === '') {
                return '';
            }
            return p.floorTotal ? `${here}/${p.floorTotal}층` : `${here}층`;
        },

        moneyHint(value) {
            const n = toNum(value);
            return n == null || n === 0 ? '' : this.fmtWon(n);
        },


        fmtWonShort(won) {
            if (won == null || won === 0) {
                return '0원';
            }
            const n = Number(won);
            if (n < 100000000) {
                return Math.floor(n / 10000).toLocaleString('ko-KR') + '만';
            }
            const eok = Math.floor(n / 1000000) / 100;
            return `${eok.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}억`;
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
