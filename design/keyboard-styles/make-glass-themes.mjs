// 從 WarmGlass.dc.html 複製出其他配色的畫板，只改配色的預設值。
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const dir = process.argv[2];
const source = readFileSync(join(dir, "WarmGlass.dc.html"), "utf8");
const needle = '"default":"暖茶"}';
const count = source.split(needle).length - 1;
if (count !== 1) {
  console.error(`expected the theme default exactly once, found ${count}`);
  process.exit(1);
}

const variants = {
  "GlassMatcha.dc.html": "抹茶",
  "GlassIndigo.dc.html": "靛夜",
  "GlassRose.dc.html": "霧櫻",
  "GlassGraphite.dc.html": "石墨金",
  "GlassMono.dc.html": "無彩",
};
for (const [file, theme] of Object.entries(variants)) {
  writeFileSync(join(dir, file), source.replace(needle, `"default":"${theme}"}`), "utf8");
  console.log(`wrote ${file} (${theme})`);
}
