#!/usr/bin/env python3
"""Convert a Google Takeout (Google Keep) ZIP into a Skeleton Notes backup ZIP.

The output ZIP matches the format that Skeleton Notes' Import feature expects:

    notes.json          # JSON array of notes (must be the first entry)
    attachments/<id>    # raw attachment bytes, entry name == attachment id

Usage:
    python3 keep_to_skeleton.py <takeout.zip> [-o skeleton-notes-backup.zip]

Standard library only; no third-party dependencies.
"""

import argparse
import json
import os
import posixpath
import re
import sys
import time
import uuid
import zipfile

# Skeleton Notes NoteStatus enum values.
STATUS_ACTIVE = 0
STATUS_TRASH = 1
STATUS_ARCHIVE = 2

# A line is a Markdown heading (and left as-is) only when it is 1-6 '#' followed by
# whitespace, mirroring MarkdownFormatter.HEADING_REGEX in the app.
HEADING_RE = re.compile(r"^#{1,6}\s")


def log_warn(message):
    print(f"warning: {message}", file=sys.stderr)


def find_keep_members(zf):
    """Return (note_members, keep_dir, members_by_dir).

    note_members: list of member names for Keep note JSON files.
    keep_dir: the directory prefix that contains the Keep export (e.g. "Takeout/Keep/").
    members_by_dir: dict of basename-without-extension -> member name, for attachment lookup.
    """
    note_members = []
    keep_dir = None
    members_by_dir = {}
    exact_members = set()

    for name in zf.namelist():
        if name.endswith("/"):
            continue
        lower = name.lower()
        # Match a "/keep/" path segment (top folder may be localized, e.g. "Takeout").
        if "/keep/" not in f"/{lower}":
            continue

        directory = posixpath.dirname(name) + "/"
        if keep_dir is None:
            keep_dir = directory

        base = posixpath.basename(name)
        exact_members.add(name)
        # Index by basename-without-extension for the Keep filePath quirk fallback.
        stem = os.path.splitext(base)[0]
        members_by_dir.setdefault(stem, name)

        if lower.endswith(".json") and base.lower() != "labels.txt":
            note_members.append(name)

    return note_members, keep_dir, members_by_dir, exact_members


def build_content(note):
    """Build the plain-text content from textContent and/or listContent."""
    parts = []
    text = note.get("textContent")
    if text:
        parts.append(text)

    list_content = note.get("listContent")
    if isinstance(list_content, list):
        lines = []
        for item in list_content:
            item_text = item.get("text")
            if item_text is not None:
                lines.append(item_text)
        if lines:
            parts.append("\n".join(lines))

    return escape_markdown("\n".join(parts))


def escape_markdown(content):
    """Escape leading '#' on non-heading lines, matching the app's save path.

    When Skeleton Notes serializes a note, a paragraph that starts with '#' but is
    not a heading (i.e. not '#'..'######' followed by whitespace, e.g. a '#hashtag')
    is written with a leading backslash (MarkdownFormatter.serializeParagraph). We
    apply the same escaping here so imported content is already in the app's
    canonical form and does not get rewritten (bumping modifiedAt) on first load.
    """
    escaped_lines = []
    for line in content.split("\n"):
        if line.startswith("#") and not HEADING_RE.match(line):
            line = "\\" + line
        escaped_lines.append(line)
    return "\n".join(escaped_lines)


def usec_to_ms(value):
    """Convert a microsecond timestamp to milliseconds, or None if absent."""
    if value is None:
        return None
    try:
        return int(value) // 1000
    except (TypeError, ValueError):
        return None


def map_status(note):
    if note.get("isTrashed"):
        return STATUS_TRASH
    if note.get("isArchived"):
        return STATUS_ARCHIVE
    return STATUS_ACTIVE


def map_tags(note):
    tags = []
    seen = set()
    for label in note.get("labels") or []:
        name = label.get("name")
        if name and name not in seen:
            seen.add(name)
            tags.append(name)
    return tags


