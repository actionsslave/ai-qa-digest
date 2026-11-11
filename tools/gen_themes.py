#!/usr/bin/env python3
"""Design token table for the 21 app themes. Emits Themes.kt and checks contrast (WCAG)."""
import sys

def lum(h):
    h = h.lstrip('#'); r, g, b = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
def contrast(a, b):
    la, lb = lum(a), lum(b)
    if la < lb: la, lb = lb, la
    return (la + 0.05) / (lb + 0.05)

D = dict(  # defaults
    surface=None, surface2=None, title_color=None, star=None, line=None,
    title_font='default', body_font='default', meta_font='default', head_font=None,
    title_weight='SemiBold', head_weight='Bold', italic=False,
    title_size=16, body_size=14, meta_size=11, head_size=24, tracking=0.0,
    upper=False, underline=False, glow=None,
    entry='Plain', radius=0, border=0, border_color='#000000', shadow_color='#000000', elevation=0, shadow_offset=0,
    gap=0, line_thickness=1,
    header='Plain', bar='#000000', bar_text='#FFFFFF', rule='#000000', rule_thickness=2, dark_variant=None)

T = []
def theme(id, name, era, bg, text, muted, accent, **kw):
    t = dict(D); t.update(kw)
    t.update(id=id, name=name, era=era, bg=bg, text=text, muted=muted, accent=accent)
    t['surface'] = t['surface'] or bg
    t['surface2'] = t['surface2'] or t['surface']
    t['title_color'] = t['title_color'] or text
    t['star'] = t['star'] or accent
    t['line'] = t['line'] or text
    t['head_font'] = t['head_font'] or t['title_font']
    T.append(t)

# 1 Bauhaus
theme('bauhaus', 'Bauhaus', '1923', '#F2F2F2', '#111111', '#4A4A4A', '#D62828', surface='#FFFFFF',
      title_font='default', title_weight='Black', title_size=17, head_weight='Black', head_size=26,
      entry='Outlined', border=2, border_color='#111111', gap=12,
      header='Bar', bar='#F7B500', bar_text='#111111')
# 2 Art Deco
theme('deco', 'Art Déco', '1928', '#0F0F12', '#F1E6C8', '#BDB293', '#C9A24B', surface='#17171C',
      title_font='serif', title_weight='Bold', title_size=15, head_size=24, tracking=1.8, upper=True,
      entry='Outlined', border=1, border_color='#C9A24B', gap=12, line='#C9A24B',
      header='Rule', rule='#C9A24B', rule_thickness=3)
# 3 Zeitung
theme('newsprint', 'Zeitung', '1950', '#D9D9D3', '#141414', '#3A3A3A', '#A40000',
      title_font='serif', body_font='serif', meta_font='serif', title_weight='Bold', title_size=19, body_size=14.5, head_size=34,
      line_thickness=1, header='Rule', rule='#141414', rule_thickness=5)
# 4 Diner
theme('diner', 'Diner', '1955', '#BFE6D6', '#1B2A41', '#3D4F66', '#D7263D', surface='#FFFFFF',
      title_weight='Bold', title_size=16, head_font='cursive', head_weight='Bold', head_size=32,
      entry='Outlined', radius=20, border=3, border_color='#D7263D', gap=12,
      header='Bar', bar='#D7263D', bar_text='#FFFFFF')
# 5 Swiss
theme('swiss', 'Swiss Style', '1957', '#FFFFFF', '#000000', '#4D4D4D', '#E30613',
      title_weight='Bold', title_size=19, head_weight='Bold', head_size=36, tracking=-0.4,
      line_thickness=1, header='Rule', rule='#000000', rule_thickness=4)
# 6 Schreibmaschine
theme('typewriter', 'Schreibmaschine', '1965', '#DCE6F0', '#1A2238', '#3C4A66', '#B3261E',
      title_font='serifmono', body_font='serifmono', meta_font='serifmono', title_weight='Bold', title_size=15, body_size=13, meta_size=11, head_size=22,
      line_thickness=1, header='Rule', rule='#1A2238', rule_thickness=1)
# 7 Pop-Art
theme('popart', 'Pop-Art', '1966', '#FFE600', '#000000', '#2B2B2B', '#C4006A', surface='#FFFFFF',
      title_weight='Black', title_size=18, head_weight='Black', head_size=32,
      entry='HardShadow', border=3, border_color='#000000', shadow_color='#000000', shadow_offset=6, gap=18)
