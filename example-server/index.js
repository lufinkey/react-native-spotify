// npm deps
const fetch = require('node-fetch');
const express = require('express');
const bodyParser = require('body-parser');
const {URLSearchParams} = require('url');

// Require the framework and instantiate it
const app = express();

// init spotify config
const spClientId = process.env.SPOTIFY_CLIENT_ID;
const spClientSecret = process.env.SPOTIFY_CLIENT_SECRET;
const spClientCallback = process.env.SPOTIFY_CLIENT_CALLBACK;
const authString = Buffer.from(`${spClientId}:${spClientSecret}`).toString('base64');
const authorizationHeader = `Basic ${authString}`;
const spotifyEndpoint = 'https://accounts.spotify.com/api/token';

// support form body
app.use(bodyParser.urlencoded({extended: true}));

/**
 * Swap endpoint
 * Uses an authentication code on body to request access and refresh tokens
 */
app.post('/swap', async (req, res) => {
  const data = new URLSearchParams();
  data.append('grant_type', 'authorization_code');
  data.append('redirect_uri', spClientCallback);
  data.append('code', req.body.code);

  const result = await fetch(spotifyEndpoint, {
    method: 'POST',
    headers: {
      Authorization: authorizationHeader,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: data,
  });
  const replyBody = await result.json();
  res.send(replyBody);
});

/**
 * Refresh endpoint
 * Uses the refresh token on request body to get a new access token
 */
app.post('/refresh', async (req, res) => {
  if (!req.body.refresh_token) {
    res.status(400).send({error: 'Refresh token is missing from body'});
    return;
  }

  const refreshToken = req.body.refresh_token;
  const data = new URLSearchParams();
  data.append('grant_type', 'refresh_token');
  data.append('refresh_token', refreshToken);

  const result = await fetch(spotifyEndpoint, {
    method: 'POST',
    headers: {
      Authorization: authorizationHeader,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: data,
  });
  const replyBody = await result.json();

  res.status(res.status).send(replyBody);
});

app.listen(3000, () => console.log('Example app listening on port 3000!'));
