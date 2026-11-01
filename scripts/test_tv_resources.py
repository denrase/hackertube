"""Static launcher/manifest checks from Android's TV Ready checklist."""
from pathlib import Path
import struct
import unittest
import xml.etree.ElementTree as ET

ROOT = Path('app/src/main')
ANDROID = '{http://schemas.android.com/apk/res/android}'

class TVResourcesTests(unittest.TestCase):
    def test_leanback_launcher(self):
        manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
        categories = [c.get(ANDROID+'name') for c in manifest.findall('.//category')]
        self.assertIn('android.intent.category.LEANBACK_LAUNCHER', categories)

    def test_no_touchscreen_requirement(self):
        manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
        features = {e.get(ANDROID+'name'): e.get(ANDROID+'required') for e in manifest.findall('uses-feature')}
        self.assertEqual(features['android.hardware.touchscreen'], 'false')
        self.assertEqual(features['android.software.leanback'], 'true')

    def test_tv_launcher_pixel_dimensions(self):
        for density, scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
            for name, size in [('banner',(160,90)), ('icon',(80,80))]:
                png = (ROOT / f'res/mipmap-{density}/{name}.png').read_bytes()
                self.assertEqual(png[:8], b'\x89PNG\r\n\x1a\n')
                self.assertEqual(struct.unpack('>II', png[16:24]), tuple(int(s*scale) for s in size))

    def test_adaptive_icon(self):
        icon = ET.parse(ROOT / 'res/mipmap-anydpi-v26/icon.xml').getroot()
        self.assertEqual(icon.tag, 'adaptive-icon')
        self.assertIsNotNone(icon.find('foreground'))
        self.assertIsNotNone(icon.find('background'))

    def test_no_backup_or_cleartext(self):
        app = ET.parse(ROOT / 'AndroidManifest.xml').getroot().find('application')
        self.assertEqual(app.get(ANDROID+'allowBackup'), 'false')
        self.assertEqual(app.get(ANDROID+'usesCleartextTraffic'), 'false')

if __name__ == '__main__':
    unittest.main()
