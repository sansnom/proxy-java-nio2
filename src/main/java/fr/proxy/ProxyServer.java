package fr.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author sbrocard
 */
public class ProxyServer implements Runnable {

	private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());

	private final AsynchronousServerSocketChannel serverChannel;
	private final ExecutorService executorService;
	private boolean stop;

	public ProxyServer(int port, boolean listenAllAddresses, String localIpAddress, int threads) throws IOException {
		this.executorService = Executors.newFixedThreadPool(threads);
		this.serverChannel = AsynchronousServerSocketChannel.open();
		InetSocketAddress isa = getInetSocketAddress(listenAllAddresses, localIpAddress, port);
		this.serverChannel.bind(isa);
	}

	private InetSocketAddress getInetSocketAddress(boolean listenAllAddresses, String localIpAddress, int port) throws UnknownHostException {
		if (listenAllAddresses) {
			return new InetSocketAddress(port);
		} else {
			if (localIpAddress == null || localIpAddress.isEmpty()) {
				return new InetSocketAddress(InetAddress.getLocalHost(), port);
			} else {
				return new InetSocketAddress(InetAddress.getByName(localIpAddress), port);
			}
		}
	}

	@Override
	public void run() {
		LOGGER.log(Level.FINE, String.format("Run on %s", getLocal(this.serverChannel)));
		this.serverChannel.accept(new SocketInfo(), new CompletionHandler<>() {

			@Override
			public void completed(AsynchronousSocketChannel result, SocketInfo attachment) {
				try {
					InetSocketAddress remoteAddress = (InetSocketAddress) result.getRemoteAddress();
					attachment.setClientAddress(remoteAddress);
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Could not retrieve remote address", e);
				}

				executorService.submit(() -> handle(result, attachment));

				// Continue accepting request ?
				if (!stop) {
					serverChannel.accept(new SocketInfo(), this);
				}
			}

			@Override
			public void failed(Throwable exc, SocketInfo attachment) {
				LOGGER.log(Level.SEVERE, "Error during accept", exc);
			}
		});
	}

	public void join() throws InterruptedException {
		synchronized (this.serverChannel) {
			this.serverChannel.wait();
		}
	}

	public void stop() throws IOException {
		this.stop = true;
		this.serverChannel.close();
	}

	private void handle(AsynchronousSocketChannel socketChannel, SocketInfo attachment) {
		LOGGER.log(Level.INFO, String.format("New connection from %s", attachment));
		attachment.setClient(socketChannel);
		SocketHandler.readFromClientSocket(attachment);
	}

	private SocketAddress getLocal(AsynchronousServerSocketChannel serverChannel) {
		try {
			return serverChannel.getLocalAddress();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Could not retrieve local address", e);
			return null;
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		configureLogger();
		ProxyServer proxyServer = new ProxyServer(8787, true, null, 10);
		proxyServer.run();
		try {
			proxyServer.join();
		} finally {
			proxyServer.stop();
		}
	}

	private static void configureLogger() {
		final Logger app = Logger.getLogger("fr.proxy");
		app.setLevel(Level.FINEST);
	}
}
