# Android v0.2.0｜管理画面に入れない間も進めた準備

## 実装
- Google Mobile Ads SDK 25.5.0：下部のアダプティブバナー。
- UMP 4.0.0：起動時の同意状態確認、必要な同意画面、プライバシー設定の再表示。
- Google Play Billing 9.1.0：非消費型の広告除去 `remove_ads`、購入・復元・承認処理。
- 署名付き購入情報とパッケージ・商品・支払い確定状態を確認するまで広告除去を許可しない。
- ローカルの購入確認は公開鍵を使う署名検証。サーバーによる検証は未実装。
- オフライン起動は過去の検証済み購入データで継続。オンライン照会が成功し未所有なら解除状態を取り消す。
- JavaScriptから広告除去状態を書き換えるAPIはない。
- Web版は従来の単一HTML。Androidの公開用HTMLだけに公式Capacitorランタイムと設定画面を埋め込む。

## 版ごとの動作
| 版 | 広告 | 購入 |
|---|---|---|
| 通常のWeb版 | 従来の仮枠 | なし |
| debug APK | Googleの専用テスト広告。UMPが広告リクエスト可能と判定した場合のみ | 未設定では無効 |
| release AAB | 本番用AdMobの2つのIDがなければ広告を無効化 | 公開鍵と有効化指定がなければ無効 |

テスト広告を本番収益と誤認しない。テストAPKの購入を販売中と表示しない。

## スマホで用意する管理情報
1. Google Play Consoleで「ローン計算ラクチンダー」を新規作成。既存2アプリは変更しない。
2. アプリID候補 `com.applylabpremium7.rakuchinder.loan` を初回アップロード前に確定。
3. AdMobでAndroidアプリを追加し「アプリID」（`~`を含む）と「バナー広告ユニットID」（`/`を含む）を取得。
4. AdMobのプライバシーとメッセージを設定。
5. Google Playで買い切り商品ID `remove_ads` を作成し、価格を目安300円に設定。
6. Google Playのライセンス用公開鍵を確認。
7. 公開済みプライバシーポリシー候補：https://loan-rakuchinder.pages.dev/privacy.html

AdMobのIDとライセンス公開鍵は秘密鍵ではない。パスワード・認証コード・サービスアカウント秘密鍵をソースやチャットへ入れない。

## 本番用ビルドの設定名
Gradleプロパティで指定する。値が揃うまで販売・本番広告は開始しない。

- `rakuchinderAdmobAppId`
- `rakuchinderBannerId`
- `rakuchinderPlayPublicKey`
- `rakuchinderBillingEnabled=true`

## 残る検証
- 実機で計算、キーボード、戻る操作、オフライン起動。
- テスト広告表示・回転・ナビゲーションバー・購入後のネイティブ広告と余白の消失。
- Playのテスト経路で購入成功／キャンセル／保留／購入復元／再インストール／返金後の再照会。
- UMPの必要地域・不要地域・通信失敗・設定変更。
- Google Playのデータセーフティは実際の配布版のSDKと設定に合わせて回答する。
- 署名付きAABとアップロード証明書、署名鍵のバックアップ。

## 公式資料
- https://developers.google.com/admob/android/quick-start
- https://developers.google.com/admob/android/banner
- https://developers.google.com/admob/android/privacy
- https://developers.google.com/admob/android/privacy/play-data-disclosure
- https://developer.android.com/google/play/billing/integrate
- https://capacitorjs.com/docs/android/custom-code
