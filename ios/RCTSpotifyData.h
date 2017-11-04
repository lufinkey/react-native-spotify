//
//  RCTSpotifyData.h
//  RCTSpotifyData
//
//  Created by Luis Finke on 11/4/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>

@interface RCTSpotifyData : NSObject <SPTAudioStreamingDelegate, SPTAudioStreamingPlaybackDelegate>

-(id)initWithAuth:(SPTAuth*)auth error:(NSError**)error;

@property (strong, readonly) NSString* instanceID;
@property (strong, readonly) SPTAuth* auth;
@property (strong, readonly) SPTAudioStreamingController* player;

@end
