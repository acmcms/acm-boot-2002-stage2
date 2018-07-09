/**
 * Created on 24.11.2002
 *
 * myx - barachta */
package ae1core;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import ru.myx.ae1.control.Control;
import ru.myx.ae1.handle.Handle;
import ru.myx.ae3.Engine;
import ru.myx.ae3.act.Act;
import ru.myx.ae3.base.Base;
import ru.myx.ae3.base.BaseArray;
import ru.myx.ae3.base.BaseObject;
import ru.myx.ae3.base.BaseProperty;
import ru.myx.ae3.binary.Transfer;
import ru.myx.ae3.exec.Exec;
import ru.myx.ae3.help.Convert;
import ru.myx.ae3.help.Format;
import ru.myx.ae3.report.Report;
import ru.myx.ae3.xml.Xml;

/** @author barachta
 *
 * myx - barachta 
 *         Window>Preferences>Java>Templates. To enable and disable the creation of type comments go
 *         to Window>Preferences>Java>Code Generation. */
public final class Main {
	
	private static final Function<String, Object> DELAYED_SERVER_STARTER = new DelayedServerStarter();
	
	private static final Function<BaseObject, Object> INTERFACE_STARTER = new InterfaceStarter();
	
	private static final BaseObject checkCreate(final File file) throws IOException, FileNotFoundException, UnsupportedEncodingException {
		
		final InputStream resource = Main.class.getClassLoader().getResourceAsStream("ae1core/" + file.getName() + ".sample");
		final byte[] bytes = Transfer.createBuffer(resource).toDirectArray();
		if (file.exists()) {
			Report.event("AE1-CORE", "INIT", "Reading '" + file.getName() + "': " + file.getAbsolutePath());
		} else {
			Report.event("AE1-CORE", "INIT", "Creating '" + file.getName() + "': " + file.getAbsolutePath());
			try (final FileOutputStream fos = new FileOutputStream(file)) {
				fos.write(bytes);
			}
		}
		final File sample = new File(file.getParentFile(), file.getName() + ".sample");
		if (!sample.exists() || sample.length() != bytes.length) {
			Report.event("AE1-CORE", "INIT", "Creating sample '" + sample.getName() + "': " + sample.getAbsolutePath());
			try (final FileOutputStream fos = new FileOutputStream(sample)) {
				fos.write(bytes);
			}
		}
		return Xml.toBase("serverStarter", Transfer.createBuffer(file).toString(Engine.CHARSET_DEFAULT), null, null, null);
	}
	
	private static final BaseObject initializeClasses(final File configFolder) throws IOException, FileNotFoundException, UnsupportedEncodingException {
		
		final File file = new File(configFolder, "initialize.xml");
		final BaseObject initialize = Main.checkCreate(file);
		{
			final BaseObject object = initialize.baseGet("init", BaseObject.UNDEFINED);
			assert object != null : "NULL java value";
			final BaseArray array = object.baseArray();
			if (array == null) {
				Main.tryInit(object);
			} else {
				final int length = array.length();
				for (int i = 0; i < length; ++i) {
					Main.tryInit(array.baseGet(i, null));
				}
			}
		}
		return initialize;
	}
	
	private static final BaseObject initializeInterfaces(final BaseObject interfaces) {
		
		final BaseObject object = interfaces.baseGet("interface", BaseObject.UNDEFINED);
		assert object != null : "NULL java value";
		final BaseArray array = object.baseArray();
		if (array == null) {
			Main.tryInterface(object);
		} else {
			final int length = array.length();
			for (int i = 0; i < length; ++i) {
				Main.tryInterface(array.baseGet(i, BaseObject.UNDEFINED));
			}
		}
		return interfaces;
	}
	
	private static final BaseObject initializeProperties(final File configFolder) throws IOException, FileNotFoundException, UnsupportedEncodingException {
		
		final File file = new File(configFolder, "properties.xml");
		final BaseObject properties = Main.checkCreate(file);
		{
			final BaseObject object = properties.baseGet("property", BaseObject.UNDEFINED);
			assert object != null : "NULL java value";
			final BaseArray array = object.baseArray();
			if (array == null) {
				Main.tryProperty(object);
			} else {
				final int length = array.length();
				for (int i = 0; i < length; ++i) {
					Main.tryProperty(array.baseGet(i, BaseObject.UNDEFINED));
				}
			}
		}
		return properties;
	}
	
