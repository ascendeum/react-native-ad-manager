#import "RNAdManagerBannerView.h"

#import <GoogleMobileAds/GoogleMobileAds.h>
#import <DTBiOSSDK/DTBiOSSDK.h>
#import <React/RCTUtils.h>

#import <React/RCTLog.h>

#include "RCTConvert+GADAdSize.h"
#import "RNAdManagerUtils.h"

@interface RNAdManagerBannerView () <GADBannerViewDelegate, GADAdSizeDelegate, GADAppEventDelegate>

@property (nonatomic, strong) GAMBannerView *bannerView;

@end

@implementation RNAdManagerBannerView

- (void)dealloc
{
    _number = 0;
    _bannerView.delegate = nil;
    _bannerView.adSizeDelegate = nil;
    _bannerView.appEventDelegate = nil;
    _bannerView.rootViewController = nil;
}

-(void)setApsSlotId:(NSString *)apsSlotId {
    _apsSlotId = apsSlotId;
}

- (void)setAdUnitID:(NSString *)adUnitID
{
  _adUnitID = adUnitID;
//  [self createViewIfCan];
}

- (void)setAdsRefresh:(NSString *)adsRefresh
{
  _adsRefresh = adsRefresh;
}

- (void)setAdSize:(NSString *)adSize
{
  _adSize = adSize;
//  [self createViewIfCan];
}

- (void)setValidAdSizes:(NSArray *)adSizes
{
    __block NSMutableArray *validAdSizes = [[NSMutableArray alloc] initWithCapacity:adSizes.count];
    [adSizes enumerateObjectsUsingBlock:^(id jsonValue, NSUInteger idx, __unused BOOL *stop) {
        GADAdSize adSize = [RCTConvert GADAdSize:jsonValue];
        if (GADAdSizeEqualToSize(adSize, GADAdSizeInvalid)) {
            RCTLogWarn(@"Invalid adSize %@", jsonValue);
        } else if (![validAdSizes containsObject:NSValueFromGADAdSize(adSize)]) {
            [validAdSizes addObject:NSValueFromGADAdSize(adSize)];
        }
    }];

    _validAdSizes = validAdSizes;
//    [self createViewIfCan];
}

- (void)setTargeting:(NSDictionary *)targeting
{
  _targeting = targeting;
//  [self createViewIfCan];
}

- (void)setCorrelator:(NSString *)correlator
{
  _correlator = correlator;
}

// Initialise BannerAdView as soon as all the props are set
- (void)createViewIfCan
{
    if (!_adUnitID || !_adSize/* || !_validAdSizes || !_targeting*/) {
        return;
    }

    if (_bannerView) {
        [_bannerView removeFromSuperview];
    }

    GADAdSize adSize = [RCTConvert GADAdSize:_adSize];
    GAMBannerView *bannerView;
    if (!GADAdSizeEqualToSize(adSize, GADAdSizeInvalid)) {
//        self.bannerView.adSize = adSize;
        bannerView = [[GAMBannerView alloc] initWithAdSize:adSize];
    } else {
        bannerView = [[GAMBannerView alloc] initWithAdSize:GADAdSizeBanner];
    }

    bannerView.delegate = self;
    bannerView.adSizeDelegate = self;
    bannerView.appEventDelegate = self;
    bannerView.rootViewController = RCTPresentedViewController();
    bannerView.translatesAutoresizingMaskIntoConstraints = YES;

    GADMobileAds.sharedInstance.requestConfiguration.testDeviceIdentifiers = _testDevices;

    // added here
    bannerView.adUnitID = _adUnitID;
    bannerView.validAdSizes = _validAdSizes;
    [self addSubview:bannerView];
    _bannerView = bannerView;
    if (!_apsSlotId ) {
        GAMRequest *request = [GAMRequest request];
        [_bannerView loadRequest:request];
        return;
    }
    [self makeAdNetworkRequest:adSize];

//     GAMRequest *request = [GAMRequest request];

//     GADExtras *extras = [[GADExtras alloc] init];
//     if (_correlator == nil) {
//         _correlator = getCorrelator(_adUnitID);
//     }
//     extras.additionalParameters = [[NSDictionary alloc] initWithObjectsAndKeys:
//                                    _correlator, @"correlator",
//                                    nil];
//     [request registerAdNetworkExtras:extras];

//     if (_targeting != nil) {
//         NSDictionary *customTargeting = [_targeting objectForKey:@"customTargeting"];
//         if (customTargeting != nil) {
//             request.customTargeting = customTargeting;
//         }
//         NSArray *categoryExclusions = [_targeting objectForKey:@"categoryExclusions"];
//         if (categoryExclusions != nil) {
//             request.categoryExclusions = categoryExclusions;
//         }
//         NSArray *keywords = [_targeting objectForKey:@"keywords"];
//         if (keywords != nil) {
//             request.keywords = keywords;
//         }
//         NSString *contentURL = [_targeting objectForKey:@"contentURL"];
//         if (contentURL != nil) {
//             request.contentURL = contentURL;
//         }
//         NSString *publisherProvidedID = [_targeting objectForKey:@"publisherProvidedID"];
//         if (publisherProvidedID != nil) {
//             request.publisherProvidedID = publisherProvidedID;
//         }
// //        NSDictionary *location = [_targeting objectForKey:@"location"];
// //        if (location != nil) {
// //            CGFloat latitude = [[location objectForKey:@"latitude"] doubleValue];
// //            CGFloat longitude = [[location objectForKey:@"longitude"] doubleValue];
// //            CGFloat accuracy = [[location objectForKey:@"accuracy"] doubleValue];
// //            [request setLocationWithLatitude:latitude longitude:longitude accuracy:accuracy];
// //        }
    // }

    // bannerView.adUnitID = _adUnitID;

    // bannerView.validAdSizes = _validAdSizes;

    // [bannerView loadRequest:request];

    // [self addSubview:bannerView];

    // _bannerView = bannerView;
}

