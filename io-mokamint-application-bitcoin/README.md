<p align="center"><img width="320" src="../pics/mokamint_logo.png" alt="Mokamint logo"></p>

[![Java-Build Action Status](https://github.com/Mokamint-chain/mokamint/actions/workflows/java_build.yml/badge.svg)](https://github.com/Mokamint-chain/mokamint/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.mokamint/io-mokamint-application-bitcoin.svg?label=Maven%20Central)](https://central.sonatype.com/search?smo=true&q=g:io.mokamint/io-mokamint-application-bitcoin)
[![Docker Hub](https://img.shields.io/docker/pulls/mokamint/mokamint.svg?label=Docker%20Hub%20Pulls)](https://hub.docker.com/u/mokamint)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

# Bitcoin

An application running on top of the Mokamint proof of space engine, functionally equivalent to Bitcoin.

## Introduction

This Bitcoin application for Mokamint is an example of definition of an application
running on top of the Mokamint proof of space engine. It implements an application functionally equivalent
to Bitcoin. Namely, the application keeps a map from each public key to a balance.
It implements only one kind of requests, for sending coins from an origin public key to
a destination public key. Requests must be signed by the sender, with its private key.
The amount of coins sent at each request must be positive and the sender must be rich
enough to send such coins. The application implements a decreasing rewarding schema:
the balance of the public key for the peer and the miner that contribute to the creation of a block
are increased by an amount of coins that becomes smaller and smaller with the time, until it reaches zero.

Below, instructions are reported about the creation of a blockchain of two nodes and the execution of
transactions on that blockchain. The docker tool is used, so that experiments can be more easily reproduced,
without having to install Mokamint.

## Start a brand new blockchain, by spawning its first node

This section shows how you can start the first node of a brand new blockchain from scratch, by minting its genesis block
and by initializing its database. Other nodes can join the new blockchain with the technique described later.

There is a docker image that provides scripts for starting a brand new blockchain.
These scripts include the creation of a local miner as well, or otherwise your node
would not be able to mine new blocks. Because of this, the scripts deal with two key pairs:
the former identifies the node, and is kept inside the machine running the script,
and the latter identifies the miner, it can be stored elsewhere and only its public key is needed here.

The process is consequently split in three steps:

* configure the node (`config-new`)
* initialize the node (`init`)
* run the node (`go`)

Each phase is the execution of a script inside the docker image. The scripts
`config-new` and `init` are meant to be run only once, while `go` can be run, stopped and run again,
whenever you want to start or stop the node. You can also pause it and unpause it. The reason for splitting
the process in three scripts is that that allows one to manually edit the configuration created
by `config-new` before initializing and running the node, although we won't show this here.
Moreover, having distinct scripts allows `go` to be stopped and run again, repeatedly,
whenever you want to stop and restart a node.

### Configure the node: `config-new`

The first thing to do is to create a key pair for the miner of the new node that you want to start.
You can do this by running the container and the `mokamint-node` command inside it:

```console
$ docker run -it --rm --name mokamint mokamint/mokamint:1.7.0 /bin/bash
mokamint@92d112f5a7c5:~$ mokamint-node keys create --name miner.pem --password
Enter value for --password (the password that will be needed later to use the key pair): 
The new key pair has been written into "miner.pem":
* public key: 3ExG53CrXsAsnrbxWgkBNNpME4F3YK7mho4R6RXqhoW2 (ed25519, base58)
* public key: IUpoJ0qP5IUWAcln9cSr1sGBEozALaWdazxC+uBT3Ys= (ed25519, base64)
* Tendermint-like address: F7DB9C42843560D0345C3583D8A16438FC8CFA00
```

Use the password that you like (or leave it blank). The command above
will create a key pair `miner.pem` inside the running container.
In another shell, you can transfer that key pair to your local machine:

```console
$ docker cp mokamint:/home/mokamint/miner.pem .
Successfully copied 2.05kB.
```
At this point, inside the container, give commands to
delete the key pair and exit the container:

```console
mokamint@afbef35bce14:~$ rm miner.pem
mokamint@afbef35bce14:~$ exit
$
```

You can configure the node now, specifying the public key of the miner.
We use two volumes: `chain` will contain the actual blockchain data and
`mokamint` will contain the configuration information created for the node.
By using volumes, we can share that information across successive invocations of docker:

```console
$ docker run -it --rm -v chain:/home/mokamint/chain -v mokamint:/home/mokamint/mokamint -e PUBLIC_KEY_MINER_BASE58=3ExG53CrXsAsnrbxWgkBNNpME4F3YK7mho4R6RXqhoW2 -e TARGET_BLOCK_CREATION_TIME=20000 -e PLOT_SIZE=1000 -e CHAIN_ID="panda" mokamint/mokamint:1.7.0 config-new
I will use the following parameters for the creation of the configuration directory of a proof of space Mokamint node:

                    CHAIN_ID="panda"
                   PLOT_SIZE=1000
     PUBLIC_KEY_MINER_BASE58="3ExG53CrXsAsnrbxWgkBNNpME4F3YK7mho4R6RXqhoW2"
  TARGET_BLOCK_CREATION_TIME=20000

Cleaning the directory mokamint... done
Cleaning the directory chain... done
Creating the node.pem key pair for signing the blocks: Enter value for --password (the password that will be needed later to use the key pair): 
done: the public key is 7M4tVKyYZXpPHoF58ZgBwyJs86iWBh9PrCAZm9BjS9dV (ed25519, base58)
Creating the Mokamint configuration file... done
Creating a plot file for the miner, containing 1000 nonces, for the chain id "panda", for a node with public key 7M4tVKyYZXpPHoF58ZgBwyJs86iWBh9PrCAZm9BjS9dV (ed25519, base58), for a miner with public key 3ExG53CrXsAsnrbxWgkBNNpME4F3YK7mho4R6RXqhoW2 (ed25519, base58)...
1% 2% 3% 4% 5% 6% 7% 8% 9% 10% 11% 12% 13% 14% 15% 16% 17% 18% 19% 20% 21% 22% 23% 24% 25% 26% 27% 28% 29% 30% 31% 32% 33% 34% 35% 36% 37% 38% 39% 40% 41% 42% 43% 44% 45% 46% 47% 48% 49% 50% 51% 52% 53% 54% 55% 56% 57% 58% 59% 60% 61% 62% 63% 64% 65% 66% 67% 68% 69% 70% 71% 72% 73% 74% 75% 76% 77% 78% 79% 80% 81% 82% 83% 84% 85% 86% 87% 88% 89% 90% 91% 92% 93% 94% 95% 96% 97% 98% 99% 100% 
done
```

Note that the public key of the miner is inserted in base58 format. The target block creation time, in milliseconds, is the average time between the creation of two successive blocks. The chain identifier identifies the new network.
The plot size is the space used by your node for mining:
the larger, the more blocks will be created by your node, but also more disk space will be allocated for mining.
Repeated requests will be allowed in this new blockchain, which is sensible since requests in the Bitcoin application are not distinguished by a progressive nonce.

The script above prompts for the password of the key pair used for signing the new blocks. Enter your chosen password here, possibly distinct from that chosen for the miner, or just leave it blank.
The key pair of the node will be kept inside the container. This key pair identifies the node and will be used to sign the blocks that the node will create. It must remain inside the container, although you may want to extract a copy from the container to your local host.

The script has configured the node and created a plot file for its miner.

### Initialize the node: `init`

The initialization of the node consists in the creation of the genesis block. You can do this with:

```console
$ docker run -it --rm -v chain:/home/mokamint/chain -v mokamint:/home/mokamint/mokamint -e APPLICATION=Bitcoin mokamint/mokamint:1.7.0 init
Initializing a node for a brand new blockchain, whose configuration has been created with config-new.

                 APPLICATION="Bitcoin"

Enter value for --password (the password of the key pair of the node): 
Loading mokamint/plot.plot... done.
The path "chain" already exists! Will restart the node from the current content of "chain".
If you want to start a blockchain from scratch, stop this process, delete "chain" and start again a node with --init.
Creating the Bitcoin application... done.
[... log messages follow ...]
```

When prompted, use the password that you have chosen above for the node.
Do not worry about the warning about the chain directory, this is perfectly fine.

### Run the node: `go`

After configuring the node, you can run it with the `go` script. It will require you to enter
the password chosen for the node:

```console
$ docker run -it --rm --name mokamint -e APPLICATION=Bitcoin -p 8025:8025 -p 8030:8030 -p 127.0.0.1:8031:8031 -p 8050:8050 -v mokamint:/home/mokamint/mokamint -v chain:/home/mokamint/chain mokamint/mokamint:1.7.0 go
Starting an already configured node of a blockchain, whose configuration has been created with config-clone or with config-new and then init.
   APPLICATION="Bitcoin"
    VISIBLE_AS=
Enter value for --password (the password of the key pair of the node): 
Loading mokamint/plot.plot... done.
The path "chain" already exists! Will restart the node from the current content of "chain".
If you want to start a blockchain from scratch, stop this process, delete "chain" and start again a node with --init.
Creating the Bitcoin application... done.
[... log messages follow ...]
```

The command above allows connections to the ports:

* 8025: this is the port where mining services can connect by default; mining services help your node produce new blocks; note that this requires to open a remote miner in your node, listening at port 8025 (`mokamint-node miners add 8025`);
* 8030: this is the port where Mokamint can be reached for public queries, by default;
* 8031: this is the port where Mokamint can be reached for restricted operations, by default; note the we have restricted its access to localhost only, since we do not want our Mokamint node to be freely reconfigured remotely;
* 8050: this is the port where the Bitcoin application can be reached.

You can leave the container execute in the background by entering ctrl+p, ctrl+q, as always in docker.

You can then monitor the progress of the node by entering the running container and executing the `mokamint-node` command
to show, for instance, the last ten blocks of the current chain:

```console
$ docker exec -it mokamint /bin/bash
mokamint@e41eda9afd3b:~$ mokamint-node chain ls 10
mokamint@e41eda9afd3b:~$ exit
```

You should see that only one node is producing blocks for now.

## Join an existing blockchain, by spawning a new node that clones an existing node

If you followed to steps above, there should be a `mokamint` docker container running in your machine.
Let us spawn a new node of the same blockchain now. This means that the new node will share the same
consensus parameters as the first node. Therefore, it will be configured as a _clone_ of the already
running node. Also this second node will have its own local miner, or otherwise it would not
be able to mine new blocks. Because of this, as for the first node, the scripts deal with two key pairs:
the former identifies the second node, and is kept inside the machine running the script, and the latter identifies
the miner, it can be stored elsewhere and only its public key is needed here.

The process is consequently split in two:

* configure the node (`config-clone`)
* run the node (`go`)

Each phase is the execution of a script inside a docker container. The script `config-clone` is meant
to be run only once, while `go` can be run, stopped and run again, whenever you want to start or stop a node.
You can also pause it and unpause it. The reason for splitting the process in two scripts is that
it allows one to manually edit the configuration created by `config-clone`, before running the node,
although we won't show this here. Moreover, having distinct scripts allows `go` to be stopped and run again,
repeatedly, whenever you want to stop and restart a node.

### Configure the node: `config-clone`

The first thing to do is to create a key pair for the miner of the new node that you want to start. You can do this
exactly as you did for the first node, but naming the key pair file as `miner2.pem`. See instructions above.

After that, you can run the script that configures the node. Use the base58-encoded public key of the key pair
that you have created above (in our example below, we assume that it is `CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4`).
Specify `ws://172.17.0.1:8030` as the URI of a node of the blockchain to join: this is port 8030 of
localhost, that is, it is the first node
that we have started before (remember that, from inside a docker container,
localhost is mapped to the IP address 172.17.0.1). Specify the size of the plot file to use for the proof of space.
Specify two more volumes `chain2` and `mokamint2`. When prompted, insert your preferred password for the
key pair of the node (or leave it blank):

```console
$ docker run -it --rm -e PUBLIC_KEY_MINER_BASE58=CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4 -e MOKAMINT_PUBLIC_SERVICE_URI=ws://172.17.0.1:8030 -v mokamint2:/home/mokamint/mokamint -v chain2:/home/mokamint/chain mokamint/mokamint:1.7.0 config-clone
Going to create the configuration directory of a proof of space Mokamint node, with the following parameters:

 MOKAMINT_PUBLIC_SERVICE_URI=ws://172.17.0.1:8030
     PUBLIC_KEY_MINER_BASE58="CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4"
                   PLOT_SIZE=1000

Cleaning the directory mokamint... done
Cleaning the directory chain... done
Cloning the Mokamint configuration file from ws://172.17.0.1:8030... done
Creating the node.pem key pair for signing the blocks: Enter value for --password (the password that will be needed later to use the key pair): 
done: the public key is HBeA4zJtZ4aHCd56a7A9G81ypTqCMqjjhcU7mRDRSPqQ (ed25519, base58)
Creating a plot file for the miner, containing 1000 nonces, for the chain id "panda", for a node with public key HBeA4zJtZ4aHCd56a7A9G81ypTqCMqjjhcU7mRDRSPqQ (ed25519, base58), for a miner with public key CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4 (ed25519, base58)...
1% 2% 3% 4% 5% 6% 7% 8% 9% 10% 11% 12% 13% 14% 15% 16% 17% 18% 19% 20% 21% 22% 23% 24% 25% 26% 27% 28% 29% 30% 31% 32% 33% 34% 35% 36% 37% 38% 39% 40% 41% 42% 43% 44% 45% 46% 47% 48% 49% 50% 51% 52% 53% 54% 55% 56% 57% 58% 59% 60% 61% 62% 63% 64% 65% 66% 67% 68% 69% 70% 71% 72% 73% 74% 75% 76% 77% 78% 79% 80% 81% 82% 83% 84% 85% 86% 87% 88% 89% 90% 91% 92% 93% 94% 95% 96% 97% 98% 99% 100% 
done
```

### Run the node: `go`

You can run the second node now. It will start by synchronizing from the first node. After synchronization is completed,
it will start contributing to the blockchain itself:

```console
$ docker run -it --rm --name mokamint2 -e APPLICATION=Parity -p 8026:8025 -p 8032:8030 -p 127.0.0.1:8033:8031 -p 8050:8050 -v mokamint2:/home/mokamint/mokamint -v chain2:/home/mokamint/chain mokamint/mokamint:1.7.0 go
Starting an already configured node of a blockchain, whose configuration has been created with config-clone or with config-new and then init.
   APPLICATION="Bitcoin"
    VISIBLE_AS=
Enter value for --password (the password of the key pair of the node): 
Loading mokamint/plot.plot... done.
The path "chain" already exists! Will restart the node from the current content of "chain".
If you want to start a blockchain from scratch, stop this process, delete "chain" and start again a node with --init.
Creating the Bitcoin application... done.
[... log messages follow ...]
```

The command above allows connections to the ports:

* 8026: this is the port where mining services can connect by default; mining services help your node produce new blocks; note that this requires to open a remote miner in your node, listening at port 8026;
* 8032: this is the port where Mokamint can be reached for public queries, by default;
* 8033: this is the port where Mokamint can be reached for restricted operations, by default; note the we have restricted its access to localhost only, since we do not want our Mokamint node to be freely reconfigured remotely;
* 8050: this is the port where the Bitcoin application can be reached.

You can leave the container execute in the background by entering ctrl+p, ctrl+q, as always in docker.

You can then monitor the progress of the node by entering the running container and executing the `mokamint-node` command
to show, for instance, the last ten blocks of the current chain. After waiting some time for
synchronization to complete, by looking at the public keys, you can spot
that two nodes are contributing to the creation of the blockchain now:

```console
$ docker exec -it mokamint2 /bin/bash
mokamint@e41eda9afd3b:~$ mokamint-node chain ls 10
1313: 381775f741fe6aaa2d638a255650cb03991e784814ebe4bcda2edd47c27d3b37  2026-03-06 16:23:54  HBeA4zJtZ4aHCd56a7A9G81ypTqCMqjjhcU7mRDRSPqQ  CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4
1312: 2a97dc0104f3cde4fee699d6b22340109d151bcfefcee7882f97593c28f817f2  2026-03-06 16:23:40  HBeA4zJtZ4aHCd56a7A9G81ypTqCMqjjhcU7mRDRSPqQ  CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4
1311: 18b2a5195a44aaaf731506a7d275012e83b590756dbcf76b9b5082fdff25e557  2026-03-06 16:23:33  HBeA4zJtZ4aHCd56a7A9G81ypTqCMqjjhcU7mRDRSPqQ  CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4
1310: ee1f3e238d26c123068223355e15a67f30a1edccce4ad91adc40a3a848c820e7  2026-03-06 16:23:27  HBeA4zJtZ4aHCd56a7A9G81ypTqCMqjjhcU7mRDRSPqQ  CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4
1309: 0ab8f8020abab83424881f99cc05bd898b2f977d300fcb55ac258bc8062306d8  2026-03-06 16:23:19  7M4tVKyYZXpPHoF58ZgBwyJs86iWBh9PrCAZm9BjS9dV  3ExG53CrXsAsnrbxWgkBNNpME4F3YK7mho4R6RXqhoW2
1308: 1b8a71d81ddff0c803d2e397dac6698bdc48b3b0308969feca25ed6b9b86fd05  2026-03-06 16:23:09  HBeA4zJtZ4aHCd56a7A9G81ypTqCMqjjhcU7mRDRSPqQ  CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4
1307: d7b9eed9b8f9a8c1b39c2b5c6b9c11483e9a2fa9eb1b6efc26e7c692333c6eaf  2026-03-06 16:22:35  7M4tVKyYZXpPHoF58ZgBwyJs86iWBh9PrCAZm9BjS9dV  3ExG53CrXsAsnrbxWgkBNNpME4F3YK7mho4R6RXqhoW2
1306: a6bdbb5098ae77ad7963391881a6e9875f071125db939a61b7ed9a4a44aa9c20  2026-03-06 16:22:06  HBeA4zJtZ4aHCd56a7A9G81ypTqCMqjjhcU7mRDRSPqQ  CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4
1305: 005013a699f5870ab439e4d0d021e7ab86ce609c2c4d2fa0408495a8e5b82c52  2026-03-06 16:22:05  7M4tVKyYZXpPHoF58ZgBwyJs86iWBh9PrCAZm9BjS9dV  3ExG53CrXsAsnrbxWgkBNNpME4F3YK7mho4R6RXqhoW2
1304: fae1c23b9b09dbdbc0b103a2aa261991850931904dd157465772c86148bf6924  2026-03-06 16:22:02  HBeA4zJtZ4aHCd56a7A9G81ypTqCMqjjhcU7mRDRSPqQ  CBSW5keMkZ5wuupC4S4c1KbtbWzdsAzbeNseY3E9v5o4
mokamint@e41eda9afd3b:~$ exit
```

## Send requests to the network

Assuming that you have both nodes running in your machine, inside containers `mokamint` and `mokamint2`,
we show now how to send requests to the network. You can run the following commands inside either container.
In our examples, we will use `mokamint`.

Enter that container then:

```console
$ docker exec -it mokamint /bin/bash
mokamint@a0c6917c3bc3:~$
```

## Further information

You can see all options of the docker scripts by executing:

```console
$ docker run -it --rm mokamint/mokamint:1.7.0 info
```

&nbsp;

<p align="center"><img width="100" src="../pics/CC_license.png" alt="This documentation is licensed under a Creative Commons Attribution 4.0 International License"></p><p align="center">This document is licensed under a Creative Commons Attribution 4.0 International License.</p>

<p align="center">Copyright 2026 by Fausto Spoto (fausto.spoto@mokamint.io).</p>