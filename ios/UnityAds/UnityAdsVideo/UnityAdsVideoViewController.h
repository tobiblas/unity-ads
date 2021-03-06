//
//  UnityAdsVideoViewController.h
//  UnityAds
//
//  Created by bluesun on 11/26/12.
//  Copyright (c) 2012 Unity Technologies. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <Foundation/Foundation.h>
#import "UnityAdsVideoPlayer.h"
#import "../UnityAdsCampaign/UnityAdsCampaign.h"

@protocol UnityAdsVideoControllerDelegate <NSObject>

@required
- (void)videoPlayerStartedPlaying;
- (void)videoPlayerPlaybackEnded:(BOOL)skipped;
- (void)videoPlayerEncounteredError;
- (void)videoPlayerReady;
@end

@interface UnityAdsVideoViewController : UIViewController <UnityAdsVideoPlayerDelegate, UIGestureRecognizerDelegate>
@property (nonatomic, weak) id<UnityAdsVideoControllerDelegate> delegate;
@property (nonatomic, assign) BOOL isPlaying;
@property (nonatomic, assign) BOOL isMuted;
- (void)playCampaign:(UnityAdsCampaign *)campaignToPlay;
- (void)forceStopVideoPlayer;
@end
