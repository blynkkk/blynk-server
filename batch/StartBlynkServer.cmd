@echo off
echo **************************
echo Starting Blynk Server
echo Script created by
echo Waleed El-Badry
echo waleed.elbadry@must.edu.eg
echo **************************
echo Your working directory is %~dp0
echo **************************
set ip_address_string="IP Address"
rem Uncomment the following line when using Windows 7 or Windows 8 / 8.1 (with removing "rem")!
set ip_address_string="IPv4 Address"
echo **************************
for /f "usebackq tokens=2 delims=:" %%f in (`ipconfig ^| findstr /c:%ip_address_string%`) do (
    echo Local Server IP Address is: %%f
    echo Hardware Port : 8442
	echo Application Port : 8443
)
echo **************************

cd /D %~dp0
echo Available server files
dir *.jar
@echo off
for /f "delims=" %%x in ('dir /od /b server*.jar') do set latestjar=%%x
@echo on
echo Server latest version on folder is %latestjar%
java -jar %latestjar% -dataFolder /path
IF /I "%ERRORLEVEL%" NEQ "0" (
    ECHO Server failed to started
)