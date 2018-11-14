package com.lufinkey.react.spotify;

import com.android.volley.AuthFailureError;
import com.android.volley.Network;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

class HTTPRequestErrorListener implements ErrorListener {
	private HTTPRequest request;

	public HTTPRequestErrorListener() {
		//
	}

	void setRequest(HTTPRequest request)
	{
		this.request = request;
	}

	@Override
	public void onErrorResponse(VolleyError volleyError) {
		if(volleyError.networkResponse != null) {
			request.onResponse(volleyError.networkResponse);
		}
		else {
			request.onError(volleyError);
		}
	}
}

public class HTTPRequest extends Request<NetworkResponse> {
	private Map<String, String> headers = null;
	private byte[] body = null;

	public HTTPRequest(String method, String url) {
		this(method, url, null, null);
	}

	public HTTPRequest(String method, String url, Map<String,String> headers, byte[] body) {
		this(method, url, headers, body, new HTTPRequestErrorListener());
	}

	private HTTPRequest(String method, String url, Map<String,String> headers, byte[] body, HTTPRequestErrorListener errorListener) {
		super(getMethodFromString(method), url, errorListener);
		this.headers = headers;
		this.body = body;
		errorListener.setRequest(this);
	}

	private static int getMethodFromString(String method) {
		if(method.equalsIgnoreCase("GET")) {
			return Request.Method.GET;
		}
		else if(method.equalsIgnoreCase("POST")) {
			return Request.Method.POST;
		}
		else if(method.equalsIgnoreCase("DELETE")) {
			return Request.Method.DELETE;
		}
		else if(method.equalsIgnoreCase("PUT")) {
			return Request.Method.PUT;
		}
		else if(method.equalsIgnoreCase("HEAD")) {
			return Request.Method.HEAD;
		}
		else if(method.equalsIgnoreCase("PATCH")) {
			return Request.Method.PATCH;
		}
		else if(method.equalsIgnoreCase("OPTIONS")) {
			return Request.Method.OPTIONS;
		}
		else if(method.equalsIgnoreCase("TRACE")) {
			return Request.Method.TRACE;
		}
		throw new IllegalArgumentException("Invalid HTTP method "+method);
	}

	@Override
	protected void deliverResponse(NetworkResponse response) {
		onResponse(response);
	}

	@Override
	protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
		return Response.success(response, HttpHeaderParser.parseCacheHeaders(response));
	}

	@Override
	public Map<String, String> getHeaders() throws AuthFailureError {
		HashMap<String, String> headers = null;
		if(this.headers == null) {
			headers = new HashMap<>();
		}
		else {
			headers = new HashMap<String, String>(this.headers);
		}
		for(String key : headers.keySet()) {
			if(key.equalsIgnoreCase("Content-Type")) {
				headers.remove(key);
			}
		}
		return headers;
	}

	@Override
	public String getBodyContentType() {
		String contentType = null;
		if(headers != null) {
			for (String key : headers.keySet()) {
				if (key.equalsIgnoreCase("Content-Type")) {
					contentType = headers.get(key);
					break;
				}
			}
		}
		if(contentType == null) {
			return super.getBodyContentType();
		}
		return contentType;
	}

	@Override
	public byte[] getBody() throws AuthFailureError {
		return body;
	}

	void onResponse(NetworkResponse response) {
		//Open for implementation
	}

	void onError(VolleyError error) {
		//Open for implementation
	}
}