# 8 Seventies
theme('seventies', 'Seventies', '1974', '#3A2418', '#F6E3C5', '#D9BE97', '#F29E38', surface='#4B3023',
      title_font='serif', body_font='default', title_weight='Bold', title_size=18, head_font='serif', head_weight='Bold', head_size=30,
      title_color='#F2B84B', entry='Card', radius=26, elevation=0, gap=12)
# 9 Terminal
theme('terminal', 'Terminal', '1980', '#000000', '#33E060', '#22A044', '#B6FF8A',
      title_font='mono', body_font='mono', meta_font='mono', title_weight='Bold', title_size=14, body_size=13, meta_size=11, head_size=20,
      glow='#33FF66', line='#14602A', line_thickness=1)
# 10 Amber
theme('amber', 'Amber-Monitor', '1982', '#140C00', '#FFB000', '#C98A00', '#FFD36B', surface='#140C00',
      title_font='mono', body_font='mono', meta_font='mono', title_weight='Bold', title_size=14, body_size=13, meta_size=11, head_size=20,
      glow='#FFB000', entry='Outlined', border=1, border_color='#7A5200', gap=10)
# 11 Memphis
theme('memphis', 'Memphis', '1984', '#FFFFFF', '#14213D', '#3A405A', '#E5287F', surface='#FFF7C2',
      title_weight='Black', title_size=18, head_weight='Black', head_size=30,
      entry='HardShadow', radius=14, border=3, border_color='#14213D', shadow_color='#00B8A9', shadow_offset=7, gap=18,
      header='Rule', rule='#E5287F', rule_thickness=8)
# 12 Synthwave
theme('synthwave', 'Synthwave', '1986', '#1A0B2E', '#F5E9FF', '#C9AEEF', '#FF3CA0', surface='#26114A',
      title_font='condensed', body_font='default', title_weight='Bold', italic=True, title_size=19, head_font='condensed', head_weight='Bold', head_size=32,
      title_color='#7DF3FF', glow='#FF3CA0', entry='Outlined', radius=6, border=2, border_color='#7DF3FF', gap=14)
# 13 Windows 95
theme('win95', 'Windows 95', '1995', '#008080', '#000000', '#3D3D3D', '#B00000', surface='#C0C0C0',
      title_weight='Bold', title_size=14, body_size=13, meta_size=11, head_size=18,
      entry='Bevel', gap=8, header='Bar', bar='#000080', bar_text='#FFFFFF')
# 14 Web 1998
theme('web98', 'Web 1998', '1998', '#FFFFFF', '#000000', '#444444', '#0000EE',
      title_font='serif', body_font='serif', meta_font='serif', title_weight='Normal', title_size=17, body_size=14, head_size=32, head_weight='Bold',
      underline=True, title_color='#0000EE', star='#CC0000', line='#999999', line_thickness=1,
      header='Rule', rule='#999999', rule_thickness=2)
# 15 Aqua
theme('aqua', 'Aqua', '2005', '#E3EAF3', '#1C2B3F', '#4B5B73', '#D4620A', surface='#FFFFFF', surface2='#E4EDF9',
      title_weight='Bold', title_size=16, head_weight='Bold', head_size=22,
      entry='Glossy', radius=12, border=1, border_color='#8FA9CC', elevation=3, shadow_color='#7C93B3', gap=12,
      header='Bar', bar='#2D6CDF', bar_text='#FFFFFF')
# 16 Metro
theme('metro', 'Metro', '2012', '#000000', '#FFFFFF', '#B3B3B3', '#E3008C',
      title_weight='Light', title_size=22, body_size=14, meta_size=12, head_weight='Light', head_size=44,
      gap=26, line_thickness=0)
# 17 Material 2014
theme('material', 'Material', '2014', '#EEEEEE', '#212121', '#5A5A5A', '#C2185B', surface='#FFFFFF',
      title_weight='Medium', title_size=16, head_weight='Medium', head_size=20,
      entry='Card', radius=3, elevation=2, shadow_color='#000000', gap=8,
      header='Bar', bar='#3F51B5', bar_text='#FFFFFF')
# 18 Dark Dev
theme('devdark', 'Dev Dark', '2018', '#282A36', '#F8F8F2', '#B9BCD0', '#FF79C6', surface='#343746',
      title_color='#BD93F9', meta_font='mono', title_weight='SemiBold', title_size=16, head_size=22,
      entry='Card', radius=6, gap=10)
