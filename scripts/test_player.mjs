// Deterministic tests of the embedded-player adapter, without contacting YouTube.
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';

function setup(autoStart = true) {
  const html = readFileSync('app/src/main/assets/player.html', 'utf8')
    .replace('__VIDEO_ID__', JSON.stringify('fNk_zzaMoSs'))
    .replace('__APP_ORIGIN__', JSON.stringify('https://app.hackertube.tv'))
    .replace('__AUTO_START__', String(autoStart))
    .replace('__START_SECONDS__', '42');
  const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  const calls = [];
  let config;
  const player = {
    getPlayerState: () => player.state,
    getCurrentTime: () => player.time,
    getDuration: () => 100,
    playVideo: () => calls.push('play'),
    pauseVideo: () => calls.push('pause'),
    seekTo: (time, seekAhead) => calls.push(['seek', time, seekAhead]),
    state: 2, time: 50,
  };
  const context = vm.createContext({
    document: { getElementById: () => ({focus: () => calls.push('focus')}) },
    YT: { Player: function(id, options) { config = options; return player; } },
  });
  context.window = context;
  vm.runInContext(script, context);
  context.onYouTubeIframeAPIReady();
  return {context, calls, player, config};
}

test('uses official embed with app identity and visible original controls', () => {
  const {config} = setup();
  assert.equal(config.host, 'https://www.youtube-nocookie.com');
  assert.equal(config.videoId, 'fNk_zzaMoSs');
  assert.equal(config.playerVars.origin, 'https://app.hackertube.tv');
  assert.equal(config.playerVars.controls, 1);
  assert.equal(config.playerVars.start, 42);
});

test('explicit Watch starts playback only when ready', () => {
  const {context, config, calls} = setup();
  assert.equal(calls.length, 0);
  config.events.onReady();
  assert.equal(context.htStatus.ready, true);
  assert.deepEqual(calls, ['play']);
});

test('background before ready cannot autoplay', () => {
  const {context, config, calls} = setup();
  context.htCommand('background');
  config.events.onReady();
  assert.deepEqual(calls, []);
  config.events.onStateChange({data: 1});
  assert.deepEqual(calls, ['pause']);
});

test('restored player is paused until explicit Play', () => {
  const {context, config, calls} = setup(false);
  config.events.onReady();
  assert.deepEqual(calls, []);
  context.htCommand('play');
  assert.deepEqual(calls, ['play']);
});

test('remote toggle and bounded 10-second seeking', () => {
  const {context, config, calls, player} = setup(false);
  config.events.onReady();
  context.htCommand('toggle');
  player.state = 1;
  context.htCommand('toggle');
  player.time = 3;
  context.htCommand('rewind');
  player.time = 98;
  context.htCommand('forward');
  assert.deepEqual(calls, ['play','pause',['seek',0,true],['seek',100,true]]);
});

test('pause in background and no automatic resume', () => {
  const {context, config, calls} = setup(false);
  config.events.onReady();
  context.htCommand('background');
  context.htCommand('foreground');
  assert.deepEqual(calls, ['pause']);
});

test('API errors, autoplay blocking and actual position are exposed', () => {
  const {context, config} = setup(false);
  config.events.onReady();
  config.events.onError({data:153});
  config.events.onAutoplayBlocked();
  assert.equal(context.htSnapshot().error, 153);
  assert.equal(context.htSnapshot().blocked, true);
  assert.equal(context.htSnapshot().time, 50);
  assert.equal(context.htSnapshot().duration, 100);
});
