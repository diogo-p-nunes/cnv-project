#!/bin/bash

# Compile the whole project

source java-config.sh
javac -proc:none ./*.java
javac -proc:none database/*.java
javac -proc:none manager/*.java
java manager.Manager