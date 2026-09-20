package main

import (
	"strings"
	"testing"
)

func TestParseReportGradleGreen(t *testing.T) {
	got, err := parseReport("testdata/gradle-green.xml")
	if err != nil {
		t.Fatal(err)
	}
	want := report{Tests: 11}
	if got != want || !got.green() {
		t.Fatalf("got %+v, want %+v", got, want)
	}
}

func TestParseReportXML(t *testing.T) {
	cases := []struct {
		name string
		xml  string
		want report
	}{
		{
			name: "gradle failure",
			xml: `<testsuite tests="2" failures="1" errors="0" skipped="0">
				<testcase name="a"/>
				<testcase name="b"><failure message="boom">trace</failure></testcase>
			</testsuite>`,
			want: report{Tests: 2, Failures: 1},
		},
		{
			name: "suite claims green but a testcase failed",
			xml: `<testsuite tests="2" failures="0" errors="0">
				<testcase name="a"/>
				<testcase name="b"><failure/></testcase>
			</testsuite>`,
			want: report{Tests: 2, Failures: 1},
		},
		{
			name: "swiftpm xunit shape",
			xml: `<?xml version="1.0" encoding="UTF-8"?>
				<testsuites>
				<testsuite name="TestResults" errors="0" tests="3" failures="1" time="0.1">
				<testcase classname="OrdersTests.ProductTests" name="testA" time="0.01"></testcase>
				<testcase classname="OrdersTests.ProductTests" name="testB" time="0.01"><failure message="failed"></failure></testcase>
				<testcase classname="OrdersTests.ProductTests" name="testC" time="0.01"></testcase>
				</testsuite>
				</testsuites>`,
			want: report{Tests: 3, Failures: 1},
		},
		{
			name: "skipped and error each land in one bucket",
			xml: `<testsuite>
				<testcase name="a"><skipped/></testcase>
				<testcase name="b"><failure/><error/></testcase>
				<testcase name="c"/>
			</testsuite>`,
			want: report{Tests: 3, Errors: 1, Skipped: 1},
		},
		{
			name: "attributes claim more tests than present",
			xml: `<testsuite tests="5" failures="0"><testcase name="a"/><testcase name="b"/></testsuite>`,
			want: report{Tests: 5, Errors: 3},
		},
		{
			name: "no testcases falls back to attributes",
			xml:  `<testsuite tests="4" failures="1" errors="0" skipped="1"/>`,
			want: report{Tests: 4, Failures: 1, Skipped: 1},
		},
		{
			name: "nested suites count outer attributes once",
			xml: `<testsuite tests="2"><testsuite tests="2">
				<testcase name="a"/><testcase name="b"/>
			</testsuite></testsuite>`,
			want: report{Tests: 2},
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, err := parseReportXML(strings.NewReader(tc.xml))
			if err != nil {
				t.Fatal(err)
			}
			if got != tc.want {
				t.Fatalf("got %+v, want %+v", got, tc.want)
			}
		})
	}
}

func TestParseReportXMLTruncatedIsError(t *testing.T) {
	truncated := `<testsuite tests="3" failures="0" errors="0"><testcase name="a"/><testcase name="b">`
	if _, err := parseReportXML(strings.NewReader(truncated)); err == nil {
		t.Fatal("truncated report must not parse as a result")
	}
}

func TestReportGreen(t *testing.T) {
	if (report{}).green() {
		t.Fatal("zero tests must not be green")
	}
	if (report{Tests: 1, Failures: 3}).passed() != 0 {
		t.Fatal("passed must not go negative")
	}
}

func TestCheckProjectPath(t *testing.T) {
	for _, bad := range []string{"", ".", "..", "../x", "/etc", ".git", ".github/checker", "config.yml"} {
		if checkProjectPath("f", bad) == nil {
			t.Errorf("%q must be rejected", bad)
		}
	}
	for _, good := range []string{"Tests", "src/test", "build/test-results/test", ".checker-reports", "gradlew"} {
		if err := checkProjectPath("f", good); err != nil {
			t.Errorf("%q must be accepted: %v", good, err)
		}
	}
}
