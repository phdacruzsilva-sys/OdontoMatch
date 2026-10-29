#!/usr/bin/env sh
set -e
cd "$(dirname "$0")"
rm -rf out build && mkdir out build
javac --release 17 -encoding UTF-8 -cp lib/hsqldb.jar -d out src/odontomatch/*.java
cp -r out/* build/
(cd build && jar xf ../lib/hsqldb.jar && rm -rf META-INF)
printf "Main-Class: odontomatch.App\n" > manifest.txt
jar cfm OdontoMatch.jar manifest.txt -C build .
rm -rf out build manifest.txt
echo "Pronto! OdontoMatch.jar atualizado."
