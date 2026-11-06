import { test } from "node:test";
import assert from "node:assert/strict";
import {
  isValidKey,
  parseSrc,
  shouldCount,
  cnDay,
  resolveRange,
} from "../src/index.js";

test("isValidKey", () => {
  assert.equal(isValidKey("jiarenxia-1.2.1.apk"), true);
  assert.equal(isValidKey("pengyouxia-1.0.0.apk"), true);
  assert.equal(isValidKey("x.apk"), true);
  assert.equal(isValidKey("../x.apk"), false);
  assert.equal(isValidKey("a/b.apk"), false);
  assert.equal(isValidKey("A.apk"), false);
  assert.equal(isValidKey("x.txt"), false);
  assert.equal(isValidKey("x.apk/"), false);
  assert.equal(isValidKey("-x.apk"), false);
  assert.equal(isValidKey(""), false);
});

test("parseSrc", () => {
  assert.equal(parseSrc("web"), "web");
  assert.equal(parseSrc("update"), "update");
  assert.equal(parseSrc("legacy"), "legacy");
  assert.equal(parseSrc("xhs-call"), "xhs-call");
  assert.equal(parseSrc("xhs-kinship"), "xhs-kinship");
  assert.equal(parseSrc("bili-story"), "bili-story");
  assert.equal(parseSrc("wx-video"), "wx-video");
  assert.equal(parseSrc("weixin"), "direct");
  assert.equal(parseSrc(""), "direct");
  assert.equal(parseSrc(null), "direct");
});

test("shouldCount", () => {
  assert.equal(shouldCount("GET", 200, 0), true);
  assert.equal(shouldCount("HEAD", 200, 0), false);
  assert.equal(shouldCount("GET", 206, 0), true);
  assert.equal(shouldCount("GET", 206, 100), false);
  assert.equal(shouldCount("GET", 304, 0), false);
  assert.equal(shouldCount("GET", 404, 0), false);
});

test("cnDay 用 UTC+8 切日", () => {
  assert.equal(cnDay(Date.parse("2026-10-21T16:30:00Z")), "2026-10-22");
  assert.equal(cnDay(Date.parse("2026-10-21T15:59:59Z")), "2026-10-21");
  assert.equal(cnDay(Date.parse("2026-10-21T16:00:00Z")), "2026-10-22");
});

test("resolveRange", () => {
  assert.deepEqual(
    resolveRange({ offset: 0, length: 100 }, 3863879),
    { offset: 0, end: 99 },
  );
  assert.deepEqual(
    resolveRange({ offset: 5 }, 10),
    { offset: 5, end: 9 },
  );
  assert.deepEqual(
    resolveRange({ suffix: 100 }, 3863879),
    { offset: 3863779, end: 3863878 },
  );
  assert.equal(resolveRange(null, 100), null);
});
