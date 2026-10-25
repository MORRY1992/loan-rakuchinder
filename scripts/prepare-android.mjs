import {readFile, writeFile} from 'node:fs/promises';
const root = new URL('../', import.meta.url);
const [web, core, panel, nativeUI] = await Promise.all([
  readFile(new URL('dist/index.html', root), 'utf8'),
  readFile(new URL('node_modules/@capacitor/core/dist/capacitor.js', root), 'utf8'),
  readFile(new URL('src/android-panel.html', root), 'utf8'),
  readFile(new URL('src/android-ui.js', root), 'utf8')
]);
let android = web.replace('<footer class="footer">', panel + '<footer class="footer">');
android = android.replace('アクセス解析、広告SDK、課金SDKは未導入です。',
  'このAndroid版はGoogleの広告・同意管理・課金機能を組み込んでいます。広告の配信状態はテスト版・本番版で異なります。');
android = android.replace('現在の広告欄は仮枠で、広告は配信していません。Android版の広告除去課金も未接続です。導入前にデータ取扱いと購入条件の記載を更新します。',
  '広告を表示する場合は画面下部に表示します。広告除去が販売中の場合はアプリの設定から購入・復元できます。購入の確定と検証が完了するまで、広告除去は反映されません。');
android = android.replace('公開準備版 v0.1.0。', 'Androidテスト版 v0.2.0。');
android = android.replace('</body>', '<script>\n' + core + '\n</script>\n<script>\n' + nativeUI + '\n</script>\n</body>');
await writeFile(new URL('android/app/src/main/assets/public/index.html', root), android);
console.log('Prepared Android-only native settings and bundled Capacitor runtime. Web HTML is unchanged.');
