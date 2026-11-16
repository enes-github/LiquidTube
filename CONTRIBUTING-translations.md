# Translating LiquidTube

LiquidTube's interface can be translated into any language — no coding needed. English and
Turkish are built in; every other language is a single JSON file that you can add or edit.

## How it works

Each language is a file at `app/src/main/assets/i18n/<code>.json`, where `<code>` is the
two-letter language code (`de` for German, `es` for Spanish, `fr` for French, `ar` for
Arabic, …). It maps each English line to its translation:

```json
{
  "Home": "Inicio",
  "Search": "Buscar",
  "Add to Library": "Añadir a la biblioteca"
}
```

Any line you leave out simply shows in English, so a partial translation is fine — it gets
better as more lines are filled in. The app picks the file up automatically and lists the
language in **Settings → App Language**; switching is instant, no restart.

## Add a language

1. Copy the template `app/src/main/assets/i18n/en.json` to `app/src/main/assets/i18n/<code>.json`.
2. Translate the **values** (the right-hand side). **Never change the keys** (the left-hand
   side) — the key is the exact English text the app looks up.
3. Keep any `%,d`, `%d` or `%s` placeholders as they are; they're filled in with numbers at
   runtime (e.g. `"%,d minutes"` → `"%,d dakika"`).
4. Open a pull request. That's it.

A few short phrases also appear with a number already in them (for example a countdown);
those stay in English for now and aren't in the template.

## Keep the template current

When the app's English text changes, regenerate the template from the source:

```bash
python scripts/extract-strings.py app/src/main/assets/i18n/en.json
```

This scans every `tr("English", "Türkçe")` call and rebuilds the list of translatable lines.
Turkish lives inside the code (the second argument), so it is never affected by these files.
