package com.matejdr.admanager;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.adsbynimbus.request.NimbusResponse;
import com.amazon.device.ads.AdError;
import com.amazon.device.ads.DTBAdCallback;
import com.amazon.device.ads.DTBAdRequest;
import com.amazon.device.ads.DTBAdResponse;
import com.amazon.device.ads.DTBAdSize;
import com.amazon.device.ads.DTBAdUtil;
import com.adsbynimbus.NimbusAdManager;
import com.adsbynimbus.NimbusError;
import com.adsbynimbus.lineitem.GoogleDynamicPrice;
import com.adsbynimbus.lineitem.LinearPriceGranularity;
import com.adsbynimbus.lineitem.LinearPriceMapping;
import com.adsbynimbus.openrtb.enumerations.Position;
import com.adsbynimbus.openrtb.request.Format;
import com.adsbynimbus.request.NimbusRequest;
import com.adsbynimbus.request.RequestManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.facebook.react.views.view.ReactViewGroup;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerAdView;
import com.google.android.gms.ads.admanager.AppEventListener;
import com.matejdr.admanager.customClasses.CustomTargeting;
import com.matejdr.admanager.utils.Targeting;
import java.util.ArrayList;
import java.util.List;

class BannerAdView extends ReactViewGroup implements AppEventListener, LifecycleEventListener {
    protected AdManagerAdView adManagerAdView;
    Activity currentActivityContext;
    String[] testDevices;
    AdSize[] validAdSizes;
    String adUnitID;
    String apsSlotId;
    Boolean adsNimbus = true;
    AdSize adSize;
    Boolean adsRefresh = true;
    int adsCount = 0;
    private NimbusResponse nimbusAdResponse = null;
    private DTBAdResponse apsAdResponse = null;

    // Targeting
    Boolean hasTargeting = false;
    CustomTargeting[] customTargeting;
    String[] categoryExclusions;
    String[] keywords;
    String contentURL;
    String publisherProvidedID;
    Location location;
    String correlator;
    String TAG = "ascAds";
    AdSize[] ascAdSizesArray;
    Handler mAdHandler = new Handler();
    Handler mDelayHandler = new Handler();
    Runnable adsRequestRunnable;
    Runnable hbRunnable;
    long adsRefreshInterval = 30000;
    Boolean bannerAdsOn = true;

    int top;
    int left;
    int width;
    int height;

