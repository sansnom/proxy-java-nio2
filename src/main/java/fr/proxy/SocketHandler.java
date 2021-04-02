package fr.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author sbrocard
 */
public class SocketHandler {

	private static final Logger LOGGER = Logger.getLogger(SocketHandler.class.getName());

	public static void readFromClientSocket(SocketInfo attachment) {
		final ByteBuffer buffer = ByteBuffer.allocate(1024);
		final AsynchronousSocketChannel client = attachment.getClient();
		if (!client.isOpen()) {
			LOGGER.log(Level.FINE, "Client has closed the connection.");
			return;
		}
		if (attachment.isReadPending()) {
			return;
		}
		attachment.setReadPending(true);
		client.read(buffer, 60, TimeUnit.SECONDS, attachment, new CompletionHandler<>() {

			@Override
			public void completed(Integer result, SocketInfo attachment) {
				try {
					attachment.setReadPending(false);
					if (result == -1) {
						LOGGER.log(Level.FINER, "No more data.");
						return;
					}

					writeToRemoteHost(attachment, buffer);

					// Continue reading
					readFromClientSocket(attachment);
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Error after read", e);
					throw new RuntimeException(e);
				}
			}

			@Override
			public void failed(Throwable e, SocketInfo attachment) {
				attachment.setReadPending(false);
				LOGGER.log(Level.WARNING, "(0) --- READ CLIENT: ERROR", e);
			}
		});
	}

	private static byte[] getContent(ByteBuffer buffer) {
		buffer.flip();
		final int limit = buffer.limit();
		byte[] content = new byte[limit];
		buffer.get(content);
		return content;
	}

	private static void writeToRemoteHost(SocketInfo attachment, ByteBuffer buffer) throws IOException {
		byte[] content = getContent(buffer);
		String strContent = new String(content, StandardCharsets.ISO_8859_1);
		LOGGER.log(Level.INFO, String.format("(0) --- READ CLIENT: OK RECEIVED %d", content.length));
		LOGGER.log(Level.FINER, strContent);

		AsynchronousSocketChannel remote = attachment.getRemote();
		if (remote == null && !attachment.isTunnel()) {
			String[] result = strContent.split("\r\n", 2);
			String[] requestSplit = result[0].split(" ");

			// GET http://hostname/path?query
			// CONNECT hostname:443
			String url = requestSplit[1].trim();

			if (requestSplit[0].equals("CONNECT")) {
				handleConnect(attachment, url);
			} else {
				handleHTTP(attachment, result, url);
			}
		} else {
			// continue
			buffer.flip();
			writeToRemote(attachment, buffer);
		}
	}

	// HTTPS proxy
	private static void handleConnect(SocketInfo attachment, String url) throws IOException {
		AsynchronousSocketChannel remote;
		attachment.setTunnel(true);

		Destination destination = buildForConnect(url);
		attachment.setDestination(destination);

		remote = AsynchronousSocketChannel.open();
		InetSocketAddress hostAddress = new InetSocketAddress(destination.getHostName(), destination.getPort());
		attachment.setRemote(remote);

		remote.connect(hostAddress, attachment, new CompletionHandler<>() {

			@Override
			public void completed(Void result, SocketInfo attachment) {
				LOGGER.log(Level.INFO, String.format("(1) --- CONNECTION REMOTE: OK TO %s", destination));
				ByteBuffer buf = buildOK();
				writeToClientSocket(attachment, buf);

				// Try to continue with existing connection
				readFromClientSocket(attachment);
			}

			@Override
			public void failed(Throwable exc, SocketInfo attachment) {
				LOGGER.log(Level.WARNING, String.format("(1) --- CONNECTION REMOTE: KO TO %s", destination));
				ByteBuffer buf = buildError();
				writeToClientSocket(attachment, buf);
			}
		});
	}

	// HTTP proxy
	private static void handleHTTP(SocketInfo attachment, String[] result, String url) throws IOException {
		String strRewritten = rewrite(result);
		byte[] rewritten = strRewritten.getBytes(StandardCharsets.ISO_8859_1);
		LOGGER.log(Level.INFO, strRewritten);

		Destination destination = build(url);
		attachment.setDestination(destination);

		LOGGER.log(Level.INFO, String.format("(0) --- READ CLIENT: OK CONNECT TO %s", destination));

		AsynchronousSocketChannel remote = AsynchronousSocketChannel.open();
		InetSocketAddress hostAddress = new InetSocketAddress(destination.getHostName(), destination.getPort());
		attachment.setRemote(remote);

		remote.connect(hostAddress, attachment, new CompletionHandler<>() {

			@Override
			public void completed(Void result, SocketInfo attachment) {
				LOGGER.log(Level.INFO, String.format("(1) --- CONNECTION REMOTE: OK TO %s", destination));
				ByteBuffer buf = ByteBuffer.wrap(rewritten);
				writeToRemote(attachment, buf);
			}

			@Override
			public void failed(Throwable e, SocketInfo attachment) {
				LOGGER.log(Level.WARNING, String.format("(1) --- CONNECTION REMOTE: KO TO %s", destination));
			}
		});
	}

