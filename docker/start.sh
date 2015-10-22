#!/usr/bin/env bash

lein with-profile production,datomic-${DATOMIC_EDITION:-free} trampoline run -p 80 -d ${DB_URI:-datomic:free://db:4334/lens-workbook} -c ${CONTEXT_PATH:-/} --token-introspection-uri ${TOKEN_INTROSPECTION_URI:-http://auth/auth/introspect}