    public BannerAdView(final Context context, ReactApplicationContext applicationContext) {
        super(context);
        currentActivityContext = applicationContext.getCurrentActivity();
        applicationContext.addLifecycleEventListener(this);
        Log.d(TAG, "BannerAdView");
        if(adsRequestRunnable == null){
            adsRequestRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "adsRequestRunnable: ");
                    requestAds();
                }
            };
        }
        if(hbRunnable == null){
            hbRunnable =  new Runnable() {
                @Override
                public void run() {
                    finishHeaderBidding();
                }
            };
        }
        this.createAdView();
    }

    private boolean isFluid() {
        return AdSize.FLUID.equals(this.adSize);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        post(new MeasureAndLayoutRunnable());
    }

    private void createAdView() {
        Log.d(TAG, "createAdView");
        if(this.adManagerAdView != null) this.adManagerAdView.destroy();
        if(nimbusAdResponse != null) { nimbusAdResponse = null;}
        if(apsAdResponse != null) {apsAdResponse = null;}
        if(this.currentActivityContext == null) return;
        this.adManagerAdView = new AdManagerAdView(currentActivityContext);

        if (isFluid()) {
            AdManagerAdView.LayoutParams layoutParams = new AdManagerAdView.LayoutParams(
                ReactViewGroup.LayoutParams.MATCH_PARENT,
                ReactViewGroup.LayoutParams.WRAP_CONTENT
            );
            this.adManagerAdView.setLayoutParams(layoutParams);
        }

        this.adManagerAdView.setAppEventListener(this);
        this.adManagerAdView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                if (isFluid()) {
                    top = 0;
                    left = 0;
                    width = getWidth();
                    height = getHeight();
                } else {
                    top = adManagerAdView.getTop();
                    left = adManagerAdView.getLeft();
                    width = adManagerAdView.getAdSize().getWidthInPixels(getContext());
                    height = adManagerAdView.getAdSize().getHeightInPixels(getContext());
                }

                if (!isFluid()) {
                    sendOnSizeChangeEvent();
                }

                WritableMap ad = Arguments.createMap();
                ad.putString("type", "banner");

                WritableMap gadSize = Arguments.createMap();
                gadSize.putString("adSize", adManagerAdView.getAdSize().toString());
                gadSize.putDouble("width", adManagerAdView.getAdSize().getWidth());
                gadSize.putDouble("height", adManagerAdView.getAdSize().getHeight());
                ad.putMap("gadSize", gadSize);
                adsCount = adsCount+1;
                ad.putString("isFluid", String.valueOf(isFluid()));

                WritableMap measurements = Arguments.createMap();
                measurements.putInt("adWidth", width);
                measurements.putInt("adHeight", height);
                measurements.putInt("width", getMeasuredWidth());
                measurements.putInt("height", getMeasuredHeight());
                measurements.putInt("left", left);
                measurements.putInt("top", top);
                ad.putMap("measurements", measurements);

                sendEvent(RNAdManagerBannerViewManager.EVENT_AD_LOADED, ad);
                // re load the ads request after ads refresh
                Log.d(TAG, "ads loaded after ad received : " + adsRefresh);
                if(adsRefresh){
                    Log.d(TAG, "re request Ads ");
                    mAdHandler.postDelayed(adsRequestRunnable,adsRefreshInterval);
                }
            }

            @Override
            public void onAdFailedToLoad(LoadAdError adError) {
                String errorMessage = "Unknown error";
                switch (adError.getCode()) {
                    case AdManagerAdRequest.ERROR_CODE_INTERNAL_ERROR:
                        errorMessage = "Internal error, an invalid response was received from the ad server.";
                        break;
                    case AdManagerAdRequest.ERROR_CODE_INVALID_REQUEST:
                        errorMessage = "Invalid ad request, possibly an incorrect ad unit ID was given.";
                        break;
                    case AdManagerAdRequest.ERROR_CODE_NETWORK_ERROR:
                        errorMessage = "The ad request was unsuccessful due to network connectivity.";
                        break;
                    case AdManagerAdRequest.ERROR_CODE_NO_FILL:
                        errorMessage = "The ad request was successful, but no ad was returned due to lack of ad inventory.";
                        break;
                }
                WritableMap event = Arguments.createMap();
                WritableMap error = Arguments.createMap();
                error.putString("message", errorMessage);
                event.putMap("error", error);
                sendEvent(RNAdManagerBannerViewManager.EVENT_AD_FAILED_TO_LOAD, event);
            }

            @Override
            public void onAdOpened() {
                WritableMap event = Arguments.createMap();
                sendEvent(RNAdManagerBannerViewManager.EVENT_AD_OPENED, event);
            }

            @Override
            public void onAdClosed() {
                WritableMap event = Arguments.createMap();
                sendEvent(RNAdManagerBannerViewManager.EVENT_AD_CLOSED, event);
            }

        });
        this.addView(this.adManagerAdView);
    }

    private void sendOnSizeChangeEvent() {
        int width;
        int height;
        WritableMap event = Arguments.createMap();
        AdSize adSize = this.adManagerAdView.getAdSize();
        width = adSize.getWidth();
        height = adSize.getHeight();
        event.putString("type", "banner");
        event.putDouble("width", width);
        event.putDouble("height", height);
        sendEvent(RNAdManagerBannerViewManager.EVENT_SIZE_CHANGE, event);
    }

    private void sendEvent(String name, @Nullable WritableMap event) {
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
            getId(),
            name,
            event);
            Log.d(TAG, "sendEvent: "+name);
    }

    public void loadBanner() {
        Log.d(TAG, "loadBanner");
        ArrayList<AdSize> adSizes = new ArrayList<AdSize>();
        if (this.adSize != null) {
            adSizes.add(this.adSize);
        }
        if (this.validAdSizes != null) {
            for (int i = 0; i < this.validAdSizes.length; i++) {
                if (!adSizes.contains(this.validAdSizes[i])) {
                    adSizes.add(this.validAdSizes[i]);
                }
            }
        }

        if (adSizes.size() == 0) {
            adSizes.add(AdSize.BANNER);
        }

        ascAdSizesArray = adSizes.toArray(new AdSize[adSizes.size()]);
        this.adManagerAdView.setAdSizes(ascAdSizesArray);

        requestAds();


        // AdManagerAdRequest.Builder adRequestBuilder = new AdManagerAdRequest.Builder();

        // List<String> testDevicesList = new ArrayList<>();
        // if (testDevices != null) {
        //     for (int i = 0; i < testDevices.length; i++) {
        //         String testDevice = testDevices[i];
        //         if (testDevice == "SIMULATOR") {
        //             testDevice = AdManagerAdRequest.DEVICE_ID_EMULATOR;
        //         }
        //         testDevicesList.add(testDevice);
        //     }
        //     RequestConfiguration requestConfiguration
        //         = new RequestConfiguration.Builder()
        //         .setTestDeviceIds(testDevicesList)
        //         .build();
        //     MobileAds.setRequestConfiguration(requestConfiguration);
        // }

        // if (correlator == null) {
        //     correlator = (String) Targeting.getCorelator(adUnitID);
        // }
        // Bundle bundle = new Bundle();
        // bundle.putString("correlator", correlator);

        // adRequestBuilder.addNetworkExtrasBundle(AdMobAdapter.class, bundle);


        // // Targeting
        // if (hasTargeting) {
        //     if (customTargeting != null && customTargeting.length > 0) {
        //         for (int i = 0; i < customTargeting.length; i++) {
        //             String key = customTargeting[i].key;
        //             if (!key.isEmpty()) {
        //                 if (customTargeting[i].value != null && !customTargeting[i].value.isEmpty()) {
        //                     adRequestBuilder.addCustomTargeting(key, customTargeting[i].value);
        //                 } else if (customTargeting[i].values != null && !customTargeting[i].values.isEmpty()) {
        //                     adRequestBuilder.addCustomTargeting(key, customTargeting[i].values);
        //                 }
        //             }
        //         }
        //     }
        //     if (categoryExclusions != null && categoryExclusions.length > 0) {
        //         for (int i = 0; i < categoryExclusions.length; i++) {
        //             String categoryExclusion = categoryExclusions[i];
        //             if (!categoryExclusion.isEmpty()) {
        //                 adRequestBuilder.addCategoryExclusion(categoryExclusion);
        //             }
        //         }
        //     }
        //     if (keywords != null && keywords.length > 0) {
        //         for (int i = 0; i < keywords.length; i++) {
        //             String keyword = keywords[i];
        //             if (!keyword.isEmpty()) {
        //                 adRequestBuilder.addKeyword(keyword);
        //             }
        //         }
        //     }
        //     if (contentURL != null) {
        //         adRequestBuilder.setContentUrl(contentURL);
        //     }
        //     if (publisherProvidedID != null) {
        //         adRequestBuilder.setPublisherProvidedId(publisherProvidedID);
        //     }
        //     if (location != null) {
        //         adRequestBuilder.setLocation(location);
        //     }
        // }

        // AdManagerAdRequest adRequest = adRequestBuilder.build();
        // this.adManagerAdView.loadAd(adRequest);
    }

    private void requestAds() {
        if(!bannerAdsOn || this.currentActivityContext == null) return;
        if(nimbusAdResponse != null){
            nimbusAdResponse = null;
        }
        if(apsAdResponse != null){
            apsAdResponse = null;
        }

        Log.d(TAG, "requestAds: " + ascAdSizesArray);
        if(this.adsNimbus){
            // Create an instance of the NimbusAdManager class
            NimbusAdManager nimbusAdManager = new NimbusAdManager();
            NimbusRequest nimbusRequest = NimbusRequest.forBannerAd(this.adUnitID, Format.LETTERBOX, Position.HEADER);
            // Make the request to Nimbus and apply Dynamic Price
            nimbusAdManager.makeRequest(currentActivityContext,nimbusRequest, new RequestManager.Listener(){
                @Override
                public void onAdResponse(NimbusResponse nimbusResponse){
                    nimbusAdResponse = nimbusResponse;
                    Log.d(TAG, "nimbus success");
                }
                @Override
                public void onError(NimbusError error) {
                    nimbusAdResponse = null;
                    Log.d(TAG, "nimbus failed");
                }
            });
        }
        if(this.apsSlotId != null) {
            DTBAdRequest loader = new DTBAdRequest();
            loader.setSizes(new DTBAdSize(ascAdSizesArray[0].getWidth(), ascAdSizesArray[0].getHeight(), this.apsSlotId));
            loader.loadAd(new DTBAdCallback() {
                @Override
                public void onFailure(AdError adError) {
                    apsAdResponse =  null;
                    Log.d(TAG, "aps failed");
                    // delay with 500ms finish header bidding
                    mDelayHandler.postDelayed(hbRunnable,750);
                }
                @Override
                public void onSuccess(DTBAdResponse dtbAdResponse) {
                    Log.d(TAG, "aps success");
                    apsAdResponse = dtbAdResponse;
                    // delay with 500ms finish header bidding
                    mDelayHandler.postDelayed(hbRunnable,750);
                }
            });
        }else{
            // call finish header bidding without aps
            mDelayHandler.postDelayed(hbRunnable,750);
        }
    }

    private void finishHeaderBidding(){
        Log.d(TAG, "finishHeaderBidding");
        AdManagerAdRequest.Builder adRequestBuilder =  null;

        if(apsAdResponse != null){
            adRequestBuilder = DTBAdUtil.INSTANCE.createAdManagerAdRequestBuilder(apsAdResponse);
        }else {
            adRequestBuilder = new AdManagerAdRequest.Builder();
        }
        if(nimbusAdResponse != null){
            LinearPriceMapping priceMapping = new LinearPriceMapping(new LinearPriceGranularity(0,2000,1));
            GoogleDynamicPrice.applyDynamicPrice(nimbusAdResponse,adRequestBuilder,priceMapping);
            nimbusAdResponse = null;
        }

        List<String> testDevicesList = new ArrayList<>();
        if (testDevices != null) {
            for (int i = 0; i < testDevices.length; i++) {
                String testDevice = testDevices[i];
                if (testDevice == "SIMULATOR") {
                    testDevice = AdManagerAdRequest.DEVICE_ID_EMULATOR;
                }
                testDevicesList.add(testDevice);
            }
            RequestConfiguration requestConfiguration
                = new RequestConfiguration.Builder()
                .setTestDeviceIds(testDevicesList)
                .build();
            MobileAds.setRequestConfiguration(requestConfiguration);
        }

        if (correlator == null) {
            correlator = (String) Targeting.getCorelator(adUnitID);
        }
        Bundle bundle = new Bundle();
        bundle.putString("correlator", correlator);

        adRequestBuilder.addNetworkExtrasBundle(AdMobAdapter.class, bundle);

        // Targeting
        if (hasTargeting) {
            if (customTargeting != null && customTargeting.length > 0) {
                if(this.adsRefresh){
                    adRequestBuilder.addCustomTargeting("refreshIteration",String.valueOf(adsCount));
                }
                for (int i = 0; i < customTargeting.length; i++) {
                    String key = customTargeting[i].key;
                    if (!key.isEmpty()) {
                        if (customTargeting[i].value != null && !customTargeting[i].value.isEmpty()) {
                            adRequestBuilder.addCustomTargeting(key, customTargeting[i].value);
                        } else if (customTargeting[i].values != null && !customTargeting[i].values.isEmpty()) {
                            adRequestBuilder.addCustomTargeting(key, customTargeting[i].values);
                        }
                    }
                }
            }
            if (categoryExclusions != null && categoryExclusions.length > 0) {
                for (int i = 0; i < categoryExclusions.length; i++) {
                    String categoryExclusion = categoryExclusions[i];
                    if (!categoryExclusion.isEmpty()) {
                        adRequestBuilder.addCategoryExclusion(categoryExclusion);
                    }
                }
            }
            if (keywords != null && keywords.length > 0) {
                for (int i = 0; i < keywords.length; i++) {
                    String keyword = keywords[i];
                    if (!keyword.isEmpty()) {
                        adRequestBuilder.addKeyword(keyword);
                    }
                }
            }
            if (contentURL != null) {
                adRequestBuilder.setContentUrl(contentURL);
            }
            if (publisherProvidedID != null) {
                adRequestBuilder.setPublisherProvidedId(publisherProvidedID);
            }
            // if (location != null) {
            //     adRequestBuilder.setLocation(location);
            // }
        }
        Log.d(TAG, "CustomTargeting : "+adRequestBuilder.build().getCustomTargeting());
        this.adManagerAdView.loadAd(adRequestBuilder.build());
    }

    public void setAdUnitID(String adUnitID) {
        Log.d(TAG, "setAdUnitID: " + adUnitID);
        if (this.adUnitID != null) {
            // We can only set adUnitID once, so when it was previously set we have
            // to recreate the view
            this.createAdView();
        }
        this.adUnitID = adUnitID;
        this.adManagerAdView.setAdUnitId(adUnitID);
    }

    public void setAdsRefresh(Boolean adsRefresh){
        this.adsRefresh = adsRefresh;
    }

    public void setApsSlotId(String apsSlotId){
        Log.d(TAG, "setApsSlotId: " + apsSlotId);
        this.apsSlotId = apsSlotId;
    }

    public void setAdsNimbus(Boolean adsNimbus){
        this.adsNimbus = adsNimbus;
    }

