#!/bin/bash
# Fetch the newest CI build log and print unique compile errors.
cd "$(dirname "$0")/.." || exit 1
git fetch origin -q
git show origin/arena/529e9226-mod:release/last-build-log.txt > /tmp/last-build.log 2>/dev/null || { echo "no log"; exit 1; }
python3 - <<'PY'
lines = open('/tmp/last-build.log', encoding='utf-8', errors='replace').read().split('\n')
seen=set(); out=[]
i=0
while i < len(lines):
    l = lines[i]
    if 'error:' in l and '.java:' in l:
        head = l.replace('/home/runner/work/mod/mod/src/','').strip()
        body=[]; j=i+1
        while j < len(lines) and j < i+5 and (lines[j].startswith('\t') or any(lines[j].strip().startswith(k) for k in ('symbol:','location:','required:','found:','reason:','no suitable method'))):
            t=lines[j].strip()
            if t and not t.startswith('^'): body.append(t)
            j+=1
        key=(head, tuple(body))
        if key not in seen: seen.add(key); out.append((head, body))
        i=j
    else: i+=1
print("total error lines:", sum(1 for l in lines if 'error:' in l))
print("unique:", len(out))
for h,b in out:
    print("ERR", h)
    for x in b: print("      ", x)
PY
