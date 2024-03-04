@echo off
set SOURCE=%~dp0
set DIR=%SOURCE%

java --module-path "%DIR%modules\explicit";"%DIR%modules\automatic" --class-path "%DIR%modules\unnamed\*" --add-modules org.glassfish.tyrus.container.grizzly.server --module io.mokamint.application.cli/io.mokamint.application.cli.MokamintApplication %*
