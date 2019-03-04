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
	NSString* accessToken = [sessionData objectForKey:@"accessToken"];
	if(accessToken == nil || ![accessToken isKindOfClass:[NSString class]]) {
		return nil;
	}
	NSDate* expireDate = [sessionData objectForKey:@"expireDate"];
	if(expireDate == nil || ![expireDate isKindOfClass:[NSDate class]]) {
		return nil;
	}
	NSString* refreshToken = [sessionData objectForKey:@"refreshToken"];
	if(refreshToken != nil && ![refreshToken isKindOfClass:[NSString class]]) {
		refreshToken = nil;
	}
	NSArray* scopes = [sessionData objectForKey:@"scopes"];
	if(scopes != nil && ![scopes isKindOfClass:[NSArray class]]) {
		scopes = nil;
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