- (void)loadBanner {
    [self createViewIfCan];
}

- (void)makeAdNetworkRequest: (GADAdSize) adSize {
    DTBAdSize *size = [[DTBAdSize alloc] initBannerAdSizeWithWidth:adSize.size.width height:adSize.size.height andSlotUUID:_apsSlotId];
    DTBAdLoader *adLoader = [DTBAdLoader new];
    [adLoader setSizes:size, nil];
    if([_adsRefresh isEqualToString:@"1"]){
        [adLoader setAutoRefresh:30];
    }
    [adLoader loadAd:self];
}

- (void)makeRequestWithNetworkResponses: (DTBAdResponse *)adResponse {
    GAMRequest *request = [GAMRequest request];
    GADExtras *extras = [[GADExtras alloc] init];
    if (_correlator == nil) {
        _correlator = getCorrelator(_adUnitID);
    }
    extras.additionalParameters = [[NSDictionary alloc] initWithObjectsAndKeys:
                                    _correlator, @"correlator",
                                    nil];
    [request registerAdNetworkExtras:extras];

    NSMutableDictionary *dict = [[NSMutableDictionary alloc] init];
    if(adResponse){
        [dict addEntriesFromDictionary:adResponse.customTargeting];

    if (_targeting != nil) {
        NSDictionary *customTargeting = [_targeting objectForKey:@"customTargeting"];
        if (customTargeting != nil) {
            [dict addEntriesFromDictionary:customTargeting];
        }
        NSArray *categoryExclusions = [_targeting objectForKey:@"categoryExclusions"];
        if (categoryExclusions != nil) {
            request.categoryExclusions = categoryExclusions;
        }
        NSArray *keywords = [_targeting objectForKey:@"keywords"];
        if (keywords != nil) {
            request.keywords = keywords;
        }
        NSString *contentURL = [_targeting objectForKey:@"contentURL"];
        if (contentURL != nil) {
            request.contentURL = contentURL;
        }
        NSString *publisherProvidedID = [_targeting objectForKey:@"publisherProvidedID"];
        if (publisherProvidedID != nil) {
            request.publisherProvidedID = publisherProvidedID;
        }
//        NSDictionary *location = [_targeting objectForKey:@"location"];
//        if (location != nil) {
//            CGFloat latitude = [[location objectForKey:@"latitude"] doubleValue];
//            CGFloat longitude = [[location objectForKey:@"longitude"] doubleValue];
//            CGFloat accuracy = [[location objectForKey:@"accuracy"] doubleValue];
//            [request setLocationWithLatitude:latitude longitude:longitude accuracy:accuracy];
//        }
    }
    NSString* refString = [NSString stringWithFormat:@"%i", _number];
    NSDictionary *refDict = @{@"refreshIteration":refString};
    [dict addEntriesFromDictionary:refDict];

    request.customTargeting = dict;
    [_bannerView loadRequest:request];
 }
}

