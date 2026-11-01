#!/usr/bin/env python3
"""D-pad regression checks on a disposable TV emulator (clears app data).
Usage: python3 scripts/smoke_tv.py --serial emulator-5554
Requires a built/installed APK and adb on PATH. Never targets a physical TV.
"""
import argparse
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
a = p.parse_args()
if not a.serial.startswith('emulator-'):
    p.error('Use a disposable emulator; this test clears application data.')
os.environ.setdefault('ADB_MDNS', '0')
PKG = 'app.hackertube.tv'

def adb(*args):
    return subprocess.check_output(['adb', '-s', a.serial, *args], text=True)

def keys(*codes):
    for code in codes:
        adb('shell', 'input', 'keyevent', str(code))
        time.sleep(.35)

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/hackertube-test.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/hackertube-test.xml'))

def focused():
    nodes = [n for n in tree().iter('node') if n.get('focused') == 'true']
    assert nodes, 'No focused actionable element'
    return nodes[0]

def expect_focus(label):
    n = focused()
    assert label in (n.get('text', '') + n.get('content-desc', '')), n.attrib

def contains(label):
    return any(label in (n.get('text', '') + n.get('content-desc', '')) for n in tree().iter('node'))

def screenshot(name):
    path = Path('docs/screenshots') / name
    path.parent.mkdir(exist_ok=True)
    path.write_bytes(subprocess.check_output(['adb', '-s', a.serial, 'exec-out', 'screencap', '-p']))

old_scale = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
try:
    adb('shell', 'pm', 'clear', PKG)
    adb('shell', 'am', 'start', '-n', PKG + '/.MainActivity')
    time.sleep(2)
    expect_focus('Discover')
    screenshot('google-tv-home.png')
    keys(20)
    expect_focus('Open video details')
    first = focused().get('content-desc')
    keys(23)
    expect_focus('Watch')
    assert not any(n.get('text') == 'Back' for n in tree().iter('node')), 'Soft Back button should not be present'
    keys(22, 23)
    expect_focus('Unsave')
    keys(20)
    expect_focus('HN comment')
    keys(23)
    assert contains('Read the original on your phone or computer')
    keys(4)
    expect_focus('HN comment')
    keys(4)
    assert focused().get('content-desc') == first, 'Detail dismiss must restore originating card'
    keys(4)
    expect_focus('Discover')
    keys(22, 23)
    expect_focus('Saved')
    keys(20, 23)
    expect_focus('Watch')
    keys(22, 23)
    expect_focus('Save for later')
    keys(4)
    expect_focus('Saved')
    assert contains('No saved videos match')
    keys(4)
    expect_focus('Discover')
    print('PASS: startup, details, save/unsave, empty-state focus and Back-to-menu')

    # Search using the system input path. A real remote enters text through its IME.
    keys(22, 22, 22)
    adb('shell', 'input', 'text', 'zzzznomatchzzzz')
    time.sleep(.5)
    assert contains('No matches.')
    keys(4)  # IME consumes the first Back when the keyboard is open.
    if focused().get('class') == 'android.widget.EditText':
        keys(4)
    expect_focus('Discover')
    assert contains('2074 videos')
    print('PASS: search empty state and Back clears filters')

    keys(20)
    for _ in range(12):
        if 'Next' in focused().get('text', ''):
            break
        keys(20)
    expect_focus('Next')
    keys(23)
    expect_focus('Open video details')
    assert focused().get('content-desc') != first
    keys(4)
    expect_focus('Discover')
    keys(4)  # reset pagination
    expect_focus('Discover')
    print('PASS: page navigation restores first-card focus')

    keys(22, 22, 23)
    assert contains('Explore a topic')
    keys(20, 23)
    assert not contains('Explore a topic')
    keys(4)
    expect_focus('Discover')
    print('PASS: topic selection and Back resets filters')

    keys(20)
    before_recreate = focused().get('content-desc')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    time.sleep(2)
    expect_focus('Open video details')
    assert focused().get('content-desc') == before_recreate, 'Configuration change lost focused video'
    screenshot('google-tv-large-text.png')
    keys(23)
    expect_focus('Watch')
    screenshot('google-tv-details-large-text.png')
    keys(4, 4)
    expect_focus('Discover')
    keys(4)
    activities = adb('shell', 'dumpsys', 'activity', 'activities')
    resumed = [line for line in activities.splitlines() if 'topResumedActivity' in line]
    assert resumed and all(PKG not in line for line in resumed), resumed
    print('PASS: 130% text D-pad navigation and bounded Back exit')
finally:
    if old_scale == 'null':
        adb('shell', 'settings', 'delete', 'system', 'font_scale')
    else:
        adb('shell', 'settings', 'put', 'system', 'font_scale', old_scale)
