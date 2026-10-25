import test from 'node:test';
import assert from 'node:assert/strict';
import {buildSchedule, validateLoan} from '../src/core.mjs';
test('360条件：両方式・低額・上限金額・0〜20%・1〜600ヶ月の整合性', () => {
  for (const method of ['annuity', 'principal']) for (const p of [1, 300, 10000, 1000000, 30000000, 1e12])
    for (const bp of [0, 1, 80, 250, 1500, 2000]) for (const n of [1, 12, 360, 599, 600]) {
      const rows = buildSchedule(p, bp, n, method);
      assert.equal(rows.length, n);
      assert.equal(rows.at(-1).balance, 0);
      assert.equal(rows.reduce((s, row) => s + row.capital, 0), p);
      assert.equal(rows.reduce((s, row) => s + row.payment, 0), p + rows.reduce((s, row) => s + row.interest, 0));
      let previous = p;
      for (const row of rows) {
        Object.values(row).forEach(value => assert.ok(Number.isSafeInteger(value) && value >= 0));
        assert.equal(row.balance, previous - row.capital);
        assert.equal(row.payment, row.capital + row.interest);
        assert.equal(row.interest, Math.round(previous * bp / 120000));
        previous = row.balance;
      }
    }
});
test('既知値：120万円・12回', () => {
  assert.equal(buildSchedule(1200000, 0, 12, 'annuity')[0].payment, 100000);
  const capital = buildSchedule(1200000, 1200, 12, 'principal');
  assert.equal(capital[0].payment, 112000);
  assert.equal(capital.at(-1).payment, 101000);
  assert.equal(buildSchedule(1200000, 1200, 12, 'annuity')[0].payment, 106619);
});
test('不正入力・境界：空欄、負数、金利の刻み、50年超過など', () => {
  const base = {amount: '100', rate: '0.80', years: '5', months: '0', method: 'annuity'};
  for (const [field, value] of [['amount',''],['amount','0'],['amount','-1'],['amount','0.00001'],['amount','100000001'],['rate',''],['rate','20.01'],['rate','0.001'],['rate','-1'],['years',''],['years','0.5'],['months','12'],['method','unknown']]) {
    assert.ok(validateLoan({...base, [field]: value}).issues.length, field + ':' + value);
  }
  assert.ok(validateLoan({...base, years:'0'}).issues.length);
  assert.ok(validateLoan({...base, years:'50', months:'1'}).issues.length);
  assert.equal(validateLoan({...base, years:'50'}).issues.length, 0);
  assert.equal(validateLoan({...base, years:'0', months:'1', rate:'0', amount:'0.0001'}).issues.length, 0);
});
