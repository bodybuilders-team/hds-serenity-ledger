import os

# Blockchain node configuration file name
server_configs = [
    "regular-node-config.json",
    "crash-node-config-0s.json",
    "crash-node-config-10s.json",
    "leader-impersonation-node-config.json",
    "non-leader-start-consensus-node-config.json",
    "corrupt-broadcasting-node-config.json",
    "corrupt-leader-node-config.json",
    "quiet-leader-node-config.json",
    "bully-leader-node-config.json",
    "robber-leader-node-config.json",
]

# Blockchain client configuration file name
client_configs = [
    "regular-client-config.json",
    "robber-client-config.json",
]

server_config = server_configs[6]
client_config = client_configs[0]

if os.name == "nt":
    import json
    import signal
    import sys
    import subprocess

    # Terminal Emulator used to spawn the processes
    terminal = "cmd"

    # Store the spawned terminal process IDs
    terminal_pids = []


    def quit_handler(*args):
        for pid in terminal_pids:
            subprocess.run(f"taskkill /PID {pid} /T /F", shell=True)
        sys.exit()


    # Compile classes
    subprocess.run("mvn clean install", shell=True)

    # Spawn blockchain nodes
    with open(f"Service/src/main/resources/{server_config}") as f:
        data = json.load(f)
        for key in data:
            process = subprocess.Popen(
                f'start "{terminal}" /wait cmd /c "cd Service && mvn exec:java -Dexec.args=\"{key["id"]} {server_config} {client_config}\""',
                shell=True,
            )
            terminal_pids.append(process.pid)

    # Spawn blockchain clients
    with open(f"Client/src/main/resources/{client_config}") as f:
        data = json.load(f)
        for key in data:
            use_script = "useScript" in key and key["useScript"]
            process = subprocess.Popen(
                f'start "{terminal}" /wait cmd /c "cd Client && mvn exec:java -Dexec.args=\"{key["id"]} {client_config} {server_config} {"-script" if use_script else ""}"',
                shell=True,
            )
            terminal_pids.append(process.pid)

    signal.signal(signal.SIGINT, quit_handler)

    while True:
        print("Type quit to quit")
        command = input(">> ")
        if command.strip() == "quit":
            quit_handler()
else:
    import json
    import os
    import signal
    import sys

    # Terminal Emulator used to spawn the processes
    terminal = "kitty"


    def quit_handler(*args):
        os.system(f"pkill -i {terminal}")
        sys.exit()


    # Compile classes
    os.system("mvn clean install")

    # Spawn blockchain nodes
    with open(f"Service/src/main/resources/{server_config}") as f:
        data = json.load(f)
        processes = list()
        for key in data:
            pid = os.fork()
            if pid == 0:
                os.system(
                    f"{terminal} sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']} {server_config} {client_config} ' ; sleep 500\"")
                sys.exit()

    # Spawn blockchain clients
    with open(f"Client/src/main/resources/{client_config}") as f:
        data = json.load(f)
        for key in data:
            pid = os.fork()
            if pid == 0:
                has_script = "scriptPath" in key
                os.system(
                    f"{terminal} sh -c \"cd Client; mvn exec:java -Dexec.args='{key['id']} {client_config} {server_config} {'-script' if has_script else ''}' ; sleep 500\" ")
                sys.exit()

    signal.signal(signal.SIGINT, quit_handler)

    while True:
        print("Type quit to quit")
        command = input(">> ")
        if command.strip() == "quit":
            quit_handler()
