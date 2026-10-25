// Loaded only into the bundled Android HTML; no external scripts are fetched.
(async function setupAndroid() {
  const root = document.documentElement;
  const get = id => document.getElementById(id);
  get('adWrap').hidden = true;
  root.style.setProperty('--ad-space', '0px');
  if (!window.Capacitor || !window.Capacitor.isNativePlatform()) return;
  const api = capacitorExports.registerPlugin('LoanMonetization');
  let current = null;
  let busy = false;
  function renderNative(state) {
    current = state;
    root.style.setProperty('--ad-space', Math.max(0, Number(state.bannerHeight) || 0) + 'px');
    const configured = state.billingConfigured;
    get('buyRemoveAds').hidden = state.adFree;
    get('buyRemoveAds').disabled = busy || !configured || !state.price || state.billingStatus !== 'ready';
    get('buyRemoveAds').textContent = state.price ? '広告を削除する（' + state.price + '・買い切り）' : '広告除去（準備中）';
    get('restorePurchases').hidden = !configured;
    get('restorePurchases').disabled = busy;
    get('privacyOptions').hidden = !state.privacyOptionsRequired;
    get('privacyOptions').disabled = busy;
    get('nativePurchaseNote').textContent = state.adFree ? '広告除去は購入済みです。' :
      state.billingStatus === 'pending' ? 'お支払いの完了待ちです。購入確定後に反映されます。' :
      !configured ? '広告除去の販売準備中です。この版では購入できません。' :
      state.price ? '一度の購入で、このアプリの広告を非表示にできます。購入前にGoogle Playの金額をご確認ください。' :
      '購入情報を確認できません。Google Playとの接続状況をご確認ください。';
  }
  await api.addListener('stateChanged', renderNative);
  renderNative(await api.getState());
  async function act(kind) {
    if (busy) return;
    busy = true; renderNative(current); get('nativeActionStatus').textContent = '確認しています…';
    try {
      const state = await api[kind](); renderNative(state);
      get('nativeActionStatus').textContent = state.cancelled ? '購入をキャンセルしました。' :
        state.billingStatus === 'pending' ? 'お支払いの完了後、アプリを開くと再確認します。' :
        kind === 'privacyOptions' ? '設定を確認しました。' :
        state.adFree ? '広告除去を確認しました。' : '購入済みの広告除去が見つかりませんでした。';
    } catch (error) {
      get('nativeActionStatus').textContent = error.message || '確認できませんでした。しばらくしてからお試しください。';
    } finally { busy = false; if (current) renderNative(current); }
  }
  get('buyRemoveAds').addEventListener('click', () => act('purchase'));
  get('restorePurchases').addEventListener('click', () => act('restore'));
  get('privacyOptions').addEventListener('click', () => act('privacyOptions'));
})().catch(() => {
  const status = document.getElementById('nativeActionStatus');
  if (status) status.textContent = 'アプリの設定を読み込めませんでした。計算は引き続き利用できます。';
});
