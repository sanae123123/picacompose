#!/usr/bin/env python3
"""重新打包 com.hiczp:picacomic-api:0.0.1，让它能在 minSdk < 30 的 Android 上 dex。

问题：PredefinedCategory 枚举里有几个常量名带空格（``英語 ENG``、``Love Live``、``SAO 刀劍神域``）。
Java 允许，但 D8 对 DEX 版本 < 040（minSdk < 30）直接报错：

    Space characters in SimpleName '英語 ENG' are not allowed prior to DEX version 040

处理：这个枚举在库里没有任何代码引用（只出现在 README 和测试里），
整个类删掉即可，其余类原样保留。

用法：
    python3 tools/patch-picacomic-api.py   # 下载原始 jar，产出 dist/picacomic-api-0.0.1.jar
"""
import pathlib
import urllib.request
import zipfile

CENTRAL = "https://repo.maven.apache.org/maven2/com/hiczp/picacomic-api/0.0.1/picacomic-api-0.0.1.jar"
DROP = "com/hiczp/picacomic/api/service/PredefinedCategory.class"
OUT = pathlib.Path("maven/com/hiczp/picacomic-api/0.0.1/picacomic-api-0.0.1.jar")

tmp = pathlib.Path("/tmp/picacomic-api-0.0.1.jar")
print(f"downloading {CENTRAL}")
urllib.request.urlretrieve(CENTRAL, tmp)

OUT.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(tmp) as zin, zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as out:
    kept = 0
    for info in zin.infolist():
        if info.filename == DROP:
            print(f"dropped {info.filename}")
            continue
        out.writestr(info, zin.read(info.filename))
        kept += 1
print(f"wrote {OUT} ({kept} entries)")