//    public void setRefreshInterval(long interval){
//        Log.d(TAG, "setRefreshInterval: " + interval);
//    }

    public void setTestDevices(String[] testDevices) {
        this.testDevices = testDevices;
    }

    // Targeting
    public void setCustomTargeting(CustomTargeting[] customTargeting) {
        this.customTargeting = customTargeting;
    }

    public void setCategoryExclusions(String[] categoryExclusions) {
        this.categoryExclusions = categoryExclusions;
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords;
    }

    public void setContentURL(String contentURL) {
        this.contentURL = contentURL;
    }

    public void setPublisherProvidedID(String publisherProvidedID) {
        this.publisherProvidedID = publisherProvidedID;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setAdSize(AdSize adSize) {
        Log.d(TAG, "setAdSize: " + adSize);
        this.adSize = adSize;
    }

    public void setValidAdSizes(AdSize[] adSizes) {
        this.validAdSizes = adSizes;
    }

    public void setCorrelator(String correlator) {
        this.correlator = correlator;
    }

    @Override
    public void onAppEvent(String name, String info) {
        WritableMap event = Arguments.createMap();
        event.putString("name", name);
        event.putString("info", info);
        sendEvent(RNAdManagerBannerViewManager.EVENT_APP_EVENT, event);
    }

    @Override
    public void onHostResume() {
        Log.d(TAG, "onHostResume ");
        if (!bannerAdsOn) {
            Log.d(TAG, "onHostResume - x");
            bannerAdsOn = true;
            requestAds();
        }
        if (this.adManagerAdView != null) {
            this.adManagerAdView.resume();
        }
    }

    @Override
    public void onHostPause() {
        Log.d(TAG, "onHostPause ");
        if (this.adManagerAdView != null) {
            this.adManagerAdView.pause();
        }
        bannerAdsOn = false;
        if(adsRequestRunnable != null){
            mAdHandler.removeCallbacks(adsRequestRunnable);
        }
        if(hbRunnable != null) {
            mDelayHandler.removeCallbacks(hbRunnable);
        }
    }

    @Override
    public void onHostDestroy() {
        Log.d(TAG, "onHostDestroy ");
        if(adsRequestRunnable != null){
            mAdHandler.removeCallbacks(adsRequestRunnable);
            adsRequestRunnable = null;
        }
        if(hbRunnable != null) {
            mDelayHandler.removeCallbacks(hbRunnable);
            hbRunnable = null;
        }
        mAdHandler = null;
        mDelayHandler = null;
        bannerAdsOn = true;
        if (this.adManagerAdView != null) {
            this.currentActivityContext = null;
            this.adManagerAdView.destroy();
        }
        if(nimbusAdResponse != null) { nimbusAdResponse = null;}
        if(apsAdResponse != null) {apsAdResponse = null;}
    }

    private class MeasureAndLayoutRunnable implements Runnable {
        @Override
        public void run() {
            if (isFluid()) {
                adManagerAdView.measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY)
                );
            } else {
                adManagerAdView.measure(width, height);
            }
            adManagerAdView.layout(left, top, left + width, top + height);
        }
    }
}
