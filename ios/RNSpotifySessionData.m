//
//  RNSpotifySessionData.m
//  RNSpotify
//
//  Created by Luis Finke on 3/4/19.
//  Copyright © 2019 Facebook. All rights reserved.
//

#import "RNSpotifySessionData.h"
#import "RNSpotifyConvert.h"
#import "RNSpotifyUtils.h"
#import "HelperMacros.h"

@implementation RNSpotifySessionData

-(BOOL)isValid {
	if(_accessToken == nil || _expireDate == nil || _expireDate.timeIntervalSince1970 < [NSDate date].timeIntervalSince1970) {
		return NO;
	}
	return YES;
}

-(BOOL)hasScope:(NSString*)scope {
	if(_scopes == nil) {
		return NO;
	}
	for(NSString* cmpScope in _scopes) {
		if([scope isEqualToString:cmpScope]) {
			return YES;
		}
	}
	return NO;
}

-(void)saveToUserDefaults:(NSUserDefaults*)userDefaults key:(NSString*)key {
	NSMutableDictionary* sessionData = [NSMutableDictionary dictionary];
	[RNSpotifyUtils setOrRemoveObject:_accessToken forKey:@"accessToken" in:sessionData];
	[RNSpotifyUtils setOrRemoveObject:_expireDate forKey:@"expireDate" in:sessionData];
	[RNSpotifyUtils setOrRemoveObject:_refreshToken forKey:@"refreshToken" in:sessionData];
	[RNSpotifyUtils setOrRemoveObject:_scopes forKey:@"scopes" in:sessionData];
	[userDefaults setObject:sessionData forKey:key];
}

+(RNSpotifySessionData*)sessionFromUserDefaults:(NSUserDefaults*)userDefaults key:(NSString*)key {
	NSDictionary* sessionData = [userDefaults objectForKey:key];
	if(sessionData == nil || ![sessionData isKindOfClass:[NSDictionary class]]) {
		return nil;
	}
	return [self sessionFromDictionary:sessionData error:nil];
}

+(RNSpotifySessionData*)sessionFromDictionary:(NSDictionary*)dict error:(RNSpotifyError**)error {
	// access token
	NSString* accessToken = [RNSpotifyUtils getObjectForKey:@"accessToken" in:dict];
	if(accessToken == nil) {
		if(error != nil) {
			*error = [RNSpotifyError nullParameterErrorForName:@"accessToken"];
		}
		return nil;
	}
	else if(![accessToken isKindOfClass:[NSString class]]) {
		if(error != nil) {
			*error = [RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadParameter message:@"accessToken must be a string"];
		}
		return nil;
	}
	// expire date
	NSDate* expireDate = [RNSpotifyUtils getObjectForKey:@"expireDate" in:dict];
	if(expireDate == nil) {
		NSNumber* expireTime = [RNSpotifyUtils getObjectForKey:@"expireTime" in:dict];
		if(expireTime == nil) {
			if(error != nil) {
				*error = [RNSpotifyError nullParameterErrorForName:@"expireTime"];
			}
			return nil;
		}
		if(![expireTime isKindOfClass:[NSNumber class]]) {
			if(error != nil) {
				*error = [RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadParameter message:@"expireTime must be a number"];
			}
			return nil;
		}
		expireDate = [NSDate dateWithTimeIntervalSince1970:(expireTime.doubleValue/1000.0)];
	}
	if(![expireDate isKindOfClass:[NSDate class]]) {
		if(error != nil) {
			*error = [RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadParameter message:@"expireDate must be a date"];
		}
		return nil;
	}
	// refresh token
	NSString* refreshToken = [RNSpotifyUtils getObjectForKey:@"refreshToken" in:dict];
	if(refreshToken != nil && ![refreshToken isKindOfClass:[NSString class]]) {
		if(error != nil) {
			*error = [RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadParameter message:@"refreshToken must be a string"];
		}
		return nil;
	}
	// scopes
	NSArray* scopes = [RNSpotifyUtils getObjectForKey:@"scopes" in:dict];
	if(scopes != nil && ![scopes isKindOfClass:[NSArray class]]) {
		if(error != nil) {
			*error = [RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadParameter message:@"scopes must be an array"];
		}
		return nil;
	}
	RNSpotifySessionData* session = [[RNSpotifySessionData alloc] init];
	session.accessToken = accessToken;
	session.expireDate = expireDate;
	session.refreshToken = refreshToken;
	session.scopes = scopes;
	return session;
}

+(NSDate*)expireDateFromSeconds:(NSInteger)seconds {
	NSTimeInterval time = NSDate.date.timeIntervalSince1970 + (NSTimeInterval)seconds;
	return [NSDate dateWithTimeIntervalSince1970:time];
}

@end
