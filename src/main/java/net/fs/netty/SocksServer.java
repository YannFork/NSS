package  net.fs.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import net.fs.client.ClientUI;
import net.fs.client.FSClient;
import org.apache.commons.cli.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import  net.fs.netty.config.Config;
import  net.fs.netty.config.ConfigXmlLoader;
import  net.fs.netty.config.PacLoader;
import  net.fs.netty.mbean.IoAcceptorStat;
import  net.fs.netty.proxy.SocksServerInitializer;

public class SocksServer {

	private static Log logger = LogFactory.getLog(SocksServer.class);

	private static final String CONFIG = "conf/config.xml";

	private static final String PAC = "conf/pac.xml";

	private EventLoopGroup bossGroup = null;
	private EventLoopGroup workerGroup = null;
	private ServerBootstrap bootstrap = null;
	private GlobalTrafficShapingHandler trafficHandler;

	private static SocksServer socksServer = new SocksServer();

	public static SocksServer getInstance() {
		return socksServer;
	}

	private SocksServer() {

	}

	public void start() {
		try {

			FSClient.start();

			Config config = ConfigXmlLoader.load(CONFIG);
			PacLoader.load(PAC);

			bossGroup = new NioEventLoopGroup(1);
			workerGroup = new NioEventLoopGroup();
			bootstrap = new ServerBootstrap();
			trafficHandler = new GlobalTrafficShapingHandler(
					Executors.newScheduledThreadPool(2), 1000);

			bootstrap
					.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.childHandler(
							new SocksServerInitializer(config, trafficHandler));

			logger.info("Start At Port " + config.get_localPort());
			startMBean();
			bootstrap.bind(config.get_localPort()).sync().channel()
					.closeFuture().sync();
		} catch (Exception e) {
			logger.error("start error", e);
		} finally {
			stop();
		}
	}

	public void stop() {
		if (bossGroup != null) {
			bossGroup.shutdownGracefully();
		}
		if (workerGroup != null) {
			workerGroup.shutdownGracefully();
		}
		logger.info("Stop Server!");
	}

	/**
	 * java MBean 进行流量统计
	 */
	private void startMBean() {
		MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
		IoAcceptorStat mbean = new IoAcceptorStat();

		try {
			ObjectName acceptorName = new ObjectName(mbean.getClass()
					.getPackage().getName()
					+ ":type=IoAcceptorStat");
			mBeanServer.registerMBean(mbean, acceptorName);
		} catch (Exception e) {
			logger.error("java MBean error", e);
		}
	}

	public TrafficCounter getTrafficCounter() {
		return trafficHandler.trafficCounter();
	}

}
