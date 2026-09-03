/**
 * 오늘 (설계 I207).
 *
 * <p><b>`toISOString()` 을 그냥 자르면 안 됩니다.</b> UTC 기준이라 한국에서는
 * <b>오전 9시 전에 어제 날짜</b>가 나옵니다 — 오늘 오후 임장을 짜려는데 날짜 칸이
 * 어제로 채워져 있고, `min` 에도 걸려 고를 수 없게 됩니다.
 */
/**
 * 주소 밀기를 다루는 값들 (설계 I211).
 *
 * <p><b>컴포넌트 밖에 둡니다.</b> 안에 두면 Alpine 이 반응형으로 감싸는데,
 * 주소를 계산하는 효과가 이것을 읽고 쓰므로 <b>스스로를 다시 부릅니다</b>.
 */
let routeApplying = false;
let routeQueued = false;
let routeTarget = null;

function todayIso() {
    const now = new Date();
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
}

function emptyPropertyForm() {
    return {
        id: null,
        name: '',
        dealType: 'SALE',
        priceDeposit: '',
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
        parkingPerHousehold: '',
        moveInType: '',
        moveInDate: '',
        editVersion: null,
        // 이 폼이 보여 주지 않는 칸들 (설계 I113). 수정 요청은 매물 전체를 덮어쓰므로
        // 안 보내면 지워진다 — 화면에 없는 값도 그대로 돌려보내야 한다
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

/** 비교 우위 분석 최소 매물 수 — 서버(ComparativeAnalysisService.MIN_PROPERTIES)와 같아야 한다. */
const COMPARE_MIN_PROPERTIES = 4;
/** 목록 한 쪽의 크기 (설계 I240). 서버의 기본값과 같아야 한다 */
const PAGE_SIZE = 30;
/** 목록 바닥에서 이만큼 남으면 다음 쪽을 부른다 — 다 내려간 뒤에 부르면 끊겨 보인다 */
const INFINITE_SCROLL_MARGIN_PX = 400;
/**
 * 겹친 지도 핀의 앞뒤 (설계 I245).
 *
 * <p>다녀온 곳은 뒤로. 임장은 <b>아직 안 가 본 곳을 고르려고</b> 보는 지도입니다.
 */
const PIN_Z = { visited: 1, fresh: 2, hover: 10 };

/** AI 결과를 기다리는 동안의 폴링 간격·상한 (설계 I72). */
/**
 * 채점 판 번호를 확인하는 주기 (설계 I85).
 * 보정과 AI 응답은 수 초~수십 초가 걸린다. 이보다 촘촘히 물어도 답이 달라지지 않는다.
 */
const SCORE_WATCH_MS = 3000;
// 응답이 이보다 빨리 오면 진행 막대를 띄우지 않는다 — 번쩍임이 더 거슬린다 (설계 I115)
const SHOW_LOADING_AFTER_MS = 250;
const LLM_POLL_INTERVAL_MS = 2000;
const LLM_POLL_MAX_ATTEMPTS = 60;
// 전망은 60개월 수집 + LLM 판단이라 1~2분 걸린다. 5초 × 36 = 3분이면 넉넉하다 (설계 I142)
/** 실거래를 배경에서 받아 오는 동안 다시 묻는 간격 (설계 I259) */
/** 점수가 채워질 때까지 다시 묻는 간격 (설계 I261) */
const SCORE_POLL_INTERVAL_MS = 3000;
/** 3초 × 40 = 2분. 보정은 1~2분이라 그 정도면 넉넉하다 */
const SCORE_POLL_MAX_ATTEMPTS = 40;
const REF_POLL_INTERVAL_MS = 3000;
/** 3초 × 20 = 1분. 12개월치를 받는 데 그 정도면 넉넉하다 */
const REF_POLL_MAX_ATTEMPTS = 20;
const FORECAST_POLL_INTERVAL_MS = 5000;
const FORECAST_POLL_MAX_ATTEMPTS = 36;

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
        mobileTab: 'map',
        dealTypeFilter: 'ALL',
        /**
         * 목록 정렬 (설계 I221 → I240).
         *
         * <p><b>줄 세우기는 서버가 합니다.</b> 30건씩 잘라 받으므로, 받은 것 안에서
         * 줄 세우면 2쪽의 1등이 1쪽의 꼴찌보다 앞에 옵니다.
         */
        sortKey: 'DEFAULT',
        sortOpen: false,
        /** 화면이 '모두 불러왔습니다'를 언제 띄울지 판단하는 데 쓴다 (설계 I240) */
        PAGE_SIZE,
        /** 지금까지 받아 온 쪽들 (설계 I240). 전체가 아니다 */
        properties: [],
        scoreWatchTimer: null,
        _scrollObserver: null,
        /** 다음에 받을 쪽 번호 */
        propertyPage: 0,
        /** 거른 뒤의 전체 건수 — 서버가 세어 준다. 화면이 다시 세면 규칙이 두 벌이 된다 */
        propertyTotal: 0,
        propertyHasNext: false,
        loadingMore: false,
        /** 치워 둔 매물이 몇 건인가 (설계 I241). 아카이빙 탭의 뱃지 */
        archivedTotal: 0,
        /**
         * 지도와 임장 플래너가 쓰는 <b>전체</b> 목록 (설계 I240).
         *
         * <p>목록은 잘려 오지만 지도는 전부 찍어야 합니다 — 잘린 것으로 그리면
         * <b>매물이 사라진 것처럼</b> 보입니다.
         */
        pins: [],
        users: [],
        soldOutRecent: [],
        // 경로를 아예 못 냈다 (설계 I274)
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
        /** 가 본 매물 id 목록 (설계 I197). 계산 결과와 달리 DB에 남는다 */
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
        /** 알림 스위치 상태 (설계 I215). 읽기 전용 — 배포로 정하는 값이다 */
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
        /** 뷰어가 걷는 목록 (설계 I203). 사진 모달의 것일 수도, 상세의 것일 수도 있다 */
        viewerImages: [],
        /** 매물 상세에 뿌릴 사진 (설계 I203). 사진 모달의 photoImages 와 별개다 */
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
        /**
         * 붙여넣기 화면에서 고른 사진 (설계 I204).
         *
         * <p><b>여기서는 아직 올릴 수 없습니다</b> — 매물이 없으니 붙일 곳이 없습니다.
         * 브라우저가 들고 있다가 저장으로 매물이 생긴 뒤 올립니다.
         */
        pasteFloorPlan: null,
        pastePhotos: [],
        pasteDraftName: null,
        showScoreModal: false,
        scoreProperty: null,
        scoreForm: {},
        // 연 시점의 채점 값. 저장할 때 달라진 항목만 가려내는 데 쓴다 (설계 I111).
        // Alpine은 선언된 것만 프록시에 올린다 — 여기 없으면 읽는 순간 던진다
        _scoreFormAtOpen: {},
        // 가격 전망 (설계 I136)
        showForecast: false,
        forecastProperty: null,
        forecastDetail: null,
        forecastNews: [],
        // 모달별 로딩 표시 (설계 I115). Alpine은 선언된 것만 프록시에 올린다 —
        // 여기 없으면 템플릿이 읽는 순간 던진다
        _loading: {},
        _loadingTimers: {},
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
        // 둘 다 기본으로 켠다 (설계 I190). 매번 다시 켜는 쪽이 더 번거롭다는 판단이다
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
        /** 가중치 드래그 중인 항목 (설계 I105). 선언하지 않으면 템플릿이 읽을 때 터진다 */
        _dragIndex: null,
        groupForm: { name: '', slackWebhookUrl: '' },
        joinForm: { code: '' },
        inviteCode: null,
        // 그룹 정보 화면이 쓰는 상세 (설계 I123)
        groupDetail: null,
        withdrawForm: { password: '' },
        passwordForm: { currentPassword: '', newPassword: '' },
        error: null,
        loading: false,

        /**
         * 로그인 전에 알아야 하는 설정 (설계 I95).
         *
         * 세션 조회는 로그아웃 상태에서 401이라 거기 담을 수 없다.
         * 못 받으면 <b>닫힌 것으로 본다</b> — 열어 두는 쪽으로 틀리면 안 된다.
         */
        async loadPublicConfig() {
            const { ok, body } = await this.request('/api/auth/config');
            this.signUpOpen = ok && body ? body.signUpOpen === true : false;
        },

        /**
         * 숫자 칸 위에서 휠을 굴려도 값이 바뀌지 않게 한다 (설계 I101).
         *
         * `type="number"`는 <b>포커스된 상태에서 휠에 반응</b>합니다. 페이지를 스크롤하다
         * 커서가 그 칸 위에 있으면 값이 조용히 오르내립니다 — 실제로 보유 현금
         * 550,000,000이 549,999,997로 바뀌어 저장됐습니다. 세 칸 내려간 것입니다.
         *
         * 금액·좌표를 다루는 앱이라 <b>한 자리가 틀리면 판단이 통째로 어긋납니다.</b>
         * 값을 되돌리는 대신 포커스를 놓아 휠이 스크롤로만 동작하게 합니다.
         */
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
            this.watchModalClose();
            this.restoreLoginId();
            // 뒤로/앞으로 가기 (설계 I188). 주소를 다시 밀지 않는다 — 기록이 두 번 쌓인다
            window.addEventListener('popstate', () => {
                if (this.session.authenticated) {
                    routeApplying = true;
                    this.closeAllModals();
                    routeApplying = false;
                    this.applyRoute();
                }
            });
            await this.loadPublicConfig();
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
            // 초기 설정이 끝나기 전에는 서버가 API를 막는다(AccountSetupFilter).
            // 어느 경로로 막히든 해당 단계의 모달을 띄워 사용자가 갇히지 않게 한다.
            if (res.status === 403 && body && body.code === 'PROFILE_SETUP_REQUIRED') {
                this.showProfileSetup = true;
            }
            if (res.status === 403 && body && body.code === 'MUST_CHANGE_PASSWORD') {
                this.showPassword = true;
            }
            // 세션이 끊기면 모든 호출이 조용히 실패한다 — 사용자에게는 '아무 반응이 없는' 상태다.
            // 다만 <b>로그인 전에는 401이 정상</b>이다. 첫 접속의 세션 확인도 401로 온다 —
            // 그때까지 '풀렸다'고 하면 아무 일도 없었는데 경고부터 보게 된다
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
                // 남은 시간을 모를 수도 있다. 0을 넣으면 tickSession이 '이미 만료'로 읽어
                // 15초 만에 로그아웃시킨다 (설계 I120) — 임시 비밀번호 변경 화면이
                // 갑자기 로그인 화면으로 바뀌던 원인이다. 모르는 것과 만료된 것은 다르다
                this.sessionExpiresAt = body.expiresInSeconds != null
                    ? Date.now() + body.expiresInSeconds * 1000 : null;
                this.startSessionTimer();
                this.showLogin = false;
                this.showPassword = body.mustChangePassword === true;
                // 값이 채워져 있는 것과 본인이 맞다고 한 것은 다르다 (설계 I100)
                this.showProfileSetup = !this.showPassword && body.profileConfirmed === false;
                if (this.showProfileSetup) {
                    await this.prefillSetupForm();
                }
                // 초기 설정(비밀번호·프로필)이 끝나기 전에는 다른 API가 403이므로 호출하지 않는다
                const setupPending = this.showPassword || this.showProfileSetup;
                if (this.session.role === 'ADMIN' && !setupPending) {
                    await this.loadUsers();
                    await this.loadGroups();
                }
                if (!setupPending) {
                    await this.loadMyGroup();
                    await this.loadDebts();
                    // 임장 화면 것이 아니라 <b>목록 정렬</b>이 쓰는 값이다 (설계 I224).
                    // 임장에 들어갈 때만 받으면, 그 화면을 한 번도 안 연 사람에게는
                    // 기본 정렬이 <b>추천점수 순과 똑같아집니다</b>
                    await this.loadVisited();
                    await this.loadProperties();
                    await this.checkSoldOutAlert();
                    // 등록 직후에는 채점이 비어 있고 보정·AI가 끝나며 채워진다 (설계 I85)
                    this.startScoreWatch();
                    // 바닥에 닿으면 다음 쪽을 부른다 (설계 I240)
                    this.startInfiniteScroll();
                    // 주소로 들어왔으면 그 화면을 연다 (설계 I188).
                    // 목록을 받은 뒤여야 매물 상세를 열 수 있다
                    this.applyRoute();
                }
            } else {
                this.session = { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false };
                this.showLogin = true;
                this.focusLogin();
            }
        },

        /**
         * 화면마다 주소를 둔다 (설계 I188).
         *
         * <p>SPA 라도 <b>지금 보는 것을 링크로 건넬 수 있어야</b> 합니다 —
         * Slack 알림에서 그 매물로 바로 가는 것이 그것 때문입니다(I189).
         */
        ROUTES: {
            list: '/properties',
            itinerary: '/itinerary',
            me: '/me',
            group: '/group',
            weights: '/weights'
        },

        /**
         * 모달에도 주소를 준다 (설계 I198).
         *
         * <p>화면에만 주소가 있었습니다. 그래서 <b>Slack 이 "누가 공간 쾌적함을 채점했다"</b>
         * 고 알려도 링크는 매물 첫 화면으로만 갔습니다 — 거기서 다시 찾아 들어가야 했습니다.
         *
         * <p><b>순서가 곧 우선순위입니다.</b> 겹쳐 뜬 모달은 맨 위의 주소를 씁니다.
         * 위에서부터 훑어 처음 열려 있는 것을 고릅니다.
         *
         * <p>`prop` 이 있으면 매물에 딸린 모달이라 `/properties/{id}/…` 가 되고,
         * 없으면 전역 주소입니다. `open` 은 그 주소로 <b>들어왔을 때</b> 어떻게 여는가입니다.
         *
         * <p>강제 모달(로그인·비밀번호 변경·프로필 확인·세션 경고)과 잠깐 뜨는 것
         * (메뉴·확인창·판매완료 알림)은 <b>일부러 뺐습니다.</b> 링크로 건넬 것이 아니고,
         * 주소로 들어올 수 있으면 안 되는 것도 있습니다.
         */
        MODAL_ROUTES: [
            { key: 'photo', flag: 'photoViewerIndex', prop: 'photoProperty',
              suffix: i => `/photos/${i}`,
              open(app, item, n) { return app.openPhotoModal(item).then(() => app.openPhotoViewer(Number(n))); } },
            { key: 'photos', flag: 'showPhotoModal', prop: 'photoProperty', suffix: () => '/photos',
              open: (app, item) => app.openPhotoModal(item) },
            { key: 'score', flag: 'showScoreModal', prop: 'scoreProperty', suffix: () => '/score',
              open: (app, item) => app.openScoreModal(item) },
            { key: 'loan', flag: 'showLoanModal', prop: 'loanProperty', suffix: () => '/loan',
              open: (app, item) => app.openLoanModal(item) },
            { key: 'transactions', flag: 'showRefModal', prop: 'refProperty', suffix: () => '/transactions',
              open: (app, item) => app.openRefModal(item) },
            { key: 'comments', flag: 'showComments', prop: 'commentProperty', suffix: () => '/comments',
              open: (app, item) => app.openComments(item) },
            { key: 'forecast', flag: 'showForecast', prop: 'forecastProperty', suffix: () => '/forecast',
              open: (app, item) => app.openForecast(item) },
            { key: 'agents', flag: 'showAgentModal', prop: 'agentProperty', suffix: () => '/agents',
              open: (app, item) => app.openAgentModal(item) },
            { key: 'roadview', flag: 'showRoadview', prop: 'roadviewProperty', suffix: () => '/roadview',
              open: (app, item) => app.openRoadview(item) },
            { key: 'edit', flag: 'showPropertyForm', suffix: () => '/edit',
              path: app => app.propertyForm.id ? `/properties/${app.propertyForm.id}/edit` : '/properties/new',
              open: (app, item) => (item ? app.openEditProperty(item) : app.openAddProperty()) },
            { key: 'paste', flag: 'showPasteModal', suffix: () => '/paste',
              path: app => app.pasteDraftId ? `/properties/${app.pasteDraftId}/paste` : '/properties/paste',
              open: (app, item) => app.openPasteModal(item) },
            { key: 'compare', flag: 'showCompare', path: () => '/compare',
              open: app => app.openCompare() },
            { key: 'userForm', flag: 'showUserForm',
              path: app => app.userForm.id ? `/users/${app.userForm.id}/edit` : '/users/new' },
            { key: 'password', flag: 'showChangePw', path: () => '/password',
              open: app => app.openChangePw() },
            { key: 'signup', flag: 'showSignUp', path: () => '/signup' },
            { key: 'users', flag: 'showUsers', path: () => '/users',
              open: app => app.openUsers() },
            { key: 'settings', flag: 'showSettings', path: () => '/settings',
              open: app => app.openSettings() }
        ],

        /**
         * 지금 열려 있는 것을 주소로 옮긴다 (설계 I198).
         *
         * <p><b>여는 함수마다 주소를 밀지 않습니다.</b> 모달이 스물 몇 개인데 각각에
         * 넣으면 반드시 하나를 빠뜨리고, 닫는 쪽은 더 그렇습니다. 상태를 지켜보다가
         * <b>바뀔 때마다 지금 상태에서 주소를 다시 계산</b>합니다 — 어느 경로로 열리고
         * 닫혀도 맞습니다.
         */
        currentPath() {
            // <b>먼저 전부 읽습니다.</b> 찾자마자 끊으면 뒤쪽 플래그를 안 읽게 되고,
            // 그러면 `x-effect` 가 그것들을 추적하지 못해 <b>그 모달에서는 주소가
            // 안 바뀝니다</b> (설계 I211)
            let chosen = null;
            for (const route of this.MODAL_ROUTES) {
                // photoViewerIndex 는 0도 '열림'이다. null 은 닫힌 것인데
                // `null >= 0` 이 true 라 그냥 비교하면 안 열린 뷰어가 열린 것으로 읽힌다
                const open = route.flag === 'photoViewerIndex'
                    ? Number.isInteger(this[route.flag]) && this[route.flag] >= 0
                    : this[route.flag] === true;
                if (open && chosen === null) {
                    chosen = route;
                }
            }
            const detail = this.detailItem;
            const base = (this.showM2 && detail)
                ? `/properties/${detail.property.id}`
                : (this.ROUTES[this.view] || '/properties');
            if (chosen === null) {
                return base;
            }
            if (chosen.path) {
                return chosen.path(this);
            }
            const id = this[chosen.prop]?.property?.id ?? this[chosen.prop]?.id;
            // 어느 매물인지 모르면 주소를 지어내지 않는다 — 열 수 없는 링크가 된다
            return id == null ? base : `/properties/${id}${chosen.suffix(this[chosen.flag])}`;
        },

        /**
         * 지금 열린 것을 주소로 (설계 I198 · I211).
         *
         * <p><b>`currentPath()` 를 먼저, 동기로 부릅니다.</b> `x-effect` 는 <b>실행하는
         * 동안 읽은 것</b>만 추적합니다 — 미뤄서 읽으면 아무것도 추적되지 않아
         * 효과가 다시 돌지 않습니다.
         *
         * <p>미루는 것은 <b>주소를 미는 일뿐</b>입니다. 모달을 닫으면 플래그 두셋이
         * 같이 꺼지는데, 그때마다 밀면 중간 상태가 기록에 남아 뒤로 가기가 이상해집니다.
         */
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

        /** 주소만 바꾼다. 화면은 이미 바뀐 뒤다 — 뒤로 가기를 위해 기록만 남긴다. */
        pushRoute(path) {
            if (window.location.pathname !== path) {
                window.history.pushState({}, '', path);
            }
        },

        /**
         * 주소를 읽어 화면을 맞춘다 (설계 I188).
         *
         * <p>주소창에 직접 넣거나, 링크로 들어오거나, 뒤로 가기를 눌렀을 때 부릅니다.
         * <b>화면을 바꾸되 주소는 다시 밀지 않습니다</b> — 그러면 기록이 두 번 쌓입니다.
         */
        async applyRoute() {
            const path = window.location.pathname;
            // 여는 사이에 주소를 다시 밀면 기록이 겹친다 (설계 I198)
            routeApplying = true;
            try {
                await this.openRoute(path);
            } finally {
                routeApplying = false;
            }
        },

        async openRoute(path) {
            // 매물에 딸린 모달: /properties/{id}/{key}[/{n}]
            const scoped = path.match(/^\/properties\/(\d+)\/([a-z]+)(?:\/(\d+))?$/);
            if (scoped) {
                this.view = 'list';
                const item = await this.findProperty(Number(scoped[1]));
                const route = this.MODAL_ROUTES.find(r => r.key === scoped[2]);
                if (item && route && route.open) {
                    await route.open(this, item, scoped[3]);
                    return;
                }
                // 없는 매물이거나 모르는 주소면 상세라도 연다 — 빈 화면보다 낫다
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
            const global = this.MODAL_ROUTES.find(
                r => r.path && r.open && !r.prop && r.path(this) === path);
            if (global) {
                this.view = 'list';
                await global.open(this);
                return;
            }
            if (path === '/properties/new' || path === '/properties/paste') {
                this.view = 'list';
                (path.endsWith('new') ? this.openAddProperty() : this.openPasteModal(null));
                return;
            }
            const entry = Object.entries(this.ROUTES).find(([, p]) => p === path);
            this.setView(entry ? entry[0] : 'list');
        },

        /**
         * 매물 하나를 찾는다 (설계 I240).
         *
         * <p>목록이 30건씩 잘려 오므로 <b>받은 쪽에 없다고 없는 매물이 아닙니다.</b>
         * 그럴 때 조용히 null 을 주면 지도에서 누른 매물이 <b>아무 반응도 없이</b>
         * 사라집니다 — 상세를 직접 받아 옵니다.
         */
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

        /** 링크로 들어온 매물 상세를 연다. 목록이 아직 없으면 받아 온 뒤 연다. */
        async openDetailById(id) {
            const item = await this.findProperty(id);
            if (item) {
                this.openDetail(item);
            }
        },

        setView(view) {
            const leaving = this.view;
            this.view = view;
            // 지도는 <b>화면마다 따로 있지 않습니다</b> — 하나를 나눠 씁니다 (설계 I206).
            // 임장을 떠나면 그 경로선도 걷어냅니다. 안 그러면 매물 화면 지도 위에
            // 어제 짠 동선이 계속 얹혀 있습니다
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
                // 기본값(오늘 09:00)이 이미 지났으면 밀어 준다 (설계 I207)
                this.normalizeItinStart();
                this.loadStartLocation();
                // 담아 둔 결과를 다시 그린다 (설계 I206). 떠날 때 걷어냈으므로
                // 돌아오면 다시 얹어야 한다 — 계산을 다시 시키지는 않는다
                this.loadItineraryDraft();
                this.loadVisited();
            }
        },

        /** 시스템 설정은 ADMIN 전용이다. 메뉴는 x-show로 숨기지만, 여는 경로에서도 한 번 더 막는다. */
        openSettings() {
            if (this.session.role !== 'ADMIN') {
                return;
            }
            this.showSettings = true;
            this.error = null;
            this.regError = null;
            this.loadSettings();
            this.loadNotifications();
            this.loadNotifySettings();
            this.loadRegulations();
        },

        closeSettings() {
            this.showSettings = false;
            this.error = null;
        },

        /** 사용자 관리도 ADMIN 전용이다 (설계 7.1 M3 · I51). */
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
            // 열 때마다 다시 읽는다 (설계 I112). 세션 확인 때 한 번 읽은 게 전부였는데,
            // 초기 설정이 남아 있으면 그 호출을 건너뛰어 목록이 영영 비어 있었다
            await this.withLoading('groups', () => this.loadGroups());
        },

        async openEditUser(u) {
            this.editingUserId = u.id;
            this.userForm = {
                loginId: u.loginId,
                nickname: u.nickname,
                // 지금 그룹을 미리 고르지 않는다 — 손대지 않으면 그대로 둔다는 뜻이다 (설계 I103)
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
                // 관리자 계정은 화면에서 만들지 않는다 (설계 I105)
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
                    // 서버는 이 값을 <b>이때 한 번만</b> 준다 — 저장은 해시라 다시 못 읽는다
                    this.tempPassword = { loginId: u.loginId, nickname: u.nickname,
                        password: body.temporaryPassword, copied: false };
                } else {
                    this.error = '비밀번호 초기화에 실패했습니다';
                }
                await this.loadUsers();
            });
        },


        /**
         * 임시 비밀번호를 지운다 (설계 I213).
         *
         * <p><b>닫으면 다시 볼 수 없습니다.</b> 저장된 것은 해시라 서버도 모릅니다 —
         * 그래서 닫기 전에 옮겨 적으라고 말해 둡니다.
         */
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
                // 클립보드를 못 쓰는 환경이 있다. 값은 화면에 그대로 보이므로 손으로 옮기면 된다
                this.tempPassword.copied = false;
            }
        },

        // ── 그룹 (설계 I89) ──────────────────────────────

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

        /** 가입하면 새 그룹이 함께 만들어진다 (규칙 14). 바로 로그인까지 이어 준다. */
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

        /** @param target 'signUp' 또는 'profile' — 어느 칸의 닉네임을 확인하는지 */
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

        /** admin 전용 그룹 목록 (규칙 7·12). 회원은 이 API를 부를 수 없다. */
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

        /** 종류별 기존 부채 (설계 I92). 연간 부담을 함께 보여 준다. */
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
            // admin은 어느 그룹에도 속하지 않으므로 그룹 정보가 없다 (규칙 5)
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
                // 화면에 띄운 상세도 같이 맞춘다 — 안 그러면 위쪽 배지만 바뀐다
                if (this.groupDetail) {
                    this.groupDetail = { ...this.groupDetail, name: body.name };
                }
            } else {
                this.error = (body && body.message) || '그룹 이름을 바꾸지 못했습니다';
            }
        },

        /** 알림이 나갈 곳 (설계 I96). 비우면 알림이 나가지 않는다. */
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

        /** 웹훅이 실제로 닿는지 확인한다 (설계 I96). 주소를 잘못 넣어도 조용히 안 갈 뿐이다. */
        async testWebhook() {
            const { ok, body } = await this.request('/api/groups/me/webhook/test', { method: 'POST' });
            this.error = (ok && body && body.sent)
                ? null
                : '테스트 메시지를 보내지 못했습니다. 웹훅 주소를 확인해 주세요';
        },

        /** 그룹 정보 화면 (설계 I123). 구성원·현금 합계·매물 수를 한 번에 받는다. */
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

        /**
         * 그룹을 옮기기 전에 경고한다 (규칙 11).
         *
         * 지금 그룹에 나만 남아 있으면 <b>그 그룹의 매물이 전부 사라집니다</b>(규칙 4).
         * 되돌릴 수 없으므로 그 경우를 따로 알린다.
         */
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

        /**
         * 확인 화면을 지금 저장된 값으로 채운다 (설계 I100).
         *
         * 빈 화면을 주면 관리자가 넣어 둔 값을 <b>본인이 다시 타이핑</b>해야 하고,
         * 그러다 원래 값이 뭐였는지 모른 채 덮어씁니다.
         */
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
            // 프로필 화면을 열 때 그룹도 함께 받는다 (설계 I102).
            // 로그인 때 한 번만 받으면, 그 사이 그룹을 옮겼을 때 옛 이름이 남는다
            await this.loadMyGroup();
        },

        /**
         * 카카오(다음) 우편번호 서비스로 주소를 고르고, 선택한 주소를 지오코딩해 좌표까지 채운다.
         * target: 'setup'(최초 설정 모달) | 'profile'(내 프로필 화면)
         */
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
                        // 출발지는 좌표가 있어야 경로를 못 짠다 — 여기서만 좌표를 필수로 본다
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
                    // 사용자가 고른 주소는 <b>무조건 담는다</b> (설계 I154).
                    // 좌표 조회가 실패했다고 주소까지 버리면, 화면에서는 아무 일도 안 일어난 것처럼
                    // 보이고 직장을 영영 못 바꾼다 — 사용자가 한 선택을 우리가 지우는 셈이다
                    form.workplaceName = label;
                    form.workplaceLat = coords ? coords.lat : '';
                    form.workplaceLng = coords ? coords.lng : '';
                    if (!coords) {
                        // 좌표가 없으면 직주근접만 못 낸다. 나머지는 저장된다
                        this.error = '주소는 담았지만 좌표를 찾지 못했습니다 — 직주근접 점수는 산출되지 않습니다.';
                    }
                }
            }).open();
        },

        /** 출발지는 입력이 끝나는 즉시 캐시한다 (TTL 7일 — 설계 I52). */
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

        // ── 규제 파라미터·규제지역 (설계 I68) ──────────
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
            // 값이 바뀐 것만 보낸다 — 안 건드린 항목까지 갱신하면 updatedAt이 전부 흐려진다
            const changed = this.regParams
                .filter(p => String(this.regParamForm[p.id] ?? '') !== String(p.paramValue))
                .map(p => ({ id: p.id, paramValue: String(this.regParamForm[p.id] ?? '').trim() }));
            if (changed.length === 0) {
                this.regError = '바뀐 값이 없습니다';
                return;
            }
            this.loading = true;
            this.regError = null;
            try {
                const { ok, body } = await this.request('/api/admin/regulations/params', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(changed)
                });
                if (ok) {
                    this.applyRegulations(body);
                    // LTV·DSR은 가격 채점의 입력이라 값이 바뀌면 전 매물 점수가 달라진다
                    await this.loadProperties();
                } else {
                    this.regError = (body && body.message) || '저장에 실패했습니다';
                }
            } catch (e) {
                this.regError = '네트워크 오류가 발생했습니다';
            } finally {
                this.loading = false;
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
                    // 활성 프로파일을 복제한다. 만들자마자 전환하지는 않는다 —
                    // 값을 고친 뒤 전환해야 중간 상태로 채점되지 않는다
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

        // ── 비교 우위 분석 (설계 I61) ─────────────────
        /** 분석 대상은 판매완료·작성 중을 뺀 매물이다 — 서버의 판정과 같은 기준을 화면에서도 쓴다. */
        comparableCount() {
            // 받은 쪽만 세면 4건이 넘는데도 "부족합니다"가 뜬다 (설계 I240)
            return this.pins.filter(p => p.active && !p.draft).length;
        },

        canCompare() {
            const min = this.compareStatus ? this.compareStatus.minProperties : COMPARE_MIN_PROPERTIES;
            if (this.comparableCount() < min) {
                return false;
            }
            // 현황을 아직 못 읽었으면 매물 수만으로 판단한다. 서버가 최종 판정을 다시 한다.
            return this.compareStatus ? this.compareStatus.analysable : true;
        },

        /** 왜 못 누르는지 버튼에 붙여 준다. 비활성 이유가 안 보이면 고장으로 읽힌다. */
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
                // 다른 화면에서 시작된 분석이 아직 도는 중일 수 있다 (설계 I72)
                this.compareRunning = !!body.pending;
            }
        },

        async runCompare() {
            // 서버가 다시 검증하지만, 여기서 막아야 눌러 놓고 에러를 받는 일이 없다
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
                    // 순위가 바뀌면 전 매물의 총점이 함께 움직인다
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

        // ── 매물 코멘트 (설계 I56) ─────────────────────
        openComments(item) {
            this.commentProperty = item;
            this.comments = [];
            this.commentNewText = '';
            this.commentEditingId = null;
            this.error = null;
            this.showComments = true;
            // 쓰다 만 글이 있으면 되살린다 (설계 I236)
            this.restoreCommentDraft();
            this.withLoading('comments', () => this.loadComments());
        },

        /**
         * 쓰던 코멘트를 브라우저에 담아 둔다 (설계 I236).
         *
         * <p><b>아이패드에서 애플펜슬로 쓰는 것</b>을 염두에 둔 장치입니다.
         * `<textarea>` 라 Scribble 은 그대로 되지만, <b>획이 필드 밖으로 조금
         * 삐져나가면</b> 배경 클릭으로 읽혀 모달이 닫힙니다([I122]에서 배경 닫기를
         * 되살렸습니다). 손으로 한참 쓴 글이 <b>한 번에 사라집니다.</b>
         *
         * <p>서버에 두지 않습니다 — 아직 남기지 않은 글은 <b>남의 눈에 보이면
         * 안 됩니다.</b> 브라우저에만 담고, 남기면 지웁니다.
         */
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
                // 사파리 비공개 모드에서는 못 쓴다. 담아 두지 못할 뿐 쓰는 데 지장은 없다
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
                // 지우지 못해도 다음에 덮어쓴다
            }
        },

        closeComments() {
            // 닫기 전에 담아 둔다 — 실수로 닫아도 다시 열면 그대로 있다 (설계 I236)
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

        /** 내가 이미 남긴 글. 있으면 입력칸 대신 수정 버튼을 보여준다. */
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
                    // 남겼으니 담아 둔 것도 지운다 (설계 I236)
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

        // 중개사·실거래가는 매물 등록 시 이미 채워져 있다. 여기서는 읽기만 하고 실패해도 모달은 그대로 뜬다.
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
            // 아직 산출 전이면 204라 body가 없다
            // 폴링용으로 결과가 없어도 200이 온다. score로 유무를 가린다
            this.detailLlm = llm.ok && llm.body && llm.body.score != null ? llm.body : null;
            this.detailLandUse = landUse.ok ? (landUse.body || []) : [];
            // 결과가 아직 없고 분석이 도는 중이면 진행 표시 + 폴링 (설계 I72)
            this.llmPending = !this.detailLlm && !!(llm.ok && llm.body && llm.body.pending);
            if (this.llmPending) {
                this.startLlmPolling(propertyId);
            }
            // 실거래를 배경에서 받아 오는 중이면 다 받을 때까지 다시 묻는다 (설계 I259).
            // 전에는 새로고침해야 채워졌다 — 그 사이 화면은 "없습니다"였다
            if (this.detailRef?.looking) {
                this.startRefPolling(propertyId);
            }
            // 아직 점수가 없으면 채워질 때까지 스스로 묻는다 (설계 I261)
            if (this.scoring(this.detailItem)) {
                this.startScorePolling(propertyId);
            }
        },

        /**
         * 점수가 채워질 때까지 <b>상세가 스스로</b> 다시 묻는다 (설계 I261).
         *
         * <p>배너({@code scoring()})는 <b>목록에서 온</b> `detailItem.scores` 만 봅니다.
         * 그래서 목록이 갱신되고 {@code syncDetailItem} 이 돌아야만 걷힙니다 —
         * <b>그 사슬 중 하나만 끊겨도 영영 남습니다.</b> 채점이 다 끝났는데도
         * "분석하고 있습니다"가 계속 떠 있는 것을 실제로 겪었습니다.
         *
         * <p>어느 링크가 끊겼는지는 아직 모릅니다. 이 고침은 <b>사슬을 타지 않으므로</b>
         * 어느 쪽이든 듣습니다 — [I72]·[I259]와 같은 모양입니다.
         */
        startScorePolling(propertyId) {
            this.stopScorePolling();
            let attempts = 0;
            this._scoreTimer = setInterval(async () => {
                // 멈추는 조건이 넷 다 있어야 한다 (설계 I72)
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

        /**
         * 실거래가 채워질 때까지 다시 묻는다 (설계 I259).
         *
         * <p>화면은 기다리지 않고 배경에서 받아 옵니다([I106]). 그래서 <b>새로고침해야
         * 보였습니다</b> — 눌러서 연 사람은 그걸 알 길이 없습니다.
         *
         * <p>AI 추천이 이미 같은 방식으로 돕니다([I72]). 같은 모양으로 맞춥니다.
         */
        startRefPolling(propertyId) {
            this.stopRefPolling();
            let attempts = 0;
            this._refTimer = setInterval(async () => {
                // 배경 조회는 12개월치라 오래 걸린다. 그래도 끝은 있어야 한다
                // 멈추는 조건이 넷 다 있어야 한다 (설계 I72) — 하나라도 빠지면
                // 탭이 열려 있는 동안 계속 두드린다
                const gaveUp = ++attempts > REF_POLL_MAX_ATTEMPTS;
                if (gaveUp || !this.showM2
                        || !this.detailItem || this.detailItem.property.id !== propertyId) {
                    this.stopRefPolling();
                    // <b>그만 물으면 그만 돈다고 말해야 한다 (설계 I262).</b>
                    // 전에는 조용히 멈추기만 해서 프로그래스바가 <b>영원히</b> 돌았습니다.
                    // 아직 열려 있는 그 매물일 때만 끕니다 — 남의 화면을 덮지 않게
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
                // 열려 있는 것이 그 사이 바뀌었으면 덮지 않는다
                if (this.detailItem && this.detailItem.property.id === propertyId) {
                    this.detailRef = body;
                }
                if (!body.looking) {
                    this.stopRefPolling();
                }
            }, REF_POLL_INTERVAL_MS);
        },

        /** 지쳐서 멈춘 뒤 사람이 직접 다시 묻는다 (설계 I262). */
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

        /** 매수 조건을 가르는 항목만 — 토지거래허가구역·정비구역 등 (설계 I69). */
        notableLandUse() {
            return this.detailLandUse.filter(l => l.notable);
        },

        /**
         * 포함 / 저촉 / 접함으로 묶는다. 35건이 통째로 나오는데 나열만 하면 읽히지 않고,
         * 무엇이 실제로 적용되는지도 가려지지 않는다 (설계 I69).
         */
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

        /**
         * AI 추천도가 아직 안 왔으면 결과가 올 때까지 짧게 폴링한다 (설계 I72).
         *
         * <p>멈추는 조건이 넷 다 있어야 한다 — 하나라도 빠지면 탭이 열려 있는 동안 계속 두드린다.
         * ① 결과 도착 ② 진행 중이 아님 ③ 모달 닫힘 ④ 시도 상한.
         */
        startLlmPolling(propertyId) {
            this.stopLlmPolling();
            let attempts = 0;
            this._llmTimer = setInterval(async () => {
                // ③ 모달이 닫혔거나 다른 매물로 옮겨갔다
                if (!this.showM2 || !this.detailItem || this.detailItem.property.id !== propertyId) {
                    this.stopLlmPolling();
                    return;
                }
                // ④ 2초 × 60 = 2분이면 그만 본다
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
                    // ① 결과가 왔다. 총점도 함께 움직이므로 목록을 다시 읽는다
                    this.detailLlm = body;
                    this.llmPending = false;
                    this.stopLlmPolling();
                    await this.loadProperties();
                    return;
                }
                // ② 진행 중이 아닌데 결과도 없다 — 실패했거나 LLM이 꺼져 있다
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
            // 남겨 두면 다음에 연 매물의 자리에 이전 매물 값이 잠깐 비친다 (설계 I112)
            this.detailLlm = null;
            this.detailLandUse = [];
            this.detailImages = [];
            this.llmPending = false;
            this.stopLlmPolling();
            this.stopRefPolling();
            this.stopScorePolling();
        },

        /**
         * 사진 줄을 끌어서 넘긴다 (설계 I214).
         *
         * <p>가로로 넘치는 줄인데 <b>마우스로는 스크롤바를 잡아야</b> 했습니다.
         * 손가락으로 미는 감각이 여기서도 자연스럽습니다.
         *
         * <p><b>클릭과 갈라야 합니다.</b> 조금이라도 끌었으면 그건 넘긴 것이지
         * 사진을 누른 것이 아닙니다 — 안 그러면 넘길 때마다 확대창이 뜹니다.
         * 문턱을 두어 손떨림은 클릭으로 남깁니다.
         */
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
                // 끄는 동안 사진이 선택되는 것을 막는다
                e.preventDefault();
            };
            const up = () => {
                window.removeEventListener('pointermove', move);
                window.removeEventListener('pointerup', up);
                // 클래스는 <b>클릭이 지나간 뒤</b> 벗긴다. 바로 벗기면 방금 끝낸
                // 드래그가 클릭으로 살아나 확대창이 뜬다
                if (moved > this.DRAG_THRESHOLD) {
                    setTimeout(() => strip.classList.remove('is-dragging'), 0);
                } else {
                    strip.classList.remove('is-dragging');
                }
            };
            window.addEventListener('pointermove', move);
            window.addEventListener('pointerup', up);
        },

        /**
         * 아직 채점 전인가 (설계 I220).
         *
         * <p>등록 응답이 <b>보정을 기다리지 않고</b> 돌아오므로, 카드가 먼저 뜨고
         * 점수는 몇 초 뒤에 채워집니다. 그 사이를 <b>0점으로 보여 주면 안 됩니다</b> —
         * "나쁜 매물"과 "아직 안 잰 매물"은 다릅니다.
         *
         * <p>판 번호 감시(I85)가 3초마다 확인하다가 채워지면 목록을 다시 받습니다.
         */
        scoring(scored) {
            return !!scored && (scored.scores || []).length === 0;
        },

        /** 상세에 뿌릴 평면도 — 매물당 한 장 (설계 I63). */
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

        /** 평면도는 매물당 한 장 (설계 I63). */
        get floorPlan() {
            return this.photoImages.find(i => i.imageType === 'FLOOR_PLAN') || null;
        },

        get photos() {
            return this.photoImages.filter(i => i.imageType === 'PHOTO');
        },

        /**
         * 크게 본다 (설계 I203).
         *
         * <p>사진은 이제 <b>두 곳</b>에 뜹니다 — 사진 모달과 매물 상세. 뷰어가
         * `photoImages` 만 보면 상세에서 연 사진이 <b>엉뚱한 장</b>을 띄웁니다.
         * 어느 목록을 걷는지 열 때 함께 넘깁니다.
         */
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

        /**
         * 좌우 방향키로 넘긴다 (설계 I203).
         *
         * <p>사진을 여러 장 볼 때 <b>화살표를 누르러 마우스를 옮기는 것</b>이 번거롭습니다.
         * 뷰어가 떠 있을 때만 받습니다 — 아니면 목록에서 방향키를 눌러도 반응합니다.
         */
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

        /**
         * 사진 모달에서 올리거나 지운 것을 <b>상세에도</b> 반영한다 (설계 I212).
         *
         * <p>사진 모달은 매물 상세 <b>위에 떠 있습니다.</b> 닫으면 아래에 상세가
         * 그대로 있는데, 방금 올린 사진이 <b>거기엔 없습니다</b> — 상세는 열 때
         * 한 번 받아 온 목록을 들고 있기 때문입니다.
         *
         * <p><b>같은 매물일 때만</b> 다시 받습니다. 다른 매물의 사진을 만지고 있다면
         * 상세를 건드릴 이유가 없습니다.
         */
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

        /**
         * 고른 파일을 종류에 맞춰 올린다. 매물사진은 여러 장을 한 번에 고를 수 있다.
         * 파일을 고르는 순간 올라가므로 별도의 '업로드' 버튼이 없다 (설계 I63).
         */
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
                    const res = await fetch(`/api/properties/${this.photoProperty.property.id}/images`, {
                        method: 'POST',
                        body: form
                    });
                    if (!res.ok) {
                        this.error = `${file.name} 업로드에 실패했습니다`;
                        break;
                    }
                }
                await this.loadPhotoImages();
                await this.refreshDetailImages();
            } catch (e) {
                this.error = '네트워크 오류가 발생했습니다';
            } finally {
                // 같은 파일을 다시 고를 수 있게 비운다
                event.target.value = '';
                this.loading = false;
            }
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
                    // 지운 것도 상세에서 같이 사라져야 한다 (설계 I212)
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

        /**
         * ID 저장 (설계 I190).
         *
         * <p><b>브라우저에 남깁니다.</b> 서버에 둘 값이 아닙니다 — 로그인하기 <b>전에</b>
         * 필요한 것이라 그때는 누구인지도 모릅니다.
         *
         * <p>비밀번호는 <b>절대 저장하지 않습니다.</b> ID 만입니다.
         */
        rememberLoginId() {
            try {
                if (this.loginForm.rememberId) {
                    localStorage.setItem('halley.loginId', this.loginForm.loginId || '');
                } else {
                    localStorage.removeItem('halley.loginId');
                }
            } catch (e) {
                // 사생활 보호 모드면 localStorage 가 막힌다. 저장이 안 될 뿐 로그인은 된다
            }
        },

        /**
         * 로그인 화면을 열면 바로 칠 수 있게 (설계 I209).
         *
         * <p>`x-effect` 로 걸어 뒀는데 <b>커서가 가지 않았습니다.</b> 모달은
         * `x-show` 라 요소가 처음부터 있고, 그래서 효과가 <b>`showLogin` 이 아직
         * 거짓일 때 한 번 돌고 맙니다</b>. 이 저장소에 이미 되는 방식이 있어
         * 그쪽에 맞춥니다(`openPasteModal`).
         *
         * <p><b>ID 가 이미 채워져 있으면 비밀번호로 갑니다.</b> ID 저장(I190)을 켠
         * 사람에게 채워진 칸을 다시 가리키는 것은 한 번 더 누르게 하는 일입니다.
         */
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
                // 위와 같다
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
                    // 세션을 다시 읽어야 다음 단계(프로필 설정)로 넘어간다. 목록 로드도 checkSession이 맡는다
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
                await fetch('/api/auth/logout', { method: 'POST' });
            } catch (e) {
                // ignore
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
            // 열려 있던 것을 전부 닫는다 (설계 I182). 예전에는 로그아웃해도 남아,
            // 다시 로그인하면 앞사람이 보던 모달이 그대로 떠 있었다
            this.closeAllModals();
            this.showLogin = true;
            this.focusLogin();
            // 지도 오버레이만이 아니라 <b>작업 중이던 것</b>도 지운다 (설계 I179).
            // 예전에는 로그아웃해도 남아, 다른 계정으로 들어오면 앞 사람의 동선이 보였다
            this.resetItineraryState();
        },

        /**
         * 계산한 것을 지운다 (설계 I206).
         *
         * <p>고른 매물·출발지는 <b>남깁니다.</b> 지우는 것은 "계산 결과"이지
         * "내가 고른 것"이 아닙니다 — 매물 열둘을 다시 고르게 하면 벌입니다.
         *
         * <p>담아 둔 것(draft)도 같이 비웁니다. 화면만 지우면 <b>새로고침하는 순간
         * 되살아납니다.</b>
         */
        clearItineraryResult() {
            this.itinResult = null;
            this.clearItinerary();
            this.error = null;
            this.saveItineraryDraft();
            // 매물 마커는 매물 화면의 것이라 그대로 둔다
        },

        /** 임장 플래너의 화면 상태를 처음으로 되돌린다 (설계 I179). */
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
            // 만료 시각을 모르면 아무 판단도 하지 않는다 (설계 I120)
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

        /** 목록 URL 한 곳에서만 만든다 — 첫 쪽과 다음 쪽이 다른 조건으로 가면 순서가 어긋난다 */
        /** 지금 아카이빙 탭을 보고 있는가 (설계 I241) */
        get archiveTab() {
            return this.dealTypeFilter === 'ARCHIVE';
        },

        /** 지금 보고 있는 탭을 조건으로 옮긴다 — 목록·지도·판 번호가 <b>같은 것</b>을 봐야 한다 */
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

        /** 첫 쪽부터 다시 받는다 (설계 I240). 정렬·필터가 바뀌면 이어 붙이면 안 된다 */
        async loadProperties() {
            this.propertyPage = 0;
            // 목록이 오기 전에는 '등록된 매물이 없습니다'가 떠서 정말 없는 줄 알았다 (설계 I122)
            const { ok, body } = await this.withLoading('properties',
                () => this.request(this.propertiesUrl(0)));
            if (ok && body) {
                this.properties = body.items || [];
                this.propertyTotal = body.total || 0;
                this.propertyHasNext = !!body.hasNext;
                this.archivedTotal = body.archivedTotal || 0;
            }
            // 지도는 잘리기 전을 본다 — 목록과 따로 받는다 (설계 I240)
            await this.loadPins();
            this.renderMap();
        },

        /**
         * 다음 쪽을 이어 붙인다 (설계 I240).
         *
         * <p><b>겹쳐 붙지 않게</b> 이미 있는 id 는 버립니다. 뒤에서 채점이 끝나
         * 순서가 조금 바뀌면([I85]) 같은 매물이 두 쪽에 걸릴 수 있습니다 —
         * 그때 그냥 이어 붙이면 <b>화면에 두 번</b> 나옵니다.
         */
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

        /** 지도·임장 플래너가 쓰는 전체 목록 (설계 I240). */
        async loadPins() {
            const { ok, body } = await this.request('/api/properties/pins' + this.listFilterQuery());
            if (ok) {
                this.pins = body || [];
            }
        },

        /**
         * 뒤에서 채점이 끝나면 화면을 맞춘다 (설계 I85).
         *
         * 채점은 사용자가 보고 있는 동안 두 번 더 바뀐다 — 보정이 끝날 때, AI 응답이 올 때.
         * 목록을 통째로 다시 받아 비교하면 무거우니 판 번호만 확인하고 달라졌을 때만 받는다.
         *
         * 탭이 가려져 있으면 쉬었다가, 돌아올 때 한 번 맞춘다. 안 보이는 화면을 위해
         * 계속 물어볼 이유가 없다.
         */
        /**
         * 바닥이 보이면 다음 쪽을 부른다 (설계 I240).
         *
         * <p><b>{@code IntersectionObserver} 로 봅니다.</b> 스크롤 위치를 재려면
         * 어디가 스크롤되는지 알아야 하는데, 이 화면은 <b>넓을 때는 목록 칸이,
         * 좁을 때는 창 자체가</b> 스크롤됩니다(`.list-panel { overflow: visible }`).
         * 두 경우를 손으로 가르면 한쪽이 반드시 어긋납니다.
         *
         * <p>감시점은 <b>늘 자리에 둡니다.</b> 더 없을 때 감춰 버리면
         * ({@code display:none}) 관찰이 풀려, 다시 생겨도 <b>안 걸립니다.</b>
         */
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
            // 목록과 <b>같은 조건</b>으로 묻는다 (설계 I241). 다른 것을 세면
            // 개수가 늘 어긋나 3초마다 목록을 다시 받는다
            const { ok, body } = await this.request(
                '/api/properties/score-versions' + this.listFilterQuery());
            if (!ok || !body) {
                return;
            }
            const latest = new Map(body.map(v => [v.propertyId, v.scoreVersion]));
            const changed = this.properties.some(
                r => latest.has(r.property.id) && latest.get(r.property.id) !== r.scoreVersion);
            // 매물이 늘거나 줄어도 목록을 다시 받아야 한다.
            // 받은 쪽 수가 아니라 <b>전체 건수</b>와 견준다 (설계 I240) — 30건씩 받는
            // 동안에는 `properties.length` 가 전체와 다른 것이 정상이라, 그대로 두면
            // 3초마다 목록을 다시 받는다
            if (changed || latest.size !== this.propertyTotal) {
                await this.loadProperties();
                if (this.detailItem) {
                    this.syncDetailItem();
                }
            }
        },

        /** 상세 모달이 열려 있으면 그 안의 점수도 함께 갱신한다 — 목록만 바뀌면 어긋난다. */
        syncDetailItem() {
            const fresh = this.properties.find(r => r.property.id === this.detailItem.property.id);
            if (fresh) {
                this.detailItem = fresh;
                if (this.showScoreModal && this.scoreProperty) {
                    this.scoreProperty = fresh;
                }
            }
        },

        /**
         * 줄 세우기를 <b>서버로 넘겼다</b> (설계 I240).
         *
         * <p>[I221]에서는 여기서 세웠습니다. 목록이 통째로 왔으니 그때는 맞았습니다.
         * 30건씩 잘라 받는 지금은 <b>받은 것 안에서만</b> 세우게 되어,
         * 2쪽의 1등이 1쪽의 꼴찌보다 앞에 옵니다.
         *
         * <p>{@code applySoldOutFilter}·{@code sortProperties}·{@code criterionScore} 를
         * 지웠습니다. <b>같은 규칙이 두 곳에 있으면 반드시 어긋납니다</b>
         * (I230·I232·I237).
         *
         * <p>값은 서버의 {@code PropertySort} 와 <b>같은 이름</b>을 씁니다 — 화면에서
         * 'default', 서버에서 'DEFAULT' 로 두면 옮기는 표가 또 하나 생깁니다.
         */
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

        /** 정렬이 바뀌면 <b>첫 쪽부터</b> 다시 받는다 — 받은 것만 다시 세우면 전체 순서가 아니다 */
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

        /**
         * 안 볼 매물을 치워 둔다 (설계 I241).
         *
         * <p><b>지우는 것과 다릅니다.</b> 코멘트도 채점도 실거래 이력도 그대로 남고,
         * 아카이빙 탭에서 그대로 보입니다 — 언제든 되돌립니다. 삭제는 되돌릴 수 없어
         * "일단 안 보이게"에 쓸 수 없었고, 그래서 <b>안 볼 매물이 목록에 계속</b>
         * 있었습니다.
         */
        archiveProperty(item) {
            this.askConfirm('아카이빙',
                `'${item.property.name}' 매물을 아카이빙할까요?\n목록에서는 사라지고 아카이빙 탭에 남습니다.`,
                () => this.setListingStatus(item.property.id, 'ARCHIVED'));
        },

        /** 치워 둔 것을 되돌린다 — 묻지 않는다. 되돌리는 일은 되돌릴 수 있다 (설계 I241) */
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
            // 목록에서 빠지거나 들어오므로 첫 쪽부터 다시 받는다 (설계 I240)
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

        // 프로필에 연소득·보유 현금이 있으므로 모달을 열면 바로 계산한다 (설계 I55)
        openLoanModal(item) {
            this.loanProperty = item;
            // MCI/MCG는 기본으로 켠다 (설계 I114). 대부분 가입하고, 꺼져 있으면 방공제
            // 5,500만원이 빠진 한도가 첫 화면에 뜬다 — 실제보다 낮게 보여 오해를 부른다.
            // 열자마자 켠 상태로 계산하므로 호출은 한 번뿐이다
            this.loanForm = { firstHome: false, mortgageInsured: true, ownedHouseCount: 0,
                rateType: 'VARIABLE' };
            this.loanOverride = { annualIncome: '', cash: '', existingLoan: '' };
            this.loanShowInputs = false;
            this.loanResult = null;
            this.error = null;
            this.showLoanModal = true;
            this.withLoading('loan', () => this.runLoanEstimate());
        },

        /**
         * 슬라이더로 대출액을 줄이면 월 상환액·필요 현금이 따라 움직인다.
         * 서버가 월 이율과 기간을 함께 내려주므로 여기서 다시 계산한다 — 매번 서버를 부르지 않는다.
         */
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

        /** 매매가에서 대출을 뺀 자기자본. 취득세는 여기에 더 필요하다. */
        loanOwnCapital() {
            const asking = this.loanResult?.askingPrice || 0;
            return Math.max(0, asking - this.loanAmount);
        },

        /** 보유 현금으로 자기자본 + 취득세를 감당할 수 있는가. 음수면 모자란다. */
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

        /** 한도를 무엇이 묶고 있는지 — 규제(LTV)인지 소득(DSR)인지 */
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
                        // 값을 비워 보내면 서버가 내 프로필로 채운다. 이 모달에서 손댄 값만 덮어쓴다.
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

        /**
         * 왜 비었는가 (설계 I232).
         *
         * <p>"실거래 내역이 없습니다"만 뜨니, <b>코드가 틀렸는지 · 단지명이 안 맞는지 ·
         * 면적이 안 맞는지</b> 알 길이 없었습니다. 실제로 그것 때문에 원인을 짚는 데
         * 오래 걸렸습니다 — 화면이 아는 것을 말하지 않았습니다.
         */
        refEmptyReason() {
            const card = this.refCard;
            if (!card) {
                return '';
            }
            if (card.looking) {
                // 아직 안 물어봤다. "없다"고 단정하면 안 된다 (설계 I259)
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

        /** 화면에서도 쓴다 (`:min`). 위의 함수를 그대로 부른다 — 계산이 두 벌이면 갈린다 */
        todayIso() {
            return todayIso();
        },

        /**
         * 고른 날에 고를 수 있는 가장 이른 시각 (설계 I207).
         *
         * <p>오늘이면 <b>지금</b>부터, 다른 날이면 하루 종일입니다.
         */
        minItinTime() {
            if (this.itinDate !== this.todayIso()) {
                return '00:00';
            }
            const now = new Date();
            return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
        },

        /**
         * 지나간 시각인가 (설계 I207).
         *
         * <p>`min` 만으로는 부족합니다 — 브라우저가 <b>손으로 친 값은 막지 않고</b>,
         * 날짜를 고른 뒤 시각을 바꾸면 조합이 과거가 될 수 있습니다.
         */
        itinDepartsInPast() {
            if (!this.itinDate) {
                return false;
            }
            const departAt = new Date(`${this.itinDate}T${this.itinWindowStart || '09:00'}`);
            return !Number.isNaN(departAt.getTime()) && departAt.getTime() < Date.now();
        },

        /**
         * 기본값이 낡았으면 밀어 준다 (설계 I207).
         *
         * <p>날짜는 오늘, 시각은 09:00 이 기본입니다. 그래서 <b>오후에 플래너를 열면
         * 곧바로 "이미 지났습니다"</b>가 뜹니다 — 아무것도 안 했는데 혼나는 셈입니다.
         *
         * <p><b>사용자가 고친 값은 안 건드립니다.</b> 화면에 들어올 때와 날짜를 바꿀 때만
         * 부릅니다 — 그때 화면에 있는 것은 기본값이지 사용자의 뜻이 아닙니다.
         *
         * <p>다음 15분 단위로 밉니다. 23:58 이면 <b>날짜도 같이</b> 내일로 넘어갑니다 —
         * `setMinutes` 가 시·일 넘김을 알아서 합니다.
         */
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
            // 지나간 시각의 교통을 물을 수는 없다 (설계 I207).
            // 카카오는 과거 시각도 받아 주지만, 그 답으로 세우는 계획이 뜻이 없다
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
                        // 날짜가 있어야 그 요일의 길을 받는다 — 화요일 14시와 일요일 14시는 다르다 (설계 I196)
                        visitDate: this.itinDate || null,
                        windowStart: this.itinWindowStart || null,
                        stayMinutes: toNum(this.itinStay)
                    })
                });
                if (ok) {
                    // <b>구간을 하나도 못 받았으면 결과가 아니다 (설계 I274).</b>
                    // 순서는 모두 같은 값(못 감)으로 매긴 것이라 <b>아무 뜻이 없고</b>,
                    // 그걸 늘어놓으면 사람은 계산된 동선으로 읽는다
                    if (body?.status === 'UNAVAILABLE') {
                        // 판단은 <b>서버가</b> 한다 (설계 I274) — 화면이 따로 세면
                        // 규칙이 두 벌이 되고, 이 저장소는 그때마다 갈렸다
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

        /**
         * 방문완료 (설계 I197).
         *
         * <p>계획을 저장하지 않으므로 <b>이것만 DB에 남습니다.</b> 계산 결과는
         * draft 캐시로 충분하지만, 어디를 가 봤는지는 그렇지 않습니다.
         *
         * <p><b>화면을 먼저 바꾸고 서버에 보냅니다.</b> 현장에서 누르는 것이라
         * 왕복을 기다리게 하지 않습니다. 실패하면 되돌립니다 — 눌렀는데 안 눌린 것으로
         * 남아 있으면 다음에 또 갑니다.
         */
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
            // 지도에 흐린 표시가 바로 반영되도록 지도용 목록도 맞춰 둔다 (설계 I240).
            // 다음 목록 요청 때 서버 값으로 덮이지만, 그 사이에 어긋나 보이면 안 된다
            this.pins = this.pins.map(p => p.id === propertyId ? { ...p, visited } : p);
            // 기본 정렬은 임장 여부로 가른다 (설계 I221) — 체크가 바뀌면 순서도 바뀐다.
            // 줄 세우는 것은 서버라 다시 받는다 (설계 I240)
            if (this.sortKey === 'DEFAULT') {
                this.loadProperties();
            }
        },

        /**
         * 이 매물에 다녀왔는가 (설계 I226).
         *
         * <p><b>쾌적함이 첫째 근거입니다.</b> 직접 가 보지 않으면 매길 수 없는
         * 항목이라, 점수가 있다는 것은 다녀왔다는 뜻입니다([I121]). 따로 칸을 두면
         * 사람이 또 눌러야 한다는 것이 그때의 결론이었는데, [I197]에서 제가
         * <b>정확히 그 칸을 만들었습니다.</b>
         *
         * <p>두 신호를 <b>합칩니다.</b> 이미 눌러 둔 체크를 버릴 이유가 없고,
         * 경로에서 바로 체크하는 것도 편합니다 — 다만 <b>안 눌러도 됩니다.</b>
         *
         * <p><b>내 점수로 봅니다</b>(`myScore`). 그룹 평균으로 보면 남이 다녀온 곳이
         * 내 목록에서 뒤로 밀립니다 — 정작 나는 안 가 봤는데요([I118]과 같은 가름).
         *
         * <p><b>판단은 서버가 합니다</b>(설계 I240). 목록이 30건씩 잘려 오므로
         * 여기서 `properties` 를 뒤지면 <b>아직 안 받은 매물은 안 가 본 것</b>이
         * 됩니다 — 지도에서 흐리게 칠해야 할 것이 안 칠해집니다.
         * 전체를 담은 `pins` 를 봅니다.
         */
        isVisited(propertyId) {
            if (this.itinVisited.includes(propertyId)) {
                return true;
            }
            const pin = this.pins.find(p => p.id === propertyId);
            return !!(pin && pin.visited);
        },

        /**
         * 쾌적함 때문에 켜진 것인가 (설계 I228).
         *
         * <p>그렇다면 <b>체크를 끌 수 없어야</b> 합니다. 근거가 채점이라 끄려면
         * 그 점수를 지워야 하는데, 그건 채점 화면의 일입니다 — 여기서 눌러도
         * 아무 일이 안 일어나면 <b>고장으로 보입니다.</b>
         */
        visitedByComfort(propertyId) {
            const pin = this.pins.find(p => p.id === propertyId);
            return !!(pin && pin.visitedByComfort);
        },

        /** 가 본 곳을 서버에서 받아 온다 — 새로고침해도, 다른 기기에서도 남는다. */
        async loadVisited() {
            const { ok, body } = await this.request('/api/itinerary/visits')
                .catch(() => ({ ok: false }));
            if (ok && Array.isArray(body)) {
                this.itinVisited = body;
            }
        },

        /** 아직 안 받은 쪽의 매물일 수 있다 — 전체를 담은 `pins` 를 먼저 본다 (설계 I240) */
        propertyName(id) {
            const pin = this.pins.find(p => p.id === id);
            if (pin) {
                return pin.name;
            }
            const item = this.properties.find(x => x.property.id === id);
            return item ? item.property.name : '#' + id;
        },

        /**
         * 내 임장 작업 상태를 불러온다 (설계 I179).
         *
         * <p><b>계정마다 다릅니다.</b> 서버가 사용자별로 담아 두므로 새로고침해도 남고,
         * 다른 계정으로 들어오면 <b>그 사람 것</b>이 뜹니다.
         */
        async loadItineraryDraft() {
            const { ok, body } = await this.request('/api/itinerary/draft').catch(() => ({ ok: false }));
            if (ok && body) {
                this.itinProperties = body.propertyIds || [];
                this.itinMode = body.travelMode || 'DRIVING';
                this.itinResult = body.result || null;
            }
            this.renderItinerary();
        },

        /** 고른 매물·이동수단·결과가 바뀌면 담아 둔다. 실패해도 화면은 그대로 쓴다. */
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

        /**
         * 실제 이동 동선을 그린다 (설계 I177).
         *
         * <p>여태 매물 사이를 <b>직선</b>으로 이었다. 실제로 그렇게 갈 수는 없으므로
         * 거리감이 왜곡된다 — 강 건너편이 가까워 보인다.
         *
         * <p>서버가 구간마다 경로선을 준다. <b>못 받은 구간만</b> 직선으로 잇는다 —
         * 하나가 비었다고 전체를 직선으로 되돌리면 받은 것까지 버리는 셈이다.
         */
        /**
         * 서울 지하철 호선 색 (설계 I195).
         *
         * <p>ODsay 의 `type` 이 호선 번호입니다. <b>운영사가 쓰는 실제 색</b>을 씁니다 —
         * 2호선이 초록이 아니면 지도에서 어느 선인지 못 알아봅니다.
         */
        SUBWAY_COLORS: {
            1: '#0052A4', 2: '#00A84D', 3: '#EF7C1C', 4: '#00A5DE', 5: '#996CAC',
            6: '#CD7C2F', 7: '#747F00', 8: '#E6186C', 9: '#BDB092',
            21: '#7CA8D5', 22: '#ED8B00',
            101: '#0090D2', 104: '#77C4A3', 107: '#6FB245', 108: '#0C8E72',
            109: '#D31145', 110: '#FDA600', 111: '#003DA5', 112: '#B7C452',
            113: '#8FC63F', 114: '#A17E46', 115: '#FABE00', 116: '#6789CA',
            117: '#9A6292'
        },

        /**
         * 정체 상태 색 (설계 I195).
         *
         * <p>카카오 `traffic_state`: 1 정체 · 2 지체 · 3 서행 · 4 원활 · 0 정보없음.
         * <b>막히는 곳이 빨강</b>입니다 — 숫자가 클수록 잘 흐릅니다.
         */
        TRAFFIC_COLORS: { 1: '#d64545', 2: '#e08b2f', 3: '#e0c22f', 4: '#3f9e56', 0: '#8a8378' },

        /**
         * 구간 하나를 무슨 색으로 그릴까 (설계 I195).
         *
         * <p>서버는 <b>무엇인지</b>만 말합니다(`SUBWAY_2` · `BUS_3` · `TRAFFIC_1`).
         * 색을 고르는 것은 화면의 몫입니다 — 색을 바꾸려고 서버를 고치지 않습니다.
         *
         * <p>모르는 값은 <b>회색</b>입니다. 그럴듯한 색을 지어내면 없는 노선이 있는
         * 것처럼 보입니다.
         */
        segmentStyle(style) {
            const [kind, raw] = String(style || '').split('_');
            const code = Number(raw);
            if (kind === 'SUBWAY') {
                return { color: this.SUBWAY_COLORS[code] || '#5a6b7a', weight: 6, dash: 'solid' };
            }
            if (kind === 'BUS') {
                // 서울 버스: 간선 파랑, 지선·마을 초록, 광역 빨강 — type 이 그 갈래다
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

        /**
         * 지도에 경로를 그린다 — <b>구간마다 다른 색으로</b> (설계 I177 · I195).
         *
         * <p>한 색으로 이어 그리면 어디서 갈아타는지, 어디가 막히는지가 지도에서
         * 사라집니다. 서버가 색이 갈리는 자리마다 끊어 주므로 구간 하나에
         * 선 하나를 긋습니다.
         *
         * <p>구간 사이가 벌어지면 <b>회색 점선</b>으로 잇습니다. 대중교통에서 그 틈은
         * 도보입니다 — ODsay 가 도보 좌표를 주지 않으니 없는 것을 지어내지 않고,
         * 대신 이어진 길이 아님이 보이게 그립니다.
         */
        drawItineraryPath(fallbackPoints) {
            const legs = (this.itinResult && this.itinResult.legs) || [];
            const bounds = new kakao.maps.LatLngBounds();
            this._itinPolylines = [];

            /**
             * 선 하나를 <b>두 번</b> 긋는다 (설계 I206).
             *
             * <p>지도 자체가 색이 많아 얇은 색선은 도로와 섞여 안 보입니다.
             * <b>굵은 검정 선을 깔고 그 위에</b> 색선을 얹으면 어떤 색이든 떠오릅니다.
             *
             * <p>`zIndex` 를 나눠야 합니다 — 안 그러면 나중에 그은 테두리가
             * 앞 구간의 색선을 덮습니다.
             */
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

            // 경로선을 못 받은 구간은 점선 직선으로 — 받은 것과 눈으로 구분된다
            if (drawn.length < legs.length || legs.length === 0) {
                if (fallbackPoints.length >= 2) {
                    add(fallbackPoints, '#8a8378', 3, 'shortdash');
                }
            }
            if (this._itinPolylines.length > 0) {
                this.map.setBounds(bounds);
            }
        },

        /**
         * ODsay `type` → 노선 이름 (설계 I195).
         *
         * <p>1~9 는 호선 번호 그대로지만 <b>100번대는 이름이 따로</b> 있습니다.
         * 109를 "109호선"이라 쓰면 없는 노선이 됩니다.
         */
        SUBWAY_NAMES: {
            21: '인천1호선', 22: '인천2호선',
            101: '공항철도', 104: '경의중앙선', 107: '에버라인', 108: '경춘선',
            109: '신분당선', 110: '의정부경전철', 111: '경강선', 112: '우이신설선',
            113: '서해선', 114: '김포골드라인', 115: '수인분당선', 116: '신림선',
            117: 'GTX-A'
        },

        /**
         * 범례에 <b>실제 노선 이름</b>을 (설계 I195 · I206).
         *
         * <p>처음에는 색깔 코드(`BUS_3`)에서 이름을 지었더니 <b>전부 "버스"</b>였습니다 —
         * 몇 번 버스인지가 알림의 핵심인데 빠졌습니다. `type` 은 노선 번호가 아니라
         * 간선·지선 같은 <b>갈래</b>입니다.
         *
         * <p>번호는 구간 안내(`steps`)에 이미 있습니다. 경로선 구간과 안내는 <b>같은
         * 순서</b>라 짝지을 수 있습니다 — 다만 <b>수가 어긋나면 짝을 짓지 않습니다.</b>
         * 좌표가 모자란 lane 은 건너뛰므로 어긋날 수 있는데, 그때 억지로 짝지으면
         * <b>7호선 색에 다른 노선 이름</b>이 붙습니다. 틀린 이름보다 덜 친절한 이름이 낫습니다.
         *
         * <p>이번 경로에 <b>실제로 나온 것</b>만 답니다. 스무 개 호선을 다 늘어놓으면
         * 범례가 경로보다 깁니다.
         */
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

        /** 구간 안내에서 읽은 이름 — "7호선" · "146번 버스". */
        rideName(step) {
            if (!step || !step.lineName) {
                return step && step.kind === 'BUS' ? '버스' : '지하철';
            }
            return step.kind === 'BUS' ? `${step.lineName}번 버스` : step.lineName;
        },

        /**
         * 짝을 못 지었을 때의 이름 (설계 I206).
         *
         * <p>지하철은 `type` 이 호선 번호라 이름을 알 수 있습니다.
         * <b>버스는 모릅니다</b> — `type` 이 갈래일 뿐이라 "버스"까지만 말합니다.
         */
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

        /**
         * 구간 한 줄 (설계 I176 · I193).
         *
         * <p>자가용은 <b>어느 길로 얼마나</b>다 — `stationCount` 자리에 미터가 온다.
         */
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

        /**
         * 그 매물에 몇 시에 닿는가 (설계 I194).
         *
         * <p><b>계획을 저장하기 전에도</b> 보여 줍니다. 서버가 계획을 만들 때 쓰는 식과
         * 같습니다(`buildStops`) — 도착 = 출발시각 + 이동시간 누계 + 체류시간 누계.
         *
         * <p>화면에서 계산하는 이유는 <b>출발시각·체류시간을 바꾸면 바로 보여야</b>
         * 하기 때문입니다. 서버에 다시 물으면 그때마다 왕복입니다.
         */
        /**
         * 몇 시에 닿는가 — <b>앞 구간을 다 알아야 말할 수 있다</b> (설계 I270).
         *
         * <p>이동시간을 못 받은 구간이 앞에 하나라도 있으면 도착 시각은
         * <b>알 수 없습니다.</b> 예전에는 그것을 999분으로 세어 `06:39 (+1일)`
         * 같은 값을 내놓았습니다 — 그럴듯해서 아무도 의심하지 않습니다.
         *
         * @returns 모르면 null. 화면은 그때 아무것도 안 보여 준다
         */
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
            // 자정을 넘기면 그렇다고 말한다 — 09:20 만 보이면 오전으로 읽힌다
            const days = Math.floor(minutes / 1440);
            const h = Math.floor(minutes / 60) % 24;
            const m = minutes % 60;
            const clock = `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
            return days > 0 ? `${clock} (+${days}일)` : clock;
        },

        closeItinUnavailable() {
            this.showItinUnavailable = false;
        },

        /** 이 구간에 걸리는 시간 — <b>못 받았으면 그렇다고 말한다</b> (설계 I270). */
        legMinutesLabel(leg) {
            return leg && leg.minutes != null ? `${leg.minutes}분` : '이동시간 미확인';
        },

        /**
         * 합계를 <b>믿을 수 있는가</b> (설계 I270).
         *
         * <p>못 받은 구간이 있으면 합계는 그만큼 빠진 값입니다. 그 사실을 안 적으면
         * 사람은 그 수를 <b>전체 이동시간</b>으로 읽습니다.
         */
        itinTotalNote() {
            const unknown = this.itinResult?.unknownLegs || 0;
            return unknown > 0
                ? `구간 ${unknown}개는 이동시간을 받지 못했습니다 — 합계에 빠져 있습니다`
                : '';
        },

        /** 몇 번째 매물로 가는 구간인가 (설계 I192). 순서와 구간은 같은 자리다. */
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
                    // 매물이 생긴 뒤라야 사진을 붙일 수 있다 (설계 I204).
                    // 사진이 실패해도 매물은 이미 저장됐다 — 되돌리지 않고 말만 한다
                    // 등록·수정 모두 ScoredPropertyResponse 를 돌려준다 — id 는 그 안에 있다.
                    // body.id 로 읽으면 늘 undefined 라 사진이 조용히 안 올라간다
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

        /** 붙여넣기 화면에서 고른 사진 목록 — 화면에 이름을 보여 주려고 따로 둔다. */
        pastePickedCount() {
            return (this.pasteFloorPlan ? 1 : 0) + this.pastePhotos.length;
        },

        pickPasteImages(event, imageType) {
            const files = Array.from(event.target.files || []);
            if (imageType === 'FLOOR_PLAN') {
                // 평면도는 매물당 한 장이다 (설계 I63) — 마지막에 고른 것만 남긴다
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

        /** @returns 올리지 못한 파일들. 비어 있으면 전부 올라갔다 */
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
                const res = await fetch(`/api/properties/${id}/images`, { method: 'POST', body: form })
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
                dealType: dealCode,
                priceDeposit: toNum(value('priceDeposit')),
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
                // 붙여넣기 텍스트에는 URL이 없어 모달에서 직접 받는다 (설계 I62)
                sourceUrl: value('sourceUrl') || null,
                maintenanceFee: toNum(value('maintenanceFee')),
                roomBath: value('roomBath') || null,
                heatingType: value('heatingType') || null,
                // 비용·세금 (설계 I53)
                brokerageFee: toNum(value('brokerageFee')),
                brokerageRate: toNum(value('brokerageRate')),
                acquisitionTax: toNum(value('acquisitionTax')),
                propertyTax: toNum(value('propertyTax')),
                comprehensiveTax: value('comprehensiveTax') || null,
                schoolName: value('school') || null,
                schoolWalkMinutes: toNum(value('schoolMinutes')),
                // 중개사 — 등록번호가 같으면 서버가 기존 중개사를 갱신해 연결한다
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

        /**
         * 파싱 항목의 화면 이름 (설계 I159).
         *
         * <p><b>여기 없으면 영문 키가 그대로 뜹니다.</b> kbPrice 가 화면에 'kbPrice' 로
         * 보이던 것이 그것입니다 — 파서는 읽고 있는데 이름표만 빠져 있었습니다.
         */
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
                dealType: p.dealType || 'SALE',
                priceDeposit: p.priceDeposit ?? '',
                maintenanceFee: p.maintenanceFee ?? '',
                // 폼에 칸이 생겼으므로 carry 에서 뺐다 (설계 I160). 양쪽에 두면
                // carry 가 폼 값을 덮어써서 고쳐도 안 바뀐다
                kbPrice: p.kbPrice ?? '',
                addressRoad: p.addressRoad || '',
                sourceUrl: p.sourceUrl || '',
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
                parkingPerHousehold: p.parkingPerHousehold ?? '',
                moveInType: p.moveInType || '',
                moveInDate: p.moveInDate || '',
                editVersion: p.editVersion ?? null,
                // 폼에 칸이 없는 값들. 손대지 않고 그대로 돌려보낸다 (설계 I113)
                carry: {
                    dongHo: p.dongHo ?? null,
                    floorRaw: p.floorRaw ?? null,
                    floorBand: p.floorBand ?? null,
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

        /**
         * 주소로 좌표를 찾는다. <b>실패와 '결과 없음'을 구분해 알린다</b> — 예전에는 둘 다
         * 빈 배열로 끝나 화면에 아무 변화가 없었고, 사용자에게는 '호출이 안 되는' 것으로 보였다.
         */
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
                    // 카카오 주소검색은 실제 주소를 매칭한다. '화성시 동탄'처럼 법정동이 아닌
                    // 이름이나 단지명으로는 0건이 나온다
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
                // 폼에 칸이 없는 값을 먼저 깔고, 폼이 가진 값으로 덮는다 (설계 I113).
                // 이게 없으면 수정할 때마다 주차·방/욕실·난방·중개보수가 조용히 지워졌다
                ...(this.propertyForm.carry || {}),
                name: this.propertyForm.name,
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
                floorNo: toNum(this.propertyForm.floorNo),
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
            // 먼저 열고 나중에 채운다 (설계 I115). 응답을 기다렸다 열면 그동안
            // 화면에 아무 일도 일어나지 않아 눌린 건지 알 수 없다 — PostgreSQL 쪽이
            // 느릴 때 특히 그렇다. 목록에 실린 값으로 즉시 그리고, 새 값이 오면 갈아 끼운다
            this.applyScoreForm(item);
            this.error = null;
            this.showScoreModal = true;

            // 목록에 실린 값이 아니라 지금 값을 읽는다 (설계 I112). 보정·AI가 배경에서
            // 채우고 있어 목록을 받아 둔 시점과 지금이 다를 수 있다
            const fresh = await this.withLoading('score',
                () => this.request(`/api/properties/${item.property.id}`).catch(() => ({ ok: false })));
            // 기다리는 사이에 닫았거나 다른 매물로 옮겨 갔으면 덮어쓰지 않는다
            if (!this.showScoreModal || this.scoreProperty?.property?.id !== item.property.id) {
                return;
            }
            if (fresh.ok && fresh.body) {
                this.applyScoreForm(fresh.body);
            }
        },

        /**
         * 열려 있는 모달을 전부 닫는다 (설계 I182).
         *
         * <p><b>이름으로 찾습니다.</b> `show`로 시작하는 불리언을 훑어 끄면,
         * 나중에 모달이 늘어도 <b>여기를 고칠 일이 없습니다</b> — 목록을 손으로 관리하면
         * 하나 빠뜨렸을 때 그것만 남아 뜹니다.
         */
        closeAllModals() {
            Object.keys(this).forEach(key => {
                if (key.startsWith('show') && this[key] === true) {
                    this[key] = false;
                }
            });
            this.confirmState = null;
            // 닫힘은 -1 이다 (초기값·closePhotoViewer 와 같게). null 을 넣으면
            // `null >= 0` 이 true 라 열린 것으로 읽힌다
            this.photoViewerIndex = -1;
            this.resetModalScroll();
        },

        /** 모달 안쪽 스크롤을 맨 위로 (설계 I183). */
        resetModalScroll() {
            document.querySelectorAll('.modal-card').forEach(card => {
                card.scrollTop = 0;
            });
        },

        /**
         * 모달이 닫힐 때 그 안의 스크롤을 되돌린다 (설계 I183).
         *
         * <p>모달은 `x-show`로 <b>숨겨질 뿐 사라지지 않습니다</b> — 스크롤 위치가 그대로
         * 남아, 다음에 열면 <b>중간부터 보입니다.</b>
         *
         * <p>닫는 함수가 스물 몇 개라 각각에 넣으면 반드시 하나를 빠뜨립니다.
         * `style` 이 바뀌는 것을 지켜보면 <b>어느 경로로 닫혀도</b> 걸립니다.
         */
        watchModalClose() {
            const observer = new MutationObserver(records => {
                records.forEach(r => {
                    const modal = r.target;
                    if (modal.style.display === 'none') {
                        modal.querySelectorAll('.modal-card').forEach(card => {
                            card.scrollTop = 0;
                        });
                    }
                });
            });
            document.querySelectorAll('.modal').forEach(modal => {
                observer.observe(modal, { attributes: true, attributeFilter: ['style'] });
            });
        },

        /**
         * Esc 또는 배경 클릭으로 맨 위 모달을 닫는다 (설계 I122 · I155).
         *
         * <p>한때 배경 클릭을 막았습니다 — 긴 폼을 채우다 옆을 잘못 눌러 입력이 사라지는
         * 일이 있었습니다. <b>쓰기 불편하다는 쪽이 더 컸습니다.</b> 되살립니다.
         *
         * <p>강제 모달(로그인·비밀번호 변경·프로필 확인·세션 경고)은 <b>아래 목록에
         * 없습니다.</b> 그래서 같은 핸들러를 달아도 배경을 눌러 빠져나갈 수 없습니다 —
         * 끝내야 넘어가는 화면이라 그래야 합니다.
         *
         * <p><b>순서가 중요합니다.</b> 겹쳐 뜬 모달은 위에 있는 것부터 닫아야 합니다 —
         * 아래 것을 먼저 닫으면 위에 뜬 모달만 남아 배경 없이 떠 있게 됩니다.
         *
         * <p>초기 설정(비밀번호·프로필)은 닫지 않습니다. 끝내야 넘어갈 수 있는 화면이라
         * Esc로 빠져나가면 아무것도 못 하는 상태가 됩니다.
         */
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
                // 이 목록에 없으면 <b>배경을 눌렀을 때 엉뚱한 모달이 닫힌다</b> (설계 I274)
                ['showItinUnavailable', () => this.closeItinUnavailable()],
                ['showSoldOutAlert', () => this.closeSoldOutAlert()],
                ['showUsers', () => this.closeUsers()],
                ['showSettings', () => this.closeSettings()],
                ['showChangePw', () => this.closeChangePw()],
            ];
            for (const [flag, close] of stack) {
                // photoViewerIndex 는 안 열렸을 때 -1 이다. 0도 '열림'이므로 >= 0 으로 본다
                const open = flag === 'photoViewerIndex' ? this[flag] >= 0 : !!this[flag];
                if (open) {
                    close();
                    return;
                }
            }
        },

        /**
         * 매물 카드의 화살표 (설계 I136).
         *
         * <p>화살표는 <b>오직 LLM 예측</b>만 나타낸다. 코드 예측과 갈려도 색을 흐리지 않는다 —
         * 목록은 여러 매물을 견주는 자리라 신호가 하나여야 읽힌다. 갈린 사실은 모달에서 말한다.
         *
         * <p>UNCERTAIN 에는 <b>화살표를 쓰지 않는다</b>. 회색 화살표를 두면 '약한 전망'으로
         * 읽히는데 실제로는 '판단하지 않았다'는 뜻이다 — 둘은 다르다. 대신 📝 를 단다
         * (설계 I150): 화살표가 아니라서 방향으로 오해되지 않고, <b>눌러 볼 것이 있다</b>는
         * 표시가 된다. 여태는 아무것도 없어 왜 판단을 못 했는지 볼 길이 없었다.
         *
         * <p>아직 낸 적이 없는 경우(stored=false)에는 안 단다 — 그때 UNCERTAIN 은
         * '결과 없음'의 자리표시일 뿐이다.
         */
        /**
         * 카드에 무엇을 띄울까 (설계 I248).
         *
         * <pre>
         * 요약이 없다        →  (없음)
         * 분석 중            →  ◌
         * 아직 안 물어봤다    →  (없음)   ← 매매가를 눌러 시킬 수 있다 (I142)
         * 셀 것이 없었다      →  🤔 ▶     ← 지표가 없거나 전부 유지
         * 상승 · 하락 · 유지  →  ▲ · ▼ · ▶
         * </pre>
         *
         * <p><b>유지와 판단 보류는 같은 것</b>입니다(I248). 그래서 옛 `UNCERTAIN` 도
         * `▶` 로 그립니다 — 저장된 값이 무엇이든 사람에게는 한 가지 뜻입니다.
         */
        forecastArrow(scored) {
            const f = scored?.forecast;
            if (!f) {
                return '';
            }
            if (f.running) {
                return '◌';
            }
            // 물어본 적이 없으면 아무것도 안 띄운다 — 없는 답을 그릴 수는 없다
            if (!f.stored) {
                return '';
            }
            if (f.noSignal) {
                return '🤔 ▶';
            }
            return this.arrowOf(f.direction) || '▶';
        },

        /**
         * 모달이 <b>결론으로 내세우는 말</b> (설계 I247).
         *
         * <p><b>상승·하락만 이름을 갖습니다.</b> 그 외에는 "판단 보류"입니다 —
         * `유지`는 <b>올라가지도 내려가지도 않는다고 단언하는 말</b>인데,
         * 요인 다수결이 그쪽으로 기울었다는 것과 <b>그렇게 될 것이라 말하는 것</b>은
         * 다릅니다([I234]에서 다수결로 바꾸며 이 구분을 잃었습니다).
         *
         * <p><b>저장된 값은 그대로 `FLAT` 입니다.</b> 화면에서만 그렇게 부릅니다 —
         * 도메인에서 `FLAT` 과 `UNCERTAIN` 을 합쳐 버리면 사후 검증(구현 10)이
         * <b>"유지를 맞혔다"와 "판단을 안 했다"를 구분하지 못합니다.</b>
         */
        forecastVerdictLabel(direction, directionLabel) {
            return (direction === 'UP' || direction === 'DOWN')
                ? directionLabel
                : '판단 보류';
        },

        /** 카드에 마우스를 올렸을 때. 결론과 <b>같은 말</b>을 쓴다 (설계 I248) */
        forecastVerdictOf(f) {
            return this.forecastVerdictLabel(f.direction, this.DIRECTION_LABEL[f.direction]);
        },

        arrowOf(direction) {
            switch (direction) {
                case 'UP': return '▲';
                case 'DOWN': return '▼';
                // 유지와 판단 보류는 같은 것이다 (설계 I248)
                case 'FLAT': case 'UNCERTAIN': return '▶';
                default: return '';
            }
        },

        /** 색만이 아니라 모양도 다르다(▲▼▶) — 색각 이상에서도 방향이 읽힌다. */
        arrowClassOf(direction) {
            switch (direction) {
                case 'UP': return 'up';
                case 'DOWN': return 'down';
                // 유지와 판단 보류는 같은 것이다 (설계 I248)
                case 'FLAT': case 'UNCERTAIN': return 'flat';
                default: return '';
            }
        },

        forecastArrowClass(scored) {
            const f = scored?.forecast;
            if (f?.running) {
                return 'running';
            }
            // 🤔 는 방향이 아니다 — 색을 주면 그것부터 방향으로 읽힌다 (설계 I150·I248)
            if (f?.noSignal) {
                return 'note';
            }
            return this.arrowClassOf(f?.direction) || 'flat';
        },

        /**
         * 매매가를 눌러 전망을 시킬 수 있는가 (설계 I142 · I145).
         *
         * <p>낸 적이 없으면 시킬 수 있다. <b>판단 보류(UNCERTAIN)도 시킬 수 있다</b> —
         * UNCERTAIN 은 화살표를 안 띄우므로 모달을 열 길이 없고, 여기까지 막으면
         * 화면에서 다시 시킬 방법이 아예 없어진다.
         *
         * <p>방향이 나온 전망(▲▼▶)은 막는다. 그건 화살표를 눌러 모달로 들어가
         * '다시 분석'을 쓰면 된다.
         *
         * <p>direction 만으로는 '낸 적 있는지'를 알 수 없다 — 결과가 없을 때도
         * UNCERTAIN 이라 서버가 stored 를 따로 준다.
         */
        canTriggerForecast(scored) {
            const f = scored?.forecast;
            if (!f || f.running) {
                return false;
            }
            // 유지 = 판단 보류다 (설계 I248). 둘 다 "방향을 못 정한 것"이니
            // 둘 다 다시 시킬 수 있어야 한다 — 하나만 열어 두면 같은 상태인데
            // 어떤 것은 눌리고 어떤 것은 안 눌린다
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

        /**
         * 매매가 클릭 — 등록 전에 만들어진 매물에는 전망이 없다 (설계 I142).
         *
         * <p><b>응답을 기다리지 않는다.</b> 60개월 수집과 LLM 판단에 1~2분이 걸리는데
         * 그동안 화면이 멈춰 있으면 사용자는 눌린 줄도 모른다. 바로 진행 표시(◌)를
         * 띄우고 목록을 폴링한다.
         */
        async triggerForecast(scored) {
            if (!this.canTriggerForecast(scored)) {
                return;
            }
            const id = scored.property.id;
            // 서버가 markRunning 을 걸어 두므로 다음 조회부터 running 이 온다.
            // 그래도 여기서 먼저 바꿔 둔다 — 첫 폴링까지의 몇 초를 비워 두지 않는다
            scored.forecast.running = true;
            this.request(`/api/properties/${id}/forecast/refresh`, { method: 'POST' })
                .catch(() => {});
            this.startForecastPolling(id);
        },

        /**
         * 아무 모달도 안 열려 있는가 (설계 I146).
         *
         * <p>분석이 끝나면 결과를 열어 보여 주는데, 그동안 사용자가 다른 걸 보고 있으면
         * <b>보던 것을 덮어써서는 안 된다.</b>
         */
        noModalOpen() {
            return !this.showForecast && !this.showM2 && !this.showPropertyForm
                && !this.showLoanModal && !this.showRefModal && !this.showComments
                && !this.showCompare && !this.showSettings && !this.showUsers;
        },

        /**
         * 결과가 올 때까지 목록을 다시 읽는다.
         *
         * <p>멈추는 조건이 둘 다 있어야 한다 — 하나라도 빠지면 탭이 열려 있는 동안 계속 두드린다.
         * ① 결과 도착(또는 분석 중이 아님) ② 시도 상한.
         */
        startForecastPolling(propertyId) {
            this.stopForecastPolling();
            let attempts = 0;
            this._forecastTimer = setInterval(async () => {
                // ② 5초 × 36 = 3분. 전망은 1~2분이라 넉넉하다
                if (++attempts > FORECAST_POLL_MAX_ATTEMPTS) {
                    this.stopForecastPolling();
                    await this.loadProperties();
                    return;
                }
                await this.loadProperties();
                const found = (this.properties || []).find(r => r.property.id === propertyId);
                // ① 분석이 끝났다 (결과가 저장됐거나, 실패해서 진행 표시가 걷혔거나)
                if (!found || !found.forecast?.running) {
                    this.stopForecastPolling();
                    /*
                     * 끝났으면 결과를 연다 (설계 I254).
                     *
                     * 전에는 `UNCERTAIN` 일 때만 열었습니다(I146) — 방향이 나오면
                     * 화살표가 곧 응답이라 굳이 덮지 않는다는 뜻이었습니다.
                     * 그런데 [I248]에서 판단 보류를 `FLAT` 으로 바꾸면서 이 조건이
                     * <b>아무것도 안 걸리게</b> 됐고, 2분을 기다려도 화면이 그대로였습니다.
                     *
                     * 이제 <b>끝나면 엽니다.</b> 눌러서 시킨 일이라 결과를 보고 싶은
                     * 것이 당연하고, 화살표만으로는 왜 그렇게 나왔는지 알 수 없습니다.
                     * 그 사이에 사람이 다른 것을 열었으면 덮지 않습니다.
                     */
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

        /**
         * 방향 이름. <b>서버의 `ForecastDirection` 과 같은 말을 씁니다</b> (설계 I247).
         *
         * <p>규칙 기반 예측(`codeDirection`)은 요약에 라벨이 실려 오지 않아 여기서
         * 붙입니다. 그래서 <b>이 표가 서버와 어긋나면 같은 방향을 두 이름으로</b>
         * 부르게 됩니다 — 실제로 한 번 그랬습니다.
         */
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
            // 카드와 모달이 같은 말을 써야 한다 (설계 I248) — 여기만 '유지'라고
            // 하면 눌러서 '판단 보류'를 보게 된다
            return '가격 전망: ' + this.forecastVerdictOf(f)
                + (f.confidenceLabel ? ' (확신도 ' + f.confidenceLabel + ')' : '');
        },

        async openForecast(scored) {
            this.forecastProperty = scored;
            this.forecastDetail = null;
            this.forecastNews = [];
            this.error = null;
            this.showForecast = true;
            // 열 때 다시 읽는다 (설계 I112) — 목록을 받아 둔 시점과 지금이 다를 수 있다
            const { ok, body } = await this.withLoading('forecast',
                () => this.request(`/api/properties/${scored.property.id}/forecast`));
            if (ok && body) {
                this.forecastDetail = body;
            }
            // 기사는 전망과 따로 받는다 (설계 I137) — 안 와도 전망은 멀쩡히 뜬다
            this.loadForecastNews(scored.property.id);
        },

        async loadForecastNews(propertyId) {
            const { ok, body } = await this.request(`/api/properties/${propertyId}/news`)
                .catch(() => ({ ok: false }));
            // 열어 둔 매물이 바뀌었으면 덮어쓰지 않는다
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

        /** 사용자가 명시적으로 다시 분석할 때. 1~2분 걸린다. */
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

        /**
         * 코드 예측과 갈렸을 때의 참고 문구 (설계 5.2).
         *
         * <p>경고(⚠)가 아니라 참고다 — 코드 임계값은 임의의 값이라 갈렸다는 것이
         * LLM이 틀렸다는 뜻은 아니다. 일치할 때도 한 줄 남긴다: 아무 말이 없으면
         * 비교를 안 한 것인지 일치한 것인지 알 수 없다.
         */
        /**
         * 규칙 기반 계산은 뭐라 했는가 (설계 I249).
         *
         * <p><b>AI 가 판단을 보류했을 때도 말합니다.</b> 전에는 그때 이 문장을 통째로
         * 건너뛰었는데, 하필 <b>그때가 가장 궁금한 순간</b>입니다 — AI 가 못 정했으면
         * 다른 셈은 뭐라 했는지 보고 싶습니다.
         *
         * <p>기준은 {@code llmDirection}(AI 가 스스로 낸 결론)입니다.
         * {@code direction} 은 규칙까지 거친 <b>최종</b> 결론이라, 그걸로 "AI 모델은…"
         * 이라고 쓰면 <b>우리가 정한 것을 AI 가 말한 것처럼</b> 적게 됩니다.
         */
        forecastCompareNote() {
            const d = this.forecastDetail;
            if (!d || !d.codeDirection) {
                return '';
            }
            const code = this.DIRECTION_LABEL[d.codeDirection] || d.codeDirection;
            const said = d.llmDirection;
            // 옛 전망에는 llmDirection 이 없다 — 없는 것을 지어내지 않는다
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
                + `규칙 기반 계산은 ${code}였음을 참고하십시오.`;
        },

        /**
         * 쾌적함이 채점됐다면 누군가 다녀온 것이다 (설계 I121).
         *
         * 쾌적함은 직접 가 보지 않으면 매길 수 없는 항목이라, 점수가 있다는 것은
         * 임장을 다녀왔다는 뜻이다. 따로 '다녀옴' 칸을 두면 사람이 또 눌러야 한다.
         */
        hasVisited(scored) {
            return (scored?.scores || []).some(
                s => s.code === 'COMFORT' && s.effectiveScore != null);
        },

        /**
         * 배지가 <b>누구의</b> 방문인지 (설계 I226).
         *
         * <p>배지는 그룹 기준입니다 — 구성원 중 누구든 매기면 뜹니다([I121]).
         * 그런데 정렬은 <b>내</b> 기준이라, 둘이 어긋나 보일 수 있습니다:
         * 남이 다녀온 매물은 <b>배지는 있지만 내 목록에서는 앞에</b> 남습니다.
         * 그게 맞습니다 — 내가 안 가 봤으니까요. 말로 적어 둡니다.
         */
        visitedTitle(scored) {
            return this.scoredComfort(scored)
                ? '내가 공간의 쾌적함을 매겼습니다 — 다녀온 곳입니다'
                : '구성원 중 누군가 공간의 쾌적함을 매겼습니다';
        },

        /**
         * <b>내가</b> 쾌적함을 매겼는가 (설계 I264).
         *
         * <p>화면이 이 이름을 부르는데 <b>함수가 없었습니다.</b> 카드마다
         * `scoredComfort is not defined` 가 났고, 배지 색이 늘 남의 것으로 보였습니다.
         *
         * <p>같은 규칙이 {@link #visitedTitle} 안에 <b>또 한 벌</b> 있었습니다.
         * 두 벌이면 언젠가 어긋납니다 — 여기 하나만 둡니다.
         */
        scoredComfort(scored) {
            // 카드에 실린 내 점수를 그대로 본다 — 카드가 보인다는 것은 이미 받았다는 뜻이다
            return (scored?.scores || []).some(s => s.code === 'COMFORT' && s.myScore != null);
        },

        /**
         * 미산출 항목을 다시 계산한다 (설계 I119).
         *
         * 미산출은 대개 그때 외부 조회가 실패한 것이고, 실패는 저장하지 않으므로
         * 다시 채점하면 다시 시도한다. 직장 좌표를 넣은 뒤 여기서 바로 되살릴 수 있다.
         */
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

        /**
         * 슬라이더의 <b>지나온 쪽</b>만 색을 채운다 (설계 I200).
         *
         * <p>기본 `input[type=range]` 는 막대 전체가 한 색이라 손잡이 위치만으로
         * 점수를 읽어야 합니다. 잠긴 항목의 읽기용 막대(`gauge-fill`)는 이미 채워진
         * 모양인데 열린 항목만 비어 있어, 나란히 두면 <b>다른 것처럼 보였습니다.</b>
         *
         * <p>가상 요소(`::-webkit-slider-runnable-track`)에는 값을 넘길 수 없어
         * 배경 그러데이션으로 그립니다. 브라우저를 가리지 않는 방법이기도 합니다.
         */
        scoreRangeFill(s) {
            const min = Number(this.scoreMin(s));
            const max = Number(this.scoreMax(s));
            const raw = this.scoreForm[s.code];
            const span = max - min;
            // 아직 안 매긴 항목은 <b>비워 둡니다</b> — 0점을 매긴 것과 다릅니다
            const pct = (raw === '' || raw == null || span <= 0)
                ? 0
                : Math.min(100, Math.max(0, ((Number(raw) - min) / span) * 100));
            return `background: linear-gradient(to right,`
                + ` var(--ocean) 0 ${pct}%, var(--line2) ${pct}% 100%)`;
        },

        /** 채점 모달의 입력 칸을 주어진 매물 값으로 채운다. */
        applyScoreForm(scored) {
            this.scoreProperty = scored;
            const form = {};
            (scored.scores || []).forEach(s => {
                // 추정값을 기본으로 채워 둔다. 예전에는 '추정값 확정' 버튼을 눌러야 들어갔는데,
                // 안 누르면 추정이 저장되지 않아 채점이 비는 것과 같았다.
                // 사용자는 여기서 자유롭게 고쳐 쓴다 (설계 I76)
                if (s.code === 'COMFORT') {
                    // 쾌적함은 사람마다 따로 매긴다 (설계 I118). 그룹 평균이 아니라
                    // 내가 매긴 값을 채운다 — 남이 매긴 값을 채우면 내가 매긴 줄 안다.
                    // 1~5 척도라 100점 만점 추정값을 넣어서도 안 된다
                    form[s.code] = s.myScore != null ? String(s.myScore) : '';
                } else if (s.manualScore != null) {
                    form[s.code] = String(s.manualScore);
                } else {
                    form[s.code] = s.effectiveScore != null ? String(s.effectiveScore) : '';
                }
            });
            this.scoreForm = form;
            // 연 시점의 값을 그대로 남겨 둔다. 저장할 때 이것과 달라진 항목만 보낸다
            // (설계 I111) — 안 그러면 추정값으로 채워진 칸이 전부 수동 채점이 된다
            this._scoreFormAtOpen = { ...form };
        },

        /**
         * 이미 자동으로 채점된 AUTO 항목인지 (설계 I111).
         *
         * HYBRID(교육여건·녹색환경)는 사람이 고치라고 만든 것이라 잠그지 않는다.
         * AUTO라도 산출에 실패해 값이 없으면 사람이 채울 수 있어야 한다.
         */
        scoreLocked(s) {
            return s.scoringType === 'AUTO' && s.autoScore != null;
        },

        /**
         * 슬라이더의 눈금 (설계 I172).
         *
         * <p>쾌적함만 <b>1~5 척도</b>다 (설계 I118) — 사람이 매기는 유일한 항목이고,
         * 100점 척도로 물으면 73점과 74점의 차이를 <b>아무도 설명할 수 없다.</b>
         */
        scoreMin(s) {
            return s.code === 'COMFORT' ? 1 : 0;
        },

        scoreMax(s) {
            return s.code === 'COMFORT' ? 5 : 100;
        },

        /** 100점 척도는 5점 단위로 끊는다 — 1점 단위는 끌어서 맞출 수 없다. */
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

        async saveScore() {
            this.loading = true;
            this.error = null;
            // 내가 실제로 고친 항목만 보낸다 (설계 I111). 전부 보내면 추정값으로
            // 채워 둔 칸까지 저장돼 자동 채점이 통째로 수동으로 굳고 산출 근거가 사라진다
            const before = this._scoreFormAtOpen || {};
            const locked = new Set((this.scoreProperty?.scores || [])
                .filter(s => this.scoreLocked(s)).map(s => s.code));
            const scores = {};
            for (const code in this.scoreForm) {
                if (locked.has(code)) {
                    continue;
                }
                if (String(this.scoreForm[code] ?? '') === String(before[code] ?? '')) {
                    continue;
                }
                const value = toNum(this.scoreForm[code]);
                if (value != null) {
                    scores[code] = value;
                }
            }
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
                    // 저장 후 닫지 않고 갱신된 점수를 그대로 보여준다
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

        /** 알림이 왜 안 오는지 보이게 (설계 I215). */
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
            const present = [...new Set(this.settings.map(s => s.category))];
            return present.sort((a, b) => rank(a) - rank(b));
        },

        settingsByCategory(category) {
            return this.settings.filter(s => s.category === category);
        },

        /**
         * 사람이 고칠 값인가 (설계 I185).
         *
         * <p>스트레스 금리의 <b>산출 근거·시각</b>은 배치가 쓰는 값입니다. 고쳐도
         * 다음 배치가 덮어쓰므로 <b>입력칸을 열어 두면 거짓말</b>이 됩니다.
         */
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

        /**
         * 지도의 매물 표시 (설계 I222).
         *
         * <p>기본 마커는 <b>어느 것이 어느 매물인지</b> 알려 주지 않아, 지도에서
         * 보다가 결국 목록으로 눈을 옮겨야 했습니다. 값을 마커에 얹습니다 —
         * 전용면적과 가격, 둘이면 충분합니다.
         *
         * <p><b>전세는 초록입니다.</b> 매매와 전세는 숫자의 뜻이 달라서
         * (5.6억을 내는 것과 맡기는 것) 색으로 갈라 두지 않으면 잘못 읽습니다.
         *
         * <p>`CustomOverlay` 를 씁니다 — `Marker` 는 그림만 바꿀 수 있고 글자를
         * 얹을 수 없습니다. 임장 순번 마커(I177)와 같은 방식입니다.
         */
        renderMarkers() {
            if (!this.map) {
                return;
            }
            Object.values(this.markers).forEach(m => m.setMap(null));
            this.markers = {};
            // 목록은 30건씩 잘려 오지만 지도는 전부 찍는다 (설계 I240)
            const coords = this.pins.filter(p => p.lat && p.lng);
            coords.forEach(p => {
                const position = new kakao.maps.LatLng(p.lat, p.lng);
                const base = this.pinZIndex(p);
                const overlay = new kakao.maps.CustomOverlay({
                    position,
                    content: this.markerContent(p),
                    yAnchor: 1,
                    clickable: true,
                    zIndex: base
                });
                overlay.setMap(this.map);
                // CustomOverlay 에는 이벤트를 못 건다 — 안쪽 요소에 직접 건다
                const el = overlay.getContent();
                if (el instanceof HTMLElement) {
                    el.addEventListener('click', () => this.selectMarker(p.id));
                    // 겹친 핀은 <b>가리킨 것</b>이 맨 앞으로 (설계 I245).
                    // CSS 의 `.map-pin:hover { z-index }` 로는 안 됩니다 — 오버레이에
                    // zIndex 를 주면 컨테이너가 쌓임 맥락을 만들어, 그 안의 z-index 는
                    // <b>형제 오버레이와 겨루지 못합니다.</b> 오버레이째로 올립니다
                    el.addEventListener('mouseenter', () => overlay.setZIndex(PIN_Z.hover));
                    el.addEventListener('mouseleave', () => overlay.setZIndex(base));
                }
                this.markers[p.id] = overlay;
            });
            if (coords.length > 0) {
                const bounds = new kakao.maps.LatLngBounds();
                coords.forEach(p => bounds.extend(new kakao.maps.LatLng(p.lat, p.lng)));
                this.map.setBounds(bounds);
            }
        },

        /**
         * 마커 안에 무엇을 적을까 (설계 I222).
         *
         * <p>문자열이 아니라 <b>요소</b>를 돌려줍니다 — 클릭을 걸어야 하고,
         * 단지명이 그대로 들어가므로 <b>HTML 로 조립하면 안 됩니다.</b>
         */
        /**
         * 겹친 핀 중 무엇을 위에 둘까 (설계 I245).
         *
         * <p><b>다녀온 곳이 안 가 본 곳을 가리고 있었습니다.</b> 오버레이에 순서를
         * 안 주면 <b>만든 차례대로</b> 쌓이는데, 그 차례는 그저 목록이 온 순서입니다 —
         * 무엇이 위에 올지가 <b>운에 달려 있었습니다.</b>
         *
         * <p>가려도 되는 것이 가려야 합니다. 임장은 <b>아직 안 가 본 곳을 고르려고</b>
         * 보는 지도인데, 이미 다녀온 곳이 그것을 덮으면 <b>정작 볼 것이 안 보입니다.</b>
         * 흐리게 칠한 것([I225])과 같은 이유입니다.
         */
        pinZIndex(p) {
            return p.visited ? PIN_Z.visited : PIN_Z.fresh;
        },

        /** 지도용 얇은 매물 하나를 받는다 (설계 I240) — 채점까지 붙은 목록이 아니다 */
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
            // 한 글자면 충분하다 (설계 I223) — 색이 이미 매매·전세를 가른다
            price.textContent = `${jeonse ? '전' : '매'} ${this.fmtWonShort(p.priceDeposit)}`;
            box.appendChild(price);

            const tail = document.createElement('i');
            box.appendChild(tail);
            box.title = p.name || '';
            return box;
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
            // 아직 안 받은 쪽의 매물일 수 있다 (설계 I240) — 지도는 전부 찍기 때문이다.
            // 그때는 상세를 받아서 연다. 눌렀는데 아무 일도 안 일어나면 고장으로 보인다
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

        /** 미산출 사유를 중복 없이 모아 모달 상단에 한 번만 보여준다. */
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

        /** 등록자 배지의 원형 이니셜 — 닉네임 첫 글자 (설계 I57). */
        ownerInitial(nickname) {
            return nickname ? Array.from(nickname.trim())[0] : '';
        },

        /** ㎡와 평을 함께 보여준다. 1평 = 3.3058㎡ (설계 I53). */
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

        /**
         * 월 이율을 연 이율 퍼센트로 되돌린다 (설계 I81).
         * 서버는 스트레스 금리를 더한 월 이율을 주므로 화면 표기도 그 기준이다.
         */
        fmtRate(monthlyRate) {
            if (monthlyRate == null) {
                return '-';
            }
            return (monthlyRate * 12 * 100).toFixed(2) + '%';
        },

        /**
         * 금액 입력칸 위에 띄우는 읽기 도움말 (설계 I86).
         *
         * 자릿수가 많은 금액은 눈으로 세기 어렵다 — `150000000`이 1억 5천인지 15억인지
         * 한눈에 안 들어온다. 치는 동안 `1억 5,000만원`으로 되읽어 준다.
         *
         * 비었거나 0이면 아무것도 띄우지 않는다. 아직 안 적은 칸에 `0원`이 떠 있으면
         * 이미 입력한 것처럼 보인다.
         */
        /**
         * 금액 칸은 `type="text"`다 (설계 I114).
         *
         * <p>`type="number"`는 휠에 반응해 값이 조용히 바뀐다 — 550000000을 넣었는데
         * 549999997이 된 적이 있다(I101). 휠을 막아 뒀지만 근본은 타입이었다.
         * 대신 숫자 말고는 아예 들어가지 않게 여기서 거른다.
         *
         * <p>DOM 값도 함께 바꾼다. 모델만 고치면 화면에는 걸러지기 전 글자가 잠깐 남는다.
         */
        /**
         * 모달이 데이터를 받아 오는 동안 진행 막대를 띄운다 (설계 I115).
         *
         * <p>바로 켜지 않고 <b>{@code SHOW_DELAY_MS} 뒤에</b> 켭니다. 빠른 응답에도 켜면
         * 막대가 번쩍였다 사라져 오히려 어수선합니다 — 기다릴 만할 때만 보여 줍니다.
         *
         * <p>{@code finally}에서 반드시 끕니다. 호출이 실패해도 막대가 남으면
         * 화면이 영영 도는 것처럼 보입니다.
         */
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

        moneyHint(value) {
            const n = toNum(value);
            return n == null || n === 0 ? '' : this.fmtWon(n);
        },

        /**
         * 지도 핀에 들어갈 짧은 가격 (설계 I223).
         *
         * <p>`7억 5,000만원` 은 핀을 <b>가로로 늘립니다.</b> 핀이 몇 개만 겹쳐도
         * 서로를 가립니다 — 지도에서는 <b>자릿수보다 폭</b>이 문제입니다.
         *
         * <pre>
         * 750,000,000 → 7.5억      645,000,000 → 6.45억
         * 700,000,000 → 7억        50,000,000  → 5,000만
         * </pre>
         *
         * <p><b>1억 미만은 만원으로</b> 둡니다. `0.5억` 은 읽는 데 한 번 더
         * 생각하게 만듭니다.
         *
         * <p>소수 둘째 자리에서 <b>버립니다</b> — 반올림하면 6.999억이 7억이 되어
         * <b>실제보다 싸 보입니다.</b> 목록·상세에는 정확한 값이 그대로 있습니다.
         */
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
