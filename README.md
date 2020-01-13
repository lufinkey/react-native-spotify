
# Spotify for React Native

A react native module for the Spotify SDK

**NOTE:** This repo is using the deprecated Spotify streaming SDKs. I'm only doing bug fixes on this repo and I don't really have a whole lot of time to update it. [react-native-spotify-remote](https://github.com/cjam/react-native-spotify-remote) is being worked on by someone else to use the newer "remote" SDK.

## Install

To add the Spotify SDK to your project, cd into your project directory and run the following commands:
```bash
npm install --save rn-spotify-sdk
react-native link react-native-events
react-native link rn-spotify-sdk
```

Next, do the manual setup for each platform:

#### iOS
Manually add `SpotifyMetadata.framework` and `SpotifyAudioPlayback.framework` from `node_modules/rn-spotify-sdk/ios/external/SpotifySDK` to *Linked Frameworks and Libraries* in your project settings. Then add `../node_modules/rn-spotify-sdk/ios/external/SpotifySDK` to *Framework Search Paths* in your project settings.

#### Android

Edit `android/build.gradle` and add `flatDir`

```
...
allprojects {
	repositories {
		mavenLocal()
		jcenter()
		maven {
			// All of React Native (JS, Obj-C sources, Android binaries) is installed from npm
			url "$rootDir/../node_modules/react-native/android"
		}
		flatDir {
			dirs project(':rn-spotify-sdk').file('libs'), 'libs'
		}
	}
}
...
```

Edit `android/app/build.gradle` and add `packagingOptions`

```
...
buildTypes {
    release {
        minifyEnabled enableProguardInReleaseBuilds
        proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
    }
}
packagingOptions {
    pickFirst 'lib/armeabi-v7a/libgnustl_shared.so'
    pickFirst 'lib/x86/libgnustl_shared.so'
    exclude 'lib/arm64-v8a/libgnustl_shared.so'
    exclude 'lib/x86_64/libgnustl_shared.so'
}
...
```

In some cases, the two `exclude` lines cause issues when compiling and can be omitted. I need to look further into what causes this.

On Android, `react-native link` has a bug where it imports `RNSpotifyPackage` using the wrong bundle. You may have to make the following change to `MainApplication.java`:
```java
...
import com.spotify.sdk.android.authentication.RNSpotifyPackage; // remove this line
import com.lufinkey.react.spotify.RNSpotifyPackage; // replace with this line
...
```

If you have issues linking the module, please check that gradle is updated to the latest version and that your project is synced. Please reference the [example app](example) to ensure you've implemented things correctly before opening any issues.



## Usage

```javascript
import Spotify from 'rn-spotify-sdk';
```

### Types

- **Session**

	Contains information about a session
	
	- *Properties*
	
		- **accessToken** - A token used to communicate with the Spotify API
		- **expireTime** - The time that the access token expires, in milliseconds from January 1, 1970 00:00:00 UTC
		- **refreshToken** - An encrypted token used to get a new access token when they expire. This should be encrypted by your token swap service, as per OAuth standards.
		- **scopes** - An array of scopes that the session has access to. A list of scopes can be found [here](https://developer.spotify.com/web-api/using-scopes/).



- **PlaybackState**

	Contains information about the current state of the player
	
	- *Properties*
	
		- **playing** - boolean indicating whether the player is playing
		- **repeating** - boolean indicating whether the player is repeating
		- **shuffling** - boolean indicating whether the player is shuffling
		- **activeDevice** - boolean indicating whether the current device is the one playing
		- **position** - the position of the player in the current track, in seconds




- **PlaybackTrack**

	Contains information about a track in the playback queue
	
	- *Properties*
	
		- **name** - The title of the track
		- **uri** - The uri of the track
		- **contextName** - The name of the playlist or album that the track is being played from
		- **contextUri** - The uri of the playlist or album that the track is being played from
		- **artistName** - The name of the track's artist
		- **artistUri** - The uri of the track's artist
		- **albumName** - The name of the album that the track belongs to
		- **albumUri** - The uri of the album that the track belongs to
		- **albumCoverArtURL** - A URL for the album art image
		- **duration** - The length of the track in seconds
		- **indexInContext** - The track index in the playlist or album that the track is being played from




- **PlaybackMetadata**

	Contains information about the previous, current, and next tracks in the player
	
	- *Properties*
	
		- **prevTrack** - A *PlaybackTrack* with information about the previous track
		- **currentTrack** - A *PlaybackTrack* with information about the current track
		- **nextTrack** - A *PlaybackTrack* with information about the next track




- **PlaybackEvent**

	Contains information about a playback event and the state of the player.
	
	- *Properties*
	
		- **state** - the player's current *PlaybackState*
		- **metadata** - the player's current *PlaybackMetadata*




### Events

This module uses [react-native-events](https://www.npmjs.com/package/react-native-events), so it has all of the same methods as an [EventEmitter](https://nodejs.org/api/events.html) object. All of the events except for **'disconnect'** / **'reconnect'** (on Android) and **'login'** / **'logout'** come from Spotify's native SDK and are simply forwarded to javascript. If one of these events occurs at a weird time or has strange data, please open an issue on Spotify's [ios-streaming-sdk](https://github.com/spotify/ios-streaming-sdk) or [android-streaming-sdk](https://github.com/spotify/android-streaming-sdk) repo, and not here.

- **'login'**

	- `session` {Session}
	
	Emitted when the module has successfully logged in.

- **'logout'**

	Emitted when the module is logged out.

- **'sessionRenewed'**

	- `session` {Session}
	
	Emitted when the session has been renewed.

- **'play'**

	- `event` {PlaybackEvent}
	
	Emitted when playback has started or has resumed.

- **'pause'**

	- `event` {PlaybackEvent}
	
	Emitted when playback is paused.

- **'trackChange'**

	- `event` {PlaybackEvent}
	
	Emitted when playback of a new/different track starts.

- **'metadataChange'**

	- `event` {PlaybackEvent}
	
	Emitted when metadata has changed. This event occurs when playback starts or changes to a different context, when a track switch occurs, etc. This is an informational event that does not require action, but should be used to keep the UI display updated with the latest metadata information.

- **'contextChange'**

	- `event` {PlaybackEvent}
	
	Emitted when playback starts or changes to a different context than was playing before, such as a change in album or playlist.

- **'shuffleStatusChange'**

	- `event` {PlaybackEvent}
	
	Emitted when "shuffle" is switched on or off.

- **'repeatStatusChange'**

	- `event` {PlaybackEvent}
	
	Emitted when "repeat" is switched on or off.

- **'active'**

	- `event` {PlaybackEvent}
	
	Emitted when this device has become the active playback device. This event occurs when the users moves playback to this device using Spotify Connect.

- **'inactive'**

	- `event` {PlaybackEvent}
	
	Emitted when this device is no longer the active playback device. This event occurs when the user moves playback to a different device using Spotify Connect.

- **'permissionLost'**

	- `event` {PlaybackEvent}
	
	Emitted when this device has temporarily lost permission to stream audio from Spotify. A user can only stream audio on one of her devices at any given time. If playback is started on a different device, this event may occur.

- **'audioFlush'**

	- `event` {PlaybackEvent}
	
	Emitted when the application should flush its audio buffers (you don't need to deal with this since that's handled by the native code). For example, this event occurs when seeking to a different position within a track.

- **'audioDeliveryDone'**

	- `event` {PlaybackEvent}
	
	Emitted when the library reaches the end of a playback context and has no more audio to deliver.

- **'trackDelivered'**

	- `event` {PlaybackEvent}
	
	Emitted when the application accepted all samples from the current track. This is an informative event that indicates that all samples from the current track have been delivered to and accepted by the application. The track has not yet finished playing the last audio sample, but no more audio will be delivered for this track. For nearly all intents and purposes, the track has finished playing.

- **'disconnect'**

	Emitted when the player loses network connectivity.

- **'reconnect'**

	Emitted when the player regains network connectivity.

- **'temporaryPlayerError'**

	Emitted when service has been interrupted, usually by lack of network access. However, it can also occur if there is a problem with Spotify's backend services, or also when the user switches from WiFi to 3G. These errors can occur in many non-critical situations, and thus it is not necessary to show toasts or alert dialogs when receiving this event, or else you will unnecessarily annoy or panic the user. However, it can be useful to know about these events if operations are consistently failing, in which case showing a toast or alert may be justified.

- **'playerMessage'**

	- `message` {String}
	
	Called when the player has recieved a message for the end user from the Spotify service.




### Initialization/Authorization Methods

- **initialize**( *options* )

	Initializes the Spotify module and resumes a logged in session if there is one. This must be the first method you call when using this module.
	
	- *Parameters*
	
		- **options** - an object with options to pass to the Spotify Module
			- **clientID** - (*Required*) Your spotify application's client ID that you registered with spotify [here](https://developer.spotify.com/dashboard/applications)
			- **redirectURL** - (*Required*) The redirect URL to use when you've finished logging in. You NEED to set this URL for your application [here](https://developer.spotify.com/dashboard/applications), otherwise the login screen will not close
			- **sessionUserDefaultsKey** - The preference key to use in order to store session data for this module. Set this to a string of your choice when you initialize in order to persist user information between app uses.
			- **scopes** - An array of scopes that define permissions for the Spotify API. A list of scopes can be found [here](https://developer.spotify.com/documentation/general/guides/scopes)
			- **tokenSwapURL** - The URL to use to swap an authentication code for an access token (see [Token swap and refresh](#token-swap-and-refresh) section for more info)
			- **tokenRefreshURL** - The URL to use to get a new access token from a refresh token
			- **tokenRefreshEarliness** - The number of seconds to set a token refresh timer before the access token expires. Default is `300`
			- **ios** - iOS specific options
				- **audioSessionCategory** - The name of the audio session category to use for playing music in the app. Default is `'AVAudioSessionCategoryPlayback'`
			- **android** - Android specific options
				- **loginLoadingText** - The "Loading" text that will show on the login popup
	
	- *Returns*
	
		- A *Promise* that resolves to a boolean when the module finishes initialization, indicating whether or not a session was automatically logged back in




- **isInitialized**()

	Checks if the Spotify module has been initialized yet.

	- *Returns*
	
		- *true* if the Spotify module has been initialized
		- *false* if the Spotify module has not been initialized




- **isInitializedAsync**()

	Checks if the Spotify module has been initialized yet, but returns a *Promise* that resolves to the result.
	
	- *Returns*
	
		- A *Promise* that resolves to a boolean, indicating whether or not the Spotify module has been initialized




- **login**( *options*? )

	Opens a UI to log into Spotify.
	
	- *Parameters*
	
		- **options**
			- **showDialog** - Whether or not to force the user to approve the app again if they’ve already done so.
			- **clientID** - Your spotify application's client ID that you registered with spotify [here](https://developer.spotify.com/dashboard/applications). Falls back to value given in **initialize**.
			- **redirectURL** - The redirect URL to use when you've finished logging in. You NEED to set this URL for your application [here](https://developer.spotify.com/dashboard/applications), otherwise the login screen will not close. Falls back to value given in **initialize**.
			- **scopes** - An array of scopes that define permissions for the Spotify API. A list of scopes can be found [here](https://developer.spotify.com/documentation/general/guides/scopes). Falls back to value given in **initialize**.
			- **tokenSwapURL** - The URL to use to swap an authentication code for an access token (see [Token swap and refresh](#token-swap-and-refresh) section for more info). Falls back to value given in **initialize**.
	
	- *Returns*
	
		- A *Promise* that resolves to a boolean, indicating whether or not the user was logged in




- **isLoggedIn**()

	Checks if the client is logged in.

	- *Returns*
		
		- *true* if the client is logged in
		- *false* if the client is not logged in




- **isLoggedInAsync**()

	Checks if the client is logged in, but returns a *Promise* that resolves to the result.
	
	- *Returns*
	
		- A *Promise* that resolves to a boolean, indicating whether or not a user is currently logged in




- **logout**()

	Logs out of Spotify.
	
	- *Returns*
	
		- A *Promise* that resolves when the logout completes




- **getSession**()

	Gives information about the current session.
	
	- *Returns*
	
		- An *Session* object, or *null* if not logged in




- **getSessionAsync**()

	Gives information about the current session, but returns a *Promise* that resolves to the result.
	
	- *Returns*
	
		- A *Promise* that resolves to an *Session* object, or *null* if not logged in




- **renewSession**()

	Renews a logged in session. If no token refresh URL was given to **initialize** or if the session does not have a refresh token, this function returns without error
	
	- *Returns*
	
		- A *Promise* that resolves when the session renewal attempt finishes




- **authenticate**( *options*? )

	Opens a UI to perform the auth flow for Spotify, but returns a session instead of logging in.
	
	- *Parameters*
	
		- **options**
			- **showDialog** - Whether or not to force the user to approve the app again if they’ve already done so.
			- **clientID** - Your spotify application's client ID that you registered with spotify [here](https://developer.spotify.com/dashboard/applications). Falls back to value given in **initialize**.
			- **redirectURL** - The redirect URL to use when you've finished logging in. You NEED to set this URL for your application [here](https://developer.spotify.com/dashboard/applications), otherwise the login screen will not close. Falls back to value given in **initialize**.
			- **scopes** - An array of scopes that define permissions for the Spotify API. A list of scopes can be found [here](https://developer.spotify.com/documentation/general/guides/scopes). Falls back to value given in **initialize**.
			- **tokenSwapURL** - The URL to use to swap an authentication code for an access token (see [Token swap and refresh](#token-swap-and-refresh) section for more info). Falls back to value given in **initialize**.
	
	- *Returns*
	
		- A *Promise* that resolves to an *Session* object, or *null* if login is cancelled




- **loginWithSession**( *options* )

	Logs into the app with a given session
	
	- *Parameters*
	
		- **options**
			- **accessToken** (*Required*) - The token to use to communicate with the Spotify API.
			- **expireTime** (*Required*) - The time that the access token expires, in milliseconds from January 1, 1970 00:00:00 UTC
			- **refreshToken** - An encrypted token used to get a new access token when it expires.
			- **scopes** - An array of scopes that the session has access to. A list of scopes can be found [here](https://developer.spotify.com/documentation/general/guides/scopes).
			- **clientID** - Your spotify application's client ID that you registered with spotify [here](https://developer.spotify.com/dashboard/applications). Falls back to value given in **initialize**.
			- **tokenRefreshURL** - The URL to use to get a new access token from a refresh token (see [Token swap and refresh](#token-swap-and-refresh) section for more info). Falls back to value given in **initialize**.
	
	- *Returns*
	
		- A *Promise* that resolves when the login finishes




### Playback Methods

- **playURI**( *spotifyURI*, *startIndex*, *startPosition* )

	Play a Spotify URI.
	
	- *Parameters*
		
		- **spotifyURI** - The Spotify URI to play
		- **startIndex** - The index of an item that should be played first, e.g. 0 - for the very first track in the playlist or a single track
		- **startPosition** - starting position for playback in seconds
	
	- *Returns*
	
		- A *Promise* that resolves or rejects when the operation is complete




- **queueURI**( *spotifyURI* )

	Queue a Spotify URI. **WARNING: This function has proven to be very [inconsistent and buggy](https://github.com/spotify/ios-streaming-sdk/issues/717).**
	
	- *Parameters*
	
		- **spotifyURI** - The Spotify URI to queue
	
	- *Returns*
	
		- A *Promise* that resolves or rejects when the operation is complete




- **setPlaying**( *playing* )

	Set the “playing” status of the player.
	
	- *Parameters*
	
		- **playing** - *true* to resume playback, or *false* to pause it
	
	- *Returns*
	
		- A *Promise* that resolves or rejects when the operation is complete

- **getPlaybackState**()

	Gives the player's current state.
	
	- *Returns*
	
		- A *PlaybackState* object, or *null* if the player has not yet initialized




- **getPlaybackStateAsync**()

	Gives the player's current state, but returns a *Promise* that resolves to the result.
	
	- *Returns*
	
		- A *Promise* that resolves to a *PlaybackState* object or *null* if the player has not yet initialized



- **getPlaybackMetadata**()

	Gives information about the previous, current, and next track in the player.
	
	- *Returns*
	
		- A *PlaybackMetadata* object, or *null* if the player has yet initialized



- **getPlaybackMetadataAsync**()

	Gives information about the previous, current, and next track in the player, but returns a *Promise* that resolves to the result.
	
	- *Returns*
	
		- A *Promise* that resolves to a *PlaybackMetadata* object or *null* if the player has not yet initialized




- **skipToNext**()

	Skips to the next track.
	
	- *Returns*
	
		- A *Promise* that resolves or rejects when the operation is complete




- **skipToPrevious**()

	Skips to the previous track.
	
	- *Returns*
	
		- A *Promise* that resolves or rejects when the operation is complete




- **seek**( *position* )

	Seeks to a position within the current track
	
	- *Parameters*
	
		- **position** - The position in seconds to seek to
	
	- *Returns*
	
		- A *Promise* that resolves or rejects when the operation is complete




- **setShuffling**( *shuffling* )

	Enables or disables shuffling on the player.
	
	- *Parameters*
	
		- **shuffling** - *true* to enable shuffle, *false* to disable it
	
	- *Returns*
	
		- A *Promise* that resolves or rejects when the operation is complete




- **setRepeating**( *repeating* )

	Enables or disables repeating on the player.
	
	- *Parameters*
	
		- **repeating** - *true* to enable repeat, *false* to disable it
	
	- *Returns*
	
		- A *Promise* that resolves or rejects when the operation is complete




### Metadata Methods

- **sendRequest**( *endpoint*, *method*, *params*, *isJSONBody* )

	Sends a general request to the spotify api. A list of potential endpoints can be found [here](https://developer.spotify.com/documentation/web-api/reference).
	
	- *Parameters*
	
		- **endpoint** - the api endpoint, without a leading slash, e.g. `'v1/browse/new-releases'`
		- **method** - the HTTP method to use
		- **params** - the request parameters
		- **isJSONBody** - whether or not to send the parameters as json in the body of the request
	
	- *Returns*
	
		- A *Promise* that resolves to the result of the API request




- **getMe**()

	Retrieves information about the logged in Spotify user.
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-current-users-profile/#example)




- **search**( *query*, *types*, *options*? )

	Sends a [search](https://developer.spotify.com/web-api/search-item/) request to spotify.
	
	- *Parameters*
	
		- **query** - The search query string. Same as the *q* parameter on the [search](https://developer.spotify.com/web-api/search-item/) endpoint
		- **types** - An array of item types to search for. Valid types are: `'album'`, `'artist'`, `'playlist'`, and `'track'`.
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the search result object. An example response can be seen [here](https://developer.spotify.com/web-api/search-item/#example)




- **getAlbum**( *albumID*, *options*? )

	Gets Spotify catalog information for a single album.
	
	- *Parameters*
	
		- **albumID** - The Spotify ID for the album
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-album/#example)




- **getAlbums**( *albumIDs*, *options*? )

	Gets Spotify catalog information for multiple albums identified by their Spotify IDs.
	
	- *Parameters*
	
		- **albumIDs** - An array of the Spotify IDs for the albums
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-several-albums/#example)




- **getAlbumTracks**( *albumID*, *options*? )

	Gets Spotify catalog information about an album’s tracks.

	- *Parameters*
	
		- **albumID** - The Spotify ID for the album
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-albums-tracks/#example)




- **getArtist**( *artistID*, *options*? )

	Gets Spotify catalog information for a single artist.
	
	- *Parameters*
	
		- **artistID** - The Spotify ID for the artist
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-artist/#example)




- **getArtists**( *artistIDs*, *options*? )

	Gets Spotify catalog information for several artists based on their Spotify IDs.
	
	- *Parameters*
	
		- **artistIDs** - An array of the Spotify IDs for the artists
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-several-artists/#example)




- **getArtistAlbums**( *artistID*, *options*? )

	Gets Spotify catalog information about an artist’s albums.
	
	- *Parameters*
	
		- **artistID** - The Spotify ID for the artist
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-artists-albums/#example)




- **getArtistTopTracks**( *artistID*, *country*, *options*? )

	Gets Spotify catalog information about an artist’s top tracks by country.
	
	- *Parameters*
	
		- **artistID** - The Spotify ID for the artist
		- **country** - The country: an [ISO 3166-1 alpha-2 country code](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2).
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-artists-top-tracks/#example)




- **getArtistRelatedArtists**( *artistID*, *options*? )

	Gets Spotify catalog information about artists similar to a given artist.
	
	- *Parameters*
	
		- **artistID** - The Spotify ID for the artist
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-related-artists/#example)




- **getTrack**( *trackID*, *options*? )

	Gets Spotify catalog information for a single track identified by its unique Spotify ID.
	
	- *Parameters*
	
		- **trackID** - The Spotify ID for the track
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-track/#example)




- **getTracks**( *trackIDs*, *options*? )

	Gets Spotify catalog information for multiple tracks based on their Spotify IDs.
	
	- *Parameters*
	
		- **trackIDs** - An array of the Spotify IDs for the tracks
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-several-tracks/#example)




- **getTrackAudioAnalysis**( *trackID*, *options*? )

	Gets a detailed audio analysis for a single track identified by its unique Spotify ID.
	
	- *Parameters*
	
		- **trackID** - The Spotify ID for the track
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-audio-analysis/#example)




- **getTrackAudioFeatures**( *trackID*, *options*? )

	Gets audio feature information for a single track identified by its unique Spotify ID.
	
	- *Parameters*
	
		- **trackID** - The Spotify ID for the track
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-audio-features/#example)




- **getTracksAudioFeatures**( *trackIDs*, *options*? )

	Gets audio features for multiple tracks based on their Spotify IDs.
	
	- *Parameters*
	
		- **trackIDs** - An array of the Spotify IDs for the tracks
		- **options** - A map of other optional parameters to specify for the query
	
	- *Returns*
	
		- A *Promise* that resolves to the request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-several-audio-features/#example)




### Token swap and refresh

In order for your app to stay logged into Spotify for more than an hour, you must set up your own server with endpoints for token swap and refresh, and specify your `tokenSwapURL` and `tokenRefreshURL` parameters in the `Spotify.initialize` method

The `tokenSwapURL` parameter is used to swap the authentication code provided by the Spotify login process for an access token and a refresh token.

The `tokenRefreshURL` parameter is used to retrieve new access tokens for the user using the refresh token received from the `tokenSwapURL`.

Both URLs are queried using POST with a Content-Type of `application/x-www-form-urlencoded`.

You can find an example server implementation [here](https://github.com/lufinkey/react-native-spotify/tree/master/example-server).

Refresh tokens are part of [OAuth standard](https://tools.ietf.org/html/rfc6749#section-1.5). If you are not familiar with them, [Understanding Refresh Tokens](https://auth0.com/learn/refresh-tokens/) can give you a basic idea on how they work.

## Additional notes

This module only works for Spotify Premium users.
