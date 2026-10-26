#!/usr/bin/env python3
"""Regression tests for YOIP proposal and historical-link governance."""
import subprocess
import tempfile
import unittest
from pathlib import Path

from check_yoips import validate_proposal, verify


def proposal(number: int, *, status="Discussion", decision="Pending", discussion=None):
    discussion = discussion or "https://github.com/weifuwan/yak-ops/pull/2100"
    return f"""# YOIP-{number}: Durable source recovery

Status: {status}

Discussion: {discussion}

Decision: {decision}

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

The recovery boundary needs a durable decision.

## Public Interfaces

None.

## Proposed Changes

A single coordinator owns the recovery transition.

## Compatibility, Deprecation, and Migration Plan

No persisted format changes.

## Test Plan

A real database recovery test and a checkpoint format test.

## Rejected Alternatives

Relying on elapsed time is not a proof of completion.
"""


class YoipDocumentTest(unittest.TestCase):
    PATH = "docs/yoips/YOIP-20-durable-source-recovery.md"

    def test_valid_discussion(self):
        self.assertEqual([], validate_proposal(self.PATH, proposal(20), None, {1: ["docs/yoips/YOIP-1-existing.md"]}))

    def test_missing_and_reordered_sections(self):
        text = proposal(20).replace("## Test Plan", "## Plan to Test")
        self.assertIn("expected exactly one '## Test Plan'", " ".join(validate_proposal(self.PATH, text, None, {})))
        changed = proposal(20).replace("## Motivation", "## TEMP").replace("## Test Plan", "## Motivation").replace("## TEMP", "## Test Plan")
        self.assertIn("template order", " ".join(validate_proposal(self.PATH, changed, None, {})))

    def test_template_placeholders_are_rejected(self):
        text = proposal(20).replace("The recovery boundary needs a durable decision.", "What problem is being solved, and why is the change needed?")
        self.assertIn("needs proposal-specific content", " ".join(validate_proposal(self.PATH, text, None, {})))

    def test_duplicate_heading_or_metadata(self):
        text = proposal(20) + "\n## Test Plan\n\nTest again.\nStatus: Accepted\n"
        errors = " ".join(validate_proposal(self.PATH, text, None, {}))
        self.assertIn("exactly one '## Test Plan'", errors)
        self.assertIn("Status must appear exactly once", errors)

    def test_reject_bad_id_or_duplicate_or_retired(self):
        self.assertIn("first line", " ".join(validate_proposal(self.PATH, proposal(21), None, {})))
        self.assertIn("already assigned", " ".join(validate_proposal(self.PATH, proposal(20), None, {20: ["docs/yoips/YOIP-20-elsewhere.md"]})))
        self.assertIn("retired", " ".join(validate_proposal("docs/yoips/YOIP-10-old.md", proposal(10), None, {})))
        self.assertIn("kebab", " ".join(validate_proposal("docs/yoips/YOIP-20-Camel.md", proposal(20), None, {})))

    def test_reject_stage_status_and_unlinked_proposal(self):
        text = proposal(20, status="PR 1 of 9", discussion="Link to the proposal PR")
        errors = " ".join(validate_proposal(self.PATH, text, None, {}))
        self.assertIn("Status must be", errors)
        self.assertIn("Discussion must link", errors)

    def test_new_proposal_acceptance_requires_vote_on_its_discussion(self):
        accepted = proposal(20, status="Accepted", decision="[VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/2100#issuecomment-100")
        self.assertEqual([], validate_proposal(self.PATH, accepted, None, {}))
        unreviewed = proposal(20, status="Accepted", decision="Tests passed")
        self.assertIn("maintainer decision", " ".join(validate_proposal(self.PATH, unreviewed, None, {})))
        wrong_pr = proposal(20, status="Accepted", decision="[VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/2999#issuecomment-100")
        self.assertIn("belong to the proposal", " ".join(validate_proposal(self.PATH, wrong_pr, None, {})))

    def test_acceptance_requires_explicit_vote(self):
        before = proposal(20)
        after = proposal(20, status="Accepted", decision="Approved by tests")
        self.assertIn("maintainer decision", " ".join(validate_proposal(self.PATH, after, before, {})))
        accepted = proposal(20, status="Accepted", decision="[VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/2100#issuecomment-100")
        self.assertEqual([], validate_proposal(self.PATH, accepted, before, {}))
        self.assertIn("cannot silently revert", " ".join(validate_proposal(self.PATH, before, accepted, {})))

    def test_historical_accepted_without_vote_is_not_retrospectively_forged(self):
        original = proposal(3, status="Accepted", decision="Original historical acceptance has no permalink")
        updated = original.replace("No persisted format changes.", "Existing persisted formats remain intact.")
        self.assertEqual([], validate_proposal("docs/yoips/YOIP-3-existing.md", updated, original, {}))


class YoipGitDiffTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.cmd("init", "-q")
        self.cmd("config", "user.name", "YOIP Test")
        self.cmd("config", "user.email", "yoip-test@example.com")
        self.write("docs/yoips/YOIP-1-base.md", proposal(1, status="Accepted", decision="[VOTE] Accepted — https://github.com/weifuwan/yak-ops/pull/200#issuecomment-200"))
        self.write("docs/yoips/YOIP-7-historical.md", "# Historical engineering note moved: old\n")
        self.write("docs/yoips/README.md", "# YOIP index\n")
        self.commit("base")
        self.base = self.cmd("rev-parse", "HEAD").strip()

    def cmd(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True, stderr=subprocess.PIPE)

    def write(self, path, text):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text, encoding="utf-8")

    def commit(self, msg="change"):
        self.cmd("add", "-A")
        self.cmd("commit", "-qm", msg)
        return self.cmd("rev-parse", "HEAD").strip()

    def merge_side_branch(self, files):
        """Commit records on a side branch and merge it, the way an upstream sync does."""
        original = self.cmd("rev-parse", "--abbrev-ref", "HEAD").strip()
        self.cmd("checkout", "-q", "-b", "upstream")
        for path, text in files.items():
            self.write(path, text)
        self.commit("upstream records")
        self.cmd("checkout", "-q", original)
        self.cmd("merge", "-q", "--no-ff", "upstream", "-m", "Merge upstream")
        return self.cmd("rev-parse", "HEAD").strip()

    def test_stub_merged_in_from_a_side_branch_is_inherited(self):
        head = self.merge_side_branch({
            "docs/yoips/YOIP-21-retired-note.md": "# Historical engineering note moved: upstream\n",
        })
        self.assertEqual((0, []), verify(self.base, head, root=self.root))

    def test_proposal_merged_in_from_a_side_branch_is_not_authored_here(self):
        head = self.merge_side_branch({"docs/yoips/YOIP-22-incomplete.md": "# YOIP-22: Incomplete\n"})
        self.assertEqual((0, []), verify(self.base, head, root=self.root))

    def test_only_new_proposal_checked(self):
        self.write("docs/yoips/YOIP-20-durable-source-recovery.md", proposal(20))
        self.write("docs/yoips/README.md", "# YOIP index\n[Proposal 20](YOIP-20-durable-source-recovery.md)\n")
        head = self.commit()
        self.assertEqual((1, []), verify(self.base, head, root=self.root))

    def test_new_proposal_requires_index_entry(self):
        self.write("docs/yoips/YOIP-20-durable-source-recovery.md", proposal(20))
        head = self.commit()
        self.assertIn("register new proposals", " ".join(verify(self.base, head, root=self.root)[1]))

    def test_retired_number_cannot_be_reused(self):
        self.write("docs/yoips/YOIP-7-new-proposal.md", proposal(7))
        head = self.commit()
        checked, errors = verify(self.base, head, root=self.root)
        self.assertEqual(1, checked)
        self.assertIn("retired", " ".join(errors))

    def test_existing_historical_stub_is_exempt(self):
        self.write("docs/yoips/YOIP-7-historical.md", "# Historical engineering note moved: updated link\n")
        head = self.commit()
        self.assertEqual((0, []), verify(self.base, head, root=self.root))

    def test_new_history_stub_cannot_bypass(self):
        self.write("docs/yoips/YOIP-20-bypass.md", "# Historical engineering note moved: suspicious\n")
        head = self.commit()
        self.assertIn("cannot create", " ".join(verify(self.base, head, root=self.root)[1]))

    def test_proposal_deletion_rejected(self):
        (self.root / "docs/yoips/YOIP-1-base.md").unlink()
        head = self.commit()
        self.assertIn("do not delete", " ".join(verify(self.base, head, root=self.root)[1]))

    def test_non_yoip_docs_are_ignored(self):
        self.write("docs/engineering/cdc/snapshot.md", "# Safe documentation\n")
        self.write("docs/yoips/README.md", "# Index\n")
        head = self.commit()
        self.assertEqual((0, []), verify(self.base, head, root=self.root))


if __name__ == "__main__":
    unittest.main()
