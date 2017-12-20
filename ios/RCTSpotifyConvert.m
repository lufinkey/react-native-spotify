
#import "RCTSpotifyConvert.h"

@implementation RCTSpotifyConvert

+(id)ID:(id)obj
{
	if(obj == nil)
	{
		return [NSNull null];
	}
	return obj;
}

+(id)NSError:(NSError*)error
{
	if(error==nil)
	{
		return [NSNull null];
	}
	NSDictionary* fields = error.userInfo[@"jsFields"];
	NSMutableDictionary* obj = nil;
	if(fields!=nil)
	{
		obj = fields.mutableCopy;
	}
	else
	{
		obj = [NSMutableDictionary dictionary];
	}
	obj[@"domain"] = error.domain;
	obj[@"code"] = @(error.code);
	obj[@"message"] = error.localizedDescription;
	return obj;
}

+(id)SPTPlaybackState:(SPTPlaybackState*)state
{
	if(state == nil)
	{
		return [NSNull null];
	}
	return @{
		@"playing":[NSNumber numberWithBool:state.isPlaying],
		@"repeating":[NSNumber numberWithBool:state.isRepeating],
		@"shuffling":[NSNumber numberWithBool:state.isShuffling],
		@"activeDevice":[NSNumber numberWithBool:state.isActiveDevice],
		@"position":@(state.position)
	};
}

+(id)SPTPlaybackTrack:(SPTPlaybackTrack*)track
{
	if(track == nil)
	{
		return [NSNull null];
	}
	return @{
		@"name":[[self class] ID:track.name],
		@"uri":[[self class] ID:track.uri],
		@"contextName":[[self class] ID:track.playbackSourceName],
		@"contextUri":[[self class] ID:track.playbackSourceUri],
		@"artistName":[[self class] ID:track.artistName],
		@"artistUri":[[self class] ID:track.artistUri],
		@"albumName":[[self class] ID:track.albumName],
		@"albumUri":[[self class] ID:track.albumUri],
		@"albumCoverArtURL":[[self class] ID:track.albumCoverArtURL],
		@"duration":@(track.duration),
		@"indexInContext":@(track.indexInContext)
	};
}

+(id)SPTPlaybackMetadata:(SPTPlaybackMetadata*)metadata
{
	if(metadata == nil)
	{
		return [NSNull null];
	}
	return @{
		@"prevTrack":[[self class] SPTPlaybackTrack:metadata.prevTrack],
		@"currentTrack":[[self class] SPTPlaybackTrack:metadata.currentTrack],
		@"nextTrack":[[self class] SPTPlaybackTrack:metadata.nextTrack]
	};
}

@end
