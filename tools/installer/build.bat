@echo off
rem Builds build\installer\ModernArmoryInstaller.exe with the C# compiler that ships with Windows (.NET Framework 4.x).
setlocal
set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe
set HERE=%~dp0
set OUT=%HERE%..\..\build\installer
if not exist "%OUT%" mkdir "%OUT%"
"%CSC%" /nologo /target:winexe /optimize+ /out:"%OUT%\ModernArmoryInstaller.exe" ^
  /reference:System.Web.Extensions.dll /reference:System.Windows.Forms.dll /reference:System.Drawing.dll ^
  "%HERE%ModernArmoryInstaller.cs"
if errorlevel 1 exit /b 1
echo Built %OUT%\ModernArmoryInstaller.exe
