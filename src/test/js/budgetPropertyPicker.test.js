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
    assert.equal(estimated, true);
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
