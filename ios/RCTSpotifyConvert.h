
#import <Foundation/Foundation.h>
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>
#import "RCTSpotifyError.h"

@interface RCTSpotifyConvert : NSObject

+(id)ID:(id)obj;
+(id)RCTSpotifyError:(RCTSpotifyError*)error;
+(id)NSError:(NSError*)error;
+(id)SPTPlaybackState:(SPTPlaybackState*)state;
+(id)SPTPlaybackTrack:(SPTPlaybackTrack*)track;
+(id)SPTPlaybackMetadata:(SPTPlaybackMetadata*)metadata;
+(id)SPTAuth:(SPTAuth*)auth;

@end