def resolve_attachment(file_path, keep_dir, members_by_dir, exact_members):
    """Resolve a Keep attachment filePath to an actual ZIP member name, or None."""
    if not file_path:
        return None
    # Try the exact path relative to the Keep folder.
    candidate = posixpath.normpath(posixpath.join(keep_dir, file_path))
    if candidate in exact_members:
        return candidate
    # Fallback: Keep sometimes records a wrong extension. Match on basename stem.
    stem = os.path.splitext(posixpath.basename(file_path))[0]
    return members_by_dir.get(stem)


def convert(input_zip, output_zip):
    with zipfile.ZipFile(input_zip) as zf:
        note_members, keep_dir, members_by_dir, exact_members = find_keep_members(zf)

        if not note_members:
            raise SystemExit(
                "error: no Google Keep notes found in the Takeout ZIP "
                "(expected JSON files under a 'Keep/' folder)."
            )

        notes_json = []
        attachments_to_copy = []  # list of (att_id, source_member)
        skipped_attachments = 0

        for member in sorted(note_members):
            try:
                note = json.loads(zf.read(member).decode("utf-8"))
            except (ValueError, UnicodeDecodeError) as exc:
                log_warn(f"could not parse note {member}: {exc}")
                continue

            created = usec_to_ms(note.get("createdTimestampUsec"))
            modified = usec_to_ms(note.get("userEditedTimestampUsec"))
            if created is None:
                created = modified
            if modified is None:
                modified = created
            if created is None:
                created = modified = int(time.time() * 1000)

            attachments_json = []
            for att in note.get("attachments") or []:
                source = resolve_attachment(
                    att.get("filePath"), keep_dir, members_by_dir, exact_members
                )
                if source is None:
                    log_warn(
                        f"attachment '{att.get('filePath')}' referenced by {member} "
                        "was not found in the archive; skipping."
                    )
                    skipped_attachments += 1
                    continue
                att_id = str(uuid.uuid4())
                entry = {"id": att_id}
                mime = att.get("mimetype")
                if mime:
                    entry["mimeType"] = mime
                attachments_json.append(entry)
                attachments_to_copy.append((att_id, source))

            notes_json.append(
                {
                    "id": str(uuid.uuid4()),
                    "title": note.get("title") or "",
                    "content": build_content(note),
                    "createdAt": created,
                    "modifiedAt": modified,
                    "tags": map_tags(note),
                    "status": map_status(note),
                    "attachments": attachments_json,
                }
            )

        with zipfile.ZipFile(output_zip, "w", zipfile.ZIP_DEFLATED) as out:
            # notes.json MUST be written first: the importer errors on attachments before it.
            out.writestr("notes.json", json.dumps(notes_json, ensure_ascii=False))
            for att_id, source in attachments_to_copy:
                out.writestr(f"attachments/{att_id}", zf.read(source))

    return len(notes_json), len(attachments_to_copy), skipped_attachments


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Convert a Google Takeout (Keep) ZIP into a Skeleton Notes backup ZIP."
    )
    parser.add_argument("input", help="Path to the Google Takeout .zip file")
    parser.add_argument(
        "-o",
        "--output",
        help="Output backup ZIP path (default: skeleton-notes-backup.zip next to input)",
    )
    args = parser.parse_args(argv)

    if not os.path.isfile(args.input):
        raise SystemExit(f"error: input file not found: {args.input}")

    output = args.output or os.path.join(
        os.path.dirname(os.path.abspath(args.input)), "skeleton-notes-backup.zip"
    )

    notes, attachments, skipped = convert(args.input, output)

    print(f"Converted {notes} note(s), copied {attachments} attachment(s).")
    if skipped:
        print(f"Skipped {skipped} attachment(s) that could not be found.")
    print(f"Wrote {output}")


if __name__ == "__main__":
    main()
