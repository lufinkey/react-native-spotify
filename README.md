
# react-native-spotify

A react native module for the Spotify SDK

## Install

To add react-native-spotify to your project, cd into your project directory and run the following commands:
```bash
npm install --save https://github.com/lufinkey/react-native-spotify
react-native link react-native-spotify
```

Next, do the manual setup for each platform:

#### iOS
Manually add the Frameworks from `node_modules/react-native-spotify/ios/external/SpotifySDK` to Embedded Binaries in your project settings

Ensure you have enabled deep linking for react native in your Objective-C code. You can follow the instructions for that [here](https://facebook.github.io/react-native/docs/linking.html)

#### Android

Edit `android/build.grandle` and add `flatDir`

```gradle
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
			dirs project(':react-native-spotify').file('libs'), 'libs'
		}
	}
}
...
```

Edit `android/app/build.grandle` and add `packagingOptions`

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



## Usage

```javascript
import Spotify from 'react-native-spotify';
```

### Initialization/Authorization Methods

* **initialize**( *options*, ( *loggedIn*, *error*? ) => {} )

	Initializes the Spotify module and resumes a logged in session if there is one
	
	* *Parameters*
		* **options** - an object with options to pass to the Spotify Module
			* **clientID** - Your spotify application's ClientID that you registered with spotify [here](https://developer.spotify.com/my-applications)
			* **redirectURL** - The redirect URL to use when you've finished logging in. You NEED to set this URL for your application [here](https://developer.spotify.com/my-applications), otherwise the login screen will not close
			* **sessionUserDefaultsKey** - The preference key to use to store session data for this module
			* **scopes** - An array of scopes to use in the application. A list of scopes can be found [here](https://developer.spotify.com/web-api/using-scopes/)
			* **tokenSwapURL** - The URL to use to swap an authentication code for an access token
			* **tokenRefreshURL** - The URL to use to get a new access token from a refresh token
  		
		* **loggedIn** - A boolean indicating whether or not a session was automatically logged back in
  	
		* **error** - An error that occurred during initialization, or *null* if no error occurred




* **isInitialized**()

	* *Returns*
	
		* *true* if the Spotify module has been initialized
		* *false* if the Spotify module has not been initialized




* **login**( ( *loggedIn*, *error*? ) => {} )

	Opens a UI to log into Spotify
	
	* *Parameters*
	
		* **loggedIn** - A boolean indicating whether or not the client was logged in
	
		* **error** - An error that occurred during login, or *null* if no error occurred




* **isLoggedIn**()

	* *Returns*
		
		* *true* if the client is logged in
		* *false* if the client is not logged in




* **handleAuthURL**( *url* )

	Handles an authentication URL sent to the app through deep linking. You are *required* to use this function in order for login to work correctly.
	
	* *Parameters*
		
		* **url** - A URL that was sent to the app
		
	* *Returns*
		
		* *true* if the url passed to it was successfully handled as an authentication URL
		* *false* if the url was not an authentication URL

	* *Example Implementation*
		```javascript
		App.handleOpenURL = (event) => {
			if(Spotify.handleAuthURL(event.url))
			{
				return true;
			}
			return false;
		}
		Linking.addEventListener('url', App.handleOpenURL);
		```




### Player Methods

* **playURI**( *spotifyURI*, *startIndex*, *startPosition*, ( *error*? ) => {} )

	Play a Spotify URI.
	
	* *Parameters*
		
		* **spotifyURI** - The Spotify URI to play
		
		* **startIndex** - The index of an item that should be played first, e.g. 0 - for the very first track in the playlist or a single track
		
		* **startPosition** - starting position for playback in seconds
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **queueURI**( *spotifyURI*, ( *error*? ) => {} )

	Queue a Spotify URI.
	
	* *Parameters*
	
		* **spotifyURI** - The Spotify URI to queue
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **setPlaying**( *playing*, ( *error*? ) => {} )

	Set the “playing” status of the player.
	
	* *Parameters*
	
		* **playing** - pass *true* to resume playback, or *false* to pause it
		
		* **error** - An error object if an error occurred, or *null* if no error occurred

* **getPlaybackState**()

	Gives the player's current state.
	
	* *Returns*
	
		An object with the current player state. The state properties are:
		
		* **playing** - boolean indicating whether the player is playing
		* **repeating** - boolean indicating whether the player is repeating
		* **shuffling** - boolean indicating whether the player is shuffling
		* **activeDevice** - boolean indicating whether the current device is the one playing
		* **position** - the position of the player in the current track, in seconds




* **skipToNext**( ( *error*? ) => {} )

	Skips to the next track.
	
	* *Parameters*
	
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **skipToPrevious**( ( *error*? ) => {} )

	Skips to the previous track.
	
	* *Parameters*
	
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **setShuffling**( *shuffling*, ( *error*? ) => {] )

	Enables or disables shuffling on the player.
	
	* *Parameters*
	
		* **shuffling** - *true* to enable shuffle, *false* to disable it
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **setRepeating**( *repeating*, ( *error*? ) => {} )

	Enables or disables repeating on the player.
	
	* *Parameters*
	
		* **repeating** - *true* to enable repeat, *false* to disable it
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




### Metadata Methods

* **sendRequest**( *endpoint*, *method*, *params*, *isJSONBody*, ( *result*?, *error*? ) => {} )

	Sends a general request to the spotify api.
	
	* *Parameters*
	
		* **endpoint** - the api endpoint, without a leading slash, e.g. `v1/browse/new-releases`
		
		* **method** - the HTTP method to use
		
		* **params** - the request parameters
		
		* **isJSONBody** - whether or not to send the parameters as json in the body of the request
		
		* **result** - the request result object
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **search**( *query*, *types*, *options*?, ( *result*?, *error*? ) => {} )

	Sends a [search](https://developer.spotify.com/web-api/search-item/) request to spotify.
	
	* *Parameters*
	
		* **query** - The search query string. Same as the *q* parameter on the [search](https://developer.spotify.com/web-api/search-item/) endpoint
		
		* **types** - An array of item types to search for. Valid types are: `album`, `artist`, `playlist`, and `track`.
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The search result object. An example response can be seen [here](https://developer.spotify.com/web-api/search-item/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getAlbum**( *albumID*, *options*?, ( *result*?. *error*? ) => {} )

	Gets Spotify catalog information for a single album.
	
	* *Parameters*
	
		* **albumID** - The Spotify ID for the album
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-album/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getAlbums**( *albumIDs*, *options*?, ( *result*?, *error*? ) => {} )

	Gets Spotify catalog information for multiple albums identified by their Spotify IDs.
	
	* *Parameters*
	
		* **albumIDs** - An array of the Spotify IDs for the albums
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-several-albums/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getAlbumTracks**( *albumID*, *options*?, ( *result*?, *error*? ) => {} )

	Gets Spotify catalog information about an album’s tracks.

	* *Parameters*
	
		* **albumID** - The Spotify ID for the album
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-albums-tracks/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getArtist**( *artistID*, *options*?, ( *result*?, *error*? ) => {} )

	Gets Spotify catalog information for a single artist.
	
	* *Parameters*
	
		* **artistID** - The Spotify ID for the artist
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-artist/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getArtists**( *artistIDs*, *options*?, ( *result*?, *error*? ) => {} )

	Gets Spotify catalog information for several artists based on their Spotify IDs.
	
	* *Parameters*
	
		* **artistIDs** - An array of the Spotify IDs for the artists
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-several-artists/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getArtistAlbums**( *artistID*, *options*?, ( *result*?, *error*? ) => {} )

	Gets Spotify catalog information about an artist’s albums.
	
	* *Parameters*
	
		* **artistID** - The Spotify ID for the artist
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-artists-albums/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getArtistTopTracks**( *artistID*, *country*, *options*?, ( *result*?, *error*? ) => {} )

	Gets Spotify catalog information about an artist’s top tracks by country.
	
	* *Parameters*
	
		* **artistID** - The Spotify ID for the artist
		
		* **country** - The country: an [ISO 3166-1 alpha-2 country code](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2).
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-artists-top-tracks/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getArtistRelatedArtists**( *artistID*, *options*?, ( *result*?, *error*? ) => {} )

	Gets Spotify catalog information about artists similar to a given artist.
	
	* *Parameters*
	
		* **artistID** - The Spotify ID for the artist
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-related-artists/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getTrack**( *trackID*, *options*?, ( *result*?, *error*? ) => {} )

	Gets Spotify catalog information for a single track identified by its unique Spotify ID.
	
	* *Parameters*
	
		* **trackID** - The Spotify ID for the track
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-track/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getTracks**( *trackIDs*, *options*?, ( *result*?, *error*? ) => {} )

	Gets Spotify catalog information for multiple tracks based on their Spotify IDs.
	
	* *Parameters*
	
		* **trackIDs** - An array of the Spotify IDs for the tracks
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-several-tracks/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getTrackAudioAnalysis**( *trackID*, *options*?, ( *result*?, *error*? ) => {} )

	Gets a detailed audio analysis for a single track identified by its unique Spotify ID.
	
	* *Parameters*
	
		* **trackID** - The Spotify ID for the track
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-audio-analysis/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getTrackAudioFeatures**( *trackID*, *options*?, ( *result*?, *error*? ) => {} )

	Gets audio feature information for a single track identified by its unique Spotify ID.
	
	* *Parameters*
	
		* **trackID** - The Spotify ID for the track
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-audio-features/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




* **getTracksAudioFeatures**( *trackIDs*, *options*?, ( *result*?, *error*? ) => {} )

	Gets audio features for multiple tracks based on their Spotify IDs.
	
	* *Parameters*
	
		* **trackIDs** - An array of the Spotify IDs for the tracks
		
		* **options** - A map of other optional parameters to specify for the query
		
		* **result** - The request result object. An example response can be seen [here](https://developer.spotify.com/web-api/get-several-audio-features/#example)
		
		* **error** - An error object if an error occurred, or *null* if no error occurred




### Error Handling

Error objects are passed to callback functions to indicate something went wrong during the function call. You can use these error objects to determine what to show to the user. Right now, there are some uniformity issues between iOS and Android on the errors that get returned, but for now, use the **description** attribute to display a message to the user. I, or someone else will eventually make constants for **domain** and **code**.

#### The Error Object

* **domain** - A string indicating what part of the system the error belongs to
* **code** - An integer containing the actual error code of the error
* **description** - A string containing a user-readable description of the error
