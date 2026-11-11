#!/usr/bin/env python3
"""Read-only Maven report/JAR audit. Run after clean verify from backend/."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
import zipfile


class ReportTreeBuilder(ET.TreeBuilder):
    def doctype(self, name, pubid, system):
        # Surefire does not need DTDs. Reject internal/external entity declarations.
        raise ValueError("DTD declarations are not supported in Surefire reports.")


def verify(root):
    reports = sorted((root / "target/surefire-reports").glob("TEST-*.xml"))
    if not reports:
        raise ValueError("No Surefire XML reports; run the full clean verify first.")
    totals = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
    for report in reports:
        if report.is_symlink() or not report.is_file():
            raise ValueError("Expected regular Surefire report files, not links/directories.")
        suite = ET.parse(report, parser=ET.XMLParser(target=ReportTreeBuilder())).getroot()
        # Current Maven Surefire emits unqualified testsuite roots. Wrappers and
        # namespaced roots are deliberately unsupported, rather than guessed.
        if suite.tag != "testsuite":
            raise ValueError("Expected an unqualified Surefire testsuite root.")
        counters = {}
        for key in totals:
            value = suite.get(key)
            if value is None or re.fullmatch(r"[0-9]+", value) is None:
                raise ValueError("Every Surefire counter must be a nonnegative decimal integer.")
            # Python integers aggregate exactly, without fixed-width overflow.
            counters[key] = int(value)
        if sum(counters[key] for key in ("failures", "errors", "skipped")) > counters["tests"]:
            raise ValueError("Surefire outcome counters exceed the suite test count.")
        if any(counters[key] for key in ("failures", "errors", "skipped")):
            raise ValueError("Every suite must have zero failures/errors/skips.")
        for key in totals:
            totals[key] += counters[key]
    print("Surefire:", ", ".join(f"{k}={v}" for k, v in totals.items()))
    if totals["tests"] == 0 or any(totals[k] for k in ("failures", "errors", "skipped")):
        raise ValueError("Full regression must execute tests with zero failures/errors/skips.")
    jars = sorted((root / "target").glob("*.jar"))
    if len(jars) != 1:
        raise ValueError("Expected exactly one executable application JAR after clean verify.")
    with zipfile.ZipFile(jars[0]) as archive:
        names = archive.namelist()
        if archive.testzip() is not None:
            raise ValueError("Corrupted JAR entry.")
        if "BOOT-INF/classes/application.yml" not in names:
            raise ValueError("Missing Spring Boot application resources.")
        media = (".wav", ".mp3", ".ogg", ".m4a", ".aac", ".flac")
        if any(n.lower().endswith(media) for n in names):
            raise ValueError("Audio binary included in application JAR.")
    print("Executable JAR integrity and audio exclusion: PASS (no artifact uploaded)")


if __name__ == "__main__":
    try:
        verify(Path.cwd())
    except (ValueError, OSError, ET.ParseError, zipfile.BadZipFile) as error:
        print(f"Verification failed: {error}", file=sys.stderr)
        sys.exit(1)
