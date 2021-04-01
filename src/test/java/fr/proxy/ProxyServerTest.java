package fr.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Since 01/04/2021
 *
 * @author sbrocard
 */
class ProxyServerTest {

	@Test
	public void testHTTP() throws IOException {
		URL url = new URL("http://www.google.fr");
		Proxy proxy = new Proxy(Type.HTTP, new InetSocketAddress("localhost", 8787));
		HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection(proxy);

		int responseCode = urlConnection.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK) {
			try (final InputStream inputStream = urlConnection.getErrorStream()) {
				print(inputStream);
			}
		} else {
			try (final InputStream inputStream = urlConnection.getInputStream()) {
				print(inputStream);
			}
		}
	}

	@Test
	public void testHTTPs() throws IOException {
		URL url = new URL("https://www.google.fr");
		Proxy proxy = new Proxy(Type.HTTP, new InetSocketAddress("localhost", 8787));
		HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection(proxy);

		int responseCode = urlConnection.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK) {
			try (final InputStream inputStream = urlConnection.getErrorStream()) {
				print(inputStream);
			}
		} else {
			try (final InputStream inputStream = urlConnection.getInputStream()) {
				print(inputStream);
			}
		}
	}

	private void print(InputStream inputStream) throws IOException {
		byte[] bytes = inputStream.readAllBytes();
		System.out.println(new String(bytes, StandardCharsets.UTF_8));
	}
}