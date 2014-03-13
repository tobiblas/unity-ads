//
//  UnityAdsCacheManagerTests.m
//  UnityAds
//
//  Created by Sergey D on 3/11/14.
//  Copyright (c) 2014 Unity Technologies. All rights reserved.
//

#import <SenTestingKit/SenTestingKit.h>
#import "UnityAdsCampaign.h"
#import "UnityAdsCacheManager.h"
#import "UnityAdsSBJsonParser.h"
#import "NSObject+UnityAdsSBJson.h"
#import "UnityAdsCampaignManager.h"
#import "UnityAdsConstants.h"

typedef enum {
  CachingResultUndefined = 0,
  CachingResultFinished,
  CachingResultFinishedAll,
  CachingResultFailed,
  CachingResultCancelled,
} CachingResult;

@interface UnityAdsCacheManagerTests : SenTestCase <UnityAdsCacheManagerDelegate> {
@private
  CachingResult _cachingResult;
  UnityAdsCacheManager * _cacheManager;
}

@end

extern void __gcov_flush();

@implementation UnityAdsCacheManagerTests

- (void)threadBlocked:(BOOL (^)())isThreadBlocked {
	@autoreleasepool {
		NSPort *port = [[NSPort alloc] init];
		[port scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
		
		while(isThreadBlocked()) {
			@autoreleasepool {
				[[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode
                                 beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.5]];
			}
		}
	}
}

- (NSString *)_cachePath {
	NSArray *paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
	return [[paths objectAtIndex:0] stringByAppendingPathComponent:@"unityads"];
}

- (void)setUp
{
  [super setUp];
  _cacheManager = [UnityAdsCacheManager new];
  _cacheManager.delegate = self;
  [[NSFileManager defaultManager] removeItemAtPath:[self _cachePath] error:nil];
  // Put setup code here. This method is called before the invocation of each test method in the class.
}

- (void)tearDown {
  __gcov_flush();
  [super tearDown];
  _cacheManager = nil;
}

- (void)testCacheNilCampaign {
  _cachingResult = CachingResultUndefined;
  UnityAdsCampaign * campaignToCache = nil;
  [_cacheManager cache:ResourceTypeTrailerVideo forCampaign:campaignToCache];
  
  [self threadBlocked:^BOOL{
    @synchronized(self) {
      return _cachingResult != CachingResultFinishedAll;
    }
  }];
  
  STAssertTrue(_cachingResult == CachingResultFailed,
               @"caching should fail instantly in same thread when caching nil campaign");
}

- (void)testCacheEmptyCampaign {
  _cachingResult = CachingResultUndefined;
  UnityAdsCampaign * campaignToCache = [UnityAdsCampaign new];
  [_cacheManager cache:ResourceTypeTrailerVideo forCampaign:campaignToCache];
  
  [self threadBlocked:^BOOL{
    @synchronized(self) {
      return _cachingResult != CachingResultFinishedAll;
    }
  }];
  
  STAssertTrue(_cachingResult == CachingResultFailed,
               @"caching should fail instantly in same thread when caching empty campaign");
}

- (void)testCachePartiallyFilledCampaign {
  _cachingResult = CachingResultUndefined;
  UnityAdsCampaign * campaignToCache = [UnityAdsCampaign new];
  campaignToCache.id = @"tmp";
  [_cacheManager cache:ResourceTypeTrailerVideo forCampaign:campaignToCache];
  
  [self threadBlocked:^BOOL{
    @synchronized(self) {
      return _cachingResult != CachingResultFinishedAll;
    }
  }];
  
  STAssertTrue(_cachingResult == CachingResultFailed,
               @"caching should fail instantly in same thread when caching partially empty campaign");
  
  _cachingResult = CachingResultUndefined;
  campaignToCache.id = @"tmp";
  campaignToCache.isValidCampaign = NO;
  [_cacheManager cache:ResourceTypeTrailerVideo forCampaign:campaignToCache];
  
  [self threadBlocked:^BOOL{
    @synchronized(self) {
      return _cachingResult != CachingResultFinishedAll;
    }
  }];
  
  STAssertTrue(_cachingResult == CachingResultFailed,
               @"caching should fail instantly in same thread when caching partially empty campaign");
  
  _cachingResult = CachingResultUndefined;
  campaignToCache.id = @"tmp";
  campaignToCache.isValidCampaign = YES;
  [_cacheManager cache:ResourceTypeTrailerVideo forCampaign:campaignToCache];
  
  
  [self threadBlocked:^BOOL{
    @synchronized(self) {
      return _cachingResult != CachingResultFinishedAll;
    }
  }];
  
  STAssertTrue(_cachingResult == CachingResultFailed,
               @"caching should fail instantly in same thread when caching partially empty campaign");
}

- (void)testCacheCampaignFilledWithWrongValues {
  _cachingResult = CachingResultUndefined;
  UnityAdsCampaign * campaignToCache = [UnityAdsCampaign new];
  campaignToCache.id = @"tmp";
  campaignToCache.isValidCampaign = YES;
  campaignToCache.trailerDownloadableURL = [NSURL URLWithString:@"tmp"];
  [_cacheManager cache:ResourceTypeTrailerVideo forCampaign:campaignToCache];
  
  [self threadBlocked:^BOOL{
    @synchronized(self) {
      return _cachingResult != CachingResultFinishedAll;
    }
  }];
  
  STAssertTrue(_cachingResult == CachingResultFailed,
               @"caching should fail campaign filled with wrong values");
}

- (void)testCacheSingleValidCampaign {
  _cachingResult = CachingResultUndefined;
  NSError * error = nil;
  NSStringEncoding encoding = NSStringEncodingConversionAllowLossy;
  NSString * pathToResource = [[NSBundle bundleForClass:[self class]] pathForResource:@"jsonData.txt" ofType:nil];
  NSString * jsonString = [[NSString alloc] initWithContentsOfFile:pathToResource
                                                      usedEncoding:&encoding
                                                             error:&error];
  NSDictionary * jsonDataDictionary = [jsonString JSONValue];
  NSDictionary *jsonDictionary = [jsonDataDictionary objectForKey:kUnityAdsJsonDataRootKey];
  NSArray  * campaignsDataArray = [jsonDictionary objectForKey:kUnityAdsCampaignsKey];
  NSArray * campaigns = [[UnityAdsCampaignManager sharedInstance] performSelector:@selector(deserializeCampaigns:) withObject:campaignsDataArray];
  STAssertTrue(jsonString != nil, @"empty json string");
  UnityAdsCampaign * campaignToCache = campaigns[0];
  STAssertTrue(campaignToCache != nil, @"campaign is nil");
  [_cacheManager cache:ResourceTypeTrailerVideo forCampaign:campaignToCache];
  
  [self threadBlocked:^BOOL{
    @synchronized(self) {
      return _cachingResult != CachingResultFinishedAll;
    }
  }];
  
  STAssertTrue(_cachingResult == CachingResultFinishedAll,
               @"caching should be ok when caching valid campaigns");
}

- (void)testCacheAllCampaigns {
  _cachingResult = CachingResultUndefined;
  NSError * error = nil;
  NSStringEncoding encoding = NSStringEncodingConversionAllowLossy;
  NSString * pathToResource = [[NSBundle bundleForClass:[self class]] pathForResource:@"jsonData.txt" ofType:nil];
  NSString * jsonString = [[NSString alloc] initWithContentsOfFile:pathToResource
                                                      usedEncoding:&encoding
                                                             error:&error];
  NSDictionary * jsonDataDictionary = [jsonString JSONValue];
  NSDictionary *jsonDictionary = [jsonDataDictionary objectForKey:kUnityAdsJsonDataRootKey];
  NSArray  * campaignsDataArray = [jsonDictionary objectForKey:kUnityAdsCampaignsKey];
  NSArray * campaigns = [[UnityAdsCampaignManager sharedInstance] performSelector:@selector(deserializeCampaigns:) withObject:campaignsDataArray];
  STAssertTrue(jsonString != nil, @"empty json string");
  [campaigns  enumerateObjectsUsingBlock:^(UnityAdsCampaign *campaign, NSUInteger idx, BOOL *stop) {
    [_cacheManager cache:ResourceTypeTrailerVideo forCampaign:campaign];
  }];
  [self threadBlocked:^BOOL{
    @synchronized(self) {
      return _cachingResult != CachingResultFinishedAll;
    }
  }];
  
  STAssertTrue(_cachingResult == CachingResultFinishedAll,
               @"caching should be ok when caching valid campaigns");
}

- (void)testCancelAllOperatons {
  _cachingResult = CachingResultUndefined;
  NSError * error = nil;
  NSStringEncoding encoding = NSStringEncodingConversionAllowLossy;
  NSString * pathToResource = [[NSBundle bundleForClass:[self class]] pathForResource:@"jsonData.txt" ofType:nil];
  NSString * jsonString = [[NSString alloc] initWithContentsOfFile:pathToResource
                                                      usedEncoding:&encoding
                                                             error:&error];
  NSDictionary * jsonDataDictionary = [jsonString JSONValue];
  NSDictionary *jsonDictionary = [jsonDataDictionary objectForKey:kUnityAdsJsonDataRootKey];
  NSArray  * campaignsDataArray = [jsonDictionary objectForKey:kUnityAdsCampaignsKey];
  NSArray * campaigns = [[UnityAdsCampaignManager sharedInstance] performSelector:@selector(deserializeCampaigns:) withObject:campaignsDataArray];
  STAssertTrue(jsonString != nil, @"empty json string");
  [campaigns  enumerateObjectsUsingBlock:^(UnityAdsCampaign *campaign, NSUInteger idx, BOOL *stop) {
    [_cacheManager cache:ResourceTypeTrailerVideo forCampaign:campaign];
  }];
  sleep(4);
  [_cacheManager cancelAllDownloads];
  [self threadBlocked:^BOOL{
    @synchronized(self) {
      return _cachingResult != CachingResultFinishedAll;
    }
  }];
  
  STAssertTrue(_cachingResult == CachingResultFinishedAll,
               @"caching should be ok when caching valid campaigns");
}

#pragma mark - UnityAdsCacheManagerDelegate

- (void)finishedCaching:(ResourceType)resourceType forCampaign:(UnityAdsCampaign *)campaign {
  @synchronized(self) {
    _cachingResult = CachingResultFinished;
  }
}

- (void)failedCaching:(ResourceType)resourceType forCampaign:(UnityAdsCampaign *)campaign {
  @synchronized(self) {
    _cachingResult = CachingResultFailed;
  }
}

- (void)cancelledCaching:(ResourceType)resourceType forCampaign:(UnityAdsCampaign *)campaign {
  @synchronized(self) {
    _cachingResult = CachingResultCancelled;
  }
}

- (void)cacheQueueEmpty {
  @synchronized(self) {
    _cachingResult = CachingResultFinishedAll;
  }
}

@end
