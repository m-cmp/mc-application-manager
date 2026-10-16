#!/usr/bin/env python3
"""Configure AM's encrypted IBM OpenBao connection; standard mc-admin-cli deployment is unchanged."""
import argparse
import getpass
import json
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['configure', 'configure-approle', 'verify'])
    parser.add_argument('--container', default='mc-application-manager')
    parser.add_argument('--namespace', default='default')
    parser.add_argument('--connection', default='ibm-jp-tok')
    parser.add_argument('--url', help='OpenBao address reachable from the standard AM networks')
    parser.add_argument('--secret-path', default='secret/data/csp/ibm')
    parser.add_argument('--auth-path', default='auth/approle/login')
    parser.add_argument('--from-stdin', action='store_true', help='Read a JSON object from stdin; never pass tokens as command-line arguments')
    args = parser.parse_args()
    if args.from_stdin:
        raw = sys.stdin.buffer.read(65537)
        if len(raw) > 65536:
            parser.error('Configuration input exceeds 64 KiB')
        try:
            values = json.loads(raw)
            if not isinstance(values, dict):
                raise ValueError()
        except (ValueError, UnicodeError):
            parser.error('Supply a valid configuration JSON object on stdin')
    else:
        values = {'namespace': args.namespace, 'connectionName': args.connection, 'secretPath': args.secret_path}
        if args.action in ('configure','configure-approle'):
            if not args.url:
                parser.error('--url is required for configure')
            values['openBaoUrl'] = args.url
            if args.action == 'configure':
                values['token'] = getpass.getpass('IBM-only OpenBao read token (hidden): ')
            else:
                values.update(authPath=args.auth_path,
                              roleId=getpass.getpass('IBM AppRole RoleID (hidden): '),
                              secretId=getpass.getpass('IBM AppRole SecretID (hidden): '))
    # Docker access is the operator boundary. Configuration has no public HTTP endpoint.
    command = ('set -eu; directory=$(mktemp -d /tmp/am-ibm-config.XXXXXX); '
               'trap \'rm -rf "$directory"\' EXIT; cd "$directory"; '
               'jar xf /am.jar BOOT-INF/classes BOOT-INF/lib; '
               'java -cp "BOOT-INF/classes:BOOT-INF/lib/*" '
               'kr.co.mcmp.softwarecatalog.kubernetes.service.IbmOpenBaoConfigurationCli "$1"')
    result = subprocess.run(['docker', 'exec', '-i', args.container, '/bin/sh', '-c', command, 'sh', args.action],
                            input=json.dumps(values).encode('utf-8'))
    return result.returncode


if __name__ == '__main__':
    sys.exit(main())
