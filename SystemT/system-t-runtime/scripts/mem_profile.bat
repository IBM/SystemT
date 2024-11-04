SET ORIGCLASSPATH=%CLASSPATH%

ECHO OFF

SET CLASSPATH=%CLASSPATH%;./bin
SET CLASSPATH=%CLASSPATH%;./lib/automaton.jar


SET CLASSPATH=%CLASSPATH%;./lib/avatar/aomIngester.jar
SET CLASSPATH=%CLASSPATH%;./lib/avatar/avatarBase.jar
SET CLASSPATH=%CLASSPATH%;./lib/avatar/avatarUtils.jar

SET CLASSPATH=%CLASSPATH%;../resources/libs/junit-4.4/junit-4.1.jar

SET CLASSPATH=%CLASSPATH%;../resources/libs/cloudscape-10.1/derbytools.jar
SET CLASSPATH=%CLASSPATH%;../resources/libs/cloudscape-10.1/derby.jar
SET CLASSPATH=%CLASSPATH%;../resources/libs/cloudscape-10.1/derbynet.jar
SET CLASSPATH=%CLASSPATH%;../resources/libs/cloudscape-10.1/derbytools.jar

SET CLASSPATH=%CLASSPATH%;../resources/libs/multilingual/dlt.jar
SET CLASSPATH=%CLASSPATH%;../resources/libs/multilingual/icu4j-51_2.jar
SET CLASSPATH=%CLASSPATH%;../resources/libs/multilingual/tagger_dlt.jar

SET JAVA_HOME=C:\Program Files\IBM\Java50\jre
REM SET JAVA_HOME=C:\Program Files\IBM\Java60\jre
REM SET JAVA_HOME=C:\Program Files\Java\jdk1.6.0_13

SET RUNJAVA=%JAVA_HOME%/bin/java

SET CLASSTORUN=com.ibm.avatar.algebra.test.stable.MemoryTests

REM "%RUNJAVA%" %CLASSTORUN%
REM "%RUNJAVA%" -Xrunjavaperf %CLASSTORUN%
REM "%RUNJAVA%" -Xrunhprof:heap=sites %CLASSTORUN%
REM "%RUNJAVA%" -Xrunhprof:cpu=samples,depth=4 %CLASSTORUN%
ECHO ON

"%RUNJAVA%" -Xrunhprof:cpu=samples,depth=4 %CLASSTORUN%

ECHO OFF

SET CLASSPATH=%ORIGCLASSPATH%
