import os
import subprocess

num_nodes = 5
num_clients = 3

if not os.path.exists('keypairs'):
    os.makedirs('keypairs')

for i in range(1, num_nodes + 1):
    node_dir = f'keypairs/node_{i}'

    if not os.path.exists(node_dir):
        os.makedirs(node_dir)

    private_key = f'{node_dir}/private_key.der'
    public_key = f'{node_dir}/public_key.der'
    subprocess.run(f'openssl genrsa 2048 | openssl pkcs8 -topk8 -inform PEM -outform DER -out {private_key} -nocrypt',
                   shell=True)
    subprocess.run(['openssl', 'rsa', '-in', private_key, '-pubout', '-outform',
                    'DER', '-out', public_key])

for j in range(1, num_clients + 1):
    client_dir = f'keypairs/client_{j}'

    if not os.path.exists(client_dir):
        os.makedirs(client_dir)

    private_key = f'{client_dir}/private_key.der'
    public_key = f'{client_dir}/public_key.der'

    subprocess.run(f'openssl genrsa 2048 | openssl pkcs8 -topk8 -inform PEM -outform DER -out {private_key} -nocrypt',
                   shell=True)
    subprocess.run(['openssl', 'rsa', '-in', private_key, '-pubout', '-outform',
                    'DER', '-out', public_key])
