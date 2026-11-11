"""Exercise CI failure gates using disposable report/JAR fixtures only."""
import importlib.util
import io
from contextlib import redirect_stdout
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location(
    "backend_reports", Path(__file__).resolve().parents[1] / "verify-backend-reports.py"
)
reports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reports)


class BackendReportsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.directory = self.root / "target/surefire-reports"
        self.directory.mkdir(parents=True)
        self.xml = self.directory / "TEST-fixture.xml"
        self.xml.write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
        self.jar = self.root / "target/app.jar"
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("BOOT-INF/classes/application.yml", "fixture")

    def test_valid_reports_and_package(self):
        reports.verify(self.root)

    def test_missing_reports_fail(self):
        self.xml.unlink()
        with self.assertRaises(ValueError):
            reports.verify(self.root)

    def test_zero_failed_errored_or_skipped_suite_fails(self):
        for values in [(0, 0, 0, 0), (2, 1, 0, 0), (2, 0, 1, 0), (2, 0, 0, 1)]:
            with self.subTest(values=values):
                self.xml.write_text('<testsuite tests="%s" failures="%s" errors="%s" skipped="%s"/>' % values)
                with self.assertRaises(ValueError):
                    reports.verify(self.root)

    def test_missing_jar_fails(self):
        self.jar.unlink()
        with self.assertRaises(ValueError):
            reports.verify(self.root)

    def test_media_binary_fails(self):
        with zipfile.ZipFile(self.jar, "a") as archive:
            archive.writestr("BOOT-INF/classes/curated/fixture.mp3", b"fixture")
        with self.assertRaises(ValueError):
            reports.verify(self.root)

    def test_corrupt_jar_fails(self):
        self.jar.write_bytes(b"invalid zip")
        with self.assertRaises(zipfile.BadZipFile):
            reports.verify(self.root)

    def test_unexpected_root_fails(self):
        self.xml.write_text('<not-a-surefire-suite tests="2" failures="0" errors="0" skipped="0"/>')
        with self.assertRaises(ValueError):
            reports.verify(self.root)

    def test_wrappers_and_namespaced_roots_fail(self):
        for xml in ['<testsuites/>', '<testsuites><testsuite tests="2" failures="0" errors="0" skipped="0"/></testsuites>',
                    '<testsuite xmlns="urn:unexpected" tests="2" failures="0" errors="0" skipped="0"/>']:
            with self.subTest(xml=xml):
                self.xml.write_text(xml)
                with self.assertRaises(ValueError):
                    reports.verify(self.root)

    def test_negative_counters_fail_individually(self):
        for key in ("tests", "failures", "errors", "skipped"):
            with self.subTest(counter=key):
                values = dict(tests="2", failures="0", errors="0", skipped="0")
                values[key] = "-1"
                self.xml.write_text('<testsuite ' + ' '.join(f'{k}="{v}"' for k, v in values.items()) + '/>')
                with self.assertRaises(ValueError):
                    reports.verify(self.root)

    def test_negative_failure_cannot_mask_failed_suite_in_either_file_order(self):
        other = self.directory / "TEST-other.xml"
        for first, second in [("1", "-1"), ("-1", "1")]:
            with self.subTest(first=first):
                self.xml.write_text(f'<testsuite tests="1" failures="{first}" errors="0" skipped="0"/>')
                other.write_text(f'<testsuite tests="1" failures="{second}" errors="0" skipped="0"/>')
                with self.assertRaises(ValueError):
                    reports.verify(self.root)

    def test_outcome_counters_exceeding_tests_fail(self):
        for outcomes in [(3, 0, 0), (0, 3, 0), (0, 0, 3), (1, 1, 1)]:
            with self.subTest(outcomes=outcomes):
                self.xml.write_text('<testsuite tests="2" failures="%s" errors="%s" skipped="%s"/>' % outcomes)
                with self.assertRaisesRegex(ValueError, "exceed"):
                    reports.verify(self.root)

    def test_noninteger_and_missing_counters_fail(self):
        for key in ("tests", "failures", "errors", "skipped"):
            for value in (None, "", "1.0", "+1", " 1", "1e2", "NaN", "１"):
                with self.subTest(counter=key, value=value):
                    values = dict(tests="2", failures="0", errors="0", skipped="0")
                    if value is None:
                        del values[key]
                    else:
                        values[key] = value
                    self.xml.write_text('<testsuite ' + ' '.join(f'{k}="{v}"' for k, v in values.items()) + '/>')
                    with self.assertRaises(ValueError):
                        reports.verify(self.root)

    def test_malformed_xml_fails(self):
        self.xml.write_text('<testsuite tests="2"')
        with self.assertRaises(reports.ET.ParseError):
            reports.verify(self.root)

    def test_multiple_valid_reports_and_large_counts_aggregate_exactly(self):
        # Larger than uint64: aggregation uses Python integers, not wrapping arithmetic.
        other = self.directory / "TEST-other.xml"
        other.write_text(f'<testsuite tests="{2**64}" failures="0" errors="0" skipped="0"/>')
        output = io.StringIO()
        with redirect_stdout(output):
            reports.verify(self.root)
        self.assertIn(f"tests={2**64 + 2},", output.getvalue())

    def test_one_invalid_report_among_valid_reports_fails(self):
        other = self.directory / "TEST-other.xml"
        other.write_text('<testsuite tests="2" errors="0" skipped="0"/>')
        with self.assertRaises(ValueError):
            reports.verify(self.root)

    def test_dtd_and_entity_declarations_fail_without_external_reads(self):
        for doctype in ['<!DOCTYPE testsuite SYSTEM "file:///must-not-be-read">',
                        '<!DOCTYPE testsuite SYSTEM "http://127.0.0.1:1/must-not-be-requested">',
                        '<!DOCTYPE testsuite [<!ENTITY example "internal expansion">]>']:
            with self.subTest(doctype=doctype):
                self.xml.write_text(doctype + '<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
                with self.assertRaisesRegex(ValueError, "DTD"):
                    reports.verify(self.root)

    def test_linked_report_fails(self):
        linked = self.directory / "TEST-linked.xml"
        linked.symlink_to(self.xml.name)
        with self.assertRaises(ValueError):
            reports.verify(self.root)


if __name__ == "__main__":
    unittest.main()
