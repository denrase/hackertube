#!/usr/bin/env python3
"""Live YouTube playback regression test. Requires network and disposable TV emulator.
Run: python3 scripts/smoke_player.py --serial emulator-5554
Unlike unit tests, this may fail due to video availability, ads, YouTube or network policy.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('Use a disposable emulator.')
os.environ.setdefault('ADB_MDNS', '0')
PKG = 'app.hackertube.tv'

def adb(*args):
    return subprocess.check_output(['adb', '-s', globals()['args'].serial, *args], text=True)

def key(*codes):
    for code in codes:
        adb('shell','input','keyevent',str(code))
        time.sleep(.4)

def tree():
    adb('shell','uiautomator','dump','/sdcard/ht-player-test.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/ht-player-test.xml'))

def focused_text():
    nodes = [n for n in tree().iter('node') if n.get('focused')=='true']
    # Chromium can expose a DOM-focused node even when a native button owns input focus.
    native = [n for n in nodes if n.get('class')=='android.widget.Button']
    return (native or nodes)[0].get('text','') if nodes else ''

def playback():
    dump = adb('shell','dumpsys','media_session')
    match = re.search(r'HackerTubePlayer app\.hackertube\.tv/HackerTubePlayer.*?active=(\w+).*?state=PlaybackState \{state=(\w+)\(\d+\), position=(\d+)', dump, re.S)
    return (match[1], match[2], int(match[3])) if match else None

def wait_state(expected):
    until = time.monotonic()+40
    while time.monotonic()<until:
        state = playback()
        if state and state[0]=='true' and state[1]==expected:
            return state[2]
        time.sleep(1)
    raise AssertionError(f'Expected {expected}; actual: {playback()}')

def screenshot(name):
    Path('docs/screenshots/'+name).write_bytes(subprocess.check_output(['adb','-s',args.serial,'exec-out','screencap','-p']))

try:
    adb('shell','am','force-stop',PKG)
    adb('shell','am','start','-n',PKG+'/.MainActivity')
    time.sleep(2)
    key(20,23,23)
    start = wait_state('PLAYING')
    time.sleep(3)
    assert wait_state('PLAYING') > start+1500, 'Actual video time must advance'
    activities = adb('shell','dumpsys','activity','activities')
    assert any(PKG+'/.PlayerActivity' in line for line in activities.splitlines() if 'topResumedActivity' in line)
    screenshot('google-tv-player.png')
    print('PASS: real embedded playback advances in HackerTube PlayerActivity')

    key(127) # MEDIA_PAUSE
    paused = wait_state('PAUSED')
    time.sleep(2)
    assert abs(wait_state('PAUSED')-paused)<1200
    key(90) # MEDIA_FAST_FORWARD
    time.sleep(2)
    assert playback()[2] >= paused+8000, playback()
    key(126) # MEDIA_PLAY
    wait_state('PLAYING')
    print('PASS: remote media pause/play and 10-second seeking')

    key(127) # Pause to keep UIAutomator's accessibility tree idle while finding controls.
    wait_state('PAUSED')
    key(22,22,22,22)
    assert focused_text()=='Cinema', focused_text()
    key(23)
    key(23) # surface center plays
    wait_state('PLAYING')
    time.sleep(2)
    screenshot('google-tv-player-cinema.png')
    key(23) # surface center pauses
    wait_state('PAUSED')
    key(20) # surface down restores controls
    assert focused_text()=='Play', focused_text()
    key(126)
    wait_state('PLAYING')
    print('PASS: cinema mode, D-pad center transport and down-to-controls')

    key(3) # Home
    time.sleep(2)
    state = playback()
    assert not state or state[0]=='false', state
    # Return from Home: player reloads at the last observed position, without autoplay.
    adb('shell','am','start','-n',PKG+'/.MainActivity')
    time.sleep(5)
    state = playback()
    assert not state or state[1]!='PLAYING', state
    print('PASS: background playback stops and does not auto-resume')

    # Move existing PlayerActivity to the foreground via the task, then Back to details.
    key(4)
    assert focused_text()=='▶ Watch', focused_text()
    print('PASS: Back returns to the in-app video details')
finally:
    adb('shell','am','force-stop',PKG)
