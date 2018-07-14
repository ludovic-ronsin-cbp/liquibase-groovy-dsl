Changes for 2.0.0
=================
- This DSL no longer has a transitive dependency on Liquibase itself.  **This is
  a breaking change!**  It is now up to you make sure Liquibase is on the 
  classpath, but you can now use whatever version of Liquibase you need. This
  resolves Issue 32.
  
Changes for 1.2.2
=================
- Added support for property tokens in `changeSet`, `include`, and `includeAll`
  attributes (Issue #26)
  
- Fixed a problem with file based attributes of the `column` method (Issue #22)
  with thanks to Viachaslau Tratsiak (@restorer)  
  
- Rollback changes that need access to resources, like `sqlFile` can find them
  (Issue #24)
 
- Explicitly set a value for the `output` change's `target` attribute if no
  value was given.  This is a workaround for a Liquibase bug. (Issue #28)
 
- Changed the way included files are found so that classpath resources can be
  used in Spring Boot applications (Issue #13)
   
Changes for 1.2.1
=================
- Fixed some issues with custom changes (Issue #5 and Issue #8) with thanks to 
  Don Valentino

Changes for 1.2.0
=================
- Updated the DSL to support most of Liquibase 3.4.2 (Issues 4 and 6 from the 
  Gradle plugin repository)

Changes for 1.1.1
=================
- Updated the DSL to support Liquibase 3.3.5 (Issue 29 from the old repository)

- Fixed a `createProcedure` bug and added support for `dropProcedure` with 
  thanks to Carlos Hernandez (Issue #3)

Changes for 1.1.0
=================
- Refactored the project to fit into the Liquibase organization.

Changes for 1.0.2
=================
- Recompiled with `sourceCompatibility` and `targetCompatibility` set so that
  the DSL works with older versions of Java (< JDK8)

Changes for 1.0.1
=================
- Updated the DSL to support Liquibase 3.3.2 (Issue #45 from the old repo)

- Updated the Groovy dependency to 2.4.1. 
