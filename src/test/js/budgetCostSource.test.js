'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { bootWindow, mountHalley } = require('./support/harness.js');

test('부대비용 자동 추정을 적용하면 각 비용의 출처도 자동 추정으로 바뀐다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    app.budgetAggregate = { plan: {}, financing: {}, assets: [], items: [] };
    app.budgetCostEstimate = { acquisitionTax: 1, brokerageFee: 2, registrationFee: 3, movingCost: 4, cleaningCost: 5 };

    // when
    app.applyBudgetCostEstimate();

    // then
    assert.equal(app.budgetAggregate.plan.registrationFee, 3);
    assert.equal(app.budgetAggregate.plan.registrationFeeSource, 'AUTO_ESTIMATE');
    assert.equal(app.budgetAggregate.plan.cleaningCostSource, 'AUTO_ESTIMATE');
});

test('특정 비용을 고치면 그 비용만 수기 입력으로 바뀐다', () => {
    // given
    const { window } = bootWindow();
    const app = mountHalley(window);
    app.budgetAggregate = { plan: { registrationFeeSource: 'AUTO_ESTIMATE', movingCostSource: 'AUTO_ESTIMATE' } };

    // when
    app.markBudgetCostManual('registrationFeeSource');

    // then
    assert.equal(app.budgetAggregate.plan.registrationFeeSource, 'MANUAL');
    assert.equal(app.budgetAggregate.plan.movingCostSource, 'AUTO_ESTIMATE');
});
