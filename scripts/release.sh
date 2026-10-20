#!/bin/bash
# ============================================================================
# NebulaStrike 一键发版脚本
#
# 用法:
#   scripts/release.sh <版本号> [GitHub token]
#   scripts/release.sh 1.2.0 ghp_xxx
#
#   版本号可带可不带 v 前缀;token 也可通过环境变量 GITHUB_TOKEN 提供。
#   试运行(只做检查/构建/签名校验,不改文件不推送): DRY_RUN=1 scripts/release.sh 1.2.0
#
# 流程:
#   1. 前置检查: 工作树干净、无未推送提交、依赖工具可用
#   2. app/build.gradle: versionName 换新、versionCode +1
#   3. scripts/verify.sh: 单元测试必须全绿
#   4. ./gradlew assembleRelease 构建签名 APK,apksigner 校验签名
#   5. 中文提交 + 打 tag + 推送 main 与 tag
#   6. GitHub API 创建 Release(正文自动汇总上个 tag 以来的中文提交)并上传 APK
# ============================================================================
set -euo pipefail

# ---- 参数与环境 ----
VER="${1:?用法: scripts/release.sh <版本号> [GitHub token],如 scripts/release.sh 1.2.0 ghp_xxx}"
VER="${VER#v}"                       # 允许 v1.2.0 或 1.2.0
TAG="v$VER"
TOKEN="${2:-${GITHUB_TOKEN:-}}"
REPO="cedarln/NebulaStrike"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

