package org.liquibase.groovy.delegate

import liquibase.changelog.IncludeAllFilter

/**
 * Created with IntelliJ IDEA.
 * User: steve
 * Date: 6/23/18
 * Time: 6:53 PM
 * To change this template use File | Settings | File Templates.
 *
 * @author Steven C. Saliman
 */
class GroovyOnlyResourceFilter implements IncludeAllFilter {
	def userFilter

	@Override
	boolean include(String changeLogPath) {
		// If this isn't a groovy file, exclude it.
		if ( !changeLogPath.endsWith(".groovy") ) {
			return false
		}
		// if it is a groovy file and the user hasn't defined a filter, return true
		if ( userFilter == null ) {
			return true
		}
		// if it is a groovy file, and the user has a filter, defer to the filter.
		return userFilter.include(changeLogPath)
	}
}
