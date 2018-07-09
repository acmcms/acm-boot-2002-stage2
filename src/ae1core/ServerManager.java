/*
 * Created on 15.06.2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package ae1core;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import ru.myx.ae1.handle.ServerManagerImpl;
import ru.myx.ae1.know.Server;
import ru.myx.ae3.act.Act;
import ru.myx.ae3.exec.Exec;
import ru.myx.ae3.exec.ExecProcess;
import ru.myx.ae3.help.Convert;
import ru.myx.ae3.report.Report;

/** @author myx
 *
 *         To change the template for this generated type comment go to Window>Preferences>Java>Code
 *         Generation>Code and Comments */
class ServerManager implements ServerManagerImpl {
	
	private static final ExecProcess ISOLATION;
	
	private static final Comparator<Map.Entry<String, ?>> DOMAIN_COMPARATOR = new DomainComparator();
	
	static {
		ISOLATION = Exec.createProcess(Exec.getRootProcess(), "server manager isolation context");
	}
	
	private final Object initializationLock = new Object();
	
	private final Object registrationLock = new Object();
	
	private final Map<String, Server> known = new HashMap<>();
	
	private final Map<String, ServerInitializer> serverInitializers;
	
	private final String[] domainNames;
	
	private final ServerInitializer[] domainInitializers;
	
	ServerManager(final Map<String, ServerInitializer> initializers, final Map<String, ServerInitializer> domainInitializers) {
		
		this.serverInitializers = initializers;
		final Map.Entry<String, ServerInitializer>[] domains = Convert.Array.toAny(domainInitializers.entrySet().toArray(new Map.Entry[domainInitializers.size()]));
		if (domains.length > 1) {
			Arrays.sort(domains, ServerManager.DOMAIN_COMPARATOR);
		}
		this.domainNames = new String[domains.length];
		this.domainInitializers = new ServerInitializer[domains.length];
		for (int i = domains.length - 1; i >= 0; --i) {
			this.domainNames[i] = domains[i].getKey();
			this.domainInitializers[i] = domains[i].getValue();
		}
	}
	
	@Override
	public Server check(final String name) {
		
		final Server result = this.known.get(name);
		return result == Server.NULL_SERVER
			? null
			: result;
	}
	
	@Override
	public final void register(final String name, final Server server) {
		
		if (Report.MODE_DEBUG) {
			Report.debug("AE1CORE/SRV_MAN", "Trying to register a server: " + name);
		}
		final Server result;
		synchronized (this.registrationLock) {
			result = this.known.put(name, server);
		}
		if (result != null && result != server) {
			Report.info("AE1CORE/SRV_MAN", "Server is already registered: (host=" + name + "), replaced!");
		}
	}
	
	@Override
	public final Server server(final String name) {
		
		Server result = this.known.get(name);
		if (result != null) {
			return result == Server.NULL_SERVER
				? null
				: result;
		}
		try {
			ServerInitializer serverInitializer;
			synchronized (this.initializationLock) {
				result = this.known.get(name);
				if (result != null) {
					return result == Server.NULL_SERVER
						? null
						: result;
				}
				serverInitializer = this.serverInitializers.get(name);
				if (serverInitializer == null) {
					final int nameLength = name.length();
					for (int i = this.domainInitializers.length - 1; i >= 0; --i) {
						final String domainName = this.domainNames[i];
						final int domainLength = domainName.length();
						if (nameLength >= domainLength && name.endsWith(domainName) && (nameLength == domainLength || name.charAt(nameLength - domainLength - 1) == '.')) {
							serverInitializer = this.domainInitializers[i];
							break;
						}
					}
					if (serverInitializer == null) {
						this.known.put(name, Server.NULL_SERVER);
						return null;
					}
				}
			}
			if (Report.MODE_DEBUG) {
				Report.debug("AE1CORE/SRV_MAN", "Trying to initialize server: " + name);
			}
			final Server initialized = Act.run(ServerManager.ISOLATION, serverInitializer, name);
			if (initialized != null) {
				assert initialized != Server.NULL_SERVER : "should not return NULL_SERVER";
				synchronized (this.registrationLock) {
					result = this.known.get(name);
					if (result == null) {
						if (Report.MODE_DEBUG) {
							Report.debug("AE1CORE/SRV_MAN", "Trying to register server: " + name);
						}
						this.known.put(name, result = initialized);
					}
				}
				return initialized;
			}
		} catch (final Throwable t) {
			Report.exception("AE1CORE/SRV_MAN", "Error initializing server, id=" + name, t);
		}
		this.known.put(name, Server.NULL_SERVER);
		return null;
	}
}
