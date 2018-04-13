
import { NativeModules } from 'react-native';
import RNEvents from 'react-native-events';


const Spotify = NativeModules.RNSpotify;
RNEvents.register(Spotify);
RNEvents.conform(Spotify);

const sendRequest = Spotify.sendRequest;



Spotify.getMe = () => {
	return sendRequest('v1/me', 'GET', null, false);
};



Spotify.search = (query, types, options) => {
	if(!(types instanceof Array))
	{
		return Promise.reject(new Error("types must be an array"));
	}

	var body = Object.assign({}, options);
	body['q'] = query;
	body['type'] = types.join(',');
	
	return sendRequest('v1/search', 'GET', body, false);
}



Spotify.getAlbum = (albumID, options) => {
	if(albumID == null)
	{
		return Promise.reject(new Error("albumID cannot be null"));
	}
	
	return sendRequest('v1/albums/'+albumID, 'GET', options, false);
}

Spotify.getAlbums = (albumIDs, options) => {
	if(!(albumIDs instanceof Array))
	{
		return Promise.reject(new Error("albumIDs must be an array"));
	}

	var body = Object.assign({}, options);
	body['ids'] = albumIDs.join(',');

	return sendRequest('v1/albums', 'GET', body, false);
}

Spotify.getAlbumTracks = (albumID, options) => {
	if(albumID == null)
	{
		return Promise.reject(new Error("albumID cannot be null"));
	}

	return sendRequest('v1/albums/'+albumID+'/tracks', 'GET', options, false);
}



Spotify.getArtist = (artistID, options) => {
	if(artistID == null)
	{
		return Promise.reject(new Error("artistID cannot be null"));
	}

	return sendRequest('v1/artists/'+artistID, 'GET', options, false);
}

Spotify.getArtists = (artistIDs, options) => {
	if(!(artistIDs instanceof Array))
	{
		return Promise.reject(new Error("artistIDs must be an array"));
	}

	var body = Object.assign({}, options);
	body['ids'] = artistIDs.join(',');

	return sendRequest('v1/artists', 'GET', body, false);
}

Spotify.getArtistAlbums = (artistID, options) => {
	if(artistID == null)
	{
		return Promise.reject(new Error("artistID cannot be null"));
	}

	return sendRequest('v1/artists/'+artistID+'/albums', 'GET', options, false);
}

Spotify.getArtistTopTracks = (artistID, country, options) => {
	if(artistID == null)
	{
		return Promise.reject(new Error("artistID cannot be null"));
	}
	else if(country == null)
	{
		return Promise.reject(new Error("country cannot be null"));
	}

	var body = Object.assign({}, options);
	body['country'] = country;

	return sendRequest('v1/artists/'+artistID+'/top-tracks', 'GET', body, false);
}

Spotify.getArtistRelatedArtists = (artistID, options) => {
	if(artistID == null)
	{
		return Promise.reject(new Error("artistID cannot be null"));
	}
	
	return sendRequest('v1/artists/'+artistID+'/related-artists', 'GET', options, false);
}



Spotify.getTrack = (trackID, options) => {
	if(trackID == null)
	{
		return Promise.reject(new Error("trackID cannot be null"));
	}

	return sendRequest('v1/tracks/'+trackID, 'GET', options, false);
}

Spotify.getTracks = (trackIDs, options) => {
	if(!(trackIDs instanceof Array))
	{
		return Promise.reject(new Error("trackIDs must be an array"));
	}

	var body = Object.assign({}, options);
	body['ids'] = trackIDs.join(',');

	return sendRequest('v1/tracks', 'GET', body, false);
}

Spotify.getTrackAudioAnalysis = (trackID, options) => {
	if(trackID == null)
	{
		return Promise.reject(new Error("trackID cannot be null"));
	}

	return sendRequest('v1/audio-analysis/'+trackID, 'GET', options, false);
}

Spotify.getTrackAudioFeatures = (trackID, options) => {
	if(trackID == null)
	{
		return Promise.reject(new Error("trackID cannot be null"));
	}

	return sendRequest('v1/audio-features/'+trackID, 'GET', options, false);
}

Spotify.getTracksAudioFeatures = (trackIDs, options) => {
	if(!(trackIDs instanceof Array))
	{
		return Promise.reject(new Error("trackIDs must be an array"));
	}

	var body = Object.assign({}, options);
	body['ids'] = trackIDs.join(',');

	return sendRequest('v1/audio-features', 'GET', body, false);
}



export default Spotify;
