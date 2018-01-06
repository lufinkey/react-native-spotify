package com.lufinkey.react.spotify;

import com.android.volley.NetworkResponse;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.Volley;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class Utils
{
	public static ReactApplicationContext reactContext = null;

	private static RequestQueue requestQueue = null;

	public static String makeQueryString(ReadableMap params)
	{
		HashMap<String, Object> map = params.toHashMap();
		return makeQueryString(map);
	}

	public static String makeQueryString(HashMap<String,Object> params)
	{
		String query = "";
		boolean firstArg = true;
		for(String key : params.keySet())
		{
			if(firstArg)
			{
				firstArg = false;
			}
			else
			{
				query += "&";
			}
			String value = params.get(key).toString();
			try
			{
				query += URLEncoder.encode(key, "UTF-8")+"="+URLEncoder.encode(value, "UTF-8");
			}
			catch (UnsupportedEncodingException e)
			{
				e.printStackTrace();
				break;
			}
		}
		return query;
	}

	public static String getResponseString(NetworkResponse response)
	{
		if(response == null)
		{
			return null;
		}
		try
		{
			return new String(response.data, HttpHeaderParser.parseCharset(response.headers));
		}
		catch (UnsupportedEncodingException e)
		{
			return new String(response.data);
		}
	}

	public static void doHTTPRequest(String url, String method, final HashMap<String,String> headers, final byte[] body, final CompletionBlock<NetworkResponse> completion)
	{
		if(requestQueue == null)
		{
			requestQueue = Volley.newRequestQueue(reactContext.getCurrentActivity());
		}

		//make request
		HTTPRequest request = new HTTPRequest(method, url, headers, body) {
			@Override
			public void onError(VolleyError error)
			{
				completion.invoke(null, new SpotifyError(SpotifyError.Code.REQUEST_ERROR, "Could not communicate with server"));
			}

			@Override
			public void onResponse(NetworkResponse response)
			{
				completion.invoke(response, null);
			}
		};

		//do request
		requestQueue.add(request);
	}
}
