#!/usr/bin/env sh
cd "$(dirname "$0")"
java -jar OdontoMatch.jar "${1:-8080}"
