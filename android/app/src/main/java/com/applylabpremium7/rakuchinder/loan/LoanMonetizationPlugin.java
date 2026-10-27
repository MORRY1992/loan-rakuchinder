package com.applylabpremium7.rakuchinder.loan;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.android.billingclient.api.*;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.ads.*;
import com.google.android.ump.*;
import java.util.Collections;
import java.util.List;

/** Native ads, consent and a non-consumable remove_ads purchase. */
@CapacitorPlugin(name = "LoanMonetization")
public class LoanMonetizationPlugin extends Plugin implements PurchasesUpdatedListener {
    private BillingClient billing;
    private ConsentInformation consent;
    private SharedPreferences preferences;
    private AdView banner;
    private FrameLayout bannerContainer;
    private boolean adFree = false;
    private boolean connecting = false;
    private boolean adsStarting = false;
    private boolean bannerVisible = false;
    private boolean destroyed = false;
    private int bottomInset = 0;
    private String billingStatus = "unconfigured";
    private String adsStatus = "idle";
    private String price = "";
    private PluginCall pendingPurchase;

    @Override public void load() {
        preferences = getContext().getSharedPreferences("verified-ad-removal", 0);
        restoreVerifiedCache();
        getActivity().runOnUiThread(() -> {
            consent = UserMessagingPlatform.getConsentInformation(getContext());
            if (BuildConfig.BILLING_ENABLED) {
                billing = BillingClient.newBuilder(getContext())
                    .setListener(this)
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .enableAutoServiceReconnection()
                    .build();
                connectBilling();
            }
            requestConsent();
        });
    }

    private boolean authentic(Purchase purchase) {
        if (!BuildConfig.BILLING_ENABLED || !purchase.getProducts().contains(BuildConfig.AD_FREE_PRODUCT_ID)
                || purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED
                || !getContext().getPackageName().equals(purchase.getPackageName())) return false;
        try {
            return PurchaseVerifier.verify(Base64.decode(BuildConfig.PLAY_PUBLIC_KEY, Base64.DEFAULT),
                purchase.getOriginalJson(), Base64.decode(purchase.getSignature(), Base64.DEFAULT));
        } catch (Exception ignored) { return false; }
    }

    private void restoreVerifiedCache() {
        String json = preferences.getString("purchase-json", "");
        String signature = preferences.getString("purchase-signature", "");
        try {
            Purchase purchase = new Purchase(json, signature);
            adFree = (purchase.isAcknowledged() || preferences.getBoolean("ack-confirmed", false)) && authentic(purchase);
        } catch (Exception ignored) { adFree = false; }
    }

    private JSObject state() {
        JSObject data = new JSObject();
        data.put("adFree", adFree);
        data.put("billingConfigured", BuildConfig.BILLING_ENABLED);
        data.put("billingStatus", billingStatus);
        data.put("price", price);
        data.put("testAds", BuildConfig.TEST_ADS);
        data.put("adsStatus", adsStatus);
        data.put("bannerHeight", bannerVisible && banner != null
            ? Math.ceil((banner.getAdSize().getHeightInPixels(getContext()) + bottomInset)
                / getContext().getResources().getDisplayMetrics().density) : 0);
        data.put("privacyOptionsRequired", consent != null &&
            consent.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED);
        return data;
    }

    private void changed() {
        if (!destroyed) notifyListeners("stateChanged", state());
    }

    @PluginMethod public void getState(PluginCall call) {
        getActivity().runOnUiThread(() -> call.resolve(state()));
    }

