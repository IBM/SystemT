
ECHO OFF

SET ORIGCLASSPATH=%CLASSPATH%
SET CLASSPATH=%CLASSPATH%;./bin
SET CLASSPATH=%CLASSPATH%;./lib/commons-codec-1.4/commons-codec-1.4.jar
SET CLASSPATH=%CLASSPATH%;./lib/uima-2.2.2-fp1/uima-core.jar
SET CLASSPATH=%CLASSPATH%;./lib/htmlparser-2.0-ibm/enc-htmlparser.jar
SET CLASSPATH=%CLASSPATH%;./lib/SimpleRegex/systemT_regex.jar
SET CLASSPATH=%CLASSPATH%;./lib/multilingual/an_dlt.jar
SET CLASSPATH=%CLASSPATH%;./lib/multilingual/antlr-2.7.5.jar
SET CLASSPATH=%CLASSPATH%;./lib/multilingual/dlt.jar
SET CLASSPATH=%CLASSPATH%;./lib/multilingual/icu4j-51_2.jar
SET CLASSPATH=%CLASSPATH%;./lib/multilingual/rule_dlt.jar


REM SET JAVA_HOME=C:\Program Files\IBM\Java50\jre
SET JAVA_HOME=C:\Program Files\Java\jdk1.6.0_18\
REM SET JAVA_HOME=C:\Program Files\IBM\Java60\jre

SET RUNJAVA=%JAVA_HOME%/bin/java

REM SET CLASSTORUN=com.ibm.avatar.algebra.test.stable.MemoryTests
SET CLASSTORUN=com.ibm.avatar.algebra.test.stable.SpeedTests
REM SET CLASSTORUN=com.ibm.avatar.algebra.test.uima.MultilingualTests

SET HEAPSZ=-Xmx1G
REM SET HEAPSZ=-Xmx256M

SET HPROF=-Xrunhprof:cpu=samples,depth=6 %CLASSTORUN%
REM SET HPROF=-Xrunhprof:heap=sites,depth=6,file=eda.hprof.txt
ECHO ON

"%RUNJAVA%" %HEAPSZ% %HPROF% %CLASSTORUN%

ECHO OFF

SET CLASSPATH=%ORIGCLASSPATH%

