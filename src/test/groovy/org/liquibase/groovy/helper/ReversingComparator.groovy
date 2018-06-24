package org.liquibase.groovy.helper

/**
 * This class is an Resource Comparator that will result in changelogs being
 * returned in reverse order.  It is used to test the resourceComparator of
 * an includeAll change.
 *
 * @author Steven C. Saliman
 */
class ReversingComparator implements Comparator<String> {
	@Override
	int compare(String s1, String s2) {
		return s2.compareTo(s1) // That's right, s2 comes first.
	}
}
