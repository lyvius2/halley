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

/** AI 결과를 기다리는 동안의 폴링 간격·상한 (설계 I72). */
/**
 * 채점 판 번호를 확인하는 주기 (설계 I85).
 * 보정과 AI 응답은 수 초~수십 초가 걸린다. 이보다 촘촘히 물어도 답이 달라지지 않는다.
 */
const SCORE_WATCH_MS = 3000;
const LLM_POLL_INTERVAL_MS = 2000;
const LLM_POLL_MAX_ATTEMPTS = 60;

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
        properties: [],
        scoreWatchTimer: null,
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
        loanForm: { firstHome: false, mortgageInsured: false, ownedHouseCount: 0 },
        loanResult: null,
        loanAmount: 0,
        loanShowInputs: false,
        loanOverride: { annualIncome: '', cash: '', existingLoan: '' },
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
        loginForm: { loginId: '', password: '' },
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
        groupForm: { name: '' },
        joinForm: { code: '' },
        inviteCode: null,
        withdrawForm: { password: '' },
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
            // 초기 설정이 끝나기 전에는 서버가 API를 막는다(AccountSetupFilter).
            // 어느 경로로 막히든 해당 단계의 모달을 띄워 사용자가 갇히지 않게 한다.
            if (res.status === 403 && body && body.code === 'PROFILE_SETUP_REQUIRED') {
                this.showProfileSetup = true;
            }
            if (res.status === 403 && body && body.code === 'MUST_CHANGE_PASSWORD') {
                this.showPassword = true;
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
                this.showProfileSetup = !this.showPassword && body.profileComplete === false;
                // 초기 설정(비밀번호·프로필)이 끝나기 전에는 다른 API가 403이므로 호출하지 않는다
                const setupPending = this.showPassword || this.showProfileSetup;
                if (this.session.role === 'ADMIN' && !setupPending) {
                    await this.loadUsers();
                    await this.loadGroups();
                }
                if (!setupPending) {
                    await this.loadMyGroup();
                    await this.loadDebts();
                    await this.loadProperties();
                    await this.checkSoldOutAlert();
                    // 등록 직후에는 채점이 비어 있고 보정·AI가 끝나며 채워진다 (설계 I85)
                    this.startScoreWatch();
                }
            } else {
                this.session = { authenticated: false, userId: null, nickname: null, role: null, mustChangePassword: false };
                this.showLogin = true;
            }
        },

        setView(view) {
            this.view = view;
            if (view === 'weights') {
                this.loadWeights();
            }
            if (view === 'me') {
                this.loadProfile();
            }
            if (view === 'itinerary') {
                this.loadStartLocation();
                this.renderItinerary();
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
            this.loadUsers();
        },

        closeUsers() {
            this.showUsers = false;
            this.error = null;
        },

        async loadUsers() {
            const { ok, body } = await this.request('/api/users');
            if (ok) {
                this.users = body || [];
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
                loginId: u.loginId,
                nickname: u.nickname,
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
                this.loginForm = { loginId: this.signUpForm.loginId, password: this.signUpForm.password };
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
            const { ok, body } = await this.request('/api/admin/groups', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: null })
            });
            if (ok) {
                await this.loadGroups();
            } else {
                this.error = (body && body.message) || '그룹을 만들지 못했습니다';
            }
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
        },

        async renameGroup() {
            const { ok, body } = await this.request('/api/groups/me', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: this.groupForm.name })
            });
            if (ok) {
                this.myGroup = body;
            } else {
                this.error = (body && body.message) || '그룹 이름을 바꾸지 못했습니다';
            }
        },

        async createInvite() {
            const { ok, body } = await this.request('/api/groups/me/invites', { method: 'POST' });
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
                    if (!coords) {
                        this.error = '선택한 주소의 좌표를 찾지 못했습니다. 다른 주소로 시도해 주세요.';
                        return;
                    }
                    if (target === 'itinerary') {
                        this.itinStart = { address: label, lat: coords.lat, lng: coords.lng };
                        await this.rememberStartLocation();
                        return;
                    }
                    const form = target === 'setup' ? this.setupForm : this.profileForm;
                    form.workplaceName = label;
                    form.workplaceLat = coords.lat;
                    form.workplaceLng = coords.lng;
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
            return this.properties.filter(r => r.property.active && !r.property.isDraft).length;
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
            this.loadComments();
        },

        closeComments() {
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
            this.llmPending = false;
            this.stopLlmPolling();
            this.showM2 = true;
            this.loadDetailExtras(item.property.id);
        },

        // 중개사·실거래가는 매물 등록 시 이미 채워져 있다. 여기서는 읽기만 하고 실패해도 모달은 그대로 뜬다.
        async loadDetailExtras(propertyId) {
            const [agents, ref, llm, landUse] = await Promise.all([
                this.request(`/api/properties/${propertyId}/agents`).catch(() => ({ ok: false })),
                this.request(`/api/properties/${propertyId}/reference-transactions`).catch(() => ({ ok: false })),
                this.request(`/api/properties/${propertyId}/llm-recommendation`).catch(() => ({ ok: false })),
                this.request(`/api/properties/${propertyId}/land-use`).catch(() => ({ ok: false }))
            ]);
            if (this.detailItem && this.detailItem.property.id !== propertyId) {
                return;
            }
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
        },

        async openPhotoModal(item) {
            this.photoProperty = item;
            this.photoImages = [];
            this.error = null;
            this.showPhotoModal = true;
            await this.loadPhotoImages();
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
                    this.loginForm = { loginId: '', password: '' };
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

        /**
         * 뒤에서 채점이 끝나면 화면을 맞춘다 (설계 I85).
         *
         * 채점은 사용자가 보고 있는 동안 두 번 더 바뀐다 — 보정이 끝날 때, AI 응답이 올 때.
         * 목록을 통째로 다시 받아 비교하면 무거우니 판 번호만 확인하고 달라졌을 때만 받는다.
         *
         * 탭이 가려져 있으면 쉬었다가, 돌아올 때 한 번 맞춘다. 안 보이는 화면을 위해
         * 계속 물어볼 이유가 없다.
         */
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
            const { ok, body } = await this.request('/api/properties/score-versions');
            if (!ok || !body) {
                return;
            }
            const latest = new Map(body.map(v => [v.propertyId, v.scoreVersion]));
            const changed = this.properties.some(
                r => latest.has(r.property.id) && latest.get(r.property.id) !== r.scoreVersion);
            // 매물이 늘거나 줄어도 목록을 다시 받아야 한다
            if (changed || latest.size !== this.properties.length) {
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

        // 프로필에 연소득·보유 현금이 있으므로 모달을 열면 바로 계산한다 (설계 I55)
        openLoanModal(item) {
            this.loanProperty = item;
            this.loanForm = { firstHome: false, mortgageInsured: false, ownedHouseCount: 0 };
            this.loanOverride = { annualIncome: '', cash: '', existingLoan: '' };
            this.loanShowInputs = false;
            this.loanResult = null;
            this.error = null;
            this.showLoanModal = true;
            this.runLoanEstimate();
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
                            ownedHouseCount: this.loanForm.ownedHouseCount
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
                priceMonthly: p.priceMonthly ?? '',
                maintenanceFee: p.maintenanceFee ?? '',
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
                moveInType: p.moveInType || '',
                moveInDate: p.moveInDate || '',
                editVersion: p.editVersion ?? null
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
                name: this.propertyForm.name,
                dealType: this.propertyForm.dealType,
                priceDeposit: toNum(this.propertyForm.priceDeposit),
                priceMonthly: toNum(this.propertyForm.priceMonthly),
                maintenanceFee: toNum(this.propertyForm.maintenanceFee),
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
                // 추정값을 기본으로 채워 둔다. 예전에는 '추정값 확정' 버튼을 눌러야 들어갔는데,
                // 안 누르면 추정이 저장되지 않아 채점이 비는 것과 같았다.
                // 사용자는 여기서 자유롭게 고쳐 쓴다 (설계 I76)
                if (s.manualScore != null) {
                    form[s.code] = String(s.manualScore);
                } else if (s.code === 'COMFORT') {
                    // 쾌적함은 1~5 척도라 100점 만점 추정값을 넣으면 안 된다.
                    // 애초에 사람만 매기는 항목이므로 비워 둔다
                    form[s.code] = '';
                } else {
                    form[s.code] = s.effectiveScore != null ? String(s.effectiveScore) : '';
                }
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
                    await this.loadProperties();
                    // 저장 후 닫지 않고 갱신된 점수를 그대로 보여준다
                    const fresh = (this.properties || []).find(
                        r => r.property.id === this.scoreProperty.property.id);
                    if (fresh) {
                        this.openScoreModal(fresh);
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

        settingCategories() {
            const order = ['BATCH', 'LOAN'];
            const rank = c => (order.indexOf(c) === -1 ? order.length : order.indexOf(c));
            const present = [...new Set(this.settings.map(s => s.category))];
            return present.sort((a, b) => rank(a) - rank(b));
        },

        settingsByCategory(category) {
            return this.settings.filter(s => s.category === category);
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
        moneyHint(value) {
            const n = toNum(value);
            return n == null || n === 0 ? '' : this.fmtWon(n);
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
