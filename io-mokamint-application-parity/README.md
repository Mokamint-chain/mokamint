<p align="center"><img width="320" src="../pics/mokamint_logo.png" alt="Mokamint logo"></p>

[![Java-Build Action Status](https://github.com/Mokamint-chain/mokamint/actions/workflows/java_build.yml/badge.svg)](https://github.com/Mokamint-chain/mokamint/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.mokamint/io-mokamint-application-parity.svg?label=Maven%20Central)](https://central.sonatype.com/search?smo=true&q=g:io.mokamint/io-mokamint-application-parity)
[![Docker Hub](https://img.shields.io/docker/pulls/mokamint/mokamint.svg?label=Docker%20Hub%20Pulls)](https://hub.docker.com/u/mokamint)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

# Parity

A simple parity application running on top of the Mokamint proof of space engine

## Introduction

This parity application for Mokamint is a minimal example about the definition of an application
running on top of the Mokamint proof of space engine. It implements an application with only two states: 0 and 1.
They represents the parity of an integer value _v_: 0 means even and 1 means odd.
The value of _v_ is assumed to be initially 0 when the blockchain starts.
Transactions are additions of an integer constant to _v_. For instance, if the parity of _v_ is currently 0
(ie., _v_ is even) and a request arrives to the blockchain, to add 13 to _v_, then the parity of _v_ becomes 1, because
0 (even) plus 13 (which is odd) yields an odd value. If later, a request arrives to add 6 to _v_, then the parity
of _v_ remains 1 (odd), because 1 (odd) plus 6 (which is even) yields an odd value. If, finally, a request
arrives to add 13 to _v_ again, then the parity of _v_ becomes 0 again, because 1 (odd) plus 13 (which is odd)
yields an even value. Note that this application is normally meant to be run over a blockchain that allows
repeated requests (such as adding 13 twice, as in this example).

Below, instructions are reported about the creation of a blockchain of two nodes and the execution of
transactions on that blockchain. The docker tool is used, so that experiments can be more easily reproduced,
without having to install Mokamint.

## Start a brand new blockchain, by spawning its first node

### Configure the node: `config-new`

### Initialize the node: `init`

### Run the node: `go`

## Join an existing blockchain, by spawning a new node that clones an existing node

### Configure the node: `config-clone`

### Run the node: `go`

## Further information

&nbsp;

<p align="center"><img width="100" src="../pics/CC_license.png" alt="This documentation is licensed under a Creative Commons Attribution 4.0 International License"></p><p align="center">This document is licensed under a Creative Commons Attribution 4.0 International License.</p>

<p align="center">Copyright 2026 by Fausto Spoto (fausto.spoto@mokamint.io).</p>