    private void connectBilling() {
        if (billing == null || connecting || destroyed) return;
        connecting = true; billingStatus = "checking"; changed();
        billing.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(@NonNull BillingResult result) {
                getActivity().runOnUiThread(() -> {
                    connecting = false;
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        billingStatus = "ready";
                        queryOwned(null);
                        queryProduct(null);
                    } else { billingStatus = "unavailable"; changed(); }
                });
            }
            @Override public void onBillingServiceDisconnected() {
                connecting = false;
                billingStatus = "unavailable";
                changed();
            }
        });
    }

    private void queryProduct(PluginCall purchaseCall) {
        if (billing == null || !billing.isReady()) {
            if (purchaseCall != null) finishPurchaseError("Google Playに接続できません。しばらくしてからお試しください。");
            return;
        }
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(
            Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BuildConfig.AD_FREE_PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build())).build();
        billing.queryProductDetailsAsync(params, (result, details) -> getActivity().runOnUiThread(() -> {
            if (destroyed) return;
            List<ProductDetails> products = details.getProductDetailsList();
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || products.isEmpty()) {
                price = ""; billingStatus = "unavailable"; changed();
                if (purchaseCall != null) finishPurchaseError("購入情報を取得できません。Google Play版からお試しください。");
                return;
            }
            ProductDetails product = products.get(0);
            ProductDetails.OneTimePurchaseOfferDetails offer = product.getOneTimePurchaseOfferDetails();
            if (offer == null) {
                List<ProductDetails.OneTimePurchaseOfferDetails> offers = product.getOneTimePurchaseOfferDetailsList();
                if (offers != null && !offers.isEmpty()) offer = offers.get(0);
            }
            if (offer == null) {
                billingStatus = "unavailable"; changed();
                if (purchaseCall != null) finishPurchaseError("現在、この商品は購入できません。");
                return;
            }
            price = offer.getFormattedPrice(); billingStatus = "ready"; changed();
            if (purchaseCall == null) return;
            BillingFlowParams.ProductDetailsParams.Builder selected = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product);
            if (offer.getOfferToken() != null && !offer.getOfferToken().isEmpty()) selected.setOfferToken(offer.getOfferToken());
            BillingResult launch = billing.launchBillingFlow(getActivity(), BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(selected.build())).build());
            if (launch.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                queryOwned(pendingPurchase); pendingPurchase = null;
            } else if (launch.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                finishPurchaseError("購入画面を開けませんでした。Google Playの状態をご確認ください。");
            }
        }));
    }

    @PluginMethod public void purchase(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (!BuildConfig.BILLING_ENABLED) { call.reject("広告除去の販売準備中です。"); return; }
            if (adFree) { call.resolve(state()); return; }
            if (pendingPurchase != null) { call.reject("購入の確認中です。しばらくお待ちください。"); return; }
            if (billing == null || !billing.isReady()) { connectBilling(); call.reject("Google Playへの接続を確認してから再度お試しください。"); return; }
            pendingPurchase = call;
            // Always obtain fresh ProductDetails before launching a purchase.
            queryProduct(call);
        });
    }

    @PluginMethod public void restore(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (!BuildConfig.BILLING_ENABLED) { call.reject("広告除去の販売準備中です。"); return; }
            if (billing == null || !billing.isReady()) { connectBilling(); call.reject("Google Playに接続してから再度お試しください。"); return; }
            queryOwned(call);
        });
    }

    private void queryOwned(PluginCall call) {
        if (billing == null || !billing.isReady()) { if (call != null) call.reject("購入履歴を確認できません。"); return; }
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            (result, purchases) -> getActivity().runOnUiThread(() -> {
                if (destroyed) return;
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    if (call != null) call.reject("購入履歴を確認できません。通信状況をご確認ください。");
                    return; // Keep previously verified ownership when offline.
                }
                for (Purchase purchase : purchases) {
                    if (purchase.getProducts().contains(BuildConfig.AD_FREE_PRODUCT_ID)) {
                        processPurchase(purchase, call); return;
                    }
                }
                adFree = false;
                preferences.edit().remove("purchase-json").remove("purchase-signature").remove("ack-confirmed").apply();
                maybeStartAds(); changed();
                if (call != null) call.resolve(state());
            }));
    }

    private void processPurchase(Purchase purchase, PluginCall call) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            billingStatus = "pending"; changed();
            if (call != null) call.resolve(state());
            return; // Never grant ad removal for a pending payment.
        }
        if (!authentic(purchase)) {
            if (call != null) call.reject("購入を確認できませんでした。再度、購入の復元をお試しください。");
            return;
        }
        if (purchase.isAcknowledged()) { grantPurchase(purchase, call); return; }
        billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(),
            result -> getActivity().runOnUiThread(() -> {
                if (destroyed) return;
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    grantPurchase(purchase, call);
                } else if (call != null) call.reject("購入確認を完了できません。通信回復後に購入の復元をお試しください。");
            }));
    }

    private void grantPurchase(Purchase purchase, PluginCall call) {
        adFree = true; billingStatus = "ready";
        preferences.edit().putString("purchase-json", purchase.getOriginalJson())
            .putString("purchase-signature", purchase.getSignature()).putBoolean("ack-confirmed", true).apply();
        hideBanner(); changed();
        if (call != null) call.resolve(state());
    }

    @Override public void onPurchasesUpdated(@NonNull BillingResult result, List<Purchase> purchases) {
        getActivity().runOnUiThread(() -> {
            PluginCall call = pendingPurchase; pendingPurchase = null;
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (Purchase purchase : purchases) if (purchase.getProducts().contains(BuildConfig.AD_FREE_PRODUCT_ID)) {
                    processPurchase(purchase, call); return;
                }
                if (call != null) call.reject("購入対象を確認できませんでした。");
            } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                if (call != null) { JSObject data = state(); data.put("cancelled", true); call.resolve(data); }
            } else if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                queryOwned(call);
            } else if (call != null) call.reject("購入処理を完了できませんでした。再度お試しください。");
        });
    }

    private void finishPurchaseError(String message) {
        if (pendingPurchase != null) { pendingPurchase.reject(message); pendingPurchase = null; }
    }

    private void requestConsent() {
        if (!BuildConfig.ADS_ENABLED || adFree) { adsStatus = adFree ? "removed" : "disabled"; changed(); return; }
        adsStatus = "consent";
        consent.requestConsentInfoUpdate(getActivity(), new ConsentRequestParameters.Builder().build(),
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(getActivity(), error -> {
                maybeStartAds(); changed();
            }), error -> { adsStatus = "unavailable"; maybeStartAds(); changed(); });
        changed();
    }

    private void maybeStartAds() {
        if (destroyed || !BuildConfig.ADS_ENABLED || adFree || consent == null || !consent.canRequestAds()) return;
        if (adsStarting || banner != null) return;
        adsStarting = true; adsStatus = "loading"; changed();
        MobileAds.initialize(getContext(), status -> getActivity().runOnUiThread(() -> {
            adsStarting = false;
            if (!destroyed && !adFree && consent.canRequestAds()) showBanner();
        }));
    }

    private void showBanner() {
        if (banner != null || destroyed || adFree) return;
        bannerContainer = new FrameLayout(getContext());
        bannerContainer.setBackgroundColor(Color.WHITE);
        bannerContainer.setVisibility(View.GONE);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        ((ViewGroup) getActivity().findViewById(android.R.id.content)).addView(bannerContainer, containerParams);
        ViewCompat.setOnApplyWindowInsetsListener(bannerContainer, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            bottomInset = bars.bottom;
            view.setPadding(bars.left, 0, bars.right, bars.bottom);
            changed();
            return insets;
        });
        ViewCompat.requestApplyInsets(bannerContainer);
        float density = getContext().getResources().getDisplayMetrics().density;
        int widthPx = getBridge().getWebView().getWidth();
        if (widthPx <= 0) widthPx = getContext().getResources().getDisplayMetrics().widthPixels;
        int widthDp = Math.max(1, (int)(widthPx / density));
        banner = new AdView(getContext());
        banner.setAdUnitId(BuildConfig.BANNER_UNIT_ID);
        banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(getContext(), widthDp));
        banner.setAdListener(new AdListener() {
            @Override public void onAdLoaded() {
                if (destroyed || adFree) { hideBanner(); return; }
                bannerVisible = true; adsStatus = "shown";
                bannerContainer.setVisibility(View.VISIBLE); changed();
            }
            @Override public void onAdFailedToLoad(@NonNull LoadAdError error) {
                hideBanner(); adsStatus = "unavailable"; changed();
            }
        });
        bannerContainer.addView(banner, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        banner.loadAd(new AdRequest.Builder().build());
    }

    private void hideBanner() {
        bannerVisible = false;
        if (banner != null) { banner.destroy(); banner = null; }
        if (bannerContainer != null) {
            ViewGroup parent = (ViewGroup) bannerContainer.getParent();
            if (parent != null) parent.removeView(bannerContainer);
            bannerContainer = null;
        }
        if (adFree) adsStatus = "removed";
    }

    @PluginMethod public void privacyOptions(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (consent == null || consent.getPrivacyOptionsRequirementStatus() != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
                call.resolve(state()); return;
            }
            UserMessagingPlatform.showPrivacyOptionsForm(getActivity(), error -> {
                hideBanner(); maybeStartAds(); changed();
                if (error == null) call.resolve(state()); else call.reject("広告のプライバシー設定を開けませんでした。");
            });
        });
    }

    @Override protected void handleOnResume() {
        if (banner != null) banner.resume();
        if (billing != null) {
            if (billing.isReady()) {
                queryOwned(null);
                queryProduct(null);
            } else {
                connectBilling();
            }
        }
        maybeStartAds();
    }
    @Override protected void handleOnPause() { if (banner != null) banner.pause(); }
    @Override protected void handleOnDestroy() {
        destroyed = true;
        hideBanner();
        if (billing != null) billing.endConnection();
        finishPurchaseError("画面が閉じられました。再起動して購入の復元をお試しください。");
    }
}
