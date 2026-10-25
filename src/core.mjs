/* 共通の円単位計算。rateBpは0.01%単位（年0.8%→80）。 */
export function validateLoan(raw) {
  const num = v => String(v).trim() === '' ? NaN : Number(v);
  const amount = num(raw.amount), rate = num(raw.rate);
  const years = num(raw.years), extraMonths = num(raw.months);
  const issues = [];
  const add = (field, message) => issues.push({field, message});
  if (!Number.isFinite(amount) || amount <= 0 || amount > 100000000) {
    add('amount', '借入金額は0より大きく、1億万円（1兆円）以下で入力してください。');
  } else if (Math.abs(amount * 10000 - Math.round(amount * 10000)) > 0.0001 || Math.round(amount * 10000) < 1) {
    add('amount', '借入金額は1円以上、万円単位で小数点以下4桁まで入力してください。');
  }
  if (!Number.isFinite(rate) || rate < 0 || rate > 20) {
    add('rate', '年利は0〜20%で入力してください。');
  } else if (Math.abs(rate * 100 - Math.round(rate * 100)) > 0.000001) {
    add('rate', '年利は0.01%刻みで入力してください。');
  }
  const validYears = Number.isInteger(years) && years >= 0 && years <= 50;
  const validMonths = Number.isInteger(extraMonths) && extraMonths >= 0 && extraMonths <= 11;
  if (!validYears) add('years', '年数は0〜50の整数で入力してください。');
  if (!validMonths) add('months', '追加月数は0〜11の整数で入力してください。');
  const months = years * 12 + extraMonths;
  if (validYears && validMonths && (months < 1 || months > 600)) {
    add('years', '返済期間は合計1ヶ月〜50年にしてください。50年の場合、追加月数は0です。');
  }
  if (!['annuity', 'principal'].includes(raw.method)) add('method', '返済方式を選んでください。');
  return {
    issues,
    data: issues.length ? null : {principal: Math.round(amount * 10000), rateBp: Math.round(rate * 100), months, method: raw.method}
  };
}

export function buildSchedule(principal, rateBp, months, method) {
  if (!Number.isSafeInteger(principal) || principal < 1 || principal > 1e12 ||
      !Number.isInteger(rateBp) || rateBp < 0 || rateBp > 2000 ||
      !Number.isInteger(months) || months < 1 || months > 600 ||
      !['annuity', 'principal'].includes(method)) throw new RangeError('計算条件が範囲外です。');
  const r = rateBp / 120000;
  const regular = rateBp === 0 ? Math.round(principal / months) :
    Math.round(principal * r / -Math.expm1(-months * Math.log1p(r)));
  const equalPrincipal = Math.round(principal / months);
  let balance = principal;
  const rows = [];
  for (let i = 1; i <= months; i++) {
    const interest = Math.round(balance * rateBp / 120000);
    const capital = i === months ? balance : Math.min(balance,
      method === 'principal' ? equalPrincipal : Math.max(0, regular - interest));
    balance -= capital;
    rows.push({number: i, payment: capital + interest, capital, interest, balance});
  }
  return rows;
}
