'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const assert = require('node:assert/strict');

const template = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/templates/index.mustache'), 'utf8');
const stylesheet = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/css/app.css'), 'utf8');

test('예산 화면에서는 공용 지도를 숨기고 예산 전용 레이아웃을 사용한다', () => {
    // given
    const budgetLayoutBinding = ":class=\"{'budget-layout': view==='budget'}\"";

    // when
    const mapIsHiddenForBudget = template.includes('x-show="view !== \'budget\'"');

    // then
    assert.ok(template.includes(budgetLayoutBinding));
    assert.ok(mapIsHiddenForBudget);
    assert.ok(template.includes('class="budget-workspace"'));
    assert.ok(template.includes('class="budget-overview"'));
    assert.match(stylesheet, /\.budget-workspace \{ grid-template-columns: minmax\(0, 1fr\) minmax\(320px, 390px\)/);
    assert.match(stylesheet, /\.budget-card :is\(input:not\(\[type=checkbox\]\):not\(\[type=radio\]\):not\(\[type=file\]\), select\)/);
    assert.match(stylesheet, /\.budget-editor > \.budget-section:first-child \{ margin-top: 0; \}/);
    assert.match(stylesheet, /\.budget-cost-actions \{[\s\S]*margin-top: 1rem;/);
    assert.match(stylesheet, /\.empty-hint \{[\s\S]*background: #f5efe5/);
    assert.ok(template.includes("'전용면적 입력 필요'"));
    assert.ok(template.includes('기본 품목과 직접 추가한 품목을 목록에서 빠르게 찾습니다.'));
});
