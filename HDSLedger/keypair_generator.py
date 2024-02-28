import os
import subprocess

num_nodes = 4

if not os.path.exists('keypairs'):
    os.makedirs('keypairs')

for i in range(1, num_nodes + 1):
    node_dir = f'keypairs/node_{i}'

    if not os.path.exists(node_dir):
        os.makedirs(node_dir)

    private_key = f'{node_dir}/private_key.pem'
    public_key = f'{node_dir}/public_key.pem'
    subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-out', private_key])
    subprocess.run(['openssl', 'rsa', '-pubout', '-in', private_key, '-out', public_key])