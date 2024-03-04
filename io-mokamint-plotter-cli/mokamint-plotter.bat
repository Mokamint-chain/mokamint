@echo off
set SOURCE=%~dp0
set DIR=%SOURCE%

java --module-path "%DIR%modules\explicit";"%DIR%modules\automatic" --class-path "%DIR%modules\unnamed\*" --module io.mokamint.plotter.cli/io.mokamint.plotter.cli.MokamintPlotter %*
