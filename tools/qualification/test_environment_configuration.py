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

    def rejected(self, message, **properties):
        result = self.configure(**properties)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(message, result.stdout + result.stderr)

    def test_partial_backend_is_rejected(self):
        self.rejected("SUPABASE configuration is incomplete", AQUALOG_SUPABASE_URL="https://project.invalid")

    def test_retired_profiles_are_rejected(self):
        self.rejected("Use the shared AQUALOG_ configuration", AQUALOG_BETA_SUPABASE_URL="https://old.invalid")

    def test_secret_and_test_store_keys_are_rejected(self):
        for key in ("sk_synthetic", "test_synthetic"):
            self.rejected("RevenueCat requires a public Google Play SDK key", AQUALOG_REVENUECAT_PUBLIC_API_KEY=key)

    def test_partial_observability_is_rejected(self):
        self.rejected("POSTHOG configuration is incomplete", AQUALOG_POSTHOG_API_KEY="synthetic")
        self.rejected("FIREBASE configuration is incomplete", AQUALOG_FIREBASE_PROJECT_ID="synthetic")
        self.rejected("LOGSNAG_REVIEW configuration is incomplete", AQUALOG_LOGSNAG_PROJECT="aqualog")

    def test_unknown_track_is_rejected(self):
        self.rejected("Invalid distribution track", AQUALOG_DISTRIBUTION_TRACK="unknown")

    def test_unconfigured_local_build(self):
        result = self.configure()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_shared_configuration_supports_play_tracks(self):
        for track in ("internal", "alpha", "production"):
            result = self.configure(AQUALOG_SUPABASE_URL="https://project.invalid",
                AQUALOG_SUPABASE_ANON_KEY="synthetic-public", AQUALOG_REVENUECAT_PUBLIC_API_KEY="goog_synthetic",
                AQUALOG_DISTRIBUTION_TRACK=track, AQUALOG_LOGSNAG_PROJECT="aqualog",
                AQUALOG_LOGSNAG_REVIEW_PUBLIC_TOKEN="synthetic-public")
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
