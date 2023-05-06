#!/bin/bash

cd "$(dirname "$0")"

CHAMPSIM_TRACES="https://dpc3.compas.cs.stonybrook.edu/champsim-traces/speccpu/"

mkdir -p "tracefile" && cd "tracefile" || exit 1
wget -r -np -nH --cut-dirs=2 -R "index.html" -R "index.html.tmp" -R "weights-and-simpoints-speccpu.tar.gz" ${CHAMPSIM_TRACES}
