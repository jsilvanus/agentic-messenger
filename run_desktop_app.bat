@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "Path=%JAVA_HOME%\bin;%Path%"
cd /d "%~dp0"
call gradlew.bat :amp-ui:run
