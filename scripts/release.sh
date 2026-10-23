#!/bin/bash
# ============================================================================
# NebulaStrike 一键发版脚本（macOS / Linux 通用）
#
# 用法:
#   scripts/release.sh <版本号> [GitHub token]
#   scripts/release.sh 1.10.0
#   scripts/release.sh 1.10.0 --notes /tmp/notes.md      # 用写好的 Release 正文
#   DRY_RUN=1 scripts/release.sh 1.10.0 --notes notes.md # 只检查+构建+校验，不改动不推送
#
#   版本号可带可不带 v 前缀。第 2 个位置参数仍可传 token（或环境变量 GITHUB_TOKEN）；
#   本机已用 `gh auth login` 登录时不需要 token，脚本会优先走 gh。
#   Release 正文里可以写占位符 {{SHA256}}，脚本会替换成真实校验和；
#   没给 --notes 时自动汇总上个 tag 以来的中文提交生成正文。
#
# 流程（一条命令走完，失败自动回滚 build.gradle）:
#   1. 环境探测   JDK 17 / Android SDK / build-tools / 签名密码
#   2. 前置检查   工作树干净、无未推送提交、tag 未占用、版本号必须更大
#   3. 升版本     versionName 换新、versionCode +1
#   4. 验证       scripts/verify.sh（SKIP_VERIFY=1 可跳过）
#   5. 构建       assembleRelease + apksigner 验签 + aapt2 核对版本 + SHA-256
#   6. 提交推送   中文提交 + tag + push main（gh 优先，其次 token）
#   7. 发布       创建 Release 并上传 APK
#   8. 回验       latest 指向 + 重新下载 asset 比对 SHA-256
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REPO="cedarln/NebulaStrike"
DRY_RUN="${DRY_RUN:-}"
SKIP_VERIFY="${SKIP_VERIFY:-}"
NOTES_FILE="${NOTES_FILE:-}"
COMMIT_MSG="${COMMIT_MESSAGE:-}"
TOKEN="${GITHUB_TOKEN:-}"
VER=""

log() { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m错误:\033[0m %s\n' "$*" >&2; exit 1; }

# ---- 参数解析 ----
while [ $# -gt 0 ]; do
    case "$1" in
        --notes) NOTES_FILE="${2:?--notes 后面要跟文件路径}"; shift 2 ;;
        --notes=*) NOTES_FILE="${1#--notes=}"; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        --message) COMMIT_MSG="${2:?--message 后面要跟提交说明}"; shift 2 ;;
        --message=*) COMMIT_MSG="${1#--message=}"; shift ;;
        -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
        v[0-9]*|[0-9]*) [ -z "$VER" ] && VER="${1#v}" || TOKEN="${1#v}"; shift ;;
        *) shift ;;
    esac
done
[ -n "$VER" ] || die "用法: scripts/release.sh <版本号> [--notes 文件] [--dry-run]"
TAG="v$VER"
GRADLE_APK="app/build/outputs/apk/release/app-release.apk"
TMP="$(mktemp -d /tmp/nebulastrike-release.XXXXXX)"

# 任何一步失败都还原版本号并删掉本地 tag，仓库不会留下半成品（成功时只清临时目录）。
# 一旦提交/推送成功，回滚不再动 build.gradle 和 tag——此时远端已有发布记录，
# 本地还原只会把工作区退回旧版本号，下一次发版会撞上重复的 versionCode。
rollback() {
    if [ "${OK:-0}" != 1 ] && [ -z "$DRY_RUN" ]; then
        if [ "${PUSHED:-0}" = 1 ]; then
            log "  发布已推送，保留 build.gradle 与 $TAG（请检查上面的失败步骤）"
        else
            if [ -f "$TMP/build.gradle.bak" ]; then
                cp "$TMP/build.gradle.bak" app/build.gradle
                git add app/build.gradle >/dev/null 2>&1 || true
                log "  已还原 app/build.gradle"
            fi
            git tag -d "$TAG" >/dev/null 2>&1 || true
        fi
    fi
    rm -rf "$TMP"
}
trap 'rollback' EXIT

# ---- 1. 环境探测 ----
log "探测构建环境"

sha256_of() {  # macOS 用 shasum，Linux 用 sha256sum
    if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | cut -d' ' -f1
    else sha256sum "$1" | cut -d' ' -f1; fi
}

