import re, glob, json, sys

# Parse a Kotlin double-quoted string literal starting at s[i]=='"'.
# Returns (value, end_index, has_template) or None for raw/triple-quoted/unterminated.
ESC = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', '\\': '\\', "'": "'", '$': '$'}

def parse_str(s, i):
    if s[i] != '"':
        return None
    if s[i:i+3] == '"""':
        return None
    i += 1
    out = []
    has_template = False
    while i < len(s):
        ch = s[i]
        if ch == '\\':
            nxt = s[i+1] if i + 1 < len(s) else ''
            if nxt == 'u' and i + 6 <= len(s):
                out.append(chr(int(s[i+2:i+6], 16)))
                i += 6
                continue
            out.append(ESC.get(nxt, nxt))
            i += 2
            continue
        if ch == '"':
            return (''.join(out), i + 1, has_template)
        if ch == '$':
            has_template = True
        if ch == '\n':
            return None
        out.append(ch)
        i += 1
    return None

strings = {}
skipped_templates = 0
for f in glob.glob('app/src/main/java/com/enes/liquidtube/**/*.kt', recursive=True):
    src = open(f, encoding='utf-8').read()
    for m in re.finditer(r'\btr\s*\(', src):
        i = m.end()
        while i < len(src) and src[i] in ' \t\n\r':
            i += 1
        if i >= len(src) or src[i] != '"':
            continue
        res = parse_str(src, i)
        if res is None:
            continue
        val, end, templ = res
        if templ:
            skipped_templates += 1
            continue
        if val.strip():
            strings[val] = val

catalog = dict(sorted(strings.items(), key=lambda kv: kv[0].lower()))
print("unique static English strings: %d  (skipped %d dynamic/$-template args)" % (len(catalog), skipped_templates))
json.dump(catalog, open(sys.argv[1], 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
