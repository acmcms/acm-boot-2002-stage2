/*
 * Created on 07.04.2006
 */
package ae1core;

import java.util.function.Function;

import ru.myx.ae1.handle.Handle;
import ru.myx.ae3.report.Report;

final class DelayedServerStarter implements Function<String, Object> {

	@Override
	public Object apply(final String id) {

		Report.event("AE1-CORE", "STARTING", "Starting: server=" + id + "...");
		return Handle.getServer(id);
	}

	@Override
	public String toString() {

		return "AUTOSTART-SERVER";
	}
}
