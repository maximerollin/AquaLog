"""Ephemeral Supabase for CI checks. Never starts or stops the historical #10 stack."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
run_id = re.sub(r"[^a-z0-9-]", "-", os.environ.get("GITHUB_RUN_ID", "local").lower())
project = f"aqualog-ci-{run_id}"
work = ROOT / "build" / project
config = work / "supabase" / "config.toml"


def run(*args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def owned():
    if not config.exists() or f'project_id = "{project}"' not in config.read_text():
        raise RuntimeError("Refusing to manage an unrelated Supabase project")


def status():
    owned()
    result = run("supabase", "status", "--workdir", str(work), "-o", "json", capture_output=True, text=True)
    return json.loads(result.stdout)


parser = argparse.ArgumentParser()
parser.add_argument("mode", choices=["prepare", "verify", "stop"])
args = parser.parse_args()
if args.mode == "prepare":
    if work.exists():
        raise RuntimeError("Use a fresh CI run ID; no database is reset automatically")
    config.parent.mkdir(parents=True)
    for name in ("migrations", "tests", "functions"):
        shutil.copytree(ROOT / "supabase" / name, config.parent / name)
    text = (ROOT / "supabase/config.toml").read_text().replace("aqualog-ticket-10", project).replace("553", "593")
    text += "\n[analytics]\nenabled = false\nport = 59328\n\n[db.pooler]\nenabled = false\nport = 59329\n"
    config.write_text(text)
    with (work / "startup.log").open("w") as log:
        run("supabase", "start", "--workdir", str(work), stdout=log, stderr=subprocess.STDOUT)
    keys = status()
    github_env = os.environ.get("GITHUB_ENV")
    if github_env:
        with open(github_env, "a") as output:
            output.write("AQUALOG_SUPABASE_URL=http://10.0.2.2:59321\n")
            output.write(f"AQUALOG_SUPABASE_ANON_KEY={keys['ANON_KEY']}\n")
    print("Isolated backend ready; only its public client configuration is exported")
elif args.mode == "verify":
    keys = status()
    run("supabase", "test", "db", "--workdir", str(work))
    deno = ["deno"] if shutil.which("deno") else ["npx", "--yes", "deno"]
    environment = os.environ | {
        "SUPABASE_URL": keys["API_URL"], "SUPABASE_ANON_KEY": keys["ANON_KEY"],
        "SUPABASE_SERVICE_ROLE_KEY": keys["SERVICE_ROLE_KEY"],
    }
    run(*deno, "test", "--allow-env", "--allow-net", "supabase/tests/integration/entity_sync_test.ts", env=environment)
else:
    if config.exists():
        owned()
        run("supabase", "stop", "--workdir", str(work))
