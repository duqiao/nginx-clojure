package nginx.clojure.bridge;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import nginx.clojure.NginxClojureRT;

public class NginxBridgeStarter {
	
	public static final String BRIDGE_LIB_CP = "bridge.lib.cp";

	public static final String BRIDGE_IMP = "bridge.imp";

	public static final String BRIDGE_LIB_DIRS = "bridge.lib.dirs";
	
	public static final String BRIDGE_ALIAS = "bridge.alias";
	
	public NginxBridgeStarter() {
	}
	
	public NginxBridge start(Map<String, String> properties) {
		
		String libDirs = properties.get(BRIDGE_LIB_DIRS);
		String cpDirs = properties.get(BRIDGE_LIB_CP);
		String loaderKey = libDirs+"\n" + cpDirs;
		ClassLoader bootstrapLoader = null;
		
		for (Entry<String, String> en : properties.entrySet()) {
			if (en.getKey().startsWith("system.")) {
				System.setProperty(en.getKey().substring("system.".length()), en.getValue());
				NginxClojureRT.getLog().info("set system property: %s=%s", en.getKey().substring("system.".length()), en.getValue());
			}
		}
		
		String bridgeImp = properties.get(BRIDGE_IMP);

		List<URL> urlList = new ArrayList<URL>();
		if (libDirs != null) {
			for (String dir : libDirs.split(File.pathSeparator)) {
				for (File f : new File(dir).listFiles()) {
					try {
						if (f.isFile() && f.getName().endsWith(".jar")) {
							urlList.add(f.toURI().toURL());
						} else if (f.isDirectory()) {
							urlList.add(f.toURI().toURL());
						}
					} catch (MalformedURLException e) {// ignore
					}
				}
			}
		}

		if (cpDirs != null) {
			for (String dir : cpDirs.split(File.pathSeparator)) {
				File f = new File(dir);
				try {
					if (f.isFile() && f.getName().endsWith(".jar")) {
						urlList.add(f.toURI().toURL());
					} else if (f.isDirectory()) {
						urlList.add(f.toURI().toURL());
					}
				} catch (MalformedURLException e) {// ignore
				}
			}
		}
		URL[] urls = new URL[urlList.size()];
		NginxClojureRT.getLog().info("%s.boot() with whole classpath: %s", bridgeImp, urlList);
		bootstrapLoader = URLClassLoader.newInstance(urlList.toArray(urls));
		
		Class bridgeClz;
		try {
			bridgeClz = bootstrapLoader.loadClass(bridgeImp);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Can't load  NginxBridge:"+bridgeImp, e);
		}
		
		NginxBridge bridge;
		ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(bootstrapLoader);
			bridge = (NginxBridge) bridgeClz.newInstance();
		} catch (Throwable e) {
			Thread.currentThread().setContextClassLoader(oldLoader);
			throw new IllegalArgumentException("Can't create  NginxBridge:"+bridgeImp, e);
		}
		
		try {
			bridge.boot(properties, bootstrapLoader);
			return bridge;
		}finally{
			Thread.currentThread().setContextClassLoader(oldLoader);
		}

	}
	
}