#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_REMAP_MODULE(VeriffSdk, VeriffSdkReactWrapper, NSObject)

RCT_EXTERN_METHOD(launchVeriff:(NSDictionary *)configuration resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)

@end
