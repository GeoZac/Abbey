package com.paolovalerdi.abbey.service;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import com.bumptech.glide.request.transition.Transition;
import com.paolovalerdi.abbey.BuildConfig;
import com.paolovalerdi.abbey.R;
import com.paolovalerdi.abbey.appwidgets.AppWidgetBig;
import com.paolovalerdi.abbey.appwidgets.AppWidgetCard;
import com.paolovalerdi.abbey.appwidgets.AppWidgetClassic;
import com.paolovalerdi.abbey.appwidgets.AppWidgetSmall;
import com.paolovalerdi.abbey.auto.AutoMediaIDHelper;
import com.paolovalerdi.abbey.auto.AutoMusicProvider;
import com.paolovalerdi.abbey.glide.AbbeyGlideExtension;
import com.paolovalerdi.abbey.glide.AbbeySimpleTarget;
import com.paolovalerdi.abbey.glide.BlurTransformation;
import com.paolovalerdi.abbey.glide.GlideApp;
import com.paolovalerdi.abbey.glide.GlideRequest;
import com.paolovalerdi.abbey.helper.MediaSearchHelper;
import com.paolovalerdi.abbey.helper.ShuffleHelper;
import com.paolovalerdi.abbey.helper.StopWatch;
import com.paolovalerdi.abbey.model.Album;
import com.paolovalerdi.abbey.model.Artist;
import com.paolovalerdi.abbey.model.Playlist;
import com.paolovalerdi.abbey.model.Song;
import com.paolovalerdi.abbey.provider.HistoryStore;
import com.paolovalerdi.abbey.provider.MusicPlaybackQueueStore;
import com.paolovalerdi.abbey.provider.SongPlayCountStore;
import com.paolovalerdi.abbey.repository.AlbumRepository;
import com.paolovalerdi.abbey.repository.ArtistRepository;
import com.paolovalerdi.abbey.repository.PlaylistRepository;
import com.paolovalerdi.abbey.repository.SongRepository;
import com.paolovalerdi.abbey.repository.TopAndRecentlyPlayedTracksRepository;
import com.paolovalerdi.abbey.service.notification.PlayingNotification;
import com.paolovalerdi.abbey.service.notification.PlayingNotificationImpl;
import com.paolovalerdi.abbey.service.notification.PlayingNotificationImpl24;
import com.paolovalerdi.abbey.service.playback.Playback;
import com.paolovalerdi.abbey.util.MusicUtil;
import com.paolovalerdi.abbey.util.PackageValidator;
import com.paolovalerdi.abbey.util.Util;
import com.paolovalerdi.abbey.util.preferences.PreferenceUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.ACTION_PAUSE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.ACTION_PENDING_QUIT;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.ACTION_PLAY;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.ACTION_PLAY_PLAYLIST;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.ACTION_QUIT;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.ACTION_REWIND;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.ACTION_SKIP;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.ACTION_STOP;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.ACTION_TOGGLE_PAUSE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.APP_WIDGET_UPDATE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.CYCLE_REPEAT;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.DUCK;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.EXTRA_APP_WIDGET_NAME;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.FOCUS_CHANGE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.INTENT_EXTRA_PLAYLIST;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.INTENT_EXTRA_SHUFFLE_MODE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.MEDIA_STORE_CHANGED;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.META_CHANGED;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.MUSIC_PACKAGE_NAME;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.MUSIC_PLAYER_PACKAGE_NAME;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.PLAY_SONG;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.PLAY_STATE_CHANGED;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.PREPARE_NEXT;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.QUEUE_CHANGED;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.RELEASE_WAKELOCK;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.REPEAT_MODE_ALL;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.REPEAT_MODE_CHANGED;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.REPEAT_MODE_NONE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.REPEAT_MODE_THIS;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.RESTORE_QUEUES;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.SAVED_POSITION;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.SAVED_POSITION_IN_TRACK;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.SAVED_REPEAT_MODE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.SAVED_SHUFFLE_MODE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.SAVE_QUEUES;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.SET_POSITION;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.SHUFFLE_MODE_CHANGED;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.SHUFFLE_MODE_NONE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.SHUFFLE_MODE_SHUFFLE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.TAG;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.TOGGLE_FAVORITE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.TOGGLE_SHUFFLE;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.TRACK_ENDED;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.TRACK_WENT_TO_NEXT;
import static com.paolovalerdi.abbey.service.MusicServiceConstantsKt.UNDUCK;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.ALBUM_ART_ON_LOCKSCREEN;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.BLURRED_ALBUM_ART;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.CLASSIC_NOTIFICATION;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.COLORED_NOTIFICATION;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.GAPLESS_PLAYBACK;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.RG_PREAMP_WITHOUT_TAG;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.RG_PREAMP_WITH_TAG;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.RG_SOURCE_MODE;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.RG_SOURCE_MODE_ALBUM;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.RG_SOURCE_MODE_NONE;
import static com.paolovalerdi.abbey.util.preferences.PreferenceConstanstKt.RG_SOURCE_MODE_TRACK;

/**
 * @author Karim Abou Zeid (kabouzeid), Andrew Neal
 */
public class MusicService extends MediaBrowserServiceCompat implements SharedPreferences.OnSharedPreferenceChangeListener, Playback.PlaybackCallbacks {

    private final IBinder musicBind = new MusicBinder();

    public boolean pendingQuit = false;

    private AppWidgetBig appWidgetBig = AppWidgetBig.getInstance();
    private AppWidgetClassic appWidgetClassic = AppWidgetClassic.getInstance();
    private AppWidgetSmall appWidgetSmall = AppWidgetSmall.getInstance();
    private AppWidgetCard appWidgetCard = AppWidgetCard.getInstance();

