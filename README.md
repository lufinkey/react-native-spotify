
# Spotify for React Native

A react native module for the Spotify SDK

## Contributing / Opening Issues

If you would like to make a pull request, fork from and merge into the *dev* branch (or a feature branch) only.

Please do not open issues about getting the module to work unless you have tried using both the example app and the example token swap server. Please make sure you have tried running on the latest react-native version before submitting a bug.

## Install

To add the Spotify SDK to your project, cd into your project directory and run the following commands:
```bash
npm install --save rn-spotify-sdk
react-native link
```

Next, do the manual setup for each platform:

#### iOS
Manually add the frameworks from `node_modules/rn-spotify-sdk/ios/external/SpotifySDK` to *Linked Frameworks and Libraries* in your project settings. Then add `../node_modules/rn-spotify-sdk/ios/external/SpotifySDK` to *Framework Search Paths* in your project settings.

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
}
...
```

If you have issues linking the module, please check that gradle is updated to the latest version and that your project is synced.



## Usage

```javascript
import Spotify from 'rn-spotify-sdk';
```

### Types

- **Auth**

	Contains information about authentication data
	
	- *Properties*
	
		- **accessToken** - A token used to communicate with the Spotify API
		- **refreshToken** - An encrypted token used to get a new access token when it expires. This should be encrypted by your token swap service, as per OAuth standards.
		- **expireTime** - The time that the access token expires, in milliseconds from January 1, 1970 00:00:00 UTC



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
		- **error** - an *Error*




### Events

This module uses [react-native-events](https://www.npmjs.com/package/react-native-events), so it has all of the same methods as an [EventEmitter](https://nodejs.org/api/events.html) object. All of the events except for **'login'**, **'logout'**, **'disconnect'** (on Android), and **'reconnect'** (on Android) come from Spotify's native SDK and are simply forwarded to javascript. If one of these events occurs at a weird time or has strange data, please open an issue on Spotify's [ios-sdk](https://github.com/spotify/ios-sdk) or [android-sdk](https://github.com/spotify/android-sdk) repo, and not here.

- **'login'**

	Emitted when the module has successfully logged in.

- **'logout'**

	Emitted when the module is logged out.

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

- **audioDeliveryDone**

	- `event` {PlaybackEvent}
	
	Emitted when the library reaches the end of a playback context and has no more audio to deliver.

- **trackDelivered**

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
			- **clientID** - (*Required*) Your spotify application's ClientID that you registered with spotify [here](https://developer.spotify.com/my-applications)
			- **redirectURL** - The redirect URL to use when you've finished logging in. You NEED to set this URL for your application [here](https://developer.spotify.com/my-applications), otherwise the login screen will not close
			- **sessionUserDefaultsKey** - The preference key to use to store session data for this module
			- **scopes** - An array of scopes to use in the application. A list of scopes can be found [here](https://developer.spotify.com/web-api/using-scopes/)
			- **tokenSwapURL** - The URL to use to swap an authentication code for an access token (see [Token swap and refresh](#token-swap-and-refresh) section for more info)
			- **tokenRefreshURL** - The URL to use to get a new access token from a refresh token
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




- **login**()

	Opens a UI to log into Spotify.
	
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




- **getAuth**()

	Gives information about authentication data.
	
	- *Returns*
	
		- An *Auth* object, or *null* if not logged in




- **getAuthAsync**()

	Gives information about authentication data, but returns a *Promise* that resolves to the result.
	
	- *Returns*
	
		- A *Promise* that resolves to an *Auth* object, or *null* if not logged in




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

	Queue a Spotify URI. **WARNING: This function has proven to be very [inconsistent and buggy](https://github.com/spotify/ios-sdk/issues/717).**
	
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

	Sends a general request to the spotify api. A list of potential endpoints can be found [here](https://developer.spotify.com/web-api/endpoint-reference/).
	
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