[ -n "${JAVA_HOME:-}" ] || {
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    fi
    [ -n "$JAVA_HOME" ] || {
        for j in /Library/Java/JavaVirtualMachines/*17*/Contents/Home \
                 /usr/lib/jvm/*17* \
                 "$HOME"/.local/share/mise/installs/java/*17* \
                 /opt/agent/jdks/*17*; do
            [ -x "$j/bin/java" ] && "$j/bin/java" -version 2>&1 | grep -q 'version "17' \
                && { JAVA_HOME="$j"; break; } || true
        done
    }
}
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] || die "未找到 JDK 17，请设置 JAVA_HOME"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
log "  JDK: $JAVA_HOME"

[ -n "${ANDROID_HOME:-}" ] || {
    for d in "$HOME/Library/Android/sdk" /opt/android-sdk /usr/local/share/android-sdk; do
        [ -d "$d/platforms" ] && { ANDROID_HOME="$d"; break; } || true
    done
    [ -n "${ANDROID_HOME:-}" ] || [ ! -f local.properties ] \
        || ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | tail -1)"
}
[ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ] || die "未找到 Android SDK，请设置 ANDROID_HOME"
export ANDROID_HOME
BT_VER="$(ls "$ANDROID_HOME/build-tools" 2>/dev/null | grep '^34' | sort -V | tail -1)"
[ -n "$BT_VER" ] && [ -x "$ANDROID_HOME/build-tools/$BT_VER/apksigner" ] \
    || die "未找到 build-tools 34.x 的 apksigner"
BT="$ANDROID_HOME/build-tools/$BT_VER"
log "  Android SDK: $ANDROID_HOME (build-tools $BT_VER)"

# 签名密码只从环境变量或 gitignore 的 local.properties 读，绝不入库
STORE_PW="${RELEASE_STORE_PASSWORD:-$(sed -n 's/^RELEASE_STORE_PASSWORD=//p' local.properties 2>/dev/null | tail -1)}"
KEY_PW="${RELEASE_KEY_PASSWORD:-$(sed -n 's/^RELEASE_KEY_PASSWORD=//p' local.properties 2>/dev/null | tail -1)}"
[ -n "$STORE_PW" ] && [ -n "$KEY_PW" ] \
    || die "缺少签名密码：在 local.properties 或环境变量设置 RELEASE_STORE_PASSWORD / RELEASE_KEY_PASSWORD"
export RELEASE_STORE_PASSWORD="$STORE_PW"
export RELEASE_KEY_PASSWORD="$KEY_PW"
log "  签名密码：已从本地配置读取"

HAS_GH=""; command -v gh >/dev/null 2>&1 && HAS_GH=1
[ -n "$HAS_GH" ] || [ -n "$TOKEN" ] || [ -n "$DRY_RUN" ] \
    || die "本机未登录 gh，且缺少 GitHub token（第 2 个参数或 GITHUB_TOKEN）"

# 沙箱/内网环境：把 http_proxy 转给 Gradle
if [ -z "${GRADLE_OPTS:-}" ] && [ -n "${http_proxy:-}" ]; then
    PX="${http_proxy#http://}"; PX="${PX%/}"
    GRADLE_OPTS="-Dhttp.proxyHost=${PX%%:*} -Dhttp.proxyPort=${PX#*:} -Dhttps.proxyHost=${PX%%:*} -Dhttps.proxyPort=${PX#*:}"
    export GRADLE_OPTS
fi

# ---- 2. 前置检查 ----
log "前置检查"
[ -z "$DRY_RUN" ] || log "  (试运行：只检查+构建+校验，不改文件不推送)"
if ! git diff --quiet || ! git diff --cached --quiet; then
    [ -n "$DRY_RUN" ] || die "工作树有未提交改动，请先提交或暂存"
    log "  (试运行：工作树有未提交改动，正式发版前必须先提交)"
fi
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then die "tag $TAG 已存在"; fi
if git rev-parse -q --verify origin/main >/dev/null; then
    UNPUSHED="$(git log origin/main..HEAD --format=%h | tr '\n' ' ')"
    if [ -n "$UNPUSHED" ]; then
        [ -n "$DRY_RUN" ] || die "存在未推送到 origin/main 的提交（$UNPUSHED），请先 git push"
        log "  (试运行：有未推送提交 $UNPUSHED，正式发版会带上)"
    fi
else
    log "  (本地无 origin/main 引用，跳过远程同步检查)"
fi
if [ -n "$NOTES_FILE" ]; then
    [ -f "$NOTES_FILE" ] || die "找不到 Release 正文文件：$NOTES_FILE"
    log "  Release 正文：$NOTES_FILE"
fi

OLD_VER="$(sed -n 's/.*versionName "\([^"]*\)".*/\1/p' app/build.gradle)"
OLD_CODE="$(sed -n 's/.*versionCode \([0-9]*\).*/\1/p' app/build.gradle)"
[ -n "$OLD_VER" ] && [ -n "$OLD_CODE" ] || die "无法从 app/build.gradle 解析当前版本"
NEW_CODE=$((OLD_CODE + 1))
# 版本号必须严格递增，否则手机上「检查更新」不会提示
if awk -v a="$VER" -v b="$OLD_VER" 'BEGIN{split(a,x,".");split(b,y,".");
        for(i=1;i<=3;i++){if(x[i]+0>y[i]+0)exit 0;if(x[i]+0<y[i]+0)exit 1}exit 1}'; then
    :
else
    die "版本号 $VER 不大于当前 $OLD_VER"
fi

# ---- 3. 升版本 ----
log "更新版本号 -> $TAG"
if [ -z "$DRY_RUN" ]; then
    cp app/build.gradle "$TMP/build.gradle.bak"
    # 不用 GNU 专属的 `sed -i`，BSD/GNU 都能跑
    sed -e "s/versionName \"[^\"]*\"/versionName \"$VER\"/" \
        -e "s/versionCode [0-9]*/versionCode $NEW_CODE/" \
        app/build.gradle > "$TMP/build.gradle.new" && mv "$TMP/build.gradle.new" app/build.gradle
fi
log "  $OLD_VER ($OLD_CODE) -> $VER ($NEW_CODE)"

# ---- 4. 验证 ----
if [ -n "$SKIP_VERIFY" ]; then
    log "跳过单元测试（SKIP_VERIFY=1）—— 只在确认测试未受影响时使用"
else
    log "运行单元测试 (scripts/verify.sh)"
    bash scripts/verify.sh >"$TMP/verify.log" 2>&1 \
        || { grep -E 'FAILURES|Tests run|AssertionError' "$TMP/verify.log" | tail -20; die "verify.sh 失败，发版中止"; }
    log "  $(grep -E '^OK \(' "$TMP/verify.log" | tail -1)"
fi

# ---- 5. 构建 ----
log "构建签名 release APK"
./gradlew :app:assembleRelease >"$TMP/build.log" 2>&1 \
    || { tail -20 "$TMP/build.log"; die "构建失败，发版中止"; }
[ -f "$GRADLE_APK" ] || die "未找到产物 $GRADLE_APK"
"$BT/apksigner" verify --print-certs "$GRADLE_APK" >"$TMP/sig.log" 2>&1 \
    || die "APK 签名校验失败"
CERT_CN="$(grep -m1 'certificate DN' "$TMP/sig.log" | sed 's/.*CN=\([^,]*\).*/\1/')"
PKG_LINE="$("$BT/aapt2" dump badging "$GRADLE_APK" 2>/dev/null | grep '^package')"
if [ -n "$DRY_RUN" ]; then
    # 试运行不改 build.gradle，产物必然还是旧版本号，只打印不断言
    log "  (试运行：跳过 APK 版本号核对，实际发版要求 versionCode=$NEW_CODE versionName=$VER)"
else
    echo "$PKG_LINE" | grep -q "versionCode='$NEW_CODE'" \
        && echo "$PKG_LINE" | grep -q "versionName='$VER'" \
        || die "APK 里的版本号与预期不符：$PKG_LINE"
fi
SHA="$(sha256_of "$GRADLE_APK")"
SIZE="$(ls -lh "$GRADLE_APK" | awk '{print $5}')"
log "  签名有效（CN=$CERT_CN）· 大小 $SIZE · SHA-256 ${SHA:0:16}…"

# ---- 6. 提交 / tag / 推送 ----
if [ -z "$DRY_RUN" ]; then
    log "提交版本号变更并打 tag $TAG"
    [ -n "$COMMIT_MSG" ] || COMMIT_MSG="发布：v$VER 版本号升级 (versionCode $NEW_CODE)"
    git add app/build.gradle
    git commit -q -m "$COMMIT_MSG"
    git tag "$TAG"
    if [ -n "$HAS_GH" ]; then
        PUSH_LOG="$(git push origin main "$TAG" 2>&1)" || die "推送失败：$PUSH_LOG"
    else
        PUSH_LOG="$(git push "https://cedarln:$TOKEN@github.com/$REPO.git" main "$TAG" 2>&1)" \
            || die "推送失败，请检查 token 权限：$PUSH_LOG"
    fi
    PUSHED=1
else
    log "  (试运行：跳过提交/tag/推送)"
fi

# ---- 7. 创建 Release 并上传 APK ----
ASSET="$TMP/NebulaStrike-$TAG.apk"
cp "$GRADLE_APK" "$ASSET"
if [ -z "$NOTES_FILE" ]; then
    PREV_TAG="$(git describe --abbrev=0 --tags "$TAG^" 2>/dev/null || true)"
    if [ -n "$PREV_TAG" ]; then CHANGES="$(git log --format='- %s' "$PREV_TAG"..HEAD | head -30)"
    else CHANGES="$(git log --format='- %s' | head -30)"; fi
    {
        echo "Android 离线太空射击游戏。纯 Java + Canvas，零第三方依赖，安装后无需联网。"
        echo
        echo "## 变更列表"
        echo "$CHANGES"
        echo
        echo "## 安装与升级"
        echo "- 与旧版本使用同一枚签名密钥（CN=$CERT_CN），已安装玩家可直接覆盖升级。"
        echo
        echo "**APK SHA-256**：\`$SHA\`"
    } > "$TMP/notes.md"
    NOTES_FILE="$TMP/notes.md"
    log "  未指定 --notes，已按提交记录自动生成 Release 正文"
fi
sed "s/{{SHA256}}/$SHA/g" "$NOTES_FILE" > "$TMP/body.md"

log "发布 GitHub Release $TAG"
if [ -z "$DRY_RUN" ]; then
    if [ -n "$HAS_GH" ]; then
        if gh release create "$TAG" "$ASSET" -R "$REPO" --target main \
               --title "NebulaStrike $TAG" --notes-file "$TMP/body.md" \
               >"$TMP/url" 2>"$TMP/gh.log"; then
            RELEASE_URL="$(tail -1 "$TMP/url")"
        else
            cat "$TMP/gh.log"; die "创建 Release 失败"
        fi
    else
        command -v python3 >/dev/null || die "无 gh 时回退 API 需要 python3"
        BODY_JSON="$(python3 -c '
import json, sys
tag = sys.argv[1]
print(json.dumps({"tag_name": tag, "name": "NebulaStrike " + tag,
                  "body": open(sys.argv[2]).read(), "target_commitish": "main"}))' "$TAG" "$TMP/body.md")"
        RELEASE_ID="$(curl -sS --max-time 30 -X POST -H "Authorization: Bearer $TOKEN" \
            -H "Accept: application/vnd.github+json" -d "$BODY_JSON" \
            "https://api.github.com/repos/$REPO/releases" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')" \
            || die "创建 Release 失败"
        RELEASE_URL="$(curl -sS --max-time 120 -X POST -H "Authorization: Bearer $TOKEN" \
            -H "Content-Type: application/vnd.android.package-archive" --data-binary "@$ASSET" \
            "https://uploads.github.com/repos/$REPO/releases/$RELEASE_ID/assets?name=NebulaStrike-$TAG.apk" \
            | python3 -c 'import sys,json; print(json.load(sys.stdin)["browser_download_url"])')" \
            || die "上传 APK 失败"
    fi
    log "  $RELEASE_URL"

    # ---- 8. 回验：latest 指向 + 重新下载比对校验和 ----
    log "回验发布产物"
    if [ -n "$HAS_GH" ]; then
        # releases/latest 有秒级延迟：刚创建完立刻查询可能仍指向旧版本，重试几次再判定
        LATEST=""
        for attempt in 1 2 3 4 5; do
            LATEST="$(gh api "repos/$REPO/releases/latest" -q .tag_name 2>/dev/null || true)"
            [ "$LATEST" = "$TAG" ] && break
            [ "$attempt" -lt 5 ] && sleep 2
        done
        gh release download "$TAG" -R "$REPO" --pattern '*.apk' --dir "$TMP/check" --clobber >/dev/null 2>&1
    else
        LATEST="$(curl -sS -H "Authorization: Bearer $TOKEN" \
            "https://api.github.com/repos/$REPO/releases/latest" | python3 -c 'import sys,json; print(json.load(sys.stdin)["tag_name"])')"
        curl -sSL --output "$TMP/check.apk" "$RELEASE_URL"
    fi
    [ "$LATEST" = "$TAG" ] || die "releases/latest 仍指向 ${LATEST:-空}，不是 $TAG"
    DOWN="$(ls "$TMP/check"/*.apk 2>/dev/null | head -1 || true)"
    [ -n "$DOWN" ] || die "下载回验产物失败"
    [ "$(sha256_of "$DOWN")" = "$SHA" ] || die "下载产物校验和不一致"
    log "  latest=$LATEST · 下载校验和一致"
else
    log "  (试运行：跳过创建 Release/上传/回验)"
    log "  正文预览（{{SHA256}} 已替换）："
    sed -e 's/^/      /' "$TMP/body.md" | head -4
    [ "$(grep -c "$SHA" "$TMP/body.md")" -ge 1 ] || die "正文里没有校验和，占位符替换失败"
    log "  试运行通过，去掉 DRY_RUN 重新执行即可正式发版"
fi

if [ -z "$DRY_RUN" ]; then
    OK=1   # 全流程成功，退出时不再回滚 tag
    log "发布完成：https://github.com/$REPO/releases/tag/$TAG"
fi
