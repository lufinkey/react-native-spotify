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

@interface RCTSpotifyData : NSObject

-(id)initWithAuth:(SPTAuth*)auth;

@property (strong) SPTAuth* auth;

@end
