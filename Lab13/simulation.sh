#!/bin/bash

TRACEFILE_DIR="./tracefile"
LOG_DIR="./log"
LOG_PREFIX="log-"
RE_TRACEFILE="([0-9]+\.[A-Za-z0-9_-]+)\.champsimtrace\.xz$"

[ ! -z "${1}" ] && LOG_PREFIX="${1}"

mkdir -p "${LOG_DIR}"

for filename in "${TRACEFILE_DIR}"/*.champsimtrace.xz; do
    if [[ ${filename} =~ ${RE_TRACEFILE} ]]; then
        echo "Simulation target: ${BASH_REMATCH[1]}"
	./bin/champsim \
            --warmup_instructions 200000000 \
            --simulation_instructions 500000000 \
            "${filename}" \
            > "${LOG_DIR}/${LOG_PREFIX}${BASH_REMATCH[1]}-result.txt"
    fi
done

echo "Simulation done."
