
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

# Extract content between [ ]
abcjs_str = re.search(r'\[(.*)\]', abcjs_line).group(1)
ours_str = re.search(r'\[(.*)\]', ours_line).group(1)

abcjs = [float(s) for s in abcjs_str.split(', ')]
ours = [parse_frac(s) for s in ours_str.split(', ')]
ours = [o for o in ours if o is not None]

print(f"abcjs size: {len(abcjs)}")
print(f"ours size: {len(ours)}")

limit = min(len(abcjs), len(ours))
for i in range(limit):
    if abs(abcjs[i] - ours[i]) > 0.0001:
        print(f"Divergence at index {i}: abcjs={abcjs[i]}, ours={ours[i]}")
        # Print a bit of context
        print(f"abcjs sequence: {abcjs[max(0, i-5):i+5]}")
        print(f"ours sequence: {ours[max(0, i-5):i+5]}")
        break
else:
    print(f"No divergence found up to index {limit}")
    if len(abcjs) != len(ours):
        print(f"Sequences have different lengths: abcjs={len(abcjs)}, ours={len(ours)}")
        if len(ours) > len(abcjs):
            print(f"Next ours duration: {ours[len(abcjs)]}")