log()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m错误:\033[0m %s\n' "$*" >&2; exit 1; }

DRY_RUN="${DRY_RUN:-}"   # 试运行开关：DRY_RUN=1 只检查+构建，不改动不推送

# ---- 1. 前置检查 ----
log "前置检查"
[ -z "$DRY_RUN" ] || log "  (试运行模式: 只检查+构建,不改文件不推送)"

command -v curl     >/dev/null || die "缺少 curl"
command -v python3  >/dev/null || die "缺少 python3"

git diff --quiet && git diff --cached --quiet \
    || die "工作树有未提交改动,请先提交或暂存"
if git rev-parse -q --verify origin/main >/dev/null; then
    [ -z "$(git log origin/main..HEAD --format=%H)" ] \
        || die "存在未推送到 origin/main 的提交,请先 git push"
else
    log "  (本地无 origin/main 引用,跳过远程同步检查)"
fi
[ -n "$TOKEN" ] || [ -n "$DRY_RUN" ] \
    || die "缺少 GitHub token(第 2 个参数或环境变量 GITHUB_TOKEN)"

# 探测 JDK 17(AGP 8.5 在 JDK 21+ 上有兼容问题)
JAVA_BIN=""
for j in "${JAVA_HOME:-/nonexistent}/bin/java" \
         /root/.local/share/mise/installs/java/*/bin/java \
         /usr/lib/jvm/*17*/bin/java \
         /opt/agent/jdks/*/bin/java; do
    [ -x "$j" ] || continue
    if "$j" -version 2>&1 | grep -q 'version "17'; then
        JAVA_BIN="$j"; JAVA_HOME="$(cd "$(dirname "$j")/.." && pwd)"
        export JAVA_HOME; break
    fi
done
[ -n "$JAVA_BIN" ] || die "未找到 JDK 17,请设置 JAVA_HOME"
log "  JDK 17: $JAVA_HOME"

# 探测 Android SDK
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
[ -d "$ANDROID_HOME/platforms" ] || die "未找到 Android SDK,请设置 ANDROID_HOME"
export ANDROID_HOME
BT="$ANDROID_HOME/build-tools/34.0.0"
[ -x "$BT/apksigner" ] || die "未找到 build-tools 34.0.0 的 apksigner"
log "  Android SDK: $ANDROID_HOME"

# ---- 签名密码（不写入仓库，从 local.properties 或环境变量读取） ----
STORE_PW="${RELEASE_STORE_PASSWORD:-$(sed -n 's/^RELEASE_STORE_PASSWORD=//p' local.properties 2>/dev/null | tail -1)}"
KEY_PW="${RELEASE_KEY_PASSWORD:-$(sed -n 's/^RELEASE_KEY_PASSWORD=//p' local.properties 2>/dev/null | tail -1)}"
[ -n "$STORE_PW" ] && [ -n "$KEY_PW" ] \
    || die "缺少签名密码: 在 local.properties 或环境变量设置 RELEASE_STORE_PASSWORD / RELEASE_KEY_PASSWORD"
export RELEASE_STORE_PASSWORD="$STORE_PW"
export RELEASE_KEY_PASSWORD="$KEY_PW"
log "  签名密码: 已从本地配置读取"

# 沙箱/内网环境: 从 http_proxy 解析代理给 Gradle 用
if [ -z "${GRADLE_OPTS:-}" ] && [ -n "${http_proxy:-}" ]; then
    PX="${http_proxy#http://}"; PX="${PX%/}"
    GRADLE_OPTS="-Dhttp.proxyHost=${PX%%:*} -Dhttp.proxyPort=${PX#*:} \
                 -Dhttps.proxyHost=${PX%%:*} -Dhttps.proxyPort=${PX#*:}"
    export GRADLE_OPTS
fi

# ---- 2. 版本号 ----
log "更新版本号 -> $TAG"
OLD_VER=$(sed -n 's/.*versionName "\([^"]*\)".*/\1/p' app/build.gradle)
OLD_CODE=$(sed -n 's/.*versionCode \([0-9]*\).*/\1/p' app/build.gradle)
[ -n "$OLD_VER" ] && [ -n "$OLD_CODE" ] || die "无法从 app/build.gradle 解析当前版本"
if [ -z "$DRY_RUN" ]; then
    sed -i "s/versionName \"[^\"]*\"/versionName \"$VER\"/" app/build.gradle
    sed -i "s/versionCode [0-9]*/versionCode $((OLD_CODE + 1))/" app/build.gradle
    cp app/build.gradle /tmp/build.gradle.bak    # 供失败回滚
fi
log "  $OLD_VER ($OLD_CODE) -> $VER ($((OLD_CODE + 1)))"

# ---- 3. 测试 ----
log "运行单元测试 (scripts/verify.sh)"
bash scripts/verify.sh >/tmp/verify.log 2>&1 \
    || { tail -5 /tmp/verify.log; die "verify.sh 失败,发版中止"; }
log "  全部通过"

# ---- 4. 构建 ----
log "构建签名 release APK"
./gradlew :app:assembleRelease >/tmp/build.log 2>&1 \
    || { tail -5 /tmp/build.log; die "构建失败,发版中止"; }
APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || die "未找到产物 $APK"
"$JAVA_HOME/bin/java" -jar "$BT/lib/apksigner.jar" verify "$APK" >/dev/null 2>&1 \
    || die "APK 签名校验失败"
SIZE=$(du -h "$APK" | cut -f1)
log "  签名有效, 大小 $SIZE"

# ---- 5. 提交 / 打 tag / 推送 ----
if [ -z "$DRY_RUN" ]; then
    log "提交版本号变更并打 tag $TAG"
    git add app/build.gradle
    git commit -q -m "发布：v$VER 版本号升级 (versionCode $((OLD_CODE + 1)))"
    git tag "$TAG"
    PUSH_URL="https://cedarln:$TOKEN@github.com/$REPO.git"
    git push "$PUSH_URL" main "$TAG" >/dev/null 2>&1 \
        || { git tag -d "$TAG" >/dev/null; die "推送失败,请检查 token 权限"; }
else
    log "  (试运行: 跳过提交/打 tag/推送)"
fi

# ---- 6. 创建 Release 并上传 APK ----
if [ -z "$DRY_RUN" ]; then
    log "创建 GitHub Release $TAG"
    PREV_TAG=$(git describe --abbrev=0 --tags "$TAG^" 2>/dev/null || true)
    if [ -n "$PREV_TAG" ]; then
        CHANGES=$(git log --format='- %s' "$PREV_TAG"..HEAD | head -30)
    else
        CHANGES=$(git log --format='- %s' | head -30)
    fi
    BODY=$(printf '%s\n' "$CHANGES" | python3 - "$VER" <<'PYEOF'
import json, sys
ver = sys.argv[1]
print(json.dumps({
    "tag_name": f"v{ver}",
    "name": f"NebulaStrike v{ver}",
    "body": "Android 离线太空射击游戏。纯 Java + Canvas,零第三方依赖,安装后无需联网。\n\n"
            "## 变更列表\n" + sys.stdin.read(),
}))
PYEOF
)
    RELEASE_ID=$(curl -sS --max-time 30 -X POST \
        -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
        -d "$BODY" "https://api.github.com/repos/$REPO/releases" \
        | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])') \
        || die "创建 Release 失败"
    log "上传 NebulaStrike-v$VER.apk"
    cp "$APK" "NebulaStrike-v$VER.apk"
    curl -sS --max-time 120 -X POST \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/vnd.android.package-archive" \
        --data-binary @"NebulaStrike-v$VER.apk" \
        "https://uploads.github.com/repos/$REPO/releases/$RELEASE_ID/assets?name=NebulaStrike-v$VER.apk" \
        | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["browser_download_url"])' \
        || die "上传 APK 失败"
    rm -f "NebulaStrike-v$VER.apk"
    log "发布完成: https://github.com/$REPO/releases/tag/$TAG"
else
    log "  (试运行: 跳过创建 Release/上传)"
    log "试运行通过,去掉 DRY_RUN=1 重新执行即可正式发版"
fi
