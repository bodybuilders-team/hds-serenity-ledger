#!/usr/bin/env bash

mvn install:install-file -Dfile=dependencies/gson-2.10.1.jar -DgroupId="com.google.code.gson" -DartifactId="gson" -Dversion="2.10.1" -Dpackaging="jar"