#pragma mark - <DTBAdCallback>

- (void)onFailure: (DTBAdError)error {
    NSLog(@"Failed to load APS bid. ERROR: %u", error);
    [self makeRequestWithNetworkResponses:nil];
}

- (void)onSuccess: (DTBAdResponse *)adResponse {
    NSLog(@"Successfully loaded APS bid");
    [self makeRequestWithNetworkResponses:adResponse];
}

# pragma mark GADBannerViewDelegate

/// Tells the delegate an ad request loaded an ad.
- (void)bannerViewDidReceiveAd:(nonnull GADBannerView *)bannerView
{
    if (self.onSizeChange) {
        self.onSizeChange(@{
                            @"type": @"banner",
                            @"width": @(bannerView.frame.size.width),
                            @"height": @(bannerView.frame.size.height) });
    }
    if (self.onAdLoaded) {
//        self.onAdLoaded(@{
//            @"type": @"banner",
//            @"gadSize": @{@"width": @(bannerView.frame.size.width),
//                          @"height": @(bannerView.frame.size.height)},
//        });
        self.onAdLoaded(@{
            @"type": @"banner",
            @"gadSize": @{@"adSize": NSStringFromGADAdSize(bannerView.adSize),
                          @"width": @(bannerView.frame.size.width),
                          @"height": @(bannerView.frame.size.height)},
            @"isFluid": GADAdSizeIsFluid(bannerView.adSize) ? @"true" : @"false",
            @"measurements": @{@"adWidth": @(bannerView.adSize.size.width),
                               @"adHeight": @(bannerView.adSize.size.height),
                               @"width": @(bannerView.frame.size.width),
                               @"height": @(bannerView.frame.size.height),
                               @"left": @(bannerView.frame.origin.x),
                               @"top": @(bannerView.frame.origin.y)},
        });
    }
    _number = _number+1;
}

/// Tells the delegate an ad request failed.
- (void)bannerView:(nonnull GADBannerView *)bannerView
    didFailToReceiveAdWithError:(nonnull NSError *)error
{
    if (self.onAdFailedToLoad) {
        self.onAdFailedToLoad(@{ @"error": @{ @"message": [error localizedDescription] } });
    }
    _bannerView.delegate = nil;
    _bannerView.adSizeDelegate = nil;
    _bannerView.appEventDelegate = nil;
    _bannerView.rootViewController = nil;
    _bannerView = nil;
}

- (void)bannerViewDidRecordImpression:(nonnull GADBannerView *)bannerView
{
    if (self.onAdRecordImpression) {
        self.onAdRecordImpression(@{});
    }
}

- (void)bannerViewDidRecordClick:(nonnull GADBannerView *)bannerView
{
    if (self.onAdRecordClick) {
        self.onAdRecordClick(@{});
    }
}

/// Tells the delegate that a full screen view will be presented in response
/// to the user clicking on an ad.
- (void)bannerViewWillPresentScreen:(nonnull GADBannerView *)bannerView
{
    if (self.onAdOpened) {
        self.onAdOpened(@{});
    }
}

/// Tells the delegate that the full screen view will be dismissed.
- (void)bannerViewWillDismissScreen:(nonnull GADBannerView *)bannerView
{
    if (self.onAdClosed) {
        self.onAdClosed(@{});
    }
}

# pragma mark GADAdSizeDelegate

- (void)adView:(GADBannerView *)bannerView willChangeAdSizeTo:(GADAdSize)size
{
    CGSize adSize = CGSizeFromGADAdSize(size);
    self.onSizeChange(@{
                        @"type": @"banner",
                        @"width": @(adSize.width),
                        @"height": @(adSize.height) });
}

# pragma mark GADAppEventDelegate

- (void)adView:(GADBannerView *)banner didReceiveAppEvent:(NSString *)name withInfo:(NSString *)info
{
    if (self.onAppEvent) {
        self.onAppEvent(@{ @"name": name, @"info": info });
    }
}

@end