# 19 Neumorphism
theme('neumorph', 'Neumorphismus', '2020', '#E0E5EC', '#2B3445', '#4D5C75', '#4A67E8',
      title_weight='SemiBold', title_size=16, head_size=26,
      entry='Card', radius=22, elevation=8, shadow_color='#A3B1C6', border=2, border_color='#F5F8FC', gap=20)
# 20 Brutalism
theme('brutal', 'Brutalismus', '2022', '#FFFFFF', '#000000', '#2E2E2E', '#E00000', surface='#F2F2F2',
      title_font='mono', body_font='mono', meta_font='mono', title_weight='Bold', title_size=15, body_size=13, meta_size=11, head_font='mono', head_size=26, upper=True,
      entry='Outlined', border=4, border_color='#000000', gap=16,
      header='Bar', bar='#000000', bar_text='#FFFFFF')
# 21 Standard (follows system light/dark)
dark = dict(bg='#141218', text='#E6E0E9', muted='#CAC4D0', accent='#E0A100', line='#49454F')
theme('standard', 'Standard', 'heute', '#FFFBFE', '#1C1B1F', '#5F5A66', '#B07A00', line='#CAC4D0',
      title_weight='SemiBold', title_size=16, head_weight='Normal', head_size=24, tracking=0.0,
      dark_variant=dark)

# ---------- contrast gate ----------
bad = []
def check(label, fg, bg, need):
    c = contrast(fg, bg)
    if c < need: bad.append(f"{label}: {fg} on {bg} = {c:.2f} < {need}")
for t in T:
    cont = [t['surface'], t['surface2']] if t['entry'] != 'Plain' else [t['bg']]
    for bgc in cont:
        check(f"{t['id']} body", t['text'], bgc, 4.5)
        check(f"{t['id']} title", t['title_color'], bgc, 4.5)
        check(f"{t['id']} meta", t['muted'], bgc, 4.5)
        check(f"{t['id']} star", t['star'], bgc, 3.0)
    if t['header'] == 'Bar': check(f"{t['id']} bar", t['bar_text'], t['bar'], 4.5)
    else:
        check(f"{t['id']} header", t['title_color'], t['bg'], 4.5); check(f"{t['id']} status", t['muted'], t['bg'], 4.5)
    if t['underline']: check(f"{t['id']} link", t['accent'], t['bg'], 4.5)
    if t['id'] == 'win95': check('win95 star', t['star'], '#C0C0C0', 3.0)
    dv = t['dark_variant']
    if dv:
        check('standard-dark text', dv['text'], dv['bg'], 4.5); check('standard-dark muted', dv['muted'], dv['bg'], 4.5); check('standard-dark star', dv['accent'], dv['bg'], 3.0)
for b in bad: print("CONTRAST:", b)
print(len(T), "themes;", "all contrast checks passed" if not bad else f"{len(bad)} failures")

# ---------- Kotlin ----------
FONT = {'default': 'FontFamily.Default', 'serif': 'FontFamily.Serif', 'mono': 'FontFamily.Monospace', 'cursive': 'FontFamily.Cursive',
        'condensed': 'Fonts.Condensed', 'serifmono': 'Fonts.SerifMono', 'casual': 'Fonts.Casual'}
