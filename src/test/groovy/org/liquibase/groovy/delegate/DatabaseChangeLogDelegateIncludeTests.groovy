/*
 * Copyright 2011-2018 Tim Berglund and Steven C. Saliman
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.liquibase.groovy.delegate

import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.parser.ChangeLogParserFactory
import liquibase.parser.ext.GroovyLiquibaseChangeLogParser
import liquibase.precondition.core.DBMSPrecondition
import liquibase.precondition.core.PreconditionContainer
import liquibase.precondition.core.RunningAsPrecondition
import liquibase.resource.FileSystemResourceAccessor
import org.junit.After
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 * One of three test classes for the {@link DatabaseChangeLogDelegate}.  The
 * number of tests for {@link DatabaseChangeLogDelegate} were getting unwieldy,
 * so they were split up.  this class deals with all the "include" element of
 * a database changelog.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegateIncludeTests {
	// Let's define some paths and directories.  These should all be relative.
	static final ROOT_CHANGELOG_PATH = "src/test/changelog"
	static final TMP_CHANGELOG_PATH = ROOT_CHANGELOG_PATH + "/tmp"
	static final INCLUDED_CHANGELOG_PATH = TMP_CHANGELOG_PATH + "/include"
	static final TMP_CHANGELOG_DIR = new File(TMP_CHANGELOG_PATH)
	static final INCLUDED_CHANGELOG_DIR = new File(INCLUDED_CHANGELOG_PATH)

	def resourceAccessor
	ChangeLogParserFactory parserFactory


	@Before
	void registerParser() {
		resourceAccessor = new FileSystemResourceAccessor()
		parserFactory = ChangeLogParserFactory.instance
		ChangeLogParserFactory.getInstance().register(new GroovyLiquibaseChangeLogParser())
		// make sure we start with clean temporary directories before each test
		TMP_CHANGELOG_DIR.deleteDir()
		INCLUDED_CHANGELOG_DIR.mkdirs()
	}

	/**
	 * Attempt to clean up included files and directories.  We do this every time
	 * to make sure we start clean each time.  The includeAll test depends on it.
	 */
	@After
	void cleanUp() {
		TMP_CHANGELOG_DIR.deleteDir()
	}

	/**
	 * Test including a file when we have an unsupported attribute.
	 */
	@Test(expected = ChangeLogParseException)
	void includeInvalidAttribute() {
		buildChangeLog {
			include(changeFile: 'invalid')
		}
	}

	/**
	 * Try including a file that references an invalid changelog property
	 * in the the name.  In this case, the fileName property is not set, so it
	 * can't be expanded and the parser will look for a file named
	 * '${fileName}.groovy', which of course doesn't exist.
	 */
	@Test(expected = ChangeLogParseException)
	void includeWithInvalidProperty() {
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: '\${fileName}.groovy')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")
		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)
	}

	/**
	 * Try including a file that has a database changelog property in the name.
	 * This proves that we can expand tokens in filenames.  We don't validate
	 * any filenames here, just that we can get the right change sets.
	 */
	@Test
	void includeWithValidProperty() {
		def includedChangeLogFile = createFileFrom(INCLUDED_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: 'included-change-set') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

		includedChangeLogFile = includedChangeLogFile.path
		includedChangeLogFile = includedChangeLogFile.replaceAll("\\\\", "/")
		// Let's strip off the extension so the include's file includes a
		// property but is not just a property.
		def len = includedChangeLogFile.length()
		def baseName = includedChangeLogFile.substring(0, len-7)

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  property(name: 'fileName', value: '${baseName}')
  include(file: '\${fileName}.groovy')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		// Let's be whimsical here - use the absolute file for the root
		// changelog to prove we can do it.
		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()
		assertEquals 'included-change-set', changeSets[0].id
		assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

		verifyIncludedPreconditions(rootChangeLog.preconditionContainer?.nestedPreconditions)
	}

	/**
	 * Try including a file with an absolute filename.  This test will also
	 * make sure the contexts are handle properly.
	 */
	@Test
	void includeAbsoluteFile() {
		def includedChangeLogFile = createFileFrom(INCLUDED_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: 'included-change-set') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")
		// The cannonicalPath will be absolute...
		includedChangeLogFile = includedChangeLogFile.canonicalPath
		includedChangeLogFile = includedChangeLogFile.replaceAll("\\\\", "/")

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: '${includedChangeLogFile}', context: 'myContext')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()
		assertEquals 'included-change-set', changeSets[0].id
		assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

		// Let's make sure we started with an absolute filename, then check
		// that the path of the included change set is absolute.  The change
		// that came from the root changelog should still be relative.
		assertAbsolutePath includedChangeLogFile
		assertAbsolutePath changeSets[0].filePath
		assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)

		// Take a look at the contexts.  The change that came in with the
		// include should have one, the change in the root changelog should not.
		assertEquals 'myContext', changeSets[0].changeLog.includeContexts.toString()
		assertNull changeSets[1].changeLog.includeContexts

		verifyIncludedPreconditions(rootChangeLog.preconditionContainer?.nestedPreconditions)
	}

	/**
	 * Try including a file with an filename that is relative to the working
	 * directory.
	 */
	@Test
	void includeRelativeToWorkDir() {
		def includedChangeLogFile = createFileFrom(INCLUDED_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: 'included-change-set') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")
		includedChangeLogFile = includedChangeLogFile.path // should be relative.
		includedChangeLogFile = includedChangeLogFile.replaceAll("\\\\", "/")

		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: '${includedChangeLogFile}', context: 'myContext')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()
		assertEquals 'included-change-set', changeSets[0].id
		assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

		// Make sure the file we were including was indeed a relative path.
		assertTrue includedChangeLogFile.startsWith(TMP_CHANGELOG_PATH)
		// Check that the paths of the included change set is relative. The 2nd
		// change set did not come from the "include", but it will be relative
		// as well..
		assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)

		// Take a look at the contexts.  The change that came in with the
		// include should have one, the change in the root changelog should not.
		assertEquals 'myContext', changeSets[0].changeLog.includeContexts.toString()
		assertNull changeSets[1].changeLog.includeContexts

		verifyIncludedPreconditions(rootChangeLog.preconditionContainer?.nestedPreconditions)
	}

	/**
	 * Try including a file relative to the changelolg file when the root
	 * changelog is absolute.  The main thing here is to make sure the included
	 * change sets keep relative paths.
	 * <p>
	 * At the moment, if you start with an absolute changelog, all the included
	 * changes will have absolute paths, even when they are supposed to be
	 * relative to the changelog.  This is an internal liquibase issue.  If
	 * liquibase ever changes how it does things, this test may need to change.
	 */
	@Test
	void includeRelativeToAbsoluteChangeLog() {
		def includedChangeLogFile = createFileFrom(INCLUDED_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: 'included-change-set') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

		includedChangeLogFile = includedChangeLogFile.name
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: 'include/${includedChangeLogFile}', relativeToChangelogFile: true, context: 'myContext')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.absolutePath, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.absolutePath, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()
		assertEquals 'included-change-set', changeSets[0].id
		assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

		// Take a look at the contexts.  The change that came in with the
		// include should have one, the change in the root changelog should not.
		assertEquals 'myContext', changeSets[0].changeLog.includeContexts.toString()
		assertNull changeSets[1].changeLog.includeContexts

		// Check that the paths of the included change set is relative. The 2nd
		// change set did not come from the "include", so it will be absolute.
		assertAbsolutePath changeSets[0].filePath
		assertAbsolutePath changeSets[1].filePath

		verifyIncludedPreconditions(rootChangeLog.preconditionContainer?.nestedPreconditions)
	}

	/**
	 * Try including a file relative to the changelolg file when the root
	 * changelog is also relative.
	 */
	@Test
	void includeRelativeToRelativeChangeLog() {
		def includedChangeLogFile = createFileFrom(INCLUDED_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: 'included-change-set') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

		includedChangeLogFile = includedChangeLogFile.name
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: 'include/${includedChangeLogFile}', relativeToChangelogFile: true, context: 'myContext')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()
		assertEquals 'included-change-set', changeSets[0].id
		assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

		// Take a look at the contexts.  The change that came in with the
		// include should have one, the change in the root changelog should not.
		assertEquals 'myContext', changeSets[0].changeLog.includeContexts.toString()
		assertNull changeSets[1].changeLog.includeContexts

		// Check that the paths of the included change set is relative. The 2nd
		// change set did not come from the "include", so it will be relative
		// as well..
		assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)

		verifyIncludedPreconditions(rootChangeLog.preconditionContainer?.nestedPreconditions)

	}

	/**
	 * Try including a file relative to the changelolg file when the root
	 * changelog is also relative, but the included file is not in the same
	 * directory (or subdirectory) as the root changelog.  The main thing here
	 * is to make sure paths like "../someDir" work.
	 */
	@Test
	void includeRelativeToRelativeChangeLogParent() {
		def includedChangeLogFile = createFileFrom(INCLUDED_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    runningAs(username: 'ssaliman')
  }

  changeSet(author: 'ssaliman', id: 'included-change-set') {
    renameTable(oldTableName: 'prosaic_table_name', newTableName: 'monkey')
  }
}
""")

		includedChangeLogFile = includedChangeLogFile.name
		def rootChangeLogFile = createFileFrom(TMP_CHANGELOG_DIR, '.groovy', """
