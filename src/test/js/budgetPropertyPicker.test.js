'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

test('등록 매물을 고르면 호가와 전용면적을 예산 계획에 반영한다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    app.budgetAggregate = { plan: {} };
    app.showBudgetPropertyPicker = true;
    app.saveBudget = async () => {};
    let loanEstimatePropertyId = null;
    app.applyBudgetLoanEstimate = async id => { loanEstimatePropertyId = id; };
    let estimated = false;
    app.estimateBudgetCosts = async () => { estimated = true; };
    const item = { property: { id: 7, name: 'Halley 아파트', priceDeposit: 650_000_000, areaExclusiveM2: 84.97 } };

    // when
    await app.selectBudgetProperty(item);

    // then
    assert.equal(app.budgetAggregate.plan.selectedPropertyId, 7);
    assert.equal(app.budgetAggregate.plan.houseName, 'Halley 아파트');
    assert.equal(app.budgetAggregate.plan.purchasePrice, 650_000_000);
    assert.equal(app.budgetAggregate.plan.exclusiveAreaM2, 84.97);
    assert.equal(app.showBudgetPropertyPicker, false);
    assert.equal(loanEstimatePropertyId, 7);
    assert.equal(estimated, true);
});

test('등록 매물 대출 추정값을 예산 금융 조건에 반영한다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    app.budgetAggregate = { financing: { loanAmount: 0, loanRatio: null, interestRate: 0, termMonths: 360 } };
    app.request = async () => ({ ok: true, body: {
        finalLimit: 420_000_000, ltvRate: 0.7, monthlyRate: 0.0035,
        termMonths: 360, monthlyPayment: 1_886_000, interestOnly: false, productLabel: '주택담보대출'
    } });
    let saved = false;
    app.saveBudgetFinancing = async () => { saved = true; };

    // when
    await app.applyBudgetLoanEstimate(7);

    // then
    assert.equal(app.budgetAggregate.financing.loanAmount, 420_000_000);
    assert.equal(app.budgetAggregate.financing.loanRatio, 70);
    assert.equal(app.budgetAggregate.financing.interestRate, 4.2);
    assert.equal(app.budgetAggregate.financing.termMonths, 360);
    assert.equal(app.budgetAggregate.financing.repaymentType, 'AMORTIZED');
    assert.equal(app.budgetAggregate.financing.monthlyPayment, 1_886_000);
    assert.equal(saved, true);
});

test('대출 저장은 함께 입력한 지원금도 예산 계획에 저장한다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    const requestedUrls = [];
    app.budgetAggregate = { plan: { id: 11, parentSupport: 20_000_000 }, financing: { planId: 11, loanAmount: 300_000_000 } };
    app.request = async url => {
        requestedUrls.push(url);
        return { ok: true };
    };
    app.loadBudgetPlans = async () => {};
    app.loadBudget = async () => {};

    // when
    await app.saveBudgetFinancing();

    // then
    assert.deepEqual(requestedUrls, [
        '/api/budget/plans/11/financing',
        '/api/budget/plans/11'
    ]);
});

test('직접 추가한 혼수 품목은 현재 예산 계획에 저장한다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    app.budgetAggregate = { plan: { id: 12 }, items: [] };
    app.budgetItemForm = { category: '생활용품', itemName: '  식탁 조명  ', budgetAmountWon: 80_000,
        selected: true, owned: false, candidateName: '후보 조명', candidateUrl: 'https://example.com/light',
        candidatePriceWon: 75_000, alternativeName: '', alternativeUrl: '', alternativePriceWon: null, note: '' };
    let request = null;
    app.request = async (url, options) => {
        request = { url, options };
        return { ok: true };
    };
    app.loadBudget = async () => {};

    // when
    await app.addBudgetItem();

    // then
    assert.equal(request.url, '/api/budget/plans/12/items');
    assert.equal(request.options.method, 'POST');
    assert.equal(JSON.parse(request.options.body).itemName, '식탁 조명');
    assert.equal(JSON.parse(request.options.body).planId, 12);
    assert.equal(app.budgetItemForm.itemName, '');
});

test('품목이 아직 없어도 카테고리 필터는 기본 선택지를 제공한다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    app.budgetAggregate = { items: [] };

    // when
    const categories = app.budgetItemCategories();

    // then
    assert.deepEqual(Array.from(categories), ['ALL', '가전', '가구', '식기', '생활용품', '기타']);
});

test('기본 품목 추가는 누락분 API를 호출하고 결과를 안내한다', async () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    app.budgetAggregate = { plan: { id: 12 }, items: [] };
    let requestedUrl = null;
    app.request = async url => {
        requestedUrl = url;
        return { ok: true, body: { addedCount: 35 } };
    };
    app.loadBudget = async () => {};

    // when
    await app.addMissingCatalogItems();

    // then
    assert.equal(requestedUrl, '/api/budget/plans/12/items/catalog');
    assert.equal(app.budgetCatalogSyncMessage, '기본 품목 35개를 이 계획에 추가했습니다.');
});
