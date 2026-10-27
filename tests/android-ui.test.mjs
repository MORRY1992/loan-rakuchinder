import {readFileSync, existsSync} from 'node:fs';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';

const ui = readFileSync(new URL('../src/android-ui.js', import.meta.url), 'utf8');
const ready = {adFree:false, billingConfigured:true, billingStatus:'ready', price:'￥300',
  bannerHeight:52, privacyOptionsRequired:true};

async function screen(initial, actions = {}) {
  const elements = new Map();
  const styles = {};
  const element = id => {
    if (!elements.has(id)) elements.set(id, {hidden:false, disabled:false, textContent:'',
      handlers:{}, addEventListener(name, fn) { this.handlers[name] = fn; }});
    return elements.get(id);
  };
  let listener;
  const api = {async addListener(_, fn) { listener = fn; }, async getState() { return initial; }, ...actions};
  const started = vm.runInNewContext(ui, {
    window:{Capacitor:{isNativePlatform:() => true}},
    document:{documentElement:{style:{setProperty:(name,value) => styles[name] = value}}, getElementById:element},
    capacitorExports:{registerPlugin:() => api}
  });
  await started;
  return {element, styles, update:state => listener(state),
    click:id => element(id).handlers.click()};
}

test('unconfigured test builds disable purchases and remove the Web ad placeholder', async () => {
  const s = await screen({...ready, billingConfigured:false, price:'', bannerHeight:0});
  assert(s.element('buyRemoveAds').disabled);
  assert(s.element('restorePurchases').hidden);
  assert(s.element('adWrap').hidden);
  assert.equal(s.styles['--ad-space'], '0px');
  assert.match(s.element('nativePurchaseNote').textContent, /この版では購入できません/);
});

test('pending payments keep ads and explain that payment is not yet completed', async () => {
  const s = await screen(ready, {purchase:async () => ({...ready, billingStatus:'pending'})});
  await s.click('buyRemoveAds');
  assert.match(s.element('nativeActionStatus').textContent, /お支払いの完了後/);
  assert.equal(s.styles['--ad-space'], '52px');
  assert(s.element('buyRemoveAds').disabled);
});

test('cancelling a purchase leaves ownership unchanged and allows retry', async () => {
  const s = await screen(ready, {purchase:async () => ({...ready, cancelled:true})});
  await s.click('buyRemoveAds');
  assert.match(s.element('nativeActionStatus').textContent, /キャンセル/);
  assert(!s.element('buyRemoveAds').hidden);
  assert(!s.element('buyRemoveAds').disabled);
});

test('a verified restoration removes the purchase button and the native ad space', async () => {
  const s = await screen(ready, {restore:async () => ({...ready, adFree:true, bannerHeight:0})});
  await s.click('restorePurchases');
  assert(s.element('buyRemoveAds').hidden);
  assert.equal(s.styles['--ad-space'], '0px');
  assert.match(s.element('nativeActionStatus').textContent, /広告除去を確認/);
});

test('connection failures preserve the current display and re-enable controls', async () => {
  const s = await screen(ready, {restore:async () => { throw new Error('接続を確認してください'); }});
  await s.click('restorePurchases');
  assert.match(s.element('nativeActionStatus').textContent, /接続を確認/);
  assert(!s.element('restorePurchases').disabled);
  assert.equal(s.styles['--ad-space'], '52px');
});

test('privacy opens the bundled document without requiring a network request', () => {
  const panel = readFileSync(new URL('../src/android-panel.html', import.meta.url), 'utf8');
  assert.match(panel, /href="privacy\.html"/);
  assert(existsSync(new URL('../android/app/src/main/assets/public/privacy.html', import.meta.url)));
});
