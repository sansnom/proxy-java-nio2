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

public class SocketHandler {

	private static final Logger LOGGER = Logger.getLogger(SocketHandler.class.getName());

	public static void readFromClientSocket(SocketInfo attachment) {
		final ByteBuffer buffer = ByteBuffer.allocate(1024);
		final AsynchronousSocketChannel client = attachment.getClient();
		if (!client.isOpen()) {
			LOGGER.log(Level.FINE, "Client has closed the connection.");
			return;
		}
		client.read(buffer, 60, TimeUnit.SECONDS, attachment, new CompletionHandler<>() {

			@Override
			public void completed(Integer result, SocketInfo attachment) {
				try {
					if (result == -1) {
						LOGGER.log(Level.FINER, "No more data.");
						return;
					}

					writeToRemoteHost(attachment, buffer);

					// Continue reading
					// TODO need to handle this properly
					// readFromClientSocket(attachment);
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Error after read", e);
					throw new RuntimeException(e);
				}
			}

			@Override
			public void failed(Throwable e, SocketInfo attachment) {
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
		String strContent = new String(content, StandardCharsets.UTF_8);
		LOGGER.log(Level.INFO, strContent);
		LOGGER.log(Level.INFO, String.format("(0) --- READ CLIENT: OK RECEIVED %d", content.length));

		AsynchronousSocketChannel remote = attachment.getRemote();
		if (remote == null) {
			String[] result = strContent.split("\r\n", 2);
			String url = extractHost(result[0]);
			String strRewritten = rewrite(result);
			byte[] rewritten = strRewritten.getBytes(StandardCharsets.UTF_8);
			LOGGER.log(Level.INFO, strRewritten);

			String hostName = URI.create(url).getHost();
			attachment.setHostname(hostName);
			int port = URI.create(url).getPort();
			int remotePortNumber = port != -1 ? port : (url.startsWith("https") ? 443 : 80);
			attachment.setPort(remotePortNumber);
			LOGGER.log(Level.INFO, String.format("(0) --- READ CLIENT: OK CONNECT TO %s:%d", hostName, remotePortNumber));

			remote = AsynchronousSocketChannel.open();
			InetSocketAddress hostAddress = new InetSocketAddress(hostName, remotePortNumber);
			attachment.setRemote(remote);

			remote.connect(hostAddress, attachment, new CompletionHandler<>() {

				@Override
				public void completed(Void result, SocketInfo attachment) {
					LOGGER.log(Level.INFO, String.format("(1) --- CONNECTION REMOTE: OK TO %s:%d", hostName, remotePortNumber));
					ByteBuffer buf = ByteBuffer.allocate(rewritten.length);
					buf.put(rewritten);
					buf.flip();

					writeToRemote(attachment, buf);
				}

				@Override
				public void failed(Throwable e, SocketInfo attachment) {
					LOGGER.log(Level.WARNING, String.format("(1) --- CONNECTION REMOTE: KO TO %s:%d", hostName, remotePortNumber));
				}
			});
		} else {
			buffer.flip();
			writeToRemote(attachment, buffer);
		}
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

	private static void writeToRemote(SocketInfo attachment, ByteBuffer buf) {
		final AsynchronousSocketChannel remote = attachment.getRemote();
		remote.write(buf, attachment, new CompletionHandler<>() {

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

				byte[] responseContent = getContent(buffer);
				LOGGER.log(Level.INFO, new String(responseContent, StandardCharsets.UTF_8));
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
				LOGGER.log(Level.WARNING, String.format("(3) --- READ REMOTE: KO TO %s", attachment));
				attachment.remoteReadDone();
				e.printStackTrace();
			}
		});
	}

	private static String extractHost(String requestContent) {
		// GET http://www.httpbin.org/get
		String[] requestSplit = requestContent.split(" ");
		return requestSplit[1].trim();
	}

	public static void writeToClientSocket(SocketInfo attachment, ByteBuffer buf) {
		final AsynchronousSocketChannel client = attachment.getClient();
		client.write(buf, attachment, new CompletionHandler<>() {

			@Override
			public void completed(Integer result, SocketInfo attachment) {
				LOGGER.log(Level.INFO, String.format("(4) --- WRITE CLIENT: OK %d", result));
				// try to continue
				if (attachment.isRemoteReadDone()) {
					readFromClientSocket(attachment);
				}
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
