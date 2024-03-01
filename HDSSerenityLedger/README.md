# HDS Serenity Ledger

## Introduction

HDS Serenity Ledger is a project aimed at developing a simplified permissioned (closed membership) blockchain system
with high dependability guarantees, known as HDS Serenity (HDS2). The system is designed to utilize the **Istanbul BFT**
Consensus Algorithm for achieving consensus among blockchain members.

> Highly Dependable Systems project of group 12 - MEIC @ IST 2023/2024.

## Authors

- [110817 André Páscoa](https://github.com/devandrepascoa)
- [110860 André Jesus](https://github.com/andre-j3sus)
- [110893 Nyckollas Brandão](https://github.com/Nyckoka)

@IST<br>
Master in Computer Science and Computer Engineering<br>
Highly Dependable Systems - Group 12<br>
Summer Semester of 2023/2024

### Table of Contents

- [Introduction](#introduction)
- [Authors](#authors)
- [Project Structure](#project-structure)
- [Usage Guide](#usage-guide)
- [Aknowledgements](#aknowledgements)

---

## Usage Guide

### Requirements

The following software is required to run the project:

- [Java 21](https://openjdk.org/projects/jdk/21/) - Programming language;
- [Maven 3.9](https://maven.apache.org/) - Build and dependency management tool;
- [Python 3](https://www.python.org/downloads/) - Programming language;

### Configuration

To run the project, you need to configure the following files:

* `Service/src/main/resources/node-config.json` - Node configuration;
* `Client/src/main/resources/client-config.json` - Client configuration;

Each node has a configuration object that contains the following fields:

```json
{
  "id": "<NODE_ID>",
  "isLeader": "<IS_LEADER>",
  "hostname": "localhost",
  "port": "<NODE_PORT>",
  "privateKeyPath": "<PRIVATE_KEY_PATH>",
  "publicKeyPath": "<PUBLIC_KEY_PATH>"
}
```

The client configuration object contains the following fields:

```json
{
  "id": "<NODE_ID>",
  "hostname": "localhost",
  "port": "<NODE_PORT>",
  "scriptPath": "<SCRIPT_PATH>",
  "privateKeyPath": "<PRIVATE_KEY_PATH>",
  "publicKeyPath": "<PUBLIC_KEY_PATH>"
}
```

### Generating Keys

To generate the keys for the nodes and clients, you can use the script `keypair_generator.py`.
There you can define the number of nodes and clients, and the script will generate the necessary keys inside
the `keypairs` folder.

```bash
python3 keypair_generator.py
```

### Dependencies

To install the necessary dependencies run the following command:

```bash
./install_deps.sh
```

This should install the following dependencies:

- [Google's Gson](https://github.com/google/gson) - A Java library that can be used to convert Java Objects into their
  JSON representation.

### Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the nodes of the blockchain.
The script runs with `kitty` terminal emulator by default since it's installed on the RNL labs.

To run the script you need to have `python3` installed.
The script has arguments which can be modified:

- `terminal` - the terminal emulator used by the script
- `server_config` - a string from the array `server_configs` which contains the possible configurations for the
  blockchain nodes
- `client_config` - a string from the array `client_configs` which contains the possible configurations for the client
  nodes

Run the script with the following command:

```bash
python3 puppet_master.py
```

Note: You may need to install **kitty** in your computer

### Maven

It's also possible to run the project manually by using Maven.

1. Compile and install all modules using:

```
mvn clean install
```

2. Run without arguments:

```
cd <module>/
mvn compile exec:java
```

or run with arguments:

```
cd <module>/
mvn compile exec:java -Dexec.args="..."
```

---

## Acknowledgement

The base code for this project was provided by the course's professors, which was kindly provided by the following
group: [David Belchior](https://github.com/DavidAkaFunky), [Diogo Santos](https://github.com/DiogoSantoss), [Vasco Correia](https://github.com/Vaascoo).
We thank all the group members for sharing their code.

Our group changed some parts of the code, and implemented some features differently for educational purposes, but the
base code was provided by the group mentioned above.

