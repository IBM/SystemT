ECHO OFF

SET ORIGCLASSPATH=%CLASSPATH%

SET CLASSPATH=%CLASSPATH%;./bin

SET CLASSPATH=%CLASSPATH%;../resources/libs/junit-4.4/junit-4.1.jar

SET CLASSPATH=%CLASSPATH%;./lib/htmlparser-2.0-ibm/enc-htmlparser.jar

SET CLASSPATH=%CLASSPATH%;./lib/multilingual/an_dlt.jar
SET CLASSPATH=%CLASSPATH%;./lib/multilingual/an_tagger_dlt.jar
SET CLASSPATH=%CLASSPATH%;./lib/multilingual/dlt.jar
SET CLASSPATH=%CLASSPATH%;./lib/multilingual/icu4j-51_2.jar
SET CLASSPATH=%CLASSPATH%;./lib/multilingual/tagger_dlt.jar

SET CLASSPATH=%CLASSPATH%;../resources/libs/uima-2.2.2-fp1/uima-core.jar

REM SET JAVA_HOME=C:\Program Files\IBM\Java50\jre
SET JAVA_HOME=C:\Program Files\IBM\Java60\jre
REM SET JAVA_HOME=C:\Program Files\Java\jdk1.6.0_12\

SET RUNJAVA=%JAVA_HOME%/bin/java

SET JAVAOPTS=-Xmx1G
REM SET JAVAOPTS=-Xmx70M
REM SET JAVAOPTS=-Xrunhprof:cpu=samples,depth=6 -Xmx1G

REM SET CLASSTORUN=com.ibm.avatar.algebra.test.stable.ToggleTests
SET CLASSTORUN=com.ibm.avatar.algebra.test.stable.SpeedTests
REM SET CLASSTORUN=com.ibm.avatar.algebra.test.stable.MemoryTests

REM "%RUNJAVA%" %JAVAOPTS% %CLASSTORUN%
REM "%RUNJAVA%" %JAVAOPTS% -Xrunjavaperf %CLASSTORUN%
REM "%RUNJAVA%" %JAVAOPTS% -Xrunhprof:heap=sites %CLASSTORUN%
REM "%RUNJAVA%" %JAVAOPTS% -Xrunhprof:cpu=samples,depth=4 %CLASSTORUN%
ECHO ON

"%RUNJAVA%" %JAVAOPTS% %CLASSTORUN%

ECHO OFF

SET CLASSPATH=%ORIGCLASSPATH%

