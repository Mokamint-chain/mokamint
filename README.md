<p align="center"><img width="320" src="pics/mokamint_logo.png" alt="Mokamint logo"></p>

[![Java-Build Action Status](https://github.com/Mokamint-chain/mokamint/actions/workflows/java_build.yml/badge.svg)](https://github.com/Mokamint-chain/mokamint/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.mokamint/io-mokamint-node.svg?label=Maven%20Central)](https://central.sonatype.com/search?smo=true&q=g:io.mokamint)
[![Docker Hub](https://img.shields.io/docker/pulls/mokamint/mokamint.svg?label=Docker%20Hub%20Pulls)](https://hub.docker.com/u/mokamint)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

# Mokamint: a generic blockchain engine based on proof-of-space.

Mokamint allows the creation of new blockchains with consensus based on proof of space: the more space
is allocated for mining, the more likely it is to mint new blocks. Mokamint implements the networking
and consensus layers of the blockchains only. The application layer is built over Mokamint, as a separate
application, connected through an abstract API.

## Mokamint applications

There are a few applications that have been built over Mokamint. They are actual blockchains
that implement different applications:

<ul>
<li>Parity: an application that keeps the parity of an integer value. It is meant to
	be a minimal example to clarify how Mokamint applications can be built.
	Its code, documentation and examples of use
	are inside <a href="https://github.com/Mokamint-chain/mokamint/tree/main/io-mokamint-application-parity">its module</a>.</li>
<li>Bitcoin: an application that implements the kernel of Bitcoin: a ledger of keys bound to balances.
	Keys earn coins by mining or by receiving coins from another key.
	Its code, documentation and examples of use
	are inside <a href="https://github.com/Mokamint-chain/mokamint/tree/main/io-mokamint-application-bitcoin">its module</a>.</li>
<li>Hotmoka: an application that implements Solidity-like smart contracts written in a subset of Java.
    An actual Hotmoka mainnet exists, as well as a testnet. See the <a href="https://www.hotmoka.io">project's web page</a>
    for information on how to install new nodes or mine for an existing network.
    The <a href="https://github.com/Hotmoka/hotmoka">GitHub page of the project</a> include
    <a href="https://github.com/Hotmoka/hotmoka/releases">a large tutorial</a> on the development of smart contracts with Hotmoka.</li>
</ul>

## Further information

The theory underlying the proof of space consensus of Mokamint has been published
at the <a href="https://link.springer.com/chapter/10.1007/978-3-032-00492-5_13">2025 WTSC Workshop</a>.

&nbsp;

<p align="center"><img width="100" src="pics/CC_license.png" alt="This documentation is licensed under a Creative Commons Attribution 4.0 International License"></p><p align="center">This document is licensed under a Creative Commons Attribution 4.0 International License.</p>

<p align="center">Copyright 2026 by Fausto Spoto (fausto.spoto@mokamint.io).</p>