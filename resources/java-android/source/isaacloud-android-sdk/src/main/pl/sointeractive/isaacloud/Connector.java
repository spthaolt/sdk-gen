package pl.sointeractive.isaacloud;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.json.JSONException;

import pl.sointeractive.isaacloud.connection.HttpResponse;
import pl.sointeractive.isaacloud.connection.HttpToken;
import pl.sointeractive.isaacloud.exceptions.InvalidConfigException;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

/**
 * Connector class for the Android SDK.
 * 
 * @author Mateusz Renes
 * 
 */
public class Connector {
	
	private static final String TAG = "Connector";
	private String baseUrl;
	private String version;

	private static HttpToken httpToken;

	private String oauthUrl;
	private String memberId;
	private String appSecret;

	private SSLContext sslContext;

	private boolean hasValidCertificate;

	/**
	 * Base constructor.
	 * 
	 * @param baseUrl
	 *            The base URL address of the API.
	 * @param oauthUrl
	 *            The OAuth URL of the API. Used to generate access token.
	 * @param version
	 *            Version of the API.
	 * @param config
	 *            Configuration parameters. Requires "clientId" and "secret"
	 *            keys and their respective values.
	 * @throws InvalidConfigException
	 *             Thrown when "clientId" or "secret" are not found in the
	 *             parameters.
	 */
	public Connector(Context appContext, String baseUrl, String oauthUrl,
			String version, Map<String, String> config)
			throws InvalidConfigException {
		this.baseUrl = baseUrl;
		this.oauthUrl = oauthUrl;
		this.setVersion(version);
		httpToken = new HttpToken();
		// check config
		if (config.containsKey("memberId")) {
			this.memberId = config.get("memberId");
		} else {
			throw new InvalidConfigException("memberId");
		}
		if (config.containsKey("appSecret")) {
			this.appSecret = config.get("appSecret");
		} else {
			throw new InvalidConfigException("appSecret");
		}
		//set valid certificate to false
		hasValidCertificate = false;
	}

	private void initializeSSLContext() {
		// certificate handling
		try {
			// Load trusted IsaaCloud certificate
			Certificate ca;
			ca = SSLCertificateFactory.getCertificate(Config.PORT, Config.HOST);
			// Create a KeyStore containing our trusted CAs
			String keyStoreType = KeyStore.getDefaultType();
			KeyStore keyStore = KeyStore.getInstance(keyStoreType);
			keyStore.load(null, null);
			keyStore.setCertificateEntry("ca", ca);
			// Create a TrustManager that trusts the CAs in our KeyStore
			String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
			TrustManagerFactory tmf = TrustManagerFactory
					.getInstance(tmfAlgorithm);
			tmf.init(keyStore);
			// Create an SSLContext that uses our TrustManager
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, tmf.getTrustManagers(), null);
		} catch (CertificateException e) {
			e.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (KeyStoreException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the authentication token.
	 * 
	 * @return Authentication header in format: <token_type> <access_token>
	 * @throws JSONException
	 *             Thrown when an error occurs during JSON operations
	 * @throws IOException
	 *             Thrown when an error occurs during IO operations
	 */
	public String getAuthentication() throws JSONException, IOException {
		if (!isTokenValid()) {
			getAccessTokenData();
		}
		System.out.println(httpToken.getAuthorizationHeader());
		return httpToken.getAuthorizationHeader();
	}

	public void getAccessTokenData() throws JSONException, IOException {
		// generate credentials
		String base64EncodedCredentials = null;
		base64EncodedCredentials = Base64.encodeToString(
				(memberId + ":" + appSecret).getBytes("US-ASCII"),
				Base64.DEFAULT);
		String auth = "Basic " + base64EncodedCredentials;
		// setup connection
		URL url = new URL(this.oauthUrl + "/token");
		HttpsURLConnection connection = (HttpsURLConnection) url
				.openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setConnectTimeout(Config.TIMEOUT);
		connection.setReadTimeout(Config.TIMEOUT);
		// set socket
		connection.setSSLSocketFactory(sslContext.getSocketFactory());
		// setup headers
		connection.setRequestProperty("Content-Type",
				"application/x-www-form-urlencoded");
		connection.setRequestProperty("Authorization", auth);
		// set body
		OutputStream os = new BufferedOutputStream(connection.getOutputStream());
		os.write("grant_type=client_credentials".getBytes("UTF-8"));
		os.flush();
		os.close();
		// connect
		connection.connect();
		// check response code
		int responseCode = connection.getResponseCode();
		Log.d(TAG, "" + responseCode);
		// get result string
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				connection.getInputStream()));
		String resultString = reader.readLine();
		// build response
		HttpResponse.Builder responseBuilder = new HttpResponse.Builder();
		responseBuilder.setMethod("POST");
		responseBuilder.setResponseCode(responseCode);
		if (resultString != null) {
			responseBuilder.setResponseString(resultString);
			responseBuilder.setIsValid(true);
		} else {
			responseBuilder.setIsValid(false);
		}
		HttpResponse response = responseBuilder.build();
		// disconnect
		connection.disconnect();
		// update token time
		long currentTime = new Date().getTime();
		httpToken.setUpdateTime(currentTime);
		// save token data
		httpToken.setTokenTimeToLive(response.getJSONObject().getInt(
				"expires_in"));
		httpToken.setAccessToken(response.getJSONObject().getString(
				"access_token"));
		httpToken
				.setTokenType(response.getJSONObject().getString("token_type"));
	}

