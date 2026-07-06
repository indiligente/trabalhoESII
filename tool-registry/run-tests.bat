@echo off
setlocal

echo ================================================
echo  Tool Registry - Executando Testes
echo ================================================
echo.

REM Tenta encontrar JAVA_HOME se nao estiver definido
if not defined JAVA_HOME (
    for /d %%i in ("C:\Program Files\Java\jdk-21*") do set JAVA_HOME=%%i
)

if not defined JAVA_HOME (
    echo [ERRO] JAVA_HOME nao encontrado.
    echo Defina a variavel de ambiente JAVA_HOME apontando para o JDK 21.
    echo Exemplo: set JAVA_HOME=C:\Program Files\Java\jdk-21.0.10
    exit /b 1
)

echo Java encontrado em: %JAVA_HOME%
echo.

REM Processa argumento opcional
set FILTER=
if not "%~1"=="" (
    set FILTER=-Dtest=%~1
    echo Rodando apenas: %~1
    echo.
)

REM Muda para o diretorio do projeto antes de executar
cd /d "%~dp0"

REM Executa os testes
call "%~dp0mvnw.cmd" test %FILTER% -Dspring.profiles.active=test

echo.
if %ERRORLEVEL%==0 (
    echo ================================================
    echo  RESULTADO: TODOS OS TESTES PASSARAM
    echo ================================================
) else (
    echo ================================================
    echo  RESULTADO: FALHA NOS TESTES
    echo ================================================
)

exit /b %ERRORLEVEL%