databaseChangeLog {
  preConditions {
    dbms(type: 'mysql')
  }
  include(file: '../tmp/include/${includedChangeLogFile}', relativeToChangelogFile: true, context: 'myContext')
  changeSet(author: 'ssaliman', id: 'ROOT_CHANGE_SET') {
    addColumn(tableName: 'monkey') {
      column(name: 'emotion', type: 'varchar(50)')
    }
  }
}
""")

		def parser = parserFactory.getParser(rootChangeLogFile.path, resourceAccessor)
		def rootChangeLog = parser.parse(rootChangeLogFile.path, new ChangeLogParameters(), resourceAccessor)

		assertNotNull rootChangeLog
		def changeSets = rootChangeLog.changeSets
		assertNotNull changeSets
		assertEquals 2, changeSets.size()
		assertEquals 'included-change-set', changeSets[0].id
		assertEquals 'ROOT_CHANGE_SET', changeSets[1].id

		// Take a look at the contexts.  The change that came in with the
		// include should have one, the change in the root changelog should not.
		assertEquals 'myContext', changeSets[0].changeLog.includeContexts.toString()
		assertNull changeSets[1].changeLog.includeContexts

		// Check that the paths of the included change set is relative. The 2nd
		// change set did not come from the "include", so it will be relative
		// as well..
		assertTrue changeSets[0].filePath.startsWith(INCLUDED_CHANGELOG_PATH)
		assertTrue changeSets[1].filePath.startsWith(TMP_CHANGELOG_PATH)

		verifyIncludedPreconditions(rootChangeLog.preconditionContainer?.nestedPreconditions)

	}

	/**
	 * Helper method that builds a changeSet from the given closure.  Tests will
	 * use this to test parsing the various closures that make up the Groovy DSL.
	 * @param closure the closure containing changes to parse.
	 * @return the changeSet, with parsed changes from the closure added.
	 */
	private def buildChangeLog(Closure closure) {
		def changelog = new DatabaseChangeLog(ROOT_CHANGELOG_PATH)
		changelog.changeLogParameters = new ChangeLogParameters()
		closure.delegate = new DatabaseChangeLogDelegate(changelog)
		closure.delegate.resourceAccessor = resourceAccessor
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		closure.call()
		return changelog
	}

	private File createFileFrom(directory, suffix, text) {
		createFileFrom(directory, 'liquibase-', suffix, text)
	}

	private File createFileFrom(directory, prefix, suffix, text) {
		def file = File.createTempFile(prefix, suffix, directory)
		file << text
	}

	/**
	 * Helper method to verify the preconditions of our included changeset.
	 * This works because all of our tests ultimately include the same changelog.
	 * We expect to have 2 preconditions, and the second one should be in a
	 * container that wraps the actual precondition.
	 * @param preconditions the preconditions to check
	 */
	private def verifyIncludedPreconditions(preconditions) {
		assertNotNull preconditions
		assertEquals 2, preconditions.size()
		assertTrue preconditions[0] instanceof DBMSPrecondition
		assertTrue preconditions[1] instanceof PreconditionContainer
		preconditions = preconditions[1].nestedPreconditions
		assertTrue preconditions[0] instanceof RunningAsPrecondition
	}

	/**
	 * Helper method to determine if a given path represents an absolute path.
	 * If the given path is not an absolute path for the platform, this method
	 * will fail the test.
	 * @param path the path to check
	 */
	private def assertAbsolutePath(path) {
		if ( !new File(path).isAbsolute() ) {
			fail "'${path}' is not an absolute path"
		}

	}
}

