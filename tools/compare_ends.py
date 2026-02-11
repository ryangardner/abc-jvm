
import json
import re

def parse_frac(s):
    s = s.strip().strip('[]')
    if not s: return None
    if '/' in s:
        n, d = s.split('/')
        return float(n) / float(d)
    return float(s)

with open('debug_output.log', 'r') as f:
    lines = f.readlines()

abcjs_line = [l for l in lines if 'abcjs durations:' in l][0]
ours_line = [l for l in lines if 'ours durations:' in l][0]

abcjs_str = re.search(r'\[(.*)\]', abcjs_line).group(1)
ours_str = re.search(r'\[(.*)\]', ours_line).group(1)

abcjs = [float(s) for s in abcjs_str.split(', ')]
ours = [parse_frac(s) for s in ours_str.split(', ')]
ours = [o for o in ours if o is not None]

print(f"abcjs size: {len(abcjs)}")
print(f"ours size: {len(ours)}")

print("Last 20 abcjs durations:")
print(abcjs[-20:])
print("Last 20 ours durations:")
print(ours[-20:])
