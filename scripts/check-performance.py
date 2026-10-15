#!/usr/bin/env python3
"""Provisional CI regression gates derived from ci-mesa-initial.json, not desktop FPS targets."""
import json
from pathlib import Path
import sys
report=json.loads(Path(sys.argv[1]).read_text())
assert report['result']=='passed', 'Gameplay scenarios did not complete'
baseline=report['baseline']; enabled=report['modulesEnabled']
assert baseline['cpuFrame']['samples']>=30 and enabled['cpuFrame']['samples']>=30, 'Insufficient real frames'
# Initial three-renderer experiment: 15 ticks to scan, 2 ticks per received update,
# p95 on/off ratios ~1.0, observed extra peak heap up to ~137 MiB. Generous VM headroom
# avoids treating a shared runner's scheduling/GC noise as a desktop FPS guarantee.
assert report['firstBlockScanTicks']<=45, 'Initial scan exceeded 3x measured tick baseline'
assert report['receivedUpdateLatencyTicks']<=10, 'Received update exceeded 5x measured tick baseline'
assert enabled['cpuFrame']['p95Ms']<=baseline['cpuFrame']['p95Ms']*1.5+5, 'p95 frame regression beyond provisional CI allowance'
assert enabled['peakHeapBytes']<=baseline['peakHeapBytes']+256*1024*1024, 'Peak Java heap regression beyond provisional CI allowance'
cache=report['sharedReadCache']
assert cache['reads']<=40960 and cache['cached']<=32768, 'Shared cache hard budget exceeded'
assert cache['totalHits']>0, 'No shared state reuse observed'
print('CI performance gates passed; software-rendered fixture only, shaders disabled.')
