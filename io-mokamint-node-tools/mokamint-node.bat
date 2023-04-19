@echo off
set SOURCE=%~dp0
set DIR=%SOURCE%

java --add-modules com.google.gson --module-path "%DIR%modules\explicit";"%DIR%modules\automatic" --class-path "%DIR%modules\unnamed\*" --module io.mokamint.node.tools/io.mokamint.node.tools.MokamintNode %*
