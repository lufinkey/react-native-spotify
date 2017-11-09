
#import <Foundation/Foundation.h>
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>

@interface RCTSpotifyConvert : NSObject

+(id)ID:(id)obj;
+(id)NSError:(NSError*)error;
+(id)SPTPlaybackState:(SPTPlaybackState*)state;
+(id)SPTPlaybackTrack:(SPTPlaybackTrack*)track;
+(id)SPTPlaybackMetadata:(SPTPlaybackMetadata*)metadata;

@end