	/**
	 * Call required service from the API. For the future implementation of the
	 * wrapper: catch the SocketTimeoutException and MalformedURLException when
	 * using this method in order to gain more control over the exception
	 * handling process
	 * 
	 * Caution: In case an IOException is NOT thrown, but the http response code
	 * is still pointing at an error, an adequate information is stored in the
	 * returned HttpResponse.
	 * 
	 * @param uri
	 *            Uri of the method. Used together with the base Uri of the API
	 *            to get the whole address.
	 * @param methodName
	 *            Name of the method (GET, POST, PUT, DELETE).
	 * @param parameters
	 *            Url parameters to add to the uri (like limit or fields).
	 * @param body
	 *            Request body.
	 * @return Request response in form of a HttpResponse class.
	 * @throws JSONException
	 *             Thrown when an error occurs during JSON operations
	 * @throws IOException
	 *             Thrown when an error occurs during IO operations
	 */
	public HttpResponse callService(String uri, String methodName,
			Map<String, Object> parameters, String body)
			throws SocketTimeoutException, MalformedURLException, IOException,
			JSONException {
		// check for valid ceritificate
		Log.d(TAG, "Check for certificate");
		if(!hasValidCertificate){
			Log.d(TAG, "No valid certificate found, downloading new certificate");
			initializeSSLContext();
			hasValidCertificate = true;
		}
		// generate uri
		String targetUri = baseUrl + version + uri;
		if (parameters != null) {
			targetUri += "?";
			for (Entry<String, Object> entry : parameters.entrySet()) {
				targetUri += entry.getKey() + "=" + entry.getValue() + "&";
			}
			targetUri = targetUri.substring(0, targetUri.length() - 1);
		}
		System.out.println(targetUri);
		// setup connection
		URL url = new URL(targetUri);
		HttpsURLConnection connection = (HttpsURLConnection) url
				.openConnection();
		if (methodName.equals("GET") || methodName.equals("DELETE")) {
			connection.setDoOutput(false);
		} else {
			connection.setDoOutput(true);
		}
		connection.setDoInput(true);
		connection.setRequestMethod(methodName);
		connection.setConnectTimeout(Config.TIMEOUT);
		connection.setReadTimeout(Config.TIMEOUT);
		// set socket
		connection.setSSLSocketFactory(sslContext.getSocketFactory());
		// setup headers
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setRequestProperty("Authorization", getAuthentication());
		// setup body (optional)
		if (body != null) {
			OutputStream os = new BufferedOutputStream(
					connection.getOutputStream());
			os.write(body.getBytes("UTF-8"));
			os.flush();
			os.close();
		}
		// connect
		connection.connect();
		// get result string
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				connection.getInputStream()));
		String resultString = reader.readLine();
		// check response code
		int responseCode = connection.getResponseCode();
		Log.d("TEST", "" + responseCode);
		// disconnect
		connection.disconnect();
		// build response
		HttpResponse.Builder responseBuilder = new HttpResponse.Builder();
		responseBuilder.setMethod(methodName);
		responseBuilder.setResponseCode(responseCode);
		if (resultString != null) {
			responseBuilder.setResponseString(resultString);
			responseBuilder.setIsValid(true);
		} else {
			responseBuilder.setIsValid(false);
		}
		HttpResponse response = responseBuilder.build();
		// return response
		return response;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	/**
	 * Checks the validity of the token.
	 * 
	 * @return
	 */
	public static boolean isTokenValid() {
		long currentTime = new Date().getTime();
		if (currentTime > httpToken.getUpdateTime()
				+ httpToken.getTokenTimeToLive() * 1000) {
			return false;
		} else
			return true;
	}

}
