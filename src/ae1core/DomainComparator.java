/**
 *
 */
package ae1core;

import java.util.Comparator;
import java.util.Map;

final class DomainComparator implements Comparator<Map.Entry<String, ?>> {

	@Override
	public final int compare(final Map.Entry<String, ?> m1, final Map.Entry<String, ?> m2) {

		final int l1 = m1.getKey().length();
		final int l2 = m2.getKey().length();
		return l1 > l2
			? 1
			: l1 == l2
				? 0
				: -1;
	}
}