	private static Destination build(String url) {
		String hostName = URI.create(url).getHost();
		int port = URI.create(url).getPort();
		int remotePortNumber = port != -1 ? port : (url.startsWith("https") ? 443 : 80);
		return new Destination(hostName, remotePortNumber);
	}

	private static Destination buildForConnect(String connect) {
		String[] splits = connect.split(":");
		String hostName = splits[0];
		int port = Integer.parseInt(splits[1]);
		return new Destination(hostName, port);
	}

	private static ByteBuffer buildOK() {
		byte[] res = "HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8);
		return  ByteBuffer.wrap(res);
	}

	private static ByteBuffer buildError() {
		byte[] res = "HTTP/1.1 500 BAD REMOTE\r\n\r\n".getBytes(StandardCharsets.UTF_8);
		return  ByteBuffer.wrap(res);
	}

	private static String rewrite(String[] result) {
		String line = result[0];
		String[] splits = line.split(" ");
		URI uri = URI.create(splits[1].trim());
		String rawQuery = uri.getRawQuery();
		String rawPath = uri.getRawPath();
		splits[1] = ((rawPath == null || rawPath.isEmpty()) ? '/' : rawPath) + (rawQuery != null ? "?" + rawQuery : "");
		String rewritten = String.join(" ", splits);
		return rewritten + "\r\n" + result[1];
	}

	private static void writeToRemote(SocketInfo attachment, ByteBuffer buffer) {
		final AsynchronousSocketChannel remote = attachment.getRemote();
		remote.write(buffer, attachment, new CompletionHandler<>() {

			@Override
			public void completed(Integer result, SocketInfo attachment) {
				LOGGER.log(Level.INFO, String.format("(2) --- WRITE REMOTE: OK TO %s", attachment));

				ByteBuffer buffer = ByteBuffer.allocate(10024);
				readFromRemote(attachment, buffer);
			}

			@Override
			public void failed(Throwable e, SocketInfo attachment) {
				LOGGER.log(Level.WARNING, String.format("(2) --- WRITE REMOTE: KO TO %s", attachment));
			}
		});
	}

	private static void readFromRemote(SocketInfo attachment, ByteBuffer buffer) {
		final AsynchronousSocketChannel remote = attachment.getRemote();
		remote.read(buffer, 60, TimeUnit.SECONDS, attachment, new CompletionHandler<>() {

			@Override
			public void completed(Integer result, SocketInfo attachment) {
				LOGGER.log(Level.INFO, String.format("(3) --- READ REMOTE: OK TO %s %d", attachment, result));

				debug(buffer);
				buffer.flip();

				boolean moreData = result != -1;
				if (!moreData) {
					attachment.remoteReadDone();
				}
				writeToClientSocket(attachment, buffer);

				if (moreData) {
					ByteBuffer buffer = ByteBuffer.allocate(10024);
					readFromRemote(attachment, buffer);
				}
			}

			@Override
			public void failed(Throwable e, SocketInfo attachment) {
				LOGGER.log(Level.WARNING, String.format("(3) --- READ REMOTE: KO TO %s", attachment), e);
				attachment.remoteReadDone();
			}
		});
	}

	private static void debug(ByteBuffer buffer) {
		if (LOGGER.isLoggable(Level.FINER)) {
			byte[] responseContent = getContent(buffer);
			LOGGER.log(Level.FINER, new String(responseContent, StandardCharsets.UTF_8));
		}
	}

	public static void writeToClientSocket(SocketInfo attachment, ByteBuffer buffer) {
		final AsynchronousSocketChannel client = attachment.getClient();
		client.write(buffer, attachment, new CompletionHandler<>() {

			@Override
			public void completed(Integer result, SocketInfo attachment) {
				LOGGER.log(Level.INFO, String.format("(4) --- WRITE CLIENT: OK %d", result));
				// try to continue
				readFromClientSocket(attachment);
			}

			@Override
			public void failed(Throwable exc, SocketInfo attachment) {
				LOGGER.log(Level.WARNING, String.format("(4) --- WRITE CLIENT: KO %s", exc.getMessage()));
				try {
					if (client.isOpen()) {
						client.close();
					}
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Unable to close channel.", e);
				}
			}
		});
	}
}