def col(h): return "Color(0xFF%s)" % h.lstrip('#').upper()
def num(x, unit): return f"{x}f.{unit}" if isinstance(x, float) or '.' in str(x) else f"{x}.{unit}"
def sp(x): return f"{float(x)}f.sp"
def dp(x): return f"{x}.dp"
def kt(t, indent='        '):
    f = lambda k, v: f"{indent}    {k} = {v},"
    out = [f"{indent}DigestTheme("]
    out += [f('id', f'"{t["id"]}"'), f('name', f'"{t["name"]}"'), f('era', f'"{t["era"]}"'),
        f('bg', col(t['bg'])), f('surface', col(t['surface'])), f('surface2', col(t['surface2'])),
        f('text', col(t['text'])), f('titleColor', col(t['title_color'])), f('muted', col(t['muted'])),
        f('accent', col(t['accent'])), f('star', col(t['star'])), f('line', col(t['line'])),
        f('titleFont', FONT[t['title_font']]), f('bodyFont', FONT[t['body_font']]), f('metaFont', FONT[t['meta_font']]), f('headFont', FONT[t['head_font']]),
        f('titleWeight', f"FontWeight.{t['title_weight']}"), f('headWeight', f"FontWeight.{t['head_weight']}"), f('italic', str(t['italic']).lower()),
        f('titleSize', sp(t['title_size'])), f('bodySize', sp(t['body_size'])), f('metaSize', sp(t['meta_size'])), f('headSize', sp(t['head_size'])),
        f('tracking', sp(t['tracking'])), f('upper', str(t['upper']).lower()), f('underline', str(t['underline']).lower()),
        f('glow', col(t['glow']) if t['glow'] else 'null'),
        f('entry', f"EntryStyle.{t['entry']}"), f('radius', dp(t['radius'])), f('border', dp(t['border'])), f('borderColor', col(t['border_color'])),
        f('shadowColor', col(t['shadow_color'])), f('elevation', dp(t['elevation'])), f('shadowOffset', dp(t['shadow_offset'])),
        f('gap', dp(t['gap'])), f('lineThickness', dp(t['line_thickness'])),
        f('header', f"HeaderStyle.{t['header']}"), f('barColor', col(t['bar'])), f('barText', col(t['bar_text'])),
        f('ruleColor', col(t['rule'])), f('ruleThickness', dp(t['rule_thickness']))]
    if t['dark_variant']:
        dv = t['dark_variant']
        out.append(f"{indent}    darkVariant =")
        d2 = dict(t); d2.update(bg=dv['bg'], surface=dv['bg'], surface2=dv['bg'], text=dv['text'], title_color=dv['text'], muted=dv['muted'],
                  accent=dv['accent'], star=dv['accent'], line=dv['line'], dark_variant=None)
        inner = kt(d2, indent + '        ')
        out.append(inner.rstrip(',') + ",")
    out.append(f"{indent}),")
    return "\n".join(out)

HEAD = '''package app.aiqadigest

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How an entry is framed. */
enum class EntryStyle { Plain, Card, Outlined, HardShadow, Bevel, Glossy }

/** How the top of the screen looks. */
enum class HeaderStyle { Plain, Rule, Bar }

/** Fonts that ship with the device. Nothing is bundled or downloaded. */
@OptIn(ExperimentalTextApi::class)
object Fonts {
    val Condensed = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed")))
    val SerifMono = FontFamily(Font(DeviceFontFamilyName("serif-monospace")))
    val Casual = FontFamily(Font(DeviceFontFamilyName("casual")))
}

/** One complete look. [darkVariant] is used instead when the system is in dark mode (only "Standard" has one). */
data class DigestTheme(
    val id: String,
    val name: String,
    val era: String,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val titleColor: Color,
    val muted: Color,
    val accent: Color,
    val star: Color,
    val line: Color,
    val titleFont: FontFamily,
    val bodyFont: FontFamily,
    val metaFont: FontFamily,
    val headFont: FontFamily,
    val titleWeight: FontWeight,
    val headWeight: FontWeight,
    val italic: Boolean,
    val titleSize: TextUnit,
    val bodySize: TextUnit,
    val metaSize: TextUnit,
    val headSize: TextUnit,
    val tracking: TextUnit,
    val upper: Boolean,
    val underline: Boolean,
    val glow: Color?,
    val entry: EntryStyle,
    val radius: Dp,
    val border: Dp,
    val borderColor: Color,
    val shadowColor: Color,
    val elevation: Dp,
    val shadowOffset: Dp,
    val gap: Dp,
    val lineThickness: Dp,
    val header: HeaderStyle,
    val barColor: Color,
    val barText: Color,
    val ruleColor: Color,
    val ruleThickness: Dp,
    val darkVariant: DigestTheme? = null,
)

object Themes {
    const val DEFAULT_ID = "standard"

    fun resolve(id: String, systemDark: Boolean): DigestTheme {
        val t = all.firstOrNull { it.id == id } ?: all.last()
        return if (systemDark && t.darkVariant != null) t.darkVariant else t
    }

    /** Ordered from retro to today. */
    val all: List<DigestTheme> = listOf(
'''
body = HEAD + "\n".join(kt(t) for t in T) + "\n    )\n}\n"
open('Themes.kt', 'w').write(body)
print("Themes.kt", len(body.splitlines()), "lines")
sys.exit(1 if bad else 0)
