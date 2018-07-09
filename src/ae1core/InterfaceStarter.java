/*
 * Created on 07.02.2005
 *
 */
package ae1core;

import java.util.Iterator;
import java.util.function.Function;

import ru.myx.ae3.base.Base;
import ru.myx.ae3.base.BaseArray;
import ru.myx.ae3.base.BaseMap;
import ru.myx.ae3.base.BaseNativeObject;
import ru.myx.ae3.base.BaseObject;
import ru.myx.ae3.flow.ObjectTarget;
import ru.myx.ae3.help.Convert;
import ru.myx.ae3.produce.Produce;
import ru.myx.ae3.report.Report;

final class InterfaceStarter implements Function<BaseObject, Object> {

	@Override
	public final Object apply(final BaseObject attributes) {

		final String name = Base.getString(attributes, "name", "").trim();
		if (name.length() == 0) {
			return null;
		}
		Report.event("AE1-CORE", "STARTING", "Launching interface: " + name + "...");
		final BaseMap sourceAttributesConverted = new BaseNativeObject();
		{
			final BaseObject sourceAttributes = attributes.baseGet("source", BaseObject.UNDEFINED);
			assert sourceAttributes != null : "NULL java value";
			if (sourceAttributes == BaseObject.UNDEFINED) {
				Report.error("AE1-CORE", "[" + name + "] Interface launch error: no source defined!");
				return null;
			}
			for (final Iterator<String> iterator = Base.keys(sourceAttributes); iterator.hasNext();) {
				final String key = iterator.next();
				sourceAttributesConverted.baseDefine(key, sourceAttributes.baseGet(key, BaseObject.UNDEFINED));
			}
		}
		final String sourceFactory = Base.getString(sourceAttributesConverted, "factory", "").trim();
		if (sourceFactory.length() == 0) {
			Report.error("AE1-CORE", "[" + name + "] Interface launch error: no source factory specified!");
			return null;
		}
		final BaseObject targetAttributesConverted = new BaseNativeObject();
		{
			final BaseObject targetAttributes = attributes.baseGet("target", BaseObject.UNDEFINED);
			assert targetAttributes != null : "NULL java value";
			if (targetAttributes == BaseObject.UNDEFINED) {
				Report.error("AE1-CORE", "[" + name + "] Interface launch error: no target defined!");
				return null;
			}
			for (final Iterator<String> iterator = Base.keys(targetAttributes); iterator.hasNext();) {
				final String key = iterator.next();
				targetAttributesConverted.baseDefine(key, targetAttributes.baseGet(key, BaseObject.UNDEFINED));
			}
		}
		final String targetFactory = Base.getString(targetAttributesConverted, "factory", "").trim();
		if (targetFactory.length() == 0) {
			Report.error("AE1-CORE", "[" + name + "] Interface launch error: no target factory specified!");
			return null;
		}
		final BaseArray filters = Convert.MapEntry.toCollection(attributes, "filter", null);
		try {
			final ObjectTarget<?> target = Produce.object(
					ObjectTarget.class, //
					targetFactory,
					targetAttributesConverted,
					null);
			if (target == null) {
				Report.error("AE1-CORE", "[" + name + "] Interface launch error: no source factory [" + targetFactory + "] available!");
				return null;
			}
			Report.event("AE1-CORE", "STARTING", "[" + name + "] target: " + target);
			final BaseObject[] filterArray;
			if (filters == null) {
				filterArray = new BaseObject[0];
			} else {
				filterArray = new BaseObject[filters.length()];
				for (int i = filterArray.length - 1; i >= 0; --i) {
					filterArray[i] = filters.baseGet(i, BaseObject.UNDEFINED);
				}
			}
			Produce.connectLeast(
					name, //
					target,
					filterArray,
					sourceFactory,
					sourceAttributesConverted);
		} catch (final Exception e) {
			Report.exception("AE1-CORE", "[" + name + "] Interface launch error!", e);
		}
		return null;
	}
}
