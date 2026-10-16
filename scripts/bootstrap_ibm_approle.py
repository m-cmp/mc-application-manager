#!/usr/bin/env python3
"""One-time operator setup of an IBM-read-only AppRole; no mc-admin-cli changes or admin token in AM."""
import argparse
import getpass
import json
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request
from urllib.parse import urlsplit


def bootstrap(url, admin_token, role, secret_path='secret/data/csp/ibm', token_ttl='1h'):
    parsed = urlsplit(url)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
        raise ValueError('Use an operator-trusted OpenBao base address')
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,127}', role):
        raise ValueError('Invalid dedicated IBM AppRole name')
    if not re.fullmatch(r'secret/data/(?:csp/ibm|users/[a-z0-9][a-z0-9-]{0,63}/csp/ibm)', secret_path):
        raise ValueError('Only a registered IBM secret path is allowed')
    if not re.fullmatch(r'[1-9][0-9]*(?:s|m|h)', token_ttl):
        raise ValueError('Use a bounded access-token TTL')

    def call(path, method='GET', body=None, allow_missing=False):
        req = urllib.request.Request(url.rstrip('/') + '/v1/' + path, method=method,
                                     data=None if body is None else json.dumps(body).encode(),
                                     headers={'X-Vault-Token': admin_token, 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                raw = response.read(65537)
                if len(raw) > 65536:
                    raise ValueError('Unexpected OpenBao response size')
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as error:
            if allow_missing and error.code == 404:
                return None
            raise ValueError('OpenBao operator request failed (HTTP ' + str(error.code) + '); response withheld') from None

    policy_name = role + '-read'
    policy = 'path "' + secret_path + '" { capabilities = ["read"] }'
    existing = call('sys/policies/acl/' + policy_name, allow_missing=True)
    if existing is not None:
        saved = existing.get('data', {}).get('policy', '')
        if re.sub(r'\s+', '', saved) != re.sub(r'\s+', '', policy):
            raise ValueError('Existing policy differs; refusing to overwrite it')
    else:
        call('sys/policies/acl/' + policy_name, 'PUT', {'policy': policy})
    mounts = call('sys/auth').get('data', {})
    if 'approle/' not in mounts:
        call('sys/auth/approle', 'POST', {'type': 'approle'})
    elif mounts['approle/'].get('type') != 'approle':
        raise ValueError('Existing auth mount is not AppRole')
    path = 'auth/approle/role/' + role
    old_role = call(path, allow_missing=True)
    if old_role is not None:
        values = old_role['data']
        if values.get('token_policies') != [policy_name] or not values.get('token_no_default_policy') or not values.get('bind_secret_id'):
            raise ValueError('Existing role is not this dedicated IBM role; refusing to overwrite it')
    # The protected source SecretID survives access-token expiry and AM downtime.
    # Only the role's short-lived IBM-read token is issued to AM; no administrative policy is granted.
    call(path, 'POST', {'bind_secret_id': True, 'secret_id_ttl': 0, 'secret_id_num_uses': 0,
                        'token_policies': [policy_name], 'token_no_default_policy': True,
                        'token_ttl': token_ttl, 'token_max_ttl': token_ttl, 'token_explicit_max_ttl': token_ttl,
                        'token_num_uses': 0, 'token_type': 'service'})
    role_id = call(path + '/role-id')['data']['role_id']
    secret_id = call(path + '/secret-id', 'POST', {})['data']['secret_id']
    return {'roleId': role_id, 'secretId': secret_id, 'authPath': 'auth/approle/login'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True, help='OpenBao address reachable by this operator')
    parser.add_argument('--am-url', required=True, help='Same OpenBao instance at an address reachable from standard AM networks')
    parser.add_argument('--namespace', default='default')
    parser.add_argument('--connection', default='ibm-jp-tok')
    parser.add_argument('--secret-path', default='secret/data/csp/ibm')
    parser.add_argument('--role', default='am-ibm-ingress')
    parser.add_argument('--container', default='mc-application-manager')
    parser.add_argument('--admin-token-stdin', action='store_true', help='Read bootstrap token from stdin instead of hidden prompt')
    args = parser.parse_args()
    admin = sys.stdin.read(8193).strip() if args.admin_token_stdin else getpass.getpass('OpenBao operator token (hidden; never stored in AM): ')
    if not admin or len(admin) > 8192:
        parser.error('Supply a valid operator token through the hidden prompt or stdin')
    try:
        values = bootstrap(args.url, admin, args.role, args.secret_path)
        values.update(namespace=args.namespace, connectionName=args.connection, secretPath=args.secret_path, openBaoUrl=args.am_url)
        result = subprocess.run([sys.executable, str(Path(__file__).with_name('configure_ibm_openbao.py')),
                                 'configure-approle', '--container', args.container, '--from-stdin'],
                                input=json.dumps(values).encode())
        return result.returncode
    except Exception:
        print('IBM AppRole bootstrap failed. Check the dedicated policy/role and operator permissions. Credentials are withheld.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
