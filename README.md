# ローン計算ラクチンダー

「めんどうに、助っ人。」ラクチンダーシリーズ第1号。
WebとAndroidの共通ソース。現在は公開準備版で、広告・課金は未接続。

## まず画面を見る
`dist/index.html` をChromeで開く。HTML/CSS/JavaScriptが1ファイルに収まっており、外部ライブラリは不要。

## Cloudflare Pages
GitHub連携後、フレームワークは「なし」、ビルドコマンドは空欄、出力ディレクトリは `dist`。
生成済みHTMLを配信する。Node.jsのインストールは公開先では不要。

## 開発
Node.js 22以上（今回の検証は24）を使用。
```sh
npm ci
npm test
npm run build
```
画面は `src/index.html`、計算は `src/core.mjs` を編集し、`npm run build` で単一HTMLを再生成。
`dist/index.html` だけを編集すると、次の生成時に失われる。

## Android
生成済みAndroidプロジェクトとCapacitor設定を含む。
```sh
npm ci
npm run android:sync
npm run android:open
```
WindowsにAndroid Studio、対応するJDK・Android SDKを準備する。
アプリID候補：`com.applylabpremium7.rakuchinder.loan`。
まだ署名・実機確認・APK/AABビルドを行っていない。
ストア用アプリIDは最初のアップロード前に確定する。

## 計算モデル
年利は0.01%単位の整数、残高は円単位の整数。
月利＝年利÷12。毎月の利息を四捨五入。
元利均等は通常の元利合計額を四捨五入、元金均等は通常の元金分を四捨五入。
最終回に残りの元金を精算。0%は別処理。
全期間固定・毎月払いで、日割り、追加借入、変動金利、ボーナス、各種費用は対象外。
月ごとに丸めた金額の合計を利息総額・総返済額として表示する。
極端な少額では途中完済や0円の回が生じる。

## 収益化
`AD_FREE` は開発用定数。利用者向けの無料広告除去機能ではない。
WebはAdSense、AndroidはAdMobを想定。現在はどちらも仮枠。
Google Play Billingによる広告除去と購入復元を別途実装する。
課金状態の確認後、HTMLの余白とネイティブSDKの表示を両方制御する必要がある。

詳しい完了・未完了・次の操作は `docs/RELEASE_STATUS.md`。
公式参考：https://capacitorjs.com/docs/getting-started
