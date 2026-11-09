# Publishing runtime bundles

On Raspberry Pi, use the publisher script to package the current Hermes-authored runtime and generate the manifest:

```bash
python3 publish_runtime.py --runtime /path/to/runtime --output /srv/homeos/launcher/v1 --version 1.0.1 --version-code 101
```

Expose the output directory at `/launcher/v1/` on the configured HomeOS HTTP server. Keep `version-code` strictly increasing. The TV polls for updates every five minutes while the launcher is active and also checks once at startup or when the user selects **Check for updates**.
