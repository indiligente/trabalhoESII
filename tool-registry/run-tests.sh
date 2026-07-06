#!/usr/bin/env bash
set -e

echo "================================================"
echo " Tool Registry - Executando Testes"
echo "================================================"
echo

# Verifica Java
if ! command -v java &>/dev/null; then
    echo "[ERRO] Java não encontrado no PATH."
    echo "Instale o JDK 21 e adicione ao PATH."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1)
echo "Java encontrado: $JAVA_VERSION"
echo

# Argumento opcional: nome da classe de teste
FILTER=""
if [ -n "$1" ]; then
    FILTER="-Dtest=$1"
    echo "Rodando apenas: $1"
    echo
fi

# Executa os testes
./mvnw test $FILTER -Dspring.profiles.active=test

echo
echo "================================================"
echo " RESULTADO: TODOS OS TESTES PASSARAM"
echo "================================================"