	private static final void initializeServers(final File configFolder) throws IOException, FileNotFoundException, UnsupportedEncodingException {
		
		final Map<String, ServerInitializer> serverInitializers = new ConcurrentHashMap<>();
		final Map<String, ServerInitializer> domainInitializers = new ConcurrentHashMap<>();
		final BaseObject servers;
		/** servers.xml file */
		{
			final File file = new File(configFolder, "servers.xml");
			servers = Main.checkCreate(file);
			{
				final BaseObject object = servers.baseGet("server", BaseObject.UNDEFINED);
				assert object != null : "NULL java value";
				final BaseArray array = object.baseArray();
				if (array == null) {
					Main.tryServer(serverInitializers, domainInitializers, object);
				} else {
					final int length = array.length();
					for (int i = 0; i < length; ++i) {
						Main.tryServer(serverInitializers, domainInitializers, array.baseGet(i, BaseObject.UNDEFINED));
					}
				}
			}
		}
		/** servers/*.xml files */
		{
			final File folder = new File(configFolder, "servers");
			if (!folder.isDirectory()) {
				folder.mkdir();
			}
			if (folder.isDirectory()) {
				folder.listFiles(new FileFilter() {
					
					@Override
					public boolean accept(final File file) {
						
						if (!file.isFile() || !file.getName().toLowerCase().endsWith(".xml")) {
							return false;
						}
						Report.event("AE1-CORE", "INIT", "Reading '" + file.getName() + "': " + file.getAbsolutePath());
						final BaseObject object = Xml.toBase(
								"serverStarter: " + file.getName(), //
								Transfer.createBuffer(file).toString(Engine.CHARSET_UTF8),
								null,
								null,
								null);
						Main.tryServer(serverInitializers, domainInitializers, object);
						return false;
					}
				});
			}
		}
		Handle.managerImpl(new ServerManager(serverInitializers, domainInitializers));
	}
	
	/** @param args
	 * @throws Throwable
	 */
	public static final void main(final String[] args) throws Throwable {
		
		System.out.println("AE1-CORE IS BEING INITIALIZED, printing context stack dump:");
		System.out.flush();
		if (System.getProperty("serve.root", "").length() == 0) {
			System.setProperty("serve.root", new File(Engine.PATH_PROTECTED, "web").getAbsolutePath());
		}
		Report.event("AE1-CORE", "INIT", "serve.root will be at: : " + System.getProperty("serve.root"));
		Exec.getRootProcess().baseDefine("Control", Base.forUnknown(Control.INSTANCE), BaseProperty.ATTRS_MASK_NNN);
		final File configFolder = new File(Engine.PATH_PROTECTED, "conf");
		if (!configFolder.exists()) {
			configFolder.mkdirs();
		}
		Main.initializeProperties(configFolder);
		Main.initializeClasses(configFolder);
		Main.initializeServers(configFolder);
		Main.initializeInterfaces(Main.checkCreate(new File(configFolder, "interfaces.xml")));
		Runtime.getRuntime().gc();
		Report.event("AE1-CORE", "INIT", "Stage2 Init Finished.");
		/** acmbsd waits for this output to start forwarding requests */
		System.out.println("AE1-CORE: stage2 init finished.");
	}
	
	private static final void tryInit(final Object object) {
		
		if (object == null) {
			// ignore
		} else if (object instanceof Map<?, ?>) {
			final Map<?, ?> map = (Map<?, ?>) object;
			final String className = Convert.MapEntry.toString(map, "id", "").trim();
			if (className.length() > 0) {
				try {
					Report.event("SYSTEM", "INITIALIZING", "Init class: " + className + "...");
					Class.forName(className);
				} catch (final Throwable t) {
					Report.event("AE1-CORE", "INIT-ERROR", Format.Throwable.toText("[class_initializer]", t));
				}
			}
		}
	}
	
