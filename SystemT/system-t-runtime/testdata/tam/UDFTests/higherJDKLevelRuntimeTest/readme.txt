udfModule.tam contains the following:

a) plan.aog compiled with SystemT v2.1, at JDK 6.0 level
b) 00000.jar (udf.jar) compiled at JDK 7.0 level

This test is to verify if SystemT runtime at JDK 6.0 level can flag an appropriate error when a UDF jar compiled at higher levels of JDK is loaded.