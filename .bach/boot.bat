REM Call this batch from the base directory to bootstrap Bach and boot into it a JShell session
rmdir /Q/S .bach\workspace
del  .bach\cache\*.jar
java .bach\build\build\Bootstrap.java
cls
jshell boot