	static final void tryInterface(final BaseObject object) {
		
		try {
			Act.run(Exec.currentProcess(), Main.INTERFACE_STARTER, object);
		} catch (final Throwable t) {
			Report.event("AE1-CORE", "INIT-ERROR", Format.Throwable.toText("[class_initializer]", t));
		}
	}
	
	private static final void tryProperty(final BaseObject object) {
		
		assert object != null : "NULL java object";
		try {
			final String id = Base.getString(object, "id", "").trim();
			if (id.length() > 0) {
				final String value = Base.getString(object, "value", "");
				System.setProperty(id, value);
				Report.event("AE1-CORE", "INIT", "System property: " + id + ", value=" + value + ", check=" + System.getProperty(id));
			}
		} catch (final Throwable t) {
			t.printStackTrace();
		}
	}
	
	static final void tryServer(final Map<String, ServerInitializer> serverInitializers, final Map<String, ServerInitializer> domainInitializers, final BaseObject object) {
		
		assert object != null : "NULL java value";
		final String id = Base.getString(object, "id", "").trim();
		if (id.length() == 0) {
			Report.warning("AE1-CORE", "Server skipped (id attribute is expected): " + Format.Describe.toEcmaSource(object, ""));
			return;
		}
		/** check parking.
		 *
		 * if 'parked' attribute is non-empty server is started only when our hostname is one
		 * mentioned in it. */
		{
			final String parked = Base.getString(object, "parked", "").trim();
			if (parked.length() > 0) {
				parkedCheck : {
					for (final StringTokenizer st = new StringTokenizer(parked.replace(';', ','), ","); st.hasMoreTokens();) {
						final String hostname = st.nextToken().trim();
						if (hostname.startsWith("*.")) {
							if (Engine.HOST_NAME.endsWith(hostname.substring(1))) {
								break parkedCheck;
							}
							if (Engine.HOST_NAME.equals(hostname.substring(2))) {
								break parkedCheck;
							}
							final String instanceName = Engine.GROUP_NAME + '.' + Engine.HOST_NAME;
							if (instanceName.endsWith(hostname.substring(1))) {
								break parkedCheck;
							}
							if (instanceName.equals(hostname.substring(2))) {
								break parkedCheck;
							}
						} else {
							if (Engine.HOST_NAME.equals(hostname)) {
								break parkedCheck;
							}
							final String instanceName = Engine.GROUP_NAME + '.' + Engine.HOST_NAME;
							if (instanceName.equals(hostname)) {
								break parkedCheck;
							}
						}
					}
					/** not parked here then 8-( */
					Report.warning(
							"AE1-CORE",
							"Server skipped (not parked here, hostname=" + Engine.HOST_NAME + ", groupname=" + Engine.GROUP_NAME + "): "
									+ Format.Describe.toEcmaSource(object, ""));
					return;
				}
			}
		}
		final String type = Base.getString(object, "class", "").trim();
		if (type.length() == 0) {
			Report.warning("AE1-CORE", "Server skipped (id and class attributes are both expected): " + Format.Describe.toEcmaSource(object, ""));
		} else {
			final Object initializerLock = new Object();
			final ServerInitializer initializer;
			serverInitializers.put(id, initializer = new ServerInitializer(initializerLock, object, type));
			final String aliases = Base.getString(object, "aliases", "").trim();
			if (aliases.length() > 0) {
				for (final StringTokenizer st = new StringTokenizer(aliases.replace(';', ','), ","); st.hasMoreTokens();) {
					final String alias = st.nextToken().trim();
					if (alias.startsWith("*.")) {
						domainInitializers.put(alias.substring(2), initializer);
					} else {
						serverInitializers.put(alias, initializer);
					}
				}
			}
			final String domain = Base.getString(object, "domain", "").trim();
			if (domain.length() > 0) {
				domainInitializers.put(domain, initializer);
			}
		}
		Report.event("AE1-CORE", "INIT", "Delay Start Set for: '" + id + "'");
		Act.later(null, Main.DELAYED_SERVER_STARTER, id, 5000 + Engine.createRandom(30000));
	}
}
