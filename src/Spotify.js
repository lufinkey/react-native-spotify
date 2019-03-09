
import { NativeModules } from 'react-native';
import RNEvents from 'react-native-events';


const Spotify = NativeModules.RNSpotify;
RNEvents.register(Spotify);
RNEvents.conform(Spotify);


const login = Spotify.login;
Spotify.login = (options={}) => {
	options = {...options};
	return login(options);
}

const authenticate = Spotify.authenticate;
Spotify.authenticate = (options={}) => {
	options = {...options};
	return authenticate(options);
}



const sendRequest = Spotify.sendRequest;
Spotify.sendRequest = async (endpoint, method, params, isJSONBody) => {
	try {
		return await sendRequest(endpoint, method, params, isJSONBody);
	}
	catch(error) {
		if(error.code === 'HTTP429') {
			const match = /[r|R]etry [a|A]fter ([0-9]+) [s|S]econds/.exec(error.message)[0];
			if(match) {
				error.retryAfter = Number.parseInt(match);
			}
		}
		throw error;
	}
}



Spotify.search = (query, types, options) => {
	if(!(types instanceof Array)) {
		return Promise.reject(new Error("types must be an array"));
	}
	const body = {...options};
	body['q'] = query;
	body['type'] = types.join(',');
	const results = Spotify.sendRequest('v1/search', 'GET', body, false);
	for(const type in ['tracks','albums','artists','playlists']) {
		const typeResults = results[type];
		if(typeResults) {
			// heck you, Spotify
			if(typeResults.next === 'null') {
				typeResults.next = null;
			}
			if(typeResults.prev === 'null') {
				typeResults.prev = null;
			}
		}
	}
	return results;
}



Spotify.getAlbum = (albumID, options) => {
	if(albumID == null) {
		return Promise.reject(new Error("albumID cannot be null"));
	}
	return Spotify.sendRequest('v1/albums/'+albumID, 'GET', options, false);
}

Spotify.getAlbums = (albumIDs, options) => {
	if(!(albumIDs instanceof Array)) {
		return Promise.reject(new Error("albumIDs must be an array"));
	}
	const body = {...options};
	body['ids'] = albumIDs.join(',');
	return Spotify.sendRequest('v1/albums', 'GET', body, false);
}

Spotify.getAlbumTracks = (albumID, options) => {
	if(albumID == null) {
		return Promise.reject(new Error("albumID cannot be null"));
	}
	const body = {...options};
	const results = Spotify.sendRequest('v1/albums/'+albumID+'/tracks', 'GET', body, false);
	// fix Spotify's mistakes
	if(results.next === 'null') {
		results.next = null;
	}
	if(results.prev === 'null') {
		results.prev = null;
	}
	return results;
}



Spotify.getArtist = (artistID, options) => {
	if(artistID == null) {
		return Promise.reject(new Error("artistID cannot be null"));
	}
	const body = {...options};
	return Spotify.sendRequest('v1/artists/'+artistID, 'GET', body, false);
}

Spotify.getArtists = (artistIDs, options) => {
	if(!(artistIDs instanceof Array)) {
		return Promise.reject(new Error("artistIDs must be an array"));
	}
	const body = {...options};
	body['ids'] = artistIDs.join(',');
	return Spotify.sendRequest('v1/artists', 'GET', body, false);
}

Spotify.getArtistAlbums = (artistID, options) => {
	if(artistID == null) {
		return Promise.reject(new Error("artistID cannot be null"));
	}
	const body = {...options};
	const results = Spotify.sendRequest('v1/artists/'+artistID+'/albums', 'GET', body, false);
	// fix Spotify's bullshit
	if(results.next === 'null') {
		results.next = null;
	}
	if(results.prev === 'null') {
		results.prev = null;
	}
	return results;
}

Spotify.getArtistTopTracks = (artistID, country, options) => {
	if(artistID == null) {
		return Promise.reject(new Error("artistID cannot be null"));
	}
	else if(country == null) {
		return Promise.reject(new Error("country cannot be null"));
	}
	const body = {...options};
	body['country'] = country;
	return Spotify.sendRequest('v1/artists/'+artistID+'/top-tracks', 'GET', body, false);
}

Spotify.getArtistRelatedArtists = (artistID, options) => {
	if(artistID == null) {
		return Promise.reject(new Error("artistID cannot be null"));
	}
	const body = {...options};
	return Spotify.sendRequest('v1/artists/'+artistID+'/related-artists', 'GET', body, false);
}



Spotify.getTrack = (trackID, options) => {
	if(trackID == null) {
		return Promise.reject(new Error("trackID cannot be null"));
	}
	const body = {...options};
	return Spotify.sendRequest('v1/tracks/'+trackID, 'GET', body, false);
}

Spotify.getTracks = (trackIDs, options) => {
	if(!(trackIDs instanceof Array)) {
		return Promise.reject(new Error("trackIDs must be an array"));
	}
	const body = {...options};
	body['ids'] = trackIDs.join(',');
	return Spotify.sendRequest('v1/tracks', 'GET', body, false);
}

Spotify.getTrackAudioAnalysis = (trackID, options) => {
	if(trackID == null) {
		return Promise.reject(new Error("trackID cannot be null"));
	}
	const body = {...options};
	return Spotify.sendRequest('v1/audio-analysis/'+trackID, 'GET', body, false);
}

Spotify.getTrackAudioFeatures = (trackID, options) => {
	if(trackID == null) {
		return Promise.reject(new Error("trackID cannot be null"));
	}
	const body = {...options};
	return Spotify.sendRequest('v1/audio-features/'+trackID, 'GET', body, false);
}

Spotify.getTracksAudioFeatures = (trackIDs, options) => {
	if(!(trackIDs instanceof Array)) {
		return Promise.reject(new Error("trackIDs must be an array"));
	}
	const body = {...options};
	body['ids'] = trackIDs.join(',');
	return Spotify.sendRequest('v1/audio-features', 'GET', body, false);
}



Spotify.getPlaylist = (playlistID, options) => {
	if(playlistID == null) {
		return Promise.reject(new Error("playlistID cannot be null"));
	}
	const body = {...options};
	return Spotify.sendRequest('v1/playlists/'+playlistID, 'GET', body, false);
}

Spotify.getPlaylistTracks = (playlistID, options) => {
	if(playlistID == null) {
		return Promise.reject(new Error("playlistID cannot be null"));
	}
	const body = {...options};
	return Spotify.sendRequest('v1/playlists/'+playlistID+'/tracks', 'GET', body, false);
}



Spotify.getMe = () => {
	return Spotify.sendRequest('v1/me', 'GET', null, false);
}

Spotify.getMyTracks = (options) => {
	const body = {...options};
	return Spotify.sendRequest('v1/me/tracks', 'GET', body, false);
}

Spotify.getMyPlaylists = (options) => {
	const body = {...options};
	return Spotify.sendRequest('v1/me/playlists', 'GET', body, false);
}

Spotify.getMyTop = (type, options) => {
	const body = {...options};
	return Spotify.sendRequest(`v1/me/top/${type}`, 'GET', body, false);
}



export default Spotify;