    private Playback playback;
    private ArrayList<Song> playingQueue = new ArrayList<>();
    private ArrayList<Song> originalPlayingQueue = new ArrayList<>();
    private int position = -1;
    private int nextPosition = -1;
    private int shuffleMode;
    private int repeatMode;
    private boolean queuesRestored;
    private boolean pausedByTransientLossOfFocus;
    private PlayingNotification playingNotification;
    private AudioManager audioManager;
    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;
    private PlaybackHandler playerHandler;
    private final AudioManager.OnAudioFocusChangeListener audioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(final int focusChange) {
            playerHandler.obtainMessage(FOCUS_CHANGE, focusChange, 0).sendToTarget();
        }
    };
    private QueueSaveHandler queueSaveHandler;
    private HandlerThread musicPlayerHandlerThread;
    private HandlerThread queueSaveHandlerThread;
    private SongPlayCountHelper songPlayCountHelper = new SongPlayCountHelper();
    private ThrottledSeekHandler throttledSeekHandler;
    private boolean becomingNoisyReceiverRegistered;
    private IntentFilter becomingNoisyReceiverIntentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction().equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                pause();
            }
        }
    };
    private ContentObserver mediaStoreObserver;
    private boolean notHandledMetaChangedForCurrentTrack;

    private Handler uiThreadHandler;

    private MediaSessionCallback mMediaSessionCallback;

    private PackageValidator mPackageValidator;

    private AutoMusicProvider mMusicProvider;

    private static String getTrackUri(@NonNull Song song) {
        return MusicUtil.getSongFileUri(song.id).toString();
    }


    @Override
    public void onCreate() {
        super.onCreate();
        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.setReferenceCounted(false);


        musicPlayerHandlerThread = new HandlerThread("PlaybackHandler");
        musicPlayerHandlerThread.start();
        playerHandler = new PlaybackHandler(this, musicPlayerHandlerThread.getLooper());

        playback = new MultiPlayer(this);
        playback.setCallbacks(this);

        setupMediaSession();

        // queue saving needs to run on a separate thread so that it doesn't block the playback handler events
        queueSaveHandlerThread = new HandlerThread("QueueSaveHandler", Process.THREAD_PRIORITY_BACKGROUND);
        queueSaveHandlerThread.start();
        queueSaveHandler = new QueueSaveHandler(this, queueSaveHandlerThread.getLooper());

        uiThreadHandler = new Handler();

        registerReceiver(widgetIntentReceiver, new IntentFilter(APP_WIDGET_UPDATE));

        initNotification();

        mediaStoreObserver = new MediaStoreObserver(playerHandler);
        throttledSeekHandler = new ThrottledSeekHandler(playerHandler);
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI, true, mediaStoreObserver);
        /*getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaStoreObserver);*/

        PreferenceUtil.INSTANCE.registerOnSharedPreferenceChangedListener(this);

        restoreState();

        mediaSession.setActive(true);

        mPackageValidator = new PackageValidator(this);
        mMusicProvider = new AutoMusicProvider(this);

        sendBroadcast(new Intent("com.paolovalerdi.abbey.VINYL_MUSIC_PLAYER_MUSIC_SERVICE_CREATED"));
    }

    private AudioManager getAudioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        return audioManager;
    }

    private void setupMediaSession() {
        ComponentName mediaButtonReceiverComponentName = new ComponentName(getApplicationContext(), MediaButtonIntentReceiver.class);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mediaButtonReceiverComponentName);

        PendingIntent mediaButtonReceiverPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, 0);
        mMediaSessionCallback = new MediaSessionCallback();
        mediaSession = new MediaSessionCompat(this, "VinylMusicPlayer", mediaButtonReceiverComponentName, mediaButtonReceiverPendingIntent);
        mediaSession.setCallback(mMediaSessionCallback);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                | MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);
        mediaSession.setMediaButtonReceiver(mediaButtonReceiverPendingIntent);
        setSessionToken(mediaSession.getSessionToken());
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getAction() != null) {
                restoreQueuesAndPositionIfNecessary();
                String action = intent.getAction();
                switch (action) {
                    case ACTION_TOGGLE_PAUSE:
                        if (isPlaying()) {
                            pause();
                        } else {
                            play();
                        }
                        break;
                    case ACTION_PAUSE:
                        pause();
                        break;
                    case ACTION_PLAY:
                        play();
                        break;
                    case ACTION_PLAY_PLAYLIST:
                        Playlist playlist = intent.getParcelableExtra(INTENT_EXTRA_PLAYLIST);
                        int shuffleMode = intent.getIntExtra(INTENT_EXTRA_SHUFFLE_MODE, getShuffleMode());
                        if (playlist != null) {
                            ArrayList<Song> playlistSongs = playlist.getSongs(getApplicationContext());
                            if (!playlistSongs.isEmpty()) {
                                if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
                                    int startPosition = 0;
                                    if (!playlistSongs.isEmpty()) {
                                        startPosition = new Random().nextInt(playlistSongs.size());
                                    }
                                    openQueue(playlistSongs, startPosition, true);
                                    setShuffleMode(shuffleMode);
                                } else {
                                    openQueue(playlistSongs, 0, true);
                                }
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.playlist_is_empty, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(getApplicationContext(), R.string.playlist_is_empty, Toast.LENGTH_LONG).show();
                        }
                        break;
                    case ACTION_REWIND:
                        back(true);
                        break;
                    case ACTION_SKIP:
                        playNextSong(true);
                        break;
                    case ACTION_STOP:
                    case ACTION_QUIT:
                        pendingQuit = false;
                        quit();
                        break;
                    case ACTION_PENDING_QUIT:
                        pendingQuit = true;
                        break;
                }
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(widgetIntentReceiver);
        if (becomingNoisyReceiverRegistered) {
            unregisterReceiver(becomingNoisyReceiver);
            becomingNoisyReceiverRegistered = false;
        }
        mediaSession.setActive(false);
        quit();
        releaseResources();
        getContentResolver().unregisterContentObserver(mediaStoreObserver);
        PreferenceUtil.INSTANCE.unregisterOnSharedPreferenceChangedListener(this);
        wakeLock.release();

        sendBroadcast(new Intent("com.paolovalerdi.abbey.VINYL_MUSIC_PLAYER_MUSIC_SERVICE_DESTROYED"));
    }

    @Override
    public IBinder onBind(Intent intent) {
        // For Android auto, need to call super, or onGetRoot won't be called.
        if (intent != null && "android.media.browse.MediaBrowserService".equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return musicBind;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        Log.d("MUSICSERVICE", clientPackageName);
        // Check origin to ensure we're not allowing any arbitrary app to browse app contents
        if (!mPackageValidator.isCallerAllowed(this, clientPackageName, clientUid)) {
            // Request from an untrusted package: return an empty browser root
            return new MediaBrowserServiceCompat.BrowserRoot(AutoMediaIDHelper.MEDIA_ID_EMPTY_ROOT, null);
        }

        return new BrowserRoot(AutoMediaIDHelper.MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentId, @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (AutoMediaIDHelper.MEDIA_ID_EMPTY_ROOT.equals(parentId)) {
            result.sendResult(new ArrayList<>());
        } else if (mMusicProvider.isInitialized()) {
            result.sendResult(mMusicProvider.getChildren(parentId, getResources()));
        } else {
            result.detach();
            mMusicProvider.retrieveMediaAsync(success -> result.sendResult(mMusicProvider.getChildren(parentId, getResources())));
        }
    }

    private static final class QueueSaveHandler extends Handler {
        @NonNull
        private final WeakReference<MusicService> mService;

        public QueueSaveHandler(final MusicService service, @NonNull final Looper looper) {
            super(looper);
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            final MusicService service = mService.get();
            switch (msg.what) {
                case SAVE_QUEUES:
                    service.saveQueuesImpl();
                    break;
            }
        }
    }

    private void saveQueuesImpl() {
        MusicPlaybackQueueStore.getInstance(this).saveQueues(playingQueue, originalPlayingQueue);
    }

    private void savePosition() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(SAVED_POSITION, getPosition()).apply();
    }

    private void savePositionInTrack() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(SAVED_POSITION_IN_TRACK, getSongProgressMillis()).apply();
    }

    public void saveState() {
        saveQueues();
        savePosition();
        savePositionInTrack();
    }

    private void saveQueues() {
        queueSaveHandler.removeMessages(SAVE_QUEUES);
        queueSaveHandler.sendEmptyMessage(SAVE_QUEUES);
    }

    private void restoreState() {
        shuffleMode = PreferenceManager.getDefaultSharedPreferences(this).getInt(SAVED_SHUFFLE_MODE, 0);
        repeatMode = PreferenceManager.getDefaultSharedPreferences(this).getInt(SAVED_REPEAT_MODE, 0);
        handleAndSendChangeInternal(SHUFFLE_MODE_CHANGED);
        handleAndSendChangeInternal(REPEAT_MODE_CHANGED);

        playerHandler.removeMessages(RESTORE_QUEUES);
        playerHandler.sendEmptyMessage(RESTORE_QUEUES);
    }

    private synchronized void restoreQueuesAndPositionIfNecessary() {
        if (!queuesRestored && playingQueue.isEmpty()) {
            ArrayList<Song> restoredQueue = MusicPlaybackQueueStore.getInstance(this).getSavedPlayingQueue();
            ArrayList<Song> restoredOriginalQueue = MusicPlaybackQueueStore.getInstance(this).getSavedOriginalPlayingQueue();
            int restoredPosition = PreferenceManager.getDefaultSharedPreferences(this).getInt(SAVED_POSITION, -1);
            int restoredPositionInTrack = PreferenceManager.getDefaultSharedPreferences(this).getInt(SAVED_POSITION_IN_TRACK, -1);

            if (restoredQueue.size() > 0 && restoredQueue.size() == restoredOriginalQueue.size() && restoredPosition != -1) {
                this.originalPlayingQueue = restoredOriginalQueue;
                this.playingQueue = restoredQueue;

                position = restoredPosition;
                openCurrent();
                prepareNext();

                if (restoredPositionInTrack > 0) seek(restoredPositionInTrack);

                notHandledMetaChangedForCurrentTrack = true;
                sendChangeInternal(META_CHANGED);
                sendChangeInternal(QUEUE_CHANGED);
            }
        }
        queuesRestored = true;
    }

    private void quit() {
        pause();
        playingNotification.stop();

        closeAudioEffectSession();
        getAudioManager().abandonAudioFocus(audioFocusListener);
        stopSelf();
    }

    private void releaseResources() {
        playerHandler.removeCallbacksAndMessages(null);
        musicPlayerHandlerThread.quitSafely();
        queueSaveHandler.removeCallbacksAndMessages(null);
        queueSaveHandlerThread.quitSafely();
        playback.release();
        playback = null;
        mediaSession.release();
    }

    public boolean isPlaying() {
        return playback != null && playback.isPlaying();
    }

    public int getPosition() {
        return position;
    }

    public void playNextSong(boolean force) {
        playSongAt(getNextPosition(force));
    }

    private boolean openTrackAndPrepareNextAt(int position) {
        synchronized (this) {
            this.position = position;
            boolean prepared = openCurrent();
            if (prepared) prepareNextImpl();
            notifyChange(META_CHANGED);
            notHandledMetaChangedForCurrentTrack = false;
            return prepared;
        }
    }

    private boolean openCurrent() {
        synchronized (this) {
            try {
                applyReplayGain();
                return playback.setDataSource(getTrackUri(getCurrentSong()));
            } catch (Exception e) {
                return false;
            }
        }
    }

    private void prepareNext() {
        playerHandler.removeMessages(PREPARE_NEXT);
        playerHandler.obtainMessage(PREPARE_NEXT).sendToTarget();
    }

    private boolean prepareNextImpl() {
        synchronized (this) {
            try {
                int nextPosition = getNextPosition(false);
                playback.setNextDataSource(getTrackUri(getSongAt(nextPosition)));
                this.nextPosition = nextPosition;
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private void closeAudioEffectSession() {
        final Intent audioEffectsIntent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playback.getAudioSessionId());
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(audioEffectsIntent);
    }

    private boolean requestFocus() {
        return (getAudioManager().requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    public void initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !PreferenceUtil.INSTANCE.getClassicNotification()) {
            playingNotification = new PlayingNotificationImpl24();
        } else {
            playingNotification = new PlayingNotificationImpl();
        }
        playingNotification.init(this);
    }

    public void updateNotification() {
        if (playingNotification != null && getCurrentSong().id != -1) {
            playingNotification.update();
        }
    }

    private void updateMediaSessionPlaybackState() {
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(MEDIA_SESSION_ACTIONS)
                .setState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        getSongProgressMillis(), 1);

        setCustomAction(stateBuilder);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void setCustomAction(PlaybackStateCompat.Builder stateBuilder) {
        int repeatIcon = R.drawable.ic_repeat_white_nocircle_48dp;  // REPEAT_MODE_NONE
        if (getRepeatMode() == REPEAT_MODE_THIS) {
            repeatIcon = R.drawable.ic_repeat_one_white_circle_48dp;
        } else if (getRepeatMode() == REPEAT_MODE_ALL) {
            repeatIcon = R.drawable.ic_repeat_white_circle_48dp;
        }
        stateBuilder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                CYCLE_REPEAT, getString(R.string.action_cycle_repeat), repeatIcon)
                .build());

        final int shuffleIcon = getShuffleMode() == SHUFFLE_MODE_NONE ? R.drawable.ic_shuffle_white_nocircle_48dp : R.drawable.ic_shuffle_white_circle_48dp;
        stateBuilder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                TOGGLE_SHUFFLE, getString(R.string.action_toggle_shuffle), shuffleIcon)
                .build());

        final int favoriteIcon = MusicUtil.isFavorite(getApplicationContext(), getCurrentSong()) ? R.drawable.ic_favorite_white_circle_48dp : R.drawable.ic_favorite_border_white_nocircle_48dp;
        stateBuilder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                TOGGLE_FAVORITE, getString(R.string.action_toggle_favorite), favoriteIcon)
                .build());
    }

    private void updateMediaSessionMetaData() {
        final Song song = getCurrentSong();

        if (song.id == -1) {
            mediaSession.setMetadata(null);
            return;
        }

        final MediaMetadataCompat.Builder metaData = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artistName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.artistName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.albumName)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, getPosition() + 1)
                .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, song.year)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            metaData.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, getPlayingQueue().size());
        }

        if (PreferenceUtil.INSTANCE.getAlbumArtOnLockScreen()) {
            final Point screenSize = Util.getScreenSize(MusicService.this);
            GlideRequest request = GlideApp.with(MusicService.this)
                    .asBitmap()
                    .load(AbbeyGlideExtension.getSongModel(song))
                    .transition(AbbeyGlideExtension.getDefaultTransition())
                    .songOptions(song);
            if (PreferenceUtil.INSTANCE.getBlurredAlbumArt()) {
                request.transform(new BlurTransformation.Builder(MusicService.this).build());
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    request.into(new AbbeySimpleTarget<Bitmap>(screenSize.x, screenSize.y) {
                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            super.onLoadFailed(errorDrawable);
                            mediaSession.setMetadata(metaData.build());
                        }

                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> glideAnimation) {
                            metaData.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, copy(resource));
                            mediaSession.setMetadata(metaData.build());
                        }
                    });
                }
            });
        } else {
            mediaSession.setMetadata(metaData.build());
        }
    }

    private static Bitmap copy(Bitmap bitmap) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == null) {
            config = Bitmap.Config.RGB_565;
        }
        try {
            return bitmap.copy(config, false);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    public void runOnUiThread(Runnable runnable) {
        uiThreadHandler.post(runnable);
    }

    public Song getCurrentSong() {
        return getSongAt(getPosition());
    }

    public Song getSongAt(int position) {
        if (position >= 0 && position < getPlayingQueue().size()) {
            return getPlayingQueue().get(position);
        } else {
            return Song.EMPTY_SONG;
        }
    }

    public int getNextPosition(boolean force) {
        int position = getPosition() + 1;
        switch (getRepeatMode()) {
            case REPEAT_MODE_ALL:
                if (isLastTrack()) {
                    position = 0;
                }
                break;
            case REPEAT_MODE_THIS:
                if (force) {
                    if (isLastTrack()) {
                        position = 0;
                    }
                } else {
                    position -= 1;
                }
                break;
            default:
            case REPEAT_MODE_NONE:
                if (isLastTrack()) {
                    position -= 1;
                }
                break;
        }
        return position;
    }

    private boolean isLastTrack() {
        return getPosition() == getPlayingQueue().size() - 1;
    }

    public ArrayList<Song> getPlayingQueue() {
        return playingQueue;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(final int repeatMode) {
        switch (repeatMode) {
            case REPEAT_MODE_NONE:
            case REPEAT_MODE_ALL:
            case REPEAT_MODE_THIS:
                this.repeatMode = repeatMode;
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putInt(SAVED_REPEAT_MODE, repeatMode)
                        .apply();
                prepareNext();
                handleAndSendChangeInternal(REPEAT_MODE_CHANGED);
                break;
        }
    }

    public void openQueue(@Nullable final ArrayList<Song> playingQueue, final int startPosition, final boolean startPlaying) {
        if (playingQueue != null && !playingQueue.isEmpty() && startPosition >= 0 && startPosition < playingQueue.size()) {
            // it is important to copy the playing queue here first as we might add/remove songs later
            originalPlayingQueue = new ArrayList<>(playingQueue);
            this.playingQueue = new ArrayList<>(originalPlayingQueue);

            int position = startPosition;
            if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
                ShuffleHelper.makeShuffleList(this.playingQueue, startPosition);
                position = 0;
            }
            if (startPlaying) {
                playSongAt(position);
            } else {
                setPosition(position);
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    public void addSong(int position, Song song) {
        playingQueue.add(position, song);
        originalPlayingQueue.add(position, song);
        notifyChange(QUEUE_CHANGED);
    }

    public void addSong(Song song) {
        playingQueue.add(song);
        originalPlayingQueue.add(song);
        notifyChange(QUEUE_CHANGED);
    }

    public void addSongs(int position, List<Song> songs) {
        playingQueue.addAll(position, songs);
        originalPlayingQueue.addAll(position, songs);
        notifyChange(QUEUE_CHANGED);
    }

    public void addSongs(List<Song> songs) {
        playingQueue.addAll(songs);
        originalPlayingQueue.addAll(songs);
        notifyChange(QUEUE_CHANGED);
    }

    public void removeSong(int position) {
        if (getShuffleMode() == SHUFFLE_MODE_NONE) {
            playingQueue.remove(position);
            originalPlayingQueue.remove(position);
        } else {
            originalPlayingQueue.remove(playingQueue.remove(position));
        }

        rePosition(position);

        notifyChange(QUEUE_CHANGED);
    }

    public void removeSong(@NonNull Song song) {
        for (int i = 0; i < playingQueue.size(); i++) {
            if (playingQueue.get(i).id == song.id) {
                playingQueue.remove(i);
                rePosition(i);
            }
        }
        for (int i = 0; i < originalPlayingQueue.size(); i++) {
            if (originalPlayingQueue.get(i).id == song.id) {
                originalPlayingQueue.remove(i);
            }
        }
        notifyChange(QUEUE_CHANGED);
    }

    private void rePosition(int deletedPosition) {
        int currentPosition = getPosition();
        if (deletedPosition < currentPosition) {
            position = currentPosition - 1;
        } else if (deletedPosition == currentPosition) {
            if (playingQueue.size() > deletedPosition) {
                setPosition(position);
            } else {
                setPosition(position - 1);
            }
        }
    }

    public void moveSong(int from, int to) {
        if (from == to) return;
        final int currentPosition = getPosition();
        Song songToMove = playingQueue.remove(from);
        playingQueue.add(to, songToMove);
        if (getShuffleMode() == SHUFFLE_MODE_NONE) {
            Song tmpSong = originalPlayingQueue.remove(from);
            originalPlayingQueue.add(to, tmpSong);
        }
        if (from > currentPosition && to <= currentPosition) {
            position = currentPosition + 1;
        } else if (from < currentPosition && to >= currentPosition) {
            position = currentPosition - 1;
        } else if (from == currentPosition) {
            position = to;
        }
        notifyChange(QUEUE_CHANGED);
    }

    public void clearQueue() {
        playingQueue.clear();
        originalPlayingQueue.clear();

        setPosition(-1);
        notifyChange(QUEUE_CHANGED);
    }

    public void playSongAt(final int position) {
        // handle this on the handlers thread to avoid blocking the ui thread
        playerHandler.removeMessages(PLAY_SONG);
        playerHandler.obtainMessage(PLAY_SONG, position, 0).sendToTarget();
    }

    public void setPosition(final int position) {
        // handle this on the handlers thread to avoid blocking the ui thread
        playerHandler.removeMessages(SET_POSITION);
        playerHandler.obtainMessage(SET_POSITION, position, 0).sendToTarget();
    }

    private void playSongAtImpl(int position) {
        if (openTrackAndPrepareNextAt(position)) {
            play();
        } else {
            Toast.makeText(this, getResources().getString(R.string.unplayable_file), Toast.LENGTH_SHORT).show();
        }
    }

    public void pause() {
        pausedByTransientLossOfFocus = false;
        if (playback.isPlaying()) {
            playback.pause();
            notifyChange(PLAY_STATE_CHANGED);
        }
    }

    public void play() {
        synchronized (this) {
            if (requestFocus()) {
                if (!playback.isPlaying()) {
                    if (!playback.isInitialized()) {
                        playSongAt(getPosition());
                    } else {
                        playback.start();
                        if (!becomingNoisyReceiverRegistered) {
                            registerReceiver(becomingNoisyReceiver, becomingNoisyReceiverIntentFilter);
                            becomingNoisyReceiverRegistered = true;
                        }
                        if (notHandledMetaChangedForCurrentTrack) {
                            handleChangeInternal(META_CHANGED);
                            notHandledMetaChangedForCurrentTrack = false;
                        }
                        notifyChange(PLAY_STATE_CHANGED);

                        // fixes a bug where the volume would stay ducked because the AudioManager.AUDIOFOCUS_GAIN event is not sent
                        playerHandler.removeMessages(DUCK);
                        playerHandler.sendEmptyMessage(UNDUCK);
                    }
                }
            } else {
                Toast.makeText(this, getResources().getString(R.string.audio_focus_denied), Toast.LENGTH_SHORT).show();
            }
        }
    }

   /* public void playSongs(ArrayList<Song> songs, int shuffleMode) {
        if (songs != null && !songs.isEmpty()) {
            if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
                int startPosition = 0;
                if (!songs.isEmpty()) {
                    startPosition = new Random().nextInt(songs.size());
                }
                openQueue(songs, startPosition, false);
                setShuffleMode(shuffleMode);
            } else {
                openQueue(songs, 0, false);
            }
            play();
        } else {
            Toast.makeText(getApplicationContext(), R.string.playlist_is_empty, Toast.LENGTH_LONG).show();
        }
    } */

    private void applyReplayGain() {
        byte mode = PreferenceUtil.INSTANCE.getReplayGainSourceMode();
        if (mode != RG_SOURCE_MODE_NONE) {
            Song song = getCurrentSong();

            float adjust = 0f;
            float rgTrack = song.getReplayGainTrack();
            float rgAlbum = song.getReplayGainAlbum();

            if (mode == RG_SOURCE_MODE_ALBUM) {
                adjust = (rgTrack != 0 ? rgTrack : adjust);
                adjust = (rgAlbum != 0 ? rgAlbum : adjust);
            } else if (mode == RG_SOURCE_MODE_TRACK) {
                adjust = (rgAlbum != 0 ? rgAlbum : adjust);
                adjust = (rgTrack != 0 ? rgTrack : adjust);
            }

            if (adjust == 0) {
                adjust = PreferenceUtil.INSTANCE.getReplayGainPreampWithoutTag();
            } else {
                adjust += PreferenceUtil.INSTANCE.getReplayGainPreampWithTag();
            }

            float rgResult = ((float) Math.pow(10, (adjust / 20)));
            rgResult = Math.max(0, Math.min(1, rgResult));

            playback.setReplayGain(rgResult);
        } else {
            playback.setReplayGain(Float.NaN);
        }
    }

    public void playPreviousSong(boolean force) {
        playSongAt(getPreviousPosition(force));
    }

    public void back(boolean force) {
        if (getSongProgressMillis() > 5000) {
            seek(0);
        } else {
            playPreviousSong(force);
        }
    }

    public int getPreviousPosition(boolean force) {
        int newPosition = getPosition() - 1;
        switch (repeatMode) {
            case REPEAT_MODE_ALL:
                if (newPosition < 0) {
                    newPosition = getPlayingQueue().size() - 1;
                }
                break;
            case REPEAT_MODE_THIS:
                if (force) {
                    if (newPosition < 0) {
                        newPosition = getPlayingQueue().size() - 1;
                    }
                } else {
                    newPosition = getPosition();
                }
                break;
            default:
            case REPEAT_MODE_NONE:
                if (newPosition < 0) {
                    newPosition = 0;
                }
                break;
        }
        return newPosition;
    }

    public int getSongProgressMillis() {
        return playback.position();
    }

    public int getSongDurationMillis() {
        return playback.duration();
    }

    public long getQueueDurationMillis(int position) {
        long duration = 0;
        for (int i = position + 1; i < playingQueue.size(); i++)
            duration += playingQueue.get(i).duration;
        return duration;
    }

    public int seek(int millis) {
        synchronized (this) {
            try {
                int newPosition = playback.seek(millis);
                throttledSeekHandler.notifySeek();
                return newPosition;
            } catch (Exception e) {
                return -1;
            }
        }
    }

    public void cycleRepeatMode() {
        switch (getRepeatMode()) {
            case REPEAT_MODE_NONE:
                setRepeatMode(REPEAT_MODE_ALL);
                break;
            case REPEAT_MODE_ALL:
                setRepeatMode(REPEAT_MODE_THIS);
                break;
            default:
                setRepeatMode(REPEAT_MODE_NONE);
                break;
        }
    }

    public void toggleShuffle() {
        if (getShuffleMode() == SHUFFLE_MODE_NONE) {
            setShuffleMode(SHUFFLE_MODE_SHUFFLE);
        } else {
            setShuffleMode(SHUFFLE_MODE_NONE);
        }
    }

    public int getShuffleMode() {
        return shuffleMode;
    }

    public void setShuffleMode(final int shuffleMode) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putInt(SAVED_SHUFFLE_MODE, shuffleMode)
                .apply();
        switch (shuffleMode) {
            case SHUFFLE_MODE_SHUFFLE:
                this.shuffleMode = shuffleMode;
                ShuffleHelper.makeShuffleList(this.getPlayingQueue(), getPosition());
                position = 0;
                break;
            case SHUFFLE_MODE_NONE:
                this.shuffleMode = shuffleMode;
                int currentSongId = getCurrentSong().id;
                playingQueue = new ArrayList<>(originalPlayingQueue);
                int newPosition = 0;
                for (Song song : getPlayingQueue()) {
                    if (song.id == currentSongId) {
                        newPosition = getPlayingQueue().indexOf(song);
                    }
                }
                position = newPosition;
                break;
        }
        handleAndSendChangeInternal(SHUFFLE_MODE_CHANGED);
        notifyChange(QUEUE_CHANGED);
    }

    private void notifyChange(@NonNull final String what) {
        handleAndSendChangeInternal(what);
        sendPublicIntent(what);
    }

    private void handleAndSendChangeInternal(@NonNull final String what) {
        handleChangeInternal(what);
        sendChangeInternal(what);
    }

    // to let other apps know whats playing. i.E. last.fm (scrobbling) or musixmatch
    private void sendPublicIntent(@NonNull final String what) {
        final Intent intent = new Intent(what.replace(MUSIC_PLAYER_PACKAGE_NAME, MUSIC_PACKAGE_NAME));

        final Song song = getCurrentSong();

        intent.putExtra("id", song.id);

        intent.putExtra("artist", song.artistName);
        intent.putExtra("album", song.albumName);
        intent.putExtra("track", song.title);

        intent.putExtra("duration", song.duration);
        intent.putExtra("position", (long) getSongProgressMillis());

        intent.putExtra("playing", isPlaying());

        intent.putExtra("scrobbling_source", MUSIC_PLAYER_PACKAGE_NAME);

        sendStickyBroadcast(intent);
    }

    private void sendChangeInternal(final String what) {
        sendBroadcast(new Intent(what));
        appWidgetBig.notifyChange(this, what);
        appWidgetClassic.notifyChange(this, what);
        appWidgetSmall.notifyChange(this, what);
        appWidgetCard.notifyChange(this, what);
    }

    private static final long MEDIA_SESSION_ACTIONS = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_STOP
            | PlaybackStateCompat.ACTION_SEEK_TO;

    private void handleChangeInternal(@NonNull final String what) {
        switch (what) {
            case PLAY_STATE_CHANGED:
                updateNotification();
                updateMediaSessionPlaybackState();
                final boolean isPlaying = isPlaying();
                if (!isPlaying && getSongProgressMillis() > 0) {
                    savePositionInTrack();
                }
                songPlayCountHelper.notifyPlayStateChanged(isPlaying);
                break;
            case META_CHANGED:
                updateNotification();
                updateMediaSessionMetaData();
                savePosition();
                savePositionInTrack();
                final Song currentSong = getCurrentSong();
                HistoryStore.getInstance(this).addSongId(currentSong.id);
                if (songPlayCountHelper.shouldBumpPlayCount()) {
                    SongPlayCountStore.getInstance(this).bumpPlayCount(songPlayCountHelper.getSong().id);
                }
                songPlayCountHelper.notifySongChanged(currentSong);
                break;
            case QUEUE_CHANGED:
                updateMediaSessionMetaData(); // because playing queue size might have changed
                saveState();
                if (playingQueue.size() > 0) {
                    prepareNext();
                } else {
                    playingNotification.stop();
                }
                break;
        }
    }

    public int getAudioSessionId() {
        return playback.getAudioSessionId();
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    public void releaseWakeLock() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public void acquireWakeLock(long milli) {
        wakeLock.acquire(milli);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case GAPLESS_PLAYBACK:
                if (sharedPreferences.getBoolean(key, false)) {
                    prepareNext();
                } else {
                    playback.setNextDataSource(null);
                }
                break;
            case ALBUM_ART_ON_LOCKSCREEN:
            case BLURRED_ALBUM_ART:
                updateMediaSessionMetaData();
                break;
            case COLORED_NOTIFICATION:
                updateNotification();
                break;
            case CLASSIC_NOTIFICATION:
                initNotification();
                updateNotification();
                break;
            case RG_SOURCE_MODE:
                applyReplayGain();
                break;
            case RG_PREAMP_WITH_TAG:
                applyReplayGain();
                break;
            case RG_PREAMP_WITHOUT_TAG:
                applyReplayGain();
                break;
        }
    }

    @Override
    public void onTrackWentToNext() {
        playerHandler.sendEmptyMessage(TRACK_WENT_TO_NEXT);
    }

    @Override
    public void onTrackEnded() {
        acquireWakeLock(30000);
        playerHandler.sendEmptyMessage(TRACK_ENDED);
    }

    public final class MediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPlay() {
            play();
        }

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            openQueue(MediaSearchHelper.INSTANCE.search(MusicService.this, query, extras), 0, true);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            super.onPlayFromMediaId(mediaId, extras);

            final String musicId = AutoMediaIDHelper.extractMusicID(mediaId);
            final int itemId = musicId != null ? Integer.valueOf(musicId) : -1;
            final ArrayList<Song> songs = new ArrayList<>();

            final String category = AutoMediaIDHelper.extractCategory(mediaId);
            switch (category) {
                case AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM:
                    Album album = AlbumRepository.getAlbum(getApplicationContext(), itemId);
                    songs.addAll(album.songs);
                    openQueue(songs, 0, true);
                    break;

                case AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST:
                    Artist artist = ArtistRepository.getArtist(getApplicationContext(), itemId);
                    songs.addAll(artist.getSongs());
                    openQueue(songs, 0, true);
                    break;

                case AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_PLAYLIST:
                    Playlist playlist = PlaylistRepository.getPlaylist(getApplicationContext(), itemId);
                    songs.addAll(playlist.getSongs(getApplicationContext()));
                    openQueue(songs, 0, true);
                    break;

                case AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY:
                case AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_TOP_TRACKS:
                case AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_QUEUE:
                    List<Song> tracks;
                    switch (category) {
                        case AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_HISTORY:
                            tracks = TopAndRecentlyPlayedTracksRepository.getRecentlyPlayedTracks(getApplicationContext());
                            break;
                        case AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_TOP_TRACKS:
                            tracks = TopAndRecentlyPlayedTracksRepository.getTopTracks(getApplicationContext());
                            break;
                        default:
                            tracks = MusicPlaybackQueueStore.getInstance(MusicService.this).getSavedOriginalPlayingQueue();
                            break;
                    }
                    songs.addAll(tracks);
                    int songIndex = MusicUtil.indexOfSongInList(tracks, itemId);
                    if (songIndex == -1) {
                        songIndex = 0;
                    }
                    openQueue(songs, songIndex, true);
                    break;

                case AutoMediaIDHelper.MEDIA_ID_MUSICS_BY_SHUFFLE:
                    ArrayList<Song> allSongs = SongRepository.getAllSongs(getApplicationContext());
                    ShuffleHelper.makeShuffleList(allSongs, -1);
                    openQueue(allSongs, 0, true);
                    break;

                default:
                    break;
            }

            play();
        }


        @Override
        public void onPause() {
            pause();
        }

        @Override
        public void onSkipToNext() {
            playNextSong(true);
        }

        @Override
        public void onSkipToPrevious() {
            back(true);
        }

        @Override
        public void onStop() {
            quit();
        }

        @Override
        public void onSeekTo(long pos) {
            seek((int) pos);
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            return MediaButtonIntentReceiver.handleIntent(MusicService.this, mediaButtonEvent);
        }

        @Override
        public void onCustomAction(@NonNull String action, Bundle extras) {
            switch (action) {
                case CYCLE_REPEAT:
                    cycleRepeatMode();
                    updateMediaSessionPlaybackState();
                    break;

                case TOGGLE_SHUFFLE:
                    toggleShuffle();
                    updateMediaSessionPlaybackState();
                    break;

                case TOGGLE_FAVORITE:
                    MusicUtil.toggleFavorite(getApplicationContext(), getCurrentSong());
                    updateMediaSessionPlaybackState();
                    break;

                default:
                    Log.d(TAG, "Unsupported action: " + action);
                    break;
            }
        }
    }

    private static final class PlaybackHandler extends Handler {
        @NonNull
        private final WeakReference<MusicService> mService;
        private float currentDuckVolume = 1.0f;

        PlaybackHandler(final MusicService service, @NonNull final Looper looper) {
            super(looper);
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(@NonNull final Message msg) {
            final MusicService service = mService.get();
            if (service == null) {
                return;
            }

            switch (msg.what) {
                case DUCK:
                    if (PreferenceUtil.INSTANCE.getAudioDucking()) {
                        currentDuckVolume -= .05f;
                        if (currentDuckVolume > .2f) {
                            sendEmptyMessageDelayed(DUCK, 10);
                        } else {
                            currentDuckVolume = .2f;
                        }
                    } else {
                        currentDuckVolume = 1f;
                    }
                    service.playback.setDuckingFactor(currentDuckVolume);
                    break;

                case UNDUCK:
                    if (PreferenceUtil.INSTANCE.getAudioDucking()) {
                        currentDuckVolume += .03f;
                        if (currentDuckVolume < 1f) {
                            sendEmptyMessageDelayed(UNDUCK, 10);
                        } else {
                            currentDuckVolume = 1f;
                        }
                    } else {
                        currentDuckVolume = 1f;
                    }
                    service.playback.setDuckingFactor(currentDuckVolume);
                    break;

                case TRACK_WENT_TO_NEXT:
                    if (service.getRepeatMode() == REPEAT_MODE_NONE && service.isLastTrack()) {
                        service.pause();
                        service.seek(0);
                    } else {
                        service.position = service.nextPosition;
                        service.prepareNextImpl();
                        service.notifyChange(META_CHANGED);
                    }
                    break;

                case TRACK_ENDED:
                    // if there is a timer finished, don't continue
                    if (service.pendingQuit ||
                            service.getRepeatMode() == REPEAT_MODE_NONE && service.isLastTrack()) {
                        service.notifyChange(PLAY_STATE_CHANGED);
                        service.seek(0);
                        if (service.pendingQuit) {
                            service.pendingQuit = false;
                            service.quit();
                            break;
                        }
                        service.notifyChange(PLAY_STATE_CHANGED);
                        service.seek(0);
                    } else {
                        service.playNextSong(false);
                    }
                    sendEmptyMessage(RELEASE_WAKELOCK);
                    break;

                case RELEASE_WAKELOCK:
                    service.releaseWakeLock();
                    break;

                case PLAY_SONG:
                    service.playSongAtImpl(msg.arg1);
                    break;

                case SET_POSITION:
                    service.openTrackAndPrepareNextAt(msg.arg1);
                    service.notifyChange(PLAY_STATE_CHANGED);
                    break;

                case PREPARE_NEXT:
                    service.prepareNextImpl();
                    break;

                case RESTORE_QUEUES:
                    service.restoreQueuesAndPositionIfNecessary();
                    break;

                case FOCUS_CHANGE:
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (!service.isPlaying() && service.pausedByTransientLossOfFocus) {
                                service.play();
                                service.pausedByTransientLossOfFocus = false;
                            }
                            removeMessages(DUCK);
                            sendEmptyMessage(UNDUCK);
                            break;

                        case AudioManager.AUDIOFOCUS_LOSS:
                            // Lost focus for an unbounded amount of time: stop playback and release media playback
                            service.pause();
                            break;

                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            // Lost focus for a short time, but we have to stop
                            // playback. We don't release the media playback because playback
                            // is likely to resume
                            boolean wasPlaying = service.isPlaying();
                            service.pause();
                            service.pausedByTransientLossOfFocus = wasPlaying;
                            break;

                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            // Lost focus for a short time, but it's ok to keep playing
                            // at an attenuated level
                            removeMessages(UNDUCK);
                            sendEmptyMessage(DUCK);
                            break;
                    }
                    break;
            }
        }
    }

    public class MusicBinder extends Binder {
        @NonNull
        public MusicService getService() {
            return MusicService.this;
        }
    }

    private final BroadcastReceiver widgetIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String command = intent.getStringExtra(EXTRA_APP_WIDGET_NAME);

            final int[] ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            switch (command) {
                case AppWidgetClassic.NAME: {
                    appWidgetClassic.performUpdate(MusicService.this, ids);
                    break;
                }
                case AppWidgetSmall.NAME: {
                    appWidgetSmall.performUpdate(MusicService.this, ids);
                    break;
                }
                case AppWidgetBig.NAME: {
                    appWidgetBig.performUpdate(MusicService.this, ids);
                    break;
                }
                case AppWidgetCard.NAME: {
                    appWidgetCard.performUpdate(MusicService.this, ids);
                    break;
                }
            }
        }
    };

    private class MediaStoreObserver extends ContentObserver implements Runnable {
        // milliseconds to delay before calling refresh to aggregate events
        private static final long REFRESH_DELAY = 500;
        private Handler mHandler;

        MediaStoreObserver(Handler handler) {
            super(handler);
            mHandler = handler;
        }

        @Override
        public void onChange(boolean selfChange) {
            // if a change is detected, remove any scheduled callback
            // then post a new one. This is intended to prevent closely
            // spaced events from generating multiple refresh calls
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, REFRESH_DELAY);
        }

        @Override
        public void run() {
            // actually call refresh when the delayed callback fires
            // do not send a sticky broadcast here
            handleAndSendChangeInternal(MEDIA_STORE_CHANGED);
        }
    }

    private class ThrottledSeekHandler implements Runnable {
        // milliseconds to throttle before calling run() to aggregate events
        private static final long THROTTLE = 500;
        private Handler mHandler;

        ThrottledSeekHandler(Handler handler) {
            mHandler = handler;
        }

        void notifySeek() {
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, THROTTLE);
        }

        @Override
        public void run() {
            savePositionInTrack();
            sendPublicIntent(PLAY_STATE_CHANGED); // for musixmatch synced lyrics
        }
    }

    private static class SongPlayCountHelper {
        public static final String TAG = SongPlayCountHelper.class.getSimpleName();

        private StopWatch stopWatch = new StopWatch();
        private Song song = Song.EMPTY_SONG;

        public Song getSong() {
            return song;
        }

        boolean shouldBumpPlayCount() {
            if (BuildConfig.DEBUG) {
                return true;
            }
            return song.duration * 0.3d < stopWatch.getElapsedTime();
        }

        void notifySongChanged(Song song) {
            synchronized (this) {
                stopWatch.reset();
                this.song = song;
            }
        }

        void notifyPlayStateChanged(boolean isPlaying) {
            synchronized (this) {
                if (isPlaying) {
                    stopWatch.start();
                } else {
                    stopWatch.pause();
                }
            }
        }
    }
}
