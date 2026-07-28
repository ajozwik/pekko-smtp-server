#!/bin/bash

sbt +clean +test +publishLocalSigned +publishSigned +sonaUpload +sonaRelease
