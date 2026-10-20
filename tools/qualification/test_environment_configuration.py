"""Exercise the public Gradle configuration boundary; values are synthetic markers."""
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]


class EnvironmentConfigurationTest(unittest.TestCase):
    def configure(self, **properties):
        environment = {key: value for key, value in os.environ.items() if not key.startswith("AQUALOG_")}
        return subprocess.run(
            [str(ROOT / "gradlew"), "help", "--console=plain", "--no-configuration-cache",
             *[f"-P{key}={value}" for key, value in properties.items()]],
            cwd=ROOT, env=environment, capture_output=True, text=True,
        )

    def test_shared_backend_is_rejected(self):
        result = self.configure(
            AQUALOG_DEV_SUPABASE_URL="https://shared.invalid",
            AQUALOG_DEV_SUPABASE_ANON_KEY="synthetic-dev",
            AQUALOG_BETA_SUPABASE_URL="https://shared.invalid/",
            AQUALOG_BETA_SUPABASE_ANON_KEY="synthetic-beta",
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("SUPABASE_URL must differ", result.stdout + result.stderr)

    def test_partial_backend_is_rejected(self):
        result = self.configure(AQUALOG_BETA_SUPABASE_URL="https://beta.invalid")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("BETA SUPABASE configuration is incomplete", result.stdout + result.stderr)

    def test_legacy_global_configuration_is_rejected(self):
        result = self.configure(AQUALOG_SUPABASE_URL="https://legacy.invalid")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Use environment-scoped AQUALOG_DEV_, AQUALOG_BETA_ or AQUALOG_PROD_", result.stdout + result.stderr)

    def test_shared_observability_projects_are_rejected(self):
        for field in ("POSTHOG_API_KEY", "FIREBASE_APPLICATION_ID", "FIREBASE_PROJECT_ID"):
            with self.subTest(field=field):
                properties = {}
                for profile in ("DEV", "BETA"):
                    properties.update({
                        f"AQUALOG_{profile}_POSTHOG_HOST": "https://collector.invalid",
                        f"AQUALOG_{profile}_POSTHOG_API_KEY": f"synthetic-{profile}",
                        f"AQUALOG_{profile}_FIREBASE_APPLICATION_ID": f"1:123:android:{profile}",
                        f"AQUALOG_{profile}_FIREBASE_PROJECT_ID": f"synthetic-{profile}",
                        f"AQUALOG_{profile}_FIREBASE_API_KEY": f"synthetic-{profile}",
                    })
                    properties[f"AQUALOG_{profile}_{field}"] = "synthetic-shared"
                result = self.configure(**properties)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(f"{field} must differ", result.stdout + result.stderr)

    def test_unconfigured_and_separate_local_profiles_build(self):
        result = self.configure()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        result = self.configure(
            AQUALOG_DEV_SUPABASE_URL="http://10.0.2.2:57321",
            AQUALOG_DEV_SUPABASE_ANON_KEY="synthetic-dev",
            AQUALOG_BETA_SUPABASE_URL="https://beta.invalid",
            AQUALOG_BETA_SUPABASE_ANON_KEY="synthetic-beta",
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
