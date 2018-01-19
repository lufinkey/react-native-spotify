# Example Token Refresh Server

An example server capable of swapping and refreshing tokens provided by Spotify API.

## Usage

1. Install dependencies using: `npm install`
2. Assign your values to:

* `SPOTIFY_CLIENT_ID` - your spotify app id
* `SPOTIFY_CLIENT_SECRET` - your spotify app secret
* `SPOTIFY_CLIENT_CALLBACK` - your spotify app callback
* `ENCRYPTION_SECRET` - secret used to encrypt/decrypt tokens

3. Run server using: `npm start`
4. In your react-natvie app set `tokenSwapURL` to `http://your.server.com/swap` and `tokenRefreshURL` to `http://your.server.com/refresh`
