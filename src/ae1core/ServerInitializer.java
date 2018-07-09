/*
 * Created on 07.04.2006
 */
package ae1core;

import java.util.function.Function;

import ru.myx.ae1.know.Server;
import ru.myx.ae3.base.BaseNativeObject;
import ru.myx.ae3.base.BaseObject;
import ru.myx.ae3.produce.Produce;
import ru.myx.ae3.report.Report;

final class ServerInitializer implements Function<String, Server> {

	private final Object lock;

	private final BaseObject attributes;

	private final String type;

	private Server initialized = null;

	ServerInitializer(final Object lock, final BaseObject attributes, final String type) {

		this.lock = lock;
		this.attributes = attributes;
		this.type = type;
	}

	@Override
	public Server apply(final String name) {

		if (this.initialized != null) {
			return this.initialized;
		}
		synchronized (this.lock) {
			if (this.initialized == null) {
				final BaseObject attributesConverted = new BaseNativeObject();
				attributesConverted.baseDefineImportAllEnumerable(this.attributes);
				try {
					this.initialized = Produce.object(Server.class, this.type, attributesConverted, name);
					assert this.initialized != null : "NULL server produced, type=" + this.type + ", name=" + name;
				} catch (final Exception e) {
					Report.exception("AE1CORE/SRV_MAN/SRV_INIT", "Error initializing server, id=" + name, e);
					this.initialized = null;
				}
			}
			return this.initialized;
		}
	}
}
