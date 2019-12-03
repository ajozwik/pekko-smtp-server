#!/bin/bash

PATH=$HOME/bin:$PATH sbt -Dscala.version=2.13.1 clean test publishLocal publishSigned sonatypeRelease
