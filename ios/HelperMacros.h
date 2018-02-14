
#import <Foundation/Foundation.h>

#define NSString_concat(...) \
	[@[ __VA_ARGS__ ] componentsJoinedByString:@""]



#pragma mark - Error Macros

#define NIL_PARAMETER_ERROR(parameter) \
	[RCTSpotify errorWithCode:RCTSpotifyErrorBadParameter description:[NSString stringWithFormat:@"%@ parameter cannot be null", parameter]]

#define NIL_PARAMETER_ERROR_OBJ(parameter) \
	[RCTSpotify objFromError:NIL_PARAMETER_ERROR(parameter)]

#define NIL_OPTION_ERROR(parameter, field) \
	[RCTSpotify errorWithCode:RCTSpotifyErrorMissingParameter description:[NSString stringWithFormat:@"\"%@\" field in %@ is required", field, parameter]]

#define NIL_OPTION_ERROR_OBJ(parameter, field) \
	[RCTSpotify objFromError:NIL_OPTION_ERROR(field, parameter)]

#define BAD_OPTION_TYPE_ERROR(parameter, field) \
	[RCTSpotify errorWithCode:RCTSpotifyErrorBadParameter description:[NSString stringWithFormat:@"invalid type for field \"%@\" in %@", field, parameter]]

#define BAD_OPTION_TYPE_ERROR_OBJ(parameter, field) \
	[RCTSpotify objFromError:BAD_OPTION_TYPE_ERROR(field, parameter)]



#pragma mark - Callback Macros

#define callbackAndReturnIfError(error, callback, ...) \
	if(error!=nil)\
	{\
		if(callback)\
		{\
			callback(__VA_ARGS__);\
		}\
		return;\
	}

#define reactCallbackAndReturnIfNil(parameter, callback, ...) \
	if(parameter == nil)\
	{\
		if(callback)\
		{\
			callback(@[ __VA_ARGS__ NIL_PARAMETER_ERROR_OBJ(@#parameter) ]);\
		}\
	}
