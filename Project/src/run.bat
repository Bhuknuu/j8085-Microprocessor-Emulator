@echo off

java -d out -verbose *.java
echo DONE COMPILING, RUN?
pause>nul
java MAIN.java