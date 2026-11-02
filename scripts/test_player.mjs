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
  let currentTrack = {languageCode:'en',languageName:'English'};
  const tracks = [currentTrack,{languageCode:'de',languageName:'German'}];
  const captionCalls = [];
  const player = {
    getOptions: () => ['track','tracklist'],
    getOption: (module,option) => option === 'tracklist' ? tracks : currentTrack,
    setOption: (module,option,value) => {
      captionCalls.push(value);
      // Like the real embed, getOption('track') can retain the last language when Off.
      if (value.languageCode) currentTrack = value;
    },
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
  return {context, calls, player, config, captionCalls};
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

test('captions default Off even if YouTube retains a previous English track', () => {
  const {context,config,captionCalls} = setup(false);
  config.events.onReady();
  assert.equal(captionCalls.length,1);
  assert.equal(Object.keys(captionCalls[0]).length,0);
  assert.equal(context.htSnapshot().captions.current,'');
});

test('subtitle picker exposes tracks, selects German and can turn Off again', () => {
  const {context,config,captionCalls} = setup(false);
  config.events.onReady();
  assert.equal(context.htSnapshot().captions.tracks[1].label,'German');
  context.htSetCaptions(1);
  assert.equal(captionCalls.at(-1).languageCode,'de');
  assert.equal(context.htSnapshot().captions.current,'de');
  context.htSetCaptions(-1);
  assert.equal(context.htSnapshot().captions.current,'');
});

test('subtitle selection survives captions module reload and rejects invalid indices', () => {
  const {context,config,captionCalls} = setup(false);
  config.events.onReady(); context.htSnapshot(); context.htSetCaptions(1);
  config.events.onApiChange();
  assert.equal(captionCalls.at(-1).languageCode,'de');
  const count=captionCalls.length;
  for (const index of [-2,2,100,1.5,'1']) context.htSetCaptions(index);
  assert.equal(captionCalls.length,count);
});

test('unsupported caption module is reported without claiming Off', () => {
  const {context,config,player} = setup(false);
  player.getOptions = () => [];
  config.events.onReady();
  assert.equal(context.htSnapshot().captions.supported,false